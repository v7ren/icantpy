package net.icantpy.dungeon.terminal

import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.gui.McUi
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import java.util.Locale

/**
 * Terminal practice anywhere (`/icantpy termsim [type]`), like Odin's terminal simulator. Opens a client-only chest
 * with a random terminal, answers clicks locally through [TerminalSimPuzzle], and opens the next one after each solve
 * until it is closed. Nothing is sent to the server: the solver panel routes simulator clicks here instead.
 *
 * Client thread only. The simulator always uses the solver panel, even for terminals switched off in its settings,
 * because the vanilla chest would send its clicks to the server.
 */
object TerminalSimulator {
    private const val COMMAND = "termsim"
    private const val PREVIEW_COMMAND = "termpreview"
    private const val FONTS_COMMAND = "termfonts"
    private const val NEXT_DELAY_TICKS = 6

    /** Ticks between melody pane moves. An approximation of Hypixel's pace, which also varies between runs. */
    private const val MELODY_STEP_TICKS = 4

    /** Far above the server's own window ids (1..100) so a late server packet never lands in a simulator menu. */
    private const val FIRST_CONTAINER_ID = 1_000
    private val mc: Minecraft get() = Minecraft.getInstance()

    private class Session(val types: List<TerminalType>) {
        var puzzle: TerminalSimPuzzle? = null
        var menu: ChestMenu? = null
        var container: SimpleContainer? = null
        var startedNs = 0L
        var nextInTicks = -1
        var solved = 0
        var totalMs = 0L
        var misclicks = 0
        var melodyTicks = 0
    }

    private var session: Session? = null

    /** Requested from chat; started on the next tick because the chat screen closes after the command runs. */
    private var pendingStart: List<TerminalType>? = null
    private var pendingPreview = false
    private var nextContainerId = FIRST_CONTAINER_ID

    private val TYPE_ALIASES: Map<String, TerminalType> = mapOf(
        "panes" to TerminalType.PANES,
        "order" to TerminalType.IN_ORDER,
        "numbers" to TerminalType.IN_ORDER,
        "startswith" to TerminalType.STARTS_WITH,
        "letter" to TerminalType.STARTS_WITH,
        "color" to TerminalType.SELECT_COLOR,
        "colour" to TerminalType.SELECT_COLOR,
        "select" to TerminalType.SELECT_COLOR,
        "rubix" to TerminalType.RUBIX,
        "cycle" to TerminalType.RUBIX,
        "melody" to TerminalType.MELODY,
        "timing" to TerminalType.MELODY,
    )

    /**
     * Handles `termsim [all|panes|order|startswith|color|rubix|melody]` and `termpreview` (the panel editor, reachable without
     * the Windows-only settings window); returns the chat reply, or null for other commands.
     */
    fun handleCommand(message: String): String? {
        val words = IcantpyCommandPrefix.body(message)?.lowercase(Locale.ROOT)?.split(Regex("\\s+")) ?: return null
        if (words.firstOrNull() == FONTS_COMMAND) {
            TerminalUserFonts.refresh()
            TerminalUserFonts.openFolder()
            val count = TerminalUserFonts.available().size
            return "§7[icantpy] Fonts folder opened ($count font${if (count == 1) "" else "s"}). Pick yours under Text → Font in /icantpy termpreview."
        }
        if (words.firstOrNull() == PREVIEW_COMMAND) {
            pendingPreview = true
            return ""
        }
        if (words.firstOrNull() != COMMAND) return null
        val arg = words.getOrNull(1) ?: "all"
        val types = when (arg) {
            "all", "random" -> TerminalType.entries.toList()
            else -> listOf(TYPE_ALIASES[arg] ?: return "§cUsage: /icantpy termsim [all|panes|order|startswith|color|rubix|melody]")
        }
        pendingStart = types
        return ""
    }

    fun start(types: List<TerminalType> = TerminalType.entries.toList()) {
        require(types.isNotEmpty()) { "types must not be empty" }
        session = Session(types)
        openNext()
    }

    fun owns(menu: AbstractContainerMenu): Boolean = session?.menu === menu

