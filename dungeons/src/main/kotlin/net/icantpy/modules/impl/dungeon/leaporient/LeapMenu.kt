package net.icantpy.modules.impl.dungeon.leaporient

import net.icantpy.gui.McUi
import net.icantpy.modules.impl.timer.TickTimers
import net.icantpy.util.Text
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Items.PLAYER_HEAD
import org.lwjgl.glfw.GLFW

object LeapMenu {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val playerNameRegex = Regex("^(?:\\[.+?] )?(\\w{1,16})$")
    private val hover = FloatArray(5)
    private var lastTickMs = System.currentTimeMillis()
    private var overlayDrawn = false

    fun settings(): LeapOrientSettings = TickTimers.settings.leap

    fun tick() {
        val screen = McUi.currentScreen(mc)
        if (screen is LeapMenuScreen) return
        if (screen != null && overlayReady(screen)) return
        dropOverlay()
    }

    fun isLeapTitle(title: String): Boolean {
        val raw = title.trim()
        val text = Text.strip(raw)
        if (text.equals("Spirit Leap", ignoreCase = true)) return true
        if (text.equals("Teleport to Player", ignoreCase = true)) return true
        return raw.contains("spirit leap", ignoreCase = true) ||
            text.contains("spirit leap", ignoreCase = true)
    }

    fun shouldTakeOver(): Boolean = settings().enabled && settings().menuEnabled

    fun shouldTakeOver(title: String): Boolean = shouldTakeOver() && isLeapTitle(title)

    fun openLive(menu: AbstractContainerMenu, title: Component) {
        McUi.setScreen(mc, LeapMenuScreen.live(menu, title))
    }

    fun shouldOverlay(screen: Screen): Boolean {
        if (!shouldTakeOver()) return false
        val chest = screen as? AbstractContainerScreen<*> ?: return false
        return isLeapTitle(chest.title.string)
    }

    fun overlayReady(screen: Screen): Boolean {
        if (McUi.currentScreen(mc) !== screen) return false
        val chest = screen as? AbstractContainerScreen<*> ?: return false
        if (!shouldOverlay(chest)) return false
        return hasTeammates(teammatesFor(chest))
    }

    private fun hasTeammates(list: List<LeapPlayer>): Boolean =
        list.any { it.clazz.isReal() && it.name.isNotBlank() && !it.name.equals("Empty", ignoreCase = true) }

    fun renderOverlay(
        screen: AbstractContainerScreen<*>,
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ): Boolean {
        if (McUi.currentScreen(mc) !== screen) {
            dropOverlay()
            return false
        }
        if (!overlayReady(screen)) {
            dropOverlay()
            return false
        }
        overlayDrawn = true
        render(graphics, teammatesFor(screen), screen.width, screen.height, mouseX, mouseY)
        return true
    }

    fun dropOverlay() {
        if (!overlayDrawn) return
        overlayDrawn = false
        LeapMenuRenderer.release()
    }

    fun render(
        graphics: GuiGraphicsExtractor,
        teammates: List<LeapPlayer>,
        screenW: Int,
        screenH: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        overlayDrawn = true
        val cfg = settings()
        val center = centerPlayer(teammates)
        lastTickMs = LeapMenuRenderer.tickHover(
            hover,
            screenW,
            screenH,
            mouseX,
            mouseY,
            lastTickMs,
            center != null,
            cfg.scale,
        )
        val remaining = LeapOrientTargets.pick(LeapGameState.currentId(), cfg)?.let { pending ->
            val seconds = LeapOrientTargets.remainingMs(pending) / 1000.0
            "${pending.centerTitle()} · ${pending.centerReason()} ${"%.1f".format(seconds)}s"
        }
        LeapMenuRenderer.render(
            graphics,
            teammates,
            screenW,
            screenH,
            mouseX,
            mouseY,
            hover,
            cfg.scale,
            cfg.colorStyle,
            cfg.onlyClass,
            center,
            remaining,
        )
    }

