package net.icantpy.qol.visual

import net.icantpy.Icantpy
import net.icantpy.api.VisualHook
import net.icantpy.compat.HandShaderGpu
import net.icantpy.compat.McCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer

/**
 * First-person hand recolor. [VisualHook.handShader] calls [afterHand] after vanilla finished drawing the
 * held-item pass, so this only runs when the hand could actually have been drawn.
 *
 * Install is done by the caller (Visuals): it sets `VisualHook.handShader = HandShader` when the config is on
 * and calls [close] on unload. This object never touches [VisualHook] itself.
 */
object HandShader : VisualHook.HandShaderProvider {
    private val startNanos = System.nanoTime()

    @Volatile
    private var enabled = false
    private var disabledForSession = false

    private var mode = HandShaderMode.CHROMA
    private var color1 = 0xFF7AF0
    private var color2 = 0x6AD5FF
    private var speed = 1f
    private var opacity = 0.85f

    /** Key of the cached [body]; the body is regenerated only when this changes. */
    private var bodyKey = 0
    private var body: String? = null
    private var hasBody = false

    /** Stores the settings and rebuilds the shader body if they changed. Turning the effect off releases GPU resources. */
    fun apply(config: VisualsConfig) {
        mode = config.handShaderMode
        color1 = config.handColor1
        color2 = config.handColor2
        speed = config.handShaderSpeed
        opacity = config.handShaderOpacity

        val key = HandShaderGlsl.key(mode, color1, color2, speed, opacity)
        if (!hasBody || key != bodyKey) {
            body = HandShaderGlsl.body(mode, color1, color2, speed, opacity)
            bodyKey = key
            hasBody = true
        }

        if (!config.handShader) {
            close()
            return
        }
        enabled = !disabledForSession
    }

    override fun afterHand(gameRenderer: Any) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!mc.options.cameraType.isFirstPerson) return
        if (McCompat.hudHidden(mc)) return
        if (player.isSpectator) return
        val renderer = gameRenderer as? GameRenderer ?: return
        val shaderBody = body ?: return
        try {
            val seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0 * speed
            HandShaderGpu.draw(mc, renderer, bodyKey, shaderBody, seconds.toFloat())
        } catch (exception: Exception) {
            disableForSession(exception)
        }
    }

    /** Stops drawing and releases GPU resources. Settings are kept so a later [apply] can re-enable cheaply. */
    fun close() {
        enabled = false
        try {
            HandShaderGpu.close()
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to release hand shader GPU resources", exception)
        }
    }

    private fun disableForSession(exception: Exception) {
        if (disabledForSession) return
        disabledForSession = true
        Icantpy.LOGGER.warn("Hand shader disabled for this session after failure while drawing", exception)
        close()
    }
}