    /** Applies a click the solver panel would otherwise send to the server. */
    fun click(slot: Int, click: TerminalClick) {
        val current = session ?: return
        val puzzle = current.puzzle ?: return
        if (current.nextInTicks >= 0) return
        if (puzzle.click(slot, click.button) != SimClickResult.APPLIED) return
        sync(current, puzzle)
        if (puzzle.solved()) finish(current, puzzle)
    }

    fun tick() {
        if (pendingPreview) {
            pendingPreview = false
            McUi.setScreen(mc, net.icantpy.gui.config.TerminalPreviewScreen())
            return
        }
        pendingStart?.let { types ->
            pendingStart = null
            start(types)
            return
        }
        val current = session ?: return
        if (current.nextInTicks > 0) {
            current.nextInTicks--
            return
        }
        if (current.nextInTicks == 0) {
            openNext()
            return
        }
        if (McUi.currentScreen(mc) !is TerminalSimScreen) {
            stop()
            return
        }
        stepMelody(current)
    }

    private fun stepMelody(current: Session) {
        val puzzle = current.puzzle ?: return
        if (puzzle.type != TerminalType.MELODY || ++current.melodyTicks < MELODY_STEP_TICKS) return
        current.melodyTicks = 0
        puzzle.advance()
        sync(current, puzzle)
    }

    /** Ends the run, gives the menu back to the player and reports the totals. */
    fun stop() {
        val current = session ?: return
        session = null
        mc.player?.let { player -> player.containerMenu = player.inventoryMenu }
        if (McUi.currentScreen(mc) is TerminalSimScreen) McUi.setScreen(mc, null)
        if (current.solved > 0) {
            val average = current.totalMs / current.solved / 1000.0
            tell("§7[icantpy] Simulator closed: ${current.solved} solved, average ${seconds(average)}, ${current.misclicks} misclicks.")
        }
    }

    private fun openNext() {
        val current = session ?: return
        val player = mc.player ?: return stop()
        val puzzle = TerminalSimPuzzle.random(current.types.random())
        val container = SimpleContainer(puzzle.rows * 9)
        val menu = ChestMenu(menuType(puzzle.rows), nextContainerId++, player.inventory, container, puzzle.rows)
        current.puzzle = puzzle
        current.container = container
        current.menu = menu
        current.nextInTicks = -1
        sync(current, puzzle)
        player.containerMenu = menu
        McUi.setScreen(mc, TerminalSimScreen(menu, player.inventory, Component.literal(puzzle.title)))
        current.startedNs = System.nanoTime()
    }

    private fun finish(current: Session, puzzle: TerminalSimPuzzle) {
        val elapsedMs = (System.nanoTime() - current.startedNs) / 1_000_000
        current.solved++
        current.totalMs += elapsedMs
        current.misclicks += puzzle.misclicks
        val misclicks = if (puzzle.misclicks == 1) "1 misclick" else "${puzzle.misclicks} misclicks"
        tell("§a[icantpy] ${puzzle.type.displayName} solved in ${seconds(elapsedMs / 1000.0)} · $misclicks")
        current.nextInTicks = NEXT_DELAY_TICKS
    }

    private fun sync(current: Session, puzzle: TerminalSimPuzzle) {
        val container = current.container ?: return
        for (index in 0 until container.containerSize) {
            container.setItem(index, puzzle.slot(index)?.let(::stackOf) ?: ItemStack.EMPTY)
        }
        current.menu?.broadcastChanges()
    }

    private fun stackOf(slot: SimSlot): ItemStack {
        val item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(slot.itemId)).orElse(null)
            ?: return ItemStack.EMPTY
        val stack = ItemStack(item, slot.count)
        slot.customName?.let { stack.set(DataComponents.CUSTOM_NAME, Component.literal(it)) }
        if (slot.glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return stack
    }

    private fun menuType(rows: Int): MenuType<ChestMenu> = when (rows) {
        4 -> MenuType.GENERIC_9x4
        5 -> MenuType.GENERIC_9x5
        else -> MenuType.GENERIC_9x6
    }

    private fun seconds(value: Double): String = String.format(Locale.ROOT, "%.2fs", value)

    private fun tell(text: String) {
        mc.player?.sendSystemMessage(Component.literal(text))
    }
}
