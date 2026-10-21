package net.icantpy.qol.auto

import net.icantpy.gui.McUi
import net.icantpy.qol.invsort.InvSortItems
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu

/**
 * Minecraft-facing cursor safety shared by the inventory sort and the command menu runs: state snapshots,
 * sync timing, recovery slots and the one close path that refuses to close while an item is held.
 */
object CursorSafety {
    /** Menu state id, read by reflection so a changed name degrades to "unknown" instead of failing. */
    private val stateIdGetter = runCatching { AbstractContainerMenu::class.java.getMethod("getStateId") }.getOrNull()

    fun snapshot(menu: AbstractContainerMenu, watched: Set<Int>): CursorGuard.Snapshot = CursorGuard.Snapshot(
        carried = InvSortItems.keyOf(menu.carried),
        slotKeys = watched.associateWith { InvSortItems.keyAt(menu, it) },
        stateId = stateIdOf(menu),
    )

    fun arm(
        mc: Minecraft,
        expectation: CursorGuard.Expectation,
        nowMs: Long,
        settleTicks: Int = CursorGuard.SETTLE_TICKS,
    ): CursorGuard.Sync = CursorGuard.Sync(expectation, nowMs, CursorGuard.timeoutMs(pingMs(mc)), settleTicks)

    /** Player main inventory and hotbar of a container menu, without its last slot (the SkyBlock star). */
    fun containerPlayerSlots(menu: AbstractContainerMenu): List<Int> {
        val first = playerSlotStart(menu)
        return (first until (menu.slots.size - 1).coerceAtLeast(first)).toList()
    }

    /** Index of the first player inventory slot; every slot before it belongs to the container. */
    fun playerSlotStart(menu: AbstractContainerMenu): Int = (menu.slots.size - PLAYER_SLOT_COUNT).coerceAtLeast(0)

    /** Keys of the items in the container's own slots (not the player inventory). */
    fun containerKeys(menu: AbstractContainerMenu): List<String> =
        (0 until playerSlotStart(menu)).mapNotNull { InvSortItems.keyAt(menu, it) }

    /**
     * Closes the open menu when the item held is empty or one of [ghostKeys] (see [CursorGuard.isGhost]).
     * Refuses for a real item on the cursor, as [safeClose] does.
     */
    fun safeCloseMenu(mc: Minecraft, ghostKeys: Set<String>): Boolean {
        val player = mc.player ?: return false
        if (!CursorGuard.mayCloseOverGhost(InvSortItems.keyOf(player.containerMenu.carried), ghostKeys)) {
            AutoActionQueue.say("Not closed: an item is still on your cursor. Place it in a slot first.")
            return false
        }
        val screen = McUi.currentScreen(mc)
        if (screen is AbstractContainerScreen<*>) screen.onClose() else player.closeContainer()
        return true
    }

    /**
     * Closes the open screen like vanilla (Esc / inventory key). Refuses while an item is on the cursor,
     * leaves the screen open and says so. Returns true when the close was sent.
     */
    fun safeClose(mc: Minecraft): Boolean {
        val player = mc.player ?: return false
        if (!CursorGuard.mayClose(InvSortItems.keyOf(player.containerMenu.carried))) {
            AutoActionQueue.say("Not closed: an item is still on your cursor. Place it in a slot first.")
            return false
        }
        val screen = McUi.currentScreen(mc)
        if (screen is AbstractContainerScreen<*>) screen.onClose() else player.closeContainer()
        return true
    }

    private fun pingMs(mc: Minecraft): Int? {
        val player = mc.player ?: return null
        return mc.connection?.getPlayerInfo(player.uuid)?.latency
    }

    private fun stateIdOf(menu: AbstractContainerMenu): Int? =
        runCatching { stateIdGetter?.invoke(menu) as? Int }.getOrNull()

    private const val PLAYER_SLOT_COUNT = 36
}
