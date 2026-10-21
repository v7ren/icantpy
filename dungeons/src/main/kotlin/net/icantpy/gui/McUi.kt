package net.icantpy.gui

import net.icantpy.compat.McCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object McUi {
    fun currentScreen(mc: Minecraft): Screen? = McCompat.currentScreen(mc)

    fun setScreen(mc: Minecraft, screen: Screen?) {
        McCompat.setScreen(mc, screen)
    }

    fun releaseMouse(mc: Minecraft) {
        McCompat.releaseMouse(mc)
    }

    fun runOnClientThread(mc: Minecraft, action: () -> Unit) {
        McCompat.runOnClientThread(mc, action)
    }

    fun clickContainerSlot(mc: Minecraft, containerId: Int, slot: Int) {
        McCompat.clickContainerSlot(mc, containerId, slot)
    }

    /** Hotbar number-key swap; [hotbarIndex] must be 0..7 (hotbar slot 9 is the SkyBlock menu star). */
    fun swapWithHotbar(mc: Minecraft, containerId: Int, slot: Int, hotbarIndex: Int) {
        require(hotbarIndex in 0..7) { "hotbarIndex must be 0..7, was $hotbarIndex" }
        McCompat.swapWithHotbar(mc, containerId, slot, hotbarIndex)
    }
}
