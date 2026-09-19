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
        if (!cfg.enabled || !dungeon.inDungeon || McUi.currentScreen(mc) != null) return false
        val player = mc.player ?: return false
        if (mc.level == null || !player.isAlive || player.isRemoved) return false
        val item = player.mainHandItem
        if (!isSpiritLeap(item)) return false
        if (pending != null) return true
        if (LeapAutoLeapPolicy.doorOpenerClick(cfg, dungeon.inDungeon, inBossRoom(player))) {
            val name = LeapAutoLeapDoorOpener.latestName() ?: return openManualMenu()
            if (LeapRoster.isSelf(name)) return openManualMenu()
            val playerInfo = LeapRoster.knownPlayers().firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (playerInfo?.isDead == true) return openManualMenu()
            return requestTarget(name, doorOpener = true)
        }
        if (!orientEnabledInScope()) return false
        val target = LeapMenu.centerPlayer(LeapRoster.knownPlayers()) ?: return openManualMenu()
        if (target.isDead || LeapRoster.isSelf(target.name)) return openManualMenu()
        return requestTarget(target.name, doorOpener = false)
    }

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
            DungeonListener.state.inDungeon,
            player != null && inBossRoom(player),
        )
    }

    private fun orientEnabledInScope(): Boolean {
        val cfg = LeapMenu.settings()
        val state = DungeonListener.state
        return cfg.enabled && cfg.orientEnabled && cfg.autoLeapEnabled && state.inDungeon &&
            (!cfg.autoLeapBossOnly || state.inBoss)
    }

    private fun scopeId(doorOpener: Boolean): String =
        if (doorOpener) "door:${DungeonListener.state.inDungeon}"
        else "orient:${DungeonListener.state.inBoss}:${LeapGameState.currentId()}"

    private fun inBossRoom(player: net.minecraft.world.entity.player.Player): Boolean =
        BossRooms.contains(DungeonListener.floor, player.x, player.z)

    private fun validatedMenu(screen: Screen?): AbstractContainerMenu? {
        val menu = when (screen) {
            is LeapMenuScreen -> screen.liveContainerMenu()
            is AbstractContainerScreen<*> -> screen.menu
            else -> null
        } ?: return null
        val title = Text.strip(screen?.title?.string ?: return null).trim()
        if (!title.equals("Spirit Leap", ignoreCase = true) &&
            !title.equals("Teleport to Player", ignoreCase = true)
        ) return null
        return menu.takeIf { mc.player?.containerMenu === it }
    }

    private fun isSpiritLeap(stack: ItemStack): Boolean {
        val id = HypixelItemData.skyblockId(stack)
        if (id != null) return id.equals("SPIRIT_LEAP", ignoreCase = true)
        return HypixelItemData.matchName(stack)?.equals("spirit leap", ignoreCase = true) == true
    }
}
