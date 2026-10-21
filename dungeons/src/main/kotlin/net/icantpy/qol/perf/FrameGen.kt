package net.icantpy.qol.perf

import net.icantpy.Icantpy
import net.icantpy.api.FrameGenHook
import net.icantpy.api.IcantpyDispatchResult
import net.icantpy.compat.FrameWarp
import net.icantpy.compat.McCompat
import net.icantpy.qol.camera.Freelook
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import kotlin.math.sqrt

/** Camera state used for a capture or a generated frame: orientation plus eye position in blocks. */
private data class FrameView(val angles: ViewAngles, val eyeX: Double, val eyeY: Double, val eyeZ: Double) {
    fun distanceTo(other: FrameView): Double {
        val dx = eyeX - other.eyeX
        val dy = eyeY - other.eyeY
        val dz = eyeZ - other.eyeZ
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

private data class FrameCapture(val view: FrameView, val capturedAtNanos: Long)

/**
 * Rotation-reprojection frame generation. After each real world frame, up to [FrameGenConfig.generatedPerReal]
 * generated frames skip the world render and redraw the captured world frame turned by the camera rotation
 * change. HUD and GUI always render normally. Cadence and forced-real rules live in [FramePacing].
 */
object FrameGen {
    private val hudId: Identifier = Identifier.fromNamespaceAndPath("icantpy", "frame_gen")

    private var current = FrameGenConfig()
    /** The HUD layer identifier in use while loaded (see [HudLayers.installFirst]), or null. */
    private var installedHudId: Identifier? = null
    private var disabledForSession = false
    private var capture: FrameCapture? = null
    private var generatedSinceReal = 0

    /** View chosen by [decideGenerated] for the generated frame being extracted. */
    private var generatedView: FrameView? = null

    /**
     * Set in the extract phase when this frame's warped image was submitted; read and cleared by the render phase.
     * A frame whose HUD layer never ran (F1, screen open) is therefore always real, so the world is never missing.
     */
    private var skipThisRender = false

    /** Kept for [FramePacingState]; with the decision made at extract time a submitted frame is always painted. */
    private var lastGeneratedUnpainted = false

    /** Vertical FOV of the most recent real render, read from its projection matrix. Null until the first one. */
    private var renderedFovDegrees: Float? = null

    /** The rendered FOV changed by more than the threshold at the most recent real frame. */
    private var fovMoving = false

    /** Nanotime of the previous real frame, 0 when there is none to measure against. */
    private var lastRealNanos = 0L

    /** Real frame rate, averaged over about [FramePacing.FPS_SMOOTHING_SECONDS]. */
    private var measuredRealFps = 0f

    /** Generated frames shown after the most recent real frame; used to scale the measured rate back up. */
    private var generatedInLastCycle = 0

    private var refreshRateHz = FramePacing.FALLBACK_REFRESH_HZ

    /** Adaptive hysteresis, advanced once per generation decision. Starts in the generating state. */
    private var adaptiveGate = AdaptiveGate()

    val config: FrameGenConfig
        get() = current

    fun load() {
        current = FrameGenStore.load()
        disabledForSession = false
        resetCadence()
        if (installedHudId == null) {
            installedHudId = HudLayers.installFirst(hudId) { graphics, _ -> drawGeneratedFrame(graphics) }
        }
        applyHookInstall()
    }

    fun unload() {
        installedHudId?.let(HudLayers::uninstall)
        installedHudId = null
        FrameGenHook.install(null)
        resetCadence()
        closeGpuResources()
    }

    fun setGeneratedPerReal(value: Int) {
        current = current.copy(generatedPerReal = FrameGenConfig.normalize(value))
        FrameGenStore.save(current)
        resetCadence()
        if (!isActive()) closeGpuResources()
        applyHookInstall()
    }

    /** Adaptive: generate only while the real frame rate is below the monitor refresh rate. */
    fun setAdaptive(value: Boolean) {
        current = current.copy(adaptive = value)
        FrameGenStore.save(current)
    }

    private object Provider : FrameGenHook.Provider {
        override fun skipLevelRender(): Boolean = FrameGen.skipLevelRenderGuarded()

        override fun afterLevelRender() {
            PerfCounters.realFrames++
            FrameGen.captureRealFrameGuarded()
        }
    }

    /**
     * Render phase. Minecraft 26.x extracts every HUD element before it renders the frame, so the real/generated
     * decision was already made (and the warped frame already submitted) by [drawGeneratedFrame] earlier in this
     * same frame. Deciding here instead put the warp one frame late: a generated frame showed the stale screen and
     * the next real frame was overdrawn by the old warp, which flickered dark/light.
     */
    private fun skipLevelRenderGuarded(): Boolean {
        val skip = skipThisRender
        skipThisRender = false
        return skip && isActive()
    }

    private fun decideGenerated(mc: Minecraft): Boolean {
        val view = currentView(mc) ?: return false
        val kind = FramePacing.decide(pacingState(mc, view))
        if (kind == FrameKind.REAL) return false
        generatedView = view
        return true
    }

    private fun captureRealFrameGuarded() {
        lastGeneratedUnpainted = false
        if (!isActive()) return
        guarded("capturing the frame") { captureRealFrame(Minecraft.getInstance()) }
    }

    private fun captureRealFrame(mc: Minecraft) {
        val now = System.nanoTime()
        recordRealFrameTiming(now)
        refreshRateHz = FramePacing.refreshRateOrFallback(McCompat.refreshRateHz(mc))
        updateRenderedFov(McCompat.renderedVerticalFovDegrees(mc))
        generatedInLastCycle = generatedSinceReal
        generatedSinceReal = 0
        val view = currentView(mc)
        if (view == null || !FrameWarp.capture(mc)) {
            // Without a fresh copy the next frame must be real, so no stale capture is ever warped.
            capture = null
            return
        }
        capture = FrameCapture(view, now)
    }

    private fun recordRealFrameTiming(nowNanos: Long) {
        val previous = lastRealNanos
        lastRealNanos = nowNanos
        if (previous == 0L) return
        measuredRealFps = FramePacing.smoothFps(measuredRealFps, (nowNanos - previous) / NANOS_PER_SECOND)
    }

    private fun updateRenderedFov(fovDegrees: Float?) {
        if (fovDegrees == null) return
        fovMoving = FramePacing.fovMoving(renderedFovDegrees, fovDegrees)
        renderedFovDegrees = fovDegrees
    }

    private fun pacingState(mc: Minecraft, view: FrameView): FramePacingState {
        val cached = capture
        val naturalFps = FramePacing.naturalFps(measuredRealFps, current.generatedPerReal, generatedInLastCycle)
        adaptiveGate = FramePacing.advanceAdaptiveGate(adaptiveGate, naturalFps, refreshRateHz, System.nanoTime() / NANOS_PER_MILLI)
        return FramePacingState(
            generatedPerReal = current.generatedPerReal,
            generatedSinceReal = generatedSinceReal,
            hasCapture = cached != null,
            inLevel = mc.level != null && mc.player != null,
            screenOpen = McCompat.currentScreen(mc) != null,
            hudHidden = McCompat.hudHidden(mc),
            lastGeneratedUnpainted = lastGeneratedUnpainted,
            rotationDeltaDegrees = cached?.let { FramePacing.rotationDeltaDegrees(it.view.angles, view.angles) } ?: 0f,
            translationBlocks = cached?.view?.distanceTo(view) ?: 0.0,
            fovChanged = fovMoving,
            captureAgeMillis = cached?.let { (System.nanoTime() - it.capturedAtNanos) / NANOS_PER_MILLI } ?: 0L,
            adaptive = current.adaptive,
            adaptiveGenerating = adaptiveGate.generating,
        )
    }

    /**
     * Orientation the world would be drawn with. Freelook angles win while active, since the camera uses them.
     * The camera entity's view rotation is used otherwise. Camera setup is skipped on generated frames, so this
     * is recomputed from game state rather than read from the camera.
     */
    private fun currentView(mc: Minecraft): FrameView? {
        val player = mc.player ?: return null
        val partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)
        val look = Freelook.queryLook(McCompat.mainCamera(mc))
        val freelookAngles = (look.value as? FloatArray)
            ?.takeIf { look.result == IcantpyDispatchResult.HANDLED && it.size >= 2 }
        val yaw = freelookAngles?.get(0) ?: player.getViewYRot(partialTick)
        val pitch = freelookAngles?.get(1) ?: player.getViewXRot(partialTick)
        val eye = player.getEyePosition(partialTick)
        val angles = ViewAngles(yaw, pitch, currentFovDegrees(mc))
        return FrameView(angles, eye.x, eye.y, eye.z)
    }

    /**
     * FOV the last real frame rendered with, so sprint, speed and zoom are included. A generated frame cannot
     * observe a FOV change until a real frame renders, so the first generated frame after an onset may be
     * slightly off. Options FOV is only a fallback before the first real frame.
     */
    private fun currentFovDegrees(mc: Minecraft): Float =
        renderedFovDegrees ?: (mc.options.fov().get()?.toFloat() ?: DEFAULT_FOV_DEGREES)

    /**
     * Extract phase (runs before this frame's render). Decides whether this frame is generated and, if so,
     * submits the warped frame and tells the render phase to skip the level. Any early return leaves the frame real.
     */
    private fun drawGeneratedFrame(graphics: GuiGraphicsExtractor) {
        skipThisRender = false
        generatedView = null
        if (!isActive()) return
        val width = graphics.guiWidth().toFloat()
        val height = graphics.guiHeight().toFloat()
        if (width <= 0f || height <= 0f) return
        val generate = guarded("choosing the frame") { decideGenerated(Minecraft.getInstance()) } ?: false
        val view = generatedView
        val cached = capture
        generatedView = null
        if (!generate || view == null || cached == null) return
        val aspect = width / height
        val drawn = guarded("drawing the generated frame") {
            FrameWarp.draw(graphics) { u, v ->
                FramePacing.warp(u, v, cached.view.angles, view.angles, aspect)
                    ?.let { floatArrayOf(it[0] * width, it[1] * height) }
            }
            true
        } ?: false
        if (!drawn) return
        generatedSinceReal++
        PerfCounters.generatedFrames++
        skipThisRender = true
    }

    private fun isActive(): Boolean = current.generatedPerReal > 0 && !disabledForSession

    private fun applyHookInstall() {
        FrameGenHook.install(if (isActive()) Provider else null)
    }

    /** Drops the capture and per-frame state. Measured FPS and the rendered FOV survive, since they describe the game. */
    private fun resetCadence() {
        capture = null
        generatedSinceReal = 0
        generatedView = null
        skipThisRender = false
        lastGeneratedUnpainted = false
        lastRealNanos = 0L
        generatedInLastCycle = 0
        fovMoving = false
        adaptiveGate = AdaptiveGate()
    }

    private fun <T> guarded(step: String, block: () -> T): T? {
        return try {
            block()
        } catch (throwable: Throwable) {
            disableForSession(step, throwable)
            null
        }
    }

    private fun disableForSession(step: String, throwable: Throwable) {
        if (disabledForSession) return
        disabledForSession = true
        Icantpy.LOGGER.warn("Frame generation disabled for this session after failure while $step", throwable)
        resetCadence()
        FrameGenHook.install(null)
        closeGpuResources()
    }

    private fun closeGpuResources() {
        try {
            FrameWarp.close()
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to release frame generation history texture", exception)
        }
    }

    private const val DEFAULT_FOV_DEGREES = 70f
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val NANOS_PER_SECOND = 1_000_000_000.0
}