    fun mouseClicked(screen: AbstractContainerScreen<*>, event: MouseButtonEvent, doubleClick: Boolean): Boolean? {
        if (!overlayReady(screen)) return null
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true
        if (settings().onRelease) return true
        clickAt(screen, event.x().toInt(), event.y().toInt())
        return true
    }

    fun mouseReleased(screen: AbstractContainerScreen<*>, event: MouseButtonEvent): Boolean? {
        if (!overlayReady(screen)) return null
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true
        if (!settings().onRelease) return true
        clickAt(screen, event.x().toInt(), event.y().toInt())
        return true
    }

    fun keyPressed(screen: AbstractContainerScreen<*>, event: KeyEvent): Boolean? {
        if (!overlayReady(screen)) return null
        val cfg = settings()
        val teammates = teammatesFor(screen.menu)
        val index = keyIndex(event.key(), cfg, teammates) ?: return null
        val player = teammates.getOrNull(index) ?: return true
        leapTo(player, screen.menu)
        return true
    }

    fun keyPressed(menu: AbstractContainerMenu, event: KeyEvent): Boolean? {
        val cfg = settings()
        val teammates = teammatesFor(menu)
        val index = keyIndex(event.key(), cfg, teammates) ?: return null
        val player = teammates.getOrNull(index) ?: return true
        leapTo(player, menu)
        return true
    }

    fun clickAt(screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int) {
        clickAt(screen.menu, screen.width, screen.height, mouseX, mouseY)
    }

    fun clickAt(menu: AbstractContainerMenu, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
        val teammates = teammatesFor(menu)
        val player = playerAt(teammates, screenW, screenH, mouseX, mouseY) ?: return
        leapTo(player, menu)
    }

    fun clickDebug(teammates: List<LeapPlayer>, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
        val player = playerAt(teammates, screenW, screenH, mouseX, mouseY) ?: return
        leapTo(player, null)
    }

    fun teammatesFor(screen: AbstractContainerScreen<*>): List<LeapPlayer> = teammatesFor(screen.menu)

    fun teammatesFor(menu: AbstractContainerMenu): List<LeapPlayer> {
        val cfg = settings()
        val odin = OdinLeapBridge.readLeapPlayers()
        val fromChest = readPlayersFromChest(menu)
        val merged = if (odin.any { it.clazz.isReal() }) odin else fromChest
        return LeapMenuSort.sort(
            merged.filter { it.name.isNotBlank() && !it.name.equals(mc.player?.name?.string, ignoreCase = true) },
            cfg.sortMode,
        )
    }

    fun centerPlayer(teammates: List<LeapPlayer>): LeapPlayer? {
        val cfg = settings()
        if (!cfg.orientEnabled) return null
        val pending = LeapOrientTargets.pick(LeapGameState.currentId(), cfg) ?: return null
        val roster = teammates + LeapRoster.knownPlayers()
        val player = LeapRoster.resolveTarget(pending, roster, cfg) ?: return null
        if (LeapRoster.isSelf(player.name)) return null
        return player
    }

    fun leapTo(player: LeapPlayer, menu: AbstractContainerMenu?) {
        if (player.name.isBlank()) return
        if (player.isDead) {
            tell("§cThis player is dead, can't leap.")
            return
        }
        if (menu != null) {
            val index = slotIndex(menu, player) ?: return
            McUi.clickContainerSlot(mc, menu.containerId, index)
            tell("§aTeleporting to ${player.name}.")
            LeapOrientTargets.clear()
            LeapOrientState.clear()
        } else {
            LeapOrient.onDebugLeap(player.name)
        }
    }

