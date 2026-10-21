package net.icantpy.qol.invsort

import net.icantpy.gui.McUi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen

/** Opens the player inventory the way vanilla's inventory key does, so a preset run can start on it. */
object InvSortOpener {
    /** Returns false (after telling the player why, when useful) when no inventory could be opened. */
    fun openPlayerInventory(mc: Minecraft): Boolean {
        val player = mc.player ?: return false
        if (mc.gameMode?.isServerControlledInventory == true) {
            InvSortItems.say("Inventory sort does not work while this server controls the inventory")
            return false
        }
        McUi.setScreen(mc, InventoryScreen(player))
        return true
    }
}
