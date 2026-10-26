package net.icantpy.compat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.BossHealthOverlay
import net.minecraft.client.gui.components.tabs.MenuTabBar
import net.minecraft.client.gui.components.tabs.Tab
import net.minecraft.client.gui.components.tabs.TabManager
import net.minecraft.client.gui.components.tabs.TabNavigationBar
import net.icantpy.mixin.GameRendererProjectionAccessor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW

object McCompat {
    fun mainCamera(mc: Minecraft): net.minecraft.client.Camera = mc.gameRenderer.mainCamera()

    fun currentScreen(mc: Minecraft): Screen? = mc.gui.screen()

    /** True when the player hid the HUD (F1). Frame generation must not skip the world while the HUD is hidden. */
    fun hudHidden(mc: Minecraft): Boolean = mc.gui.hud.isHidden

    /** Monitor refresh rate of the game window in Hz. Callers fall back to 60 when the value is not usable. */
    fun refreshRateHz(mc: Minecraft): Int = mc.window.refreshRate

    /**
     * Vertical FOV, in degrees, of the last real level render. Read from its camera projection matrix, so it
     * includes sprint, speed and zoom effects. Null when the matrix is unavailable or not a perspective projection.
     */
    fun renderedVerticalFovDegrees(mc: Minecraft): Float? {
        val accessor = mc.gameRenderer as? GameRendererProjectionAccessor ?: return null
        val projection = accessor.`icantpy$renderState`().levelRenderState.cameraRenderState.projectionMatrix
        val focal = projection.m11()
        if (!(focal > 0f) || !focal.isFinite()) return null
        return Math.toDegrees(2.0 * Math.atan(1.0 / focal.toDouble())).toFloat()
    }

    /** Tab list entries as plain display text: the display name when set, otherwise the profile name. */
    fun tabLines(mc: Minecraft): List<String> {
        val players = mc.connection?.onlinePlayers ?: return emptyList()
        return players.map { info -> info.tabListDisplayName?.string ?: info.profile.name }
    }

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

    /** Container click with an explicit mouse [button]; [clone] sends a middle click (CLONE) instead of PICKUP. */
    fun clickContainerSlot(mc: Minecraft, containerId: Int, slot: Int, button: Int, clone: Boolean) {
        val player = mc.player ?: return
        val input = if (clone) ContainerInput.CLONE else ContainerInput.PICKUP
        mc.gameMode?.handleContainerInput(containerId, slot, button, input, player)
    }

    /** Hotbar number-key swap: [hotbarIndex] is 0..8. Index 8 (hotbar slot 9) is the SkyBlock menu star and is rejected. */
    fun swapWithHotbar(mc: Minecraft, containerId: Int, slot: Int, hotbarIndex: Int) {
        require(hotbarIndex in 0..7) { "hotbarIndex must be 0..7, was $hotbarIndex" }
        val player = mc.player ?: return
        mc.gameMode?.handleContainerInput(containerId, slot, hotbarIndex, ContainerInput.SWAP, player)
    }

    /** Moves the OS cursor to a GUI-pixel position, scaled to window pixels. */
    fun moveCursorToGuiPixel(mc: Minecraft, guiX: Int, guiY: Int) {
        val window = mc.window
        val scale = window.guiScale.toDouble()
        GLFW.glfwSetCursorPos(window.handle(), (guiX + 0.5) * scale, (guiY + 0.5) * scale)
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
