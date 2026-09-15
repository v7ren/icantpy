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
}
