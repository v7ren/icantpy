package net.icantpy.dungeon.leap

import net.icantpy.cosmetics.items.HypixelItemData
import net.icantpy.dungeon.BossRooms
import net.icantpy.dungeon.DungeonListener
import net.icantpy.gui.McUi
import net.icantpy.util.Text
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

/** Payload-owned work; existing attack queries and tick forwarding survive payload reloads. */
object LeapAutoLeapController {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private var pending: PendingAutoLeap? = null
    private var requestedItem: ItemStack = ItemStack.EMPTY
    private var requestedHotbarSlot: Int = -1
    private var openedScreen: Screen? = null
    private var openedMenu: AbstractContainerMenu? = null
    private var requestedDoorOpener: Boolean = false
    private var attackInput = LeapAutoLeapInputState()

    /**
     * Older loaders forward ticks without precise attack hooks. Sub-tick clicks can be missed,
     * and a vanilla attack may already have happened before this fallback sees the held key.
     */
    fun pollAttack() {
        val edge = attackInput.poll(mc.options.keyAttack.isDown)
        attackInput = edge.state
        if (edge.pressed) onAttack()
    }

    /** Returns true only when this left click has been handled. */
    fun onAttack(): Boolean {
        val handled = handleAttack()
        if (handled) attackInput = attackInput.handled()
        return handled
    }

    private fun handleAttack(): Boolean {
        val cfg = LeapMenu.settings()
        val dungeon = DungeonListener.state
        val player = mc.player ?: return false
        if (mc.level == null || !player.isAlive || player.isRemoved) return false
        val item = player.mainHandItem
        if (!isSpiritLeap(item)) {
            if (LeapOrient.debugActive()) {
                val id = HypixelItemData.skyblockId(item)
                LeapOrient.noteAutoLeap("left click seen, but the held item is not a Spirit Leap (${item.hoverName.string}, id=${id ?: "none"}, debug item ok=${localTest()})")
            }
            return false
        }
        if (!cfg.enabled) return noted("blocked: Spirit Leap is disabled")
        if (!inDungeon()) return noted("blocked: not in a dungeon")
        if (McUi.currentScreen(mc) != null) return noted("blocked: a screen is open")
        if (pending != null) return true
        if (LeapAutoLeapPolicy.doorOpenerClick(cfg, inDungeon(), inBossRoom(player))) {
            val name = LeapAutoLeapDoorOpener.latestName()
            val playerInfo = name?.let { n -> LeapRoster.knownPlayers().firstOrNull { it.name.equals(n, ignoreCase = true) } }
            when {
                name == null -> LeapOrient.noteAutoLeap("door opener mode: no door opener known yet, trying the center target")
                LeapRoster.isSelf(name) -> LeapOrient.noteAutoLeap("door opener mode: you opened the door, trying the center target")
                playerInfo?.isDead == true -> LeapOrient.noteAutoLeap("door opener mode: $name is dead, trying the center target")
                else -> {
                    LeapOrient.flow("left click: door opener mode, leaping to $name")
                    return requestTarget(name, doorOpener = true)
                }
            }
        }
        if (!orientEnabledInScope()) return ignored(scopeBlock())
        // Left click only leaps to the oriented center target. With none, it does nothing; right-click opens the menu.
        val target = LeapMenu.centerPlayer(LeapRoster.knownPlayers(), liveOnly = true)
        if (target == null) {
            return ignored("no center target is leap-oriented, so there is nothing to leap to")
        }
        if (target.isDead || LeapRoster.isSelf(target.name)) {
            return ignored("center target ${target.name} is dead or you")
        }
        LeapOrient.flow("left click: opening Spirit Leap and clicking the center target ${target.name} (${target.clazz.displayName}) after ${LeapMenu.settings().autoLeapDelayMs}ms")
        LeapOrient.noteAutoLeap("leaping to ${target.name}")
        return requestTarget(target.name, doorOpener = false)
    }

    /**
     * A left click the mod will not turn into a leap. While left-click leap is on, the click is swallowed too: the
     * server opens the Spirit Leap menu from the click itself, and that menu must not pop up unless a leap was armed.
     */
    private fun ignored(reason: String): Boolean {
        LeapOrient.noteAutoLeap("ignored: $reason")
        val cfg = LeapMenu.settings()
        return cfg.autoLeapEnabled || cfg.doorOpenerLeapEnabled
    }

    private fun noted(reason: String): Boolean {
        LeapOrient.noteAutoLeap(reason)
        return false
    }

