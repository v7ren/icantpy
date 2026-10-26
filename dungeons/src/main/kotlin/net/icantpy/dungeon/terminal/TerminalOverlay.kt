package net.icantpy.dungeon.terminal

import net.icantpy.gui.McUi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * Replaces the terminal chest with the solver panel. The vanilla screen and its menu stay open underneath, so the
 * server's slot updates keep arriving; only drawing and mouse input are redirected through the loader's container
 * hooks. Every click is the player's own: the overlay sends at most one container click per mouse press.
 *
 * Client thread only. Session state (pending clicks, the locked colour target) ends when no terminal is open.
 */
object TerminalOverlay {
    private val LOGGER = LoggerFactory.getLogger("icantpy")
    private const val NO_CONTAINER = -1
    private const val LAST_RUBIX_SLOT = 32
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val pending = TerminalPendingClicks()
    private val animations = TerminalAnimations()
    private var sessionOpenedMs = 0L
    private var sessionType: TerminalType? = null
    private var sessionContainerId = NO_CONTAINER
    private var lockedRubixTarget: RubixColor? = null
    private var serverTicksOpened = 0

    /** Self-glinting items clicked this terminal: slot to the stack object that was there, so a new one means the server answered. */
    private val naturalClicks = HashMap<Int, Int>()
    private val confirmedNatural = HashSet<Int>()

    var settings: TerminalSettings = TerminalSettings()
        private set

    /** Reads config/icantpy-terminals.json. A failed read keeps defaults rather than blocking the payload load. */
    fun load() {
        settings = try {
            TerminalSettingsStore.load()
        } catch (error: java.io.IOException) {
            LOGGER.warn("Could not read terminal settings; using defaults", error)
            TerminalSettings()
        }
    }

    /** Applies and persists a settings change made from the config screen or the preview editor. */
    fun updateSettings(transform: (TerminalSettings) -> TerminalSettings) {
        val next = transform(settings)
        settings = next
        try {
            TerminalSettingsStore.save(TerminalSettingsStore.defaultPath(), next)
        } catch (error: java.io.IOException) {
            LOGGER.warn("Could not save terminal settings", error)
        }
    }

    /** One server tick, from the loader's tick callback; counts how long the open terminal has been up. */
    fun onServerTick() {
        if (sessionType != null) serverTicksOpened++
    }

    fun tick() {
        val screen = McUi.currentScreen(mc)
        if (screen == null || activeType(screen) == null) endSession()
    }

    fun reset() {
        endSession()
    }

    /** True while [screen] is the open terminal chest and the solver UI should replace it. */
    fun overlayReady(screen: Screen): Boolean = activeType(screen) != null

    fun render(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
        val type = activeType(screen) ?: return false
        val title = screen.title.string
        val solution = solve(screen, type)
        val layout = TerminalRenderer.layout(screen.width, screen.height, solution, screen.title.string, settings)
        animations.begin(screen.menu.containerId)
        TerminalRenderer.render(
            graphics, solution, layout, settings, title, screen.width, screen.height, mouseX, mouseY, animations,
            stackAt = { slot -> stackAt(screen, slot) },
            canClick = { cell -> TerminalClickPolicy.allows(cell, type, settings) },
        )
        return true
    }

    /** Handles a mouse press on the terminal; any press is consumed so vanilla never acts on the hidden chest. */
    fun mouseClicked(screen: AbstractContainerScreen<*>, event: MouseButtonEvent): Boolean? {
        val type = activeType(screen) ?: return null
        val solution = solve(screen, type)
        // First-click protection, as in Odin: a click already on its way when the terminal opens is dropped.
        if (System.currentTimeMillis() - sessionOpenedMs < settings.firstClickDelayMs) return true
        if (settings.lagProtection && serverTicksOpened < settings.lagProtectionTicks) return true
        val layout = TerminalRenderer.layout(screen.width, screen.height, solution, screen.title.string, settings)
        val slot = layout.slotAt(event.x().toInt(), event.y().toInt(), solution.cells.map { it.slot }) ?: return true
        val cell = solution.cellAt(slot) ?: return true
        if (!TerminalClickPolicy.allows(cell, type, settings)) return true
        if (!TerminalClickPolicy.allowsButton(cell, type, settings, event.button())) return true
        val click = TerminalClickPolicy.clickFor(type, settings, event.button()) ?: return true
        val target = TerminalClickPolicy.slotFor(cell, solution) ?: return true
        if (TerminalSimulator.owns(screen.menu)) {
            TerminalSimulator.click(target, click)
            return true
        }
        McUi.clickContainerSlot(mc, screen.menu.containerId, target, click.button, click.clone)
        if (cell.item.naturalGlint && (type == TerminalType.STARTS_WITH || type == TerminalType.SELECT_COLOR)) {
            naturalClicks.putIfAbsent(cell.slot, cell.item.stackId)
        }
        if (settings.predictClicks && type != TerminalType.MELODY) pending.record(cell.item, click.steps, System.currentTimeMillis())
        return true
    }

    /** Releases are swallowed too: vanilla's drag-to-split logic must not run on the hidden slots. */
    fun mouseReleased(screen: AbstractContainerScreen<*>): Boolean? = if (activeType(screen) != null) true else null

    private fun activeType(screen: Screen): TerminalType? {
        if (McUi.currentScreen(mc) !== screen) return null
        val chest = screen as? AbstractContainerScreen<*> ?: return null
        val type = TerminalType.fromTitle(chest.title.string) ?: return null
        if (TerminalSimulator.owns(chest.menu)) return type
        return type.takeIf(settings::takesOver)
    }

    private fun solve(screen: AbstractContainerScreen<*>, type: TerminalType): TerminalSolution {
        // A new window is a new terminal: pending clicks and the colour target belong to the old one.
        if (sessionType != type || sessionContainerId != screen.menu.containerId) {
            // Hypixel can swap the window mid-terminal; only a fresh open restarts the first-click delay.
            val freshOpen = sessionType != type
            endSession()
            sessionType = type
            sessionContainerId = screen.menu.containerId
            if (freshOpen) {
                sessionOpenedMs = System.currentTimeMillis()
                serverTicksOpened = 0
            }
        }
        val snapshot = TerminalSnapshotReader.read(screen.menu, screen.title.string)
        pending.timeoutMs = settings.resolveTimeoutMs.toLong()
        pending.prune(snapshot, System.currentTimeMillis())
        for (item in snapshot.items) {
            val clickedStack = naturalClicks[item.slot] ?: continue
            if (item.stackId != clickedStack) confirmedNatural += item.slot
        }
        val solution = TerminalSolver.solve(type, snapshot, pending.asMap(), lockedRubixTarget, confirmedNatural)
        // The colour target is chosen once per terminal so it does not flip while the panes are half turned. Like
        // Odin it waits for the last pane (slot 32), so a half-filled window cannot fix the wrong target.
        if (type == TerminalType.RUBIX && lockedRubixTarget == null && solution.cells.any { it.slot == LAST_RUBIX_SLOT }) {
            lockedRubixTarget = solution.rubixTarget
        }
        return solution
    }

    private fun stackAt(screen: AbstractContainerScreen<*>, slot: Int): ItemStack =
        screen.menu.slots.getOrNull(slot)?.item ?: ItemStack.EMPTY

    private fun endSession() {
        sessionType = null
        sessionContainerId = NO_CONTAINER
        lockedRubixTarget = null
        pending.clear()
        naturalClicks.clear()
        confirmedNatural.clear()
    }
}