    private fun playerAt(
        teammates: List<LeapPlayer>,
        screenW: Int,
        screenH: Int,
        mouseX: Int,
        mouseY: Int,
    ): LeapPlayer? {
        val cfg = settings()
        val center = centerPlayer(teammates)
        if (center != null &&
            LeapOrientSpots.centerHit(
                screenW,
                screenH,
                mouseX,
                mouseY,
                cfg.scale,
                LeapMenuRenderer.CENTER_WIDTH,
                LeapMenuRenderer.CENTER_HEIGHT,
            )
        ) {
            return center
        }
        val quadrant = LeapOrientSpots.cursorQuadrant(screenW, screenH, mouseX, mouseY)
        return teammates.getOrNull(quadrant)
    }

    fun debugKeyIndex(key: Int, teammates: List<LeapPlayer>): Int? =
        keyIndex(key, settings(), teammates)

    private fun keyIndex(key: Int, cfg: LeapOrientSettings, teammates: List<LeapPlayer>): Int? {
        if (key == LeapOrientSettings.UNBOUND) return null
        if (cfg.keybindMode == LeapKeybindMode.CORNERS) {
            val keys = listOf(cfg.topLeftKey, cfg.topRightKey, cfg.bottomLeftKey, cfg.bottomRightKey)
            val index = keys.indexOf(key)
            return index.takeIf { it >= 0 }
        }
        val keys = listOf(cfg.archerKey, cfg.berserkKey, cfg.healerKey, cfg.mageKey, cfg.tankKey)
        val classIndex = keys.indexOf(key)
        if (classIndex < 0) return null
        val clazz = LeapDungeonClass.entries.getOrNull(classIndex) ?: return null
        val index = teammates.indexOfFirst { it.clazz == clazz }
        return index.takeIf { it >= 0 }
    }

    private fun slotIndex(menu: AbstractContainerMenu, player: LeapPlayer): Int? {
        slotIndex(menu, player.name)?.let { return it }
        if (!player.clazz.isReal()) return null
        val match = readPlayersFromChest(menu).firstOrNull { it.clazz == player.clazz } ?: return null
        return slotIndex(menu, match.name)
    }

    private fun slotIndex(menu: AbstractContainerMenu, name: String): Int? =
        leapHeadSlots(menu).firstOrNull { slot ->
            val slotName = headName(slot.item.hoverName.string) ?: return@firstOrNull false
            slotName.equals(name, ignoreCase = true)
        }?.index

    fun readPlayersFromChest(menu: AbstractContainerMenu): List<LeapPlayer> {
        val odin = OdinLeapBridge.readLeapPlayers()
        return leapHeadSlots(menu).mapNotNull { slot ->
            val name = headName(slot.item.hoverName.string) ?: return@mapNotNull null
            odin.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: LeapPlayer(name, LeapDungeonClass.MAGE)
        }
    }

    private fun leapHeadSlots(menu: AbstractContainerMenu) =
        odinLeapSlots(menu).ifEmpty { containerHeadSlots(menu) }

    private fun odinLeapSlots(menu: AbstractContainerMenu) =
        if (menu.slots.size < 16) emptyList()
        else menu.slots.subList(11, 16).filter { slot ->
            val stack = slot.item
            !stack.isEmpty && stack.item == PLAYER_HEAD && headName(stack.hoverName.string) != null
        }

    private fun containerHeadSlots(menu: AbstractContainerMenu): List<Slot> {
        val containerEnd = (menu.slots.size - 36).coerceAtLeast(0).coerceAtMost(menu.slots.size)
        if (containerEnd <= 0) return emptyList()
        return menu.slots.subList(0, containerEnd).filter { slot ->
            val stack = slot.item
            !stack.isEmpty && stack.item == PLAYER_HEAD && headName(stack.hoverName.string) != null
        }
    }

    private fun headName(raw: String): String? {
        val text = Text.strip(raw)
        return playerNameRegex.find(text)?.groupValues?.get(1)
            ?: text.substringAfter(' ').takeIf { it.matches(Regex("\\w{1,16}")) }
    }

    private fun tell(text: String) {
        mc.player?.sendSystemMessage(Component.literal(text))
    }
}