    /** A singleplayer test world with leaporient debug on counts as a dungeon, so the click path can be tried there. */
    private fun localTest(): Boolean = LeapOrient.debugActive() && mc.hasSingleplayerServer()

    private fun inDungeon(): Boolean = DungeonListener.state.inDungeon || localTest()

    private fun openManualMenu(): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        gameMode.useItem(player, InteractionHand.MAIN_HAND)
        return true
    }

    private fun requestTarget(name: String, doorOpener: Boolean): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val request = LeapAutoLeapState.request(
            name,
            scopeId(doorOpener),
            LeapMenu.settings().autoLeapDelayMs.toLong(),
            System.currentTimeMillis(),
        ) ?: return false
        requestedItem = player.mainHandItem.copy()
        requestedHotbarSlot = player.inventory.selectedSlot
        openedScreen = null
        openedMenu = null
        requestedDoorOpener = doorOpener
        pending = request
        // Sends the normal use-item packet. No new resident hook or reflective Minecraft access.
        gameMode.useItem(player, InteractionHand.MAIN_HAND)
        return true
    }

    fun tick() {
        val request = pending ?: return
        val player = mc.player
        val screen = McUi.currentScreen(mc)
        if (localTest() && screen is LeapMenuScreen && screen.liveContainerMenu() == null) {
            // The debug menu has no container to click, so the leap goes through the debug leap path after the delay.
            val waited = System.currentTimeMillis() - request.requestedAtMs
            if (waited >= request.delayMs) {
                val clazz = LeapRoster.classOf(request.targetName, LeapMenu.settings())
                LeapMenu.leapTo(LeapPlayer(request.targetName, clazz), null)
                McUi.setScreen(mc, null)
                LeapOrientTargets.clear()
                LeapOrientState.clear()
                clearRequest()
            }
            return
        }
        val menu = validatedMenu(screen)
        val sameScreen = openedScreen == null || openedScreen === screen
        val sameMenu = openedMenu == null || openedMenu === menu
        val targetSlot = menu?.let { LeapMenu.validatedSlotIndex(it, request.targetName) }
        val frame = LeapAutoLeapFrame(
            nowMs = System.currentTimeMillis(),
            connected = player != null && mc.level != null && player.isAlive && !player.isRemoved,
            enabledInScope = enabledInScope(),
            itemUnchanged = player != null && player.inventory.selectedSlot == requestedHotbarSlot &&
                ItemStack.isSameItemSameComponents(player.mainHandItem, requestedItem),
            scopeId = scopeId(requestedDoorOpener),
            screenId = screen?.let(System::identityHashCode),
            menuId = menu?.let(System::identityHashCode),
            validatedLeapMenu = menu != null && sameScreen && sameMenu,
            namedTargetSlot = targetSlot,
        )
        val result = LeapAutoLeapState.advance(request, frame)
        narrate(request, frame, result, screen)
        pending = result.pending
        if (result.pending?.openedAtMs != null && openedScreen == null) {
            openedScreen = screen
            openedMenu = menu
        }
        val slot = result.clickSlot
        if (slot != null && menu != null) {
            // Revalidate identity and name at the action boundary; a wrong-class fallback is unsafe.
            if (player?.containerMenu === menu && LeapMenu.validatedSlotIndex(menu, request.targetName) == slot) {
                McUi.clickContainerSlot(mc, menu.containerId, slot)
                LeapOrientTargets.clear()
                LeapOrientState.clear()
            }
        }
        if (pending == null) clearRequest()
    }

    private var lastNarration = ""

    /** Debug narration of where an auto leap is, and why it was dropped when it ends without a click. */
    private fun narrate(request: PendingAutoLeap, frame: LeapAutoLeapFrame, result: LeapAutoLeapStep, screen: Screen?) {
        if (!LeapOrient.debugActive()) return
        val shown = "${screen?.javaClass?.simpleName ?: "no screen"} \"${screen?.title?.string.orEmpty()}\""
        val text = when {
            result.clickSlot != null -> "auto leap: clicking ${request.targetName} (slot ${result.clickSlot})"
            result.pending == null -> "auto leap to ${request.targetName} DROPPED: ${dropReason(request, frame, shown)}"
            else -> "auto leap waiting: $shown, menu validated=${frame.validatedLeapMenu}, head ${request.targetName} slot=${frame.namedTargetSlot}"
        }
        if (text == lastNarration) return
        lastNarration = text
        LeapOrient.flow(text)
    }

    private fun dropReason(request: PendingAutoLeap, frame: LeapAutoLeapFrame, shown: String): String = when {
        !frame.connected -> "not connected"
        !frame.enabledInScope -> "auto leap is no longer enabled for this scope (boss-only, orient or dungeon check)"
        !frame.itemUnchanged -> "the held item or hotbar slot changed"
        request.scopeId != frame.scopeId -> "scope changed (${request.scopeId} -> ${frame.scopeId})"
        request.openedAtMs == null && frame.nowMs - request.requestedAtMs >= LeapAutoLeapState.OPEN_TIMEOUT_MS ->
            "no Spirit Leap menu opened within ${LeapAutoLeapState.OPEN_TIMEOUT_MS}ms (screen: $shown)"
        request.openedAtMs == null -> "the open screen is not a Spirit Leap menu the mod can click ($shown)"
        frame.screenId != request.screenId || frame.menuId != request.menuId -> "the menu screen changed after it opened ($shown)"
        else -> "head ${request.targetName} never appeared in the menu within ${request.delayMs + LeapAutoLeapState.MENU_TIMEOUT_MS}ms"
    }

    fun clear() {
        clearRequest()
        attackInput = LeapAutoLeapInputState()
    }

    private fun clearRequest() {
        pending = null
        requestedItem = ItemStack.EMPTY
        requestedHotbarSlot = -1
        openedScreen = null
        openedMenu = null
        requestedDoorOpener = false
    }

    private fun enabledInScope(): Boolean {
        if (!requestedDoorOpener) return orientEnabledInScope()
        val cfg = LeapMenu.settings()
        val player = mc.player
        return LeapAutoLeapPolicy.doorOpenerClick(
            cfg,
            inDungeon(),
            player != null && inBossRoom(player),
        )
    }

    private fun orientEnabledInScope(): Boolean {
        val cfg = LeapMenu.settings()
        val state = DungeonListener.state
        return cfg.enabled && cfg.orientEnabled && cfg.autoLeapEnabled && inDungeon() &&
            (!cfg.autoLeapBossOnly || inBossFight() || localTest())
    }

    private fun scopeBlock(): String {
        val cfg = LeapMenu.settings()
        return when {
            !cfg.enabled -> "blocked: Spirit Leap is disabled"
            !cfg.orientEnabled -> "blocked: orient is off"
            !cfg.autoLeapEnabled -> "blocked: Left-click auto leap is off"
            cfg.autoLeapBossOnly && !inBossFight() && !localTest() -> "blocked: auto leap is boss-only and no boss fight detected"
            !inDungeon() -> "blocked: not in a dungeon"
            else -> "blocked: scope changed"
        }
    }

    private fun scopeId(doorOpener: Boolean): String =
        if (doorOpener) "door:${DungeonListener.state.inDungeon}"
        else "orient:${inBossFight()}:${LeapGameState.currentId()}"

    /** The boss fight flag, or standing inside the boss room, so one missed detection does not block left click. */
    private fun inBossFight(): Boolean =
        DungeonListener.state.inBoss || mc.player?.let { inBossRoom(it) } == true

    private fun inBossRoom(player: net.minecraft.world.entity.player.Player): Boolean =
        BossRooms.contains(DungeonListener.floor, player.x, player.z)

    private fun validatedMenu(screen: Screen?): AbstractContainerMenu? {
        val menu = when (screen) {
            is LeapMenuScreen -> screen.liveContainerMenu()
            is AbstractContainerScreen<*> -> screen.menu
            // Another mod's own Spirit Leap screen: the real container is still the player's open menu.
            else -> mc.player?.containerMenu?.takeIf { it !== mc.player?.inventoryMenu && LeapMenu.isLeapTitle(screen?.title?.string.orEmpty()) }
        } ?: return null
        val title = Text.strip(screen?.title?.string ?: return null).trim()
        if (!title.equals("Spirit Leap", ignoreCase = true) &&
            !title.equals("Teleport to Player", ignoreCase = true)
        ) return null
        return menu.takeIf { mc.player?.containerMenu === it }
    }

    private fun isSpiritLeap(stack: ItemStack): Boolean {
        if (localTest() && LeapOrient.isDebugLeapItem(stack)) return true
        val id = HypixelItemData.skyblockId(stack)
        if (id != null) return id.equals("SPIRIT_LEAP", ignoreCase = true) || id.equals("INFINITE_SPIRIT_LEAP", ignoreCase = true)
        val name = HypixelItemData.matchName(stack) ?: return false
        return name.equals("spirit leap", ignoreCase = true) || name.equals("infinileap", ignoreCase = true)
    }
}
