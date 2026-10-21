package net.icantpy.qol.perf

import net.icantpy.Icantpy
import net.icantpy.compat.FrameHistory
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

/**
 * Cheap accumulation motion blur.
 *
 * [onFrameStart] runs at the start of the world render, when the main render target still holds the
 * previous finished frame. That frame is copied into a persistent history texture. The HUD element
 * registered in [load] then blits the history over the new frame at [MotionBlurConfig.strength], so
 * each frame keeps a decaying share of earlier frames.
 */
object MotionBlur {
    private val hudId: Identifier = Identifier.fromNamespaceAndPath("icantpy", "motion_blur")

    private var current = MotionBlurConfig()
    /** The HUD layer identifier in use while loaded (see [HudLayers.installFirst]), or null. */
    private var installedHudId: Identifier? = null
    private var disabledForSession = false
    private var drawThisFrame = false

    val config: MotionBlurConfig
        get() = current

    fun load() {
        current = MotionBlurStore.load()
        if (installedHudId != null) return
        installedHudId = HudLayers.installFirst(hudId) { graphics, _ -> drawHistory(graphics) }
    }

    fun unload() {
        installedHudId?.let(HudLayers::uninstall)
        installedHudId = null
        drawThisFrame = false
        closeGpuResources()
    }

    fun setEnabled(value: Boolean) {
        current = current.copy(enabled = value)
        MotionBlurStore.save(current)
        if (!value) closeGpuResources()
    }

    fun setStrength(value: Float) {
        current = current.copy(strength = MotionBlurConfig.clampStrength(value))
        MotionBlurStore.save(current)
    }

    fun onFrameStart() {
        drawThisFrame = false
        if (!isActive()) return
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null || minecraft.player == null) return
        drawThisFrame = guarded("capturing the frame") { FrameHistory.capture(minecraft) } == true
    }

    private fun drawHistory(graphics: GuiGraphicsExtractor) {
        if (!drawThisFrame) return
        drawThisFrame = false
        if (!isActive()) return
        guarded("drawing the frame") { FrameHistory.draw(graphics, current.strength) }
    }

    private fun isActive(): Boolean = current.enabled && !disabledForSession

    private fun <T> guarded(step: String, block: () -> T): T? {
        if (!isActive()) return null
        return try {
            block()
        } catch (exception: Exception) {
            disableForSession(step, exception)
            null
        }
    }

    private fun disableForSession(step: String, exception: Exception) {
        if (disabledForSession) return
        disabledForSession = true
        drawThisFrame = false
        Icantpy.LOGGER.warn("Motion blur disabled for this session after failure while $step", exception)
        closeGpuResources()
    }

    private fun closeGpuResources() {
        try {
            FrameHistory.close()
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to release motion blur history texture", exception)
        }
    }
}
