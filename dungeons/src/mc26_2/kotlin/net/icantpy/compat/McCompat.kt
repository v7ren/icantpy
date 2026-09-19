package net.icantpy.compat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.BossHealthOverlay
import net.minecraft.client.gui.components.tabs.MenuTabBar
import net.minecraft.client.gui.components.tabs.Tab
import net.minecraft.client.gui.components.tabs.TabManager
import net.minecraft.client.gui.components.tabs.TabNavigationBar
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ContainerInput

object McCompat {
    fun mainCamera(mc: Minecraft): net.minecraft.client.Camera = mc.gameRenderer.mainCamera()

    fun currentScreen(mc: Minecraft): Screen? = mc.gui.screen()

    fun setScreen(mc: Minecraft, screen: Screen?) {
        mc.gui.setScreen(screen)
        if (screen != null) {
            mc.mouseHandler.releaseMouse()
        }
    }

    fun buildTabBar(tabManager: TabManager, width: Int, tabs: List<Tab>): TabNavigationBar =
        MenuTabBar.builder(tabManager, width).addTabs(*tabs.toTypedArray()).build()

    fun arrangeTabBar(bar: TabNavigationBar, width: Int) = bar.arrangeElements(width)

    fun releaseMouse(mc: Minecraft) {
        mc.mouseHandler.releaseMouse()
    }

    fun runOnClientThread(mc: Minecraft, action: () -> Unit) {
        if (mc.isSameThread) {
            action()
        } else {
            mc.execute(action)
        }
    }

    fun clickContainerSlot(mc: Minecraft, containerId: Int, slot: Int) {
        val player = mc.player ?: return
        mc.gameMode?.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player)
    }

    fun bossOverlay(mc: Minecraft): BossHealthOverlay = mc.gui.hud.bossOverlay

    fun drawScaledText(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        scale: Float,
        color: Int,
    ) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        graphics.text(font, text, 0, 0, color, true)
        pose.popMatrix()
    }
}
