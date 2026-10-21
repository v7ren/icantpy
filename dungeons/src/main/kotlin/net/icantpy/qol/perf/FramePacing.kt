package net.icantpy.qol.perf

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tan

enum class FrameKind { REAL, GENERATED }

/** Plain-number inputs for one frame decision. Angles are degrees, distances are blocks, times are milliseconds. */
data class FramePacingState(
    val generatedPerReal: Int,
    val generatedSinceReal: Int,
    val hasCapture: Boolean,
    val inLevel: Boolean,
    val screenOpen: Boolean,
    /** The player hid the HUD. Generated frames redraw only through the HUD layer, so they would freeze. */
    val hudHidden: Boolean,
    /** The previous generated frame never reached the HUD element, so it was not redrawn. */
    val lastGeneratedUnpainted: Boolean,
    val rotationDeltaDegrees: Float,
    val translationBlocks: Double,
    /** The rendered FOV moved by more than [FramePacing.FOV_CHANGE_THRESHOLD_DEGREES] between recent real frames. */
    val fovChanged: Boolean,
    val captureAgeMillis: Long,
    /** Adaptive mode: generation is allowed only while the gate says so, see [AdaptiveGate]. */
    val adaptive: Boolean,
    val adaptiveGenerating: Boolean,
)

/**
 * Hysteresis state for adaptive mode. [generating] is the output switch. [pendingSinceMillis] is when the
 * opposite state first became wanted, or null when the current state is holding.
 */
data class AdaptiveGate(val generating: Boolean = true, val pendingSinceMillis: Long? = null)

/** Camera orientation in Minecraft convention: yaw 0 looks toward +Z, positive pitch looks down. [fovDegrees] is vertical. */
data class ViewAngles(val yawDegrees: Float, val pitchDegrees: Float, val fovDegrees: Float)

/**
 * Pure frame cadence and rotation reprojection math. No Minecraft types, so it can be unit tested.
 * Cadence: with N generated frames per real frame, a cycle is one real frame followed by N generated frames.
 */
object FramePacing {
    const val MAX_ROTATION_DEGREES: Float = 30f
    const val MAX_TRANSLATION_BLOCKS: Double = 0.75
    const val MAX_CAPTURE_AGE_MILLIS: Long = 100L
    const val FOV_CHANGE_THRESHOLD_DEGREES: Float = 0.5f
    /** Generation turns on while the natural rate is below refresh minus this many fps. */
    const val ADAPTIVE_ON_BELOW_MARGIN_FPS: Float = 8f
    /** Generation turns off only while the natural rate is above refresh minus this many fps. */
    const val ADAPTIVE_OFF_ABOVE_MARGIN_FPS: Float = 3f
    /** A wanted switch must hold this long before it takes effect. */
    const val ADAPTIVE_HOLD_MILLIS: Long = 500L
    const val FALLBACK_REFRESH_HZ: Int = 60
    /** Time constant of the real frame rate average. */
    const val FPS_SMOOTHING_SECONDS: Double = 0.5
    private const val MIN_DEPTH = 1e-6
    private const val MIN_PLAUSIBLE_REFRESH_HZ = 20
    private const val MAX_PLAUSIBLE_REFRESH_HZ = 1000

    fun decide(state: FramePacingState): FrameKind {
        if (state.generatedPerReal <= 0) return FrameKind.REAL
        if (mustRenderReal(state)) return FrameKind.REAL
        if (state.adaptive && !state.adaptiveGenerating) return FrameKind.REAL
        return if (state.generatedSinceReal < state.generatedPerReal) FrameKind.GENERATED else FrameKind.REAL
    }

    /**
     * One step of the adaptive hysteresis. Generation turns on below refresh minus 8 fps and off above refresh
     * minus 3 fps; inside that band the current state holds. A switch takes effect only after the opposite state
     * has been wanted continuously for [ADAPTIVE_HOLD_MILLIS]. NaN never counts as a reason to change state.
     */
    fun advanceAdaptiveGate(gate: AdaptiveGate, naturalFps: Float, refreshRateHz: Int, nowMillis: Long): AdaptiveGate {
        val refresh = refreshRateOrFallback(refreshRateHz)
        val wanted = wantedAdaptiveState(gate.generating, naturalFps, refresh)
        if (wanted == gate.generating) return AdaptiveGate(gate.generating, null)
        val since = gate.pendingSinceMillis ?: return AdaptiveGate(gate.generating, nowMillis)
        return if (nowMillis - since >= ADAPTIVE_HOLD_MILLIS) AdaptiveGate(wanted, null) else gate
    }

    private fun wantedAdaptiveState(currentlyGenerating: Boolean, naturalFps: Float, refreshHz: Int): Boolean =
        if (currentlyGenerating) {
            !(naturalFps > refreshHz - ADAPTIVE_OFF_ABOVE_MARGIN_FPS)
        } else {
            naturalFps < refreshHz - ADAPTIVE_ON_BELOW_MARGIN_FPS
        }

    /** Monitor refresh from the window, or [FALLBACK_REFRESH_HZ] when the reported value is implausible. */
    fun refreshRateOrFallback(reportedHz: Int): Int =
        if (reportedHz in MIN_PLAUSIBLE_REFRESH_HZ..MAX_PLAUSIBLE_REFRESH_HZ) reportedHz else FALLBACK_REFRESH_HZ

    /**
     * Frame rate the game would reach without generation. Real frames measured while generation was running
     * arrive once per cycle of (generated + 1) presented frames, so they are scaled back up. Without this the
     * rate would look halved whenever generation is on and adaptive mode could never switch off.
     */
    fun naturalFps(measuredRealFps: Float, generatedPerReal: Int, generatedInLastCycle: Int): Float {
        if (generatedInLastCycle <= 0) return measuredRealFps
        return measuredRealFps * (generatedPerReal + 1)
    }

    /**
     * Exponential average of the real frame rate, weighted by the time since the previous real frame.
     * Non-positive or non-finite intervals (first frame, clock issues) keep the previous value.
     */
    fun smoothFps(previousFps: Float, frameIntervalSeconds: Double): Float {
        if (!(frameIntervalSeconds > 0.0) || !frameIntervalSeconds.isFinite()) return previousFps
        val instant = (1.0 / frameIntervalSeconds).toFloat()
        if (!(previousFps > 0f)) return instant
        val weight = (1.0 - exp(-frameIntervalSeconds / FPS_SMOOTHING_SECONDS)).toFloat()
        return previousFps + (instant - previousFps) * weight
    }

    /** True when the rendered vertical FOV moved more than the threshold since the previous real frame. */
    fun fovMoving(previousFovDegrees: Float?, currentFovDegrees: Float): Boolean {
        if (previousFovDegrees == null) return false
        return abs(currentFovDegrees - previousFovDegrees) > FOV_CHANGE_THRESHOLD_DEGREES
    }

    /** Conditions that always force a real world render. NaN measurements also force one. */
    fun mustRenderReal(state: FramePacingState): Boolean =
        !state.hasCapture ||
            !state.inLevel ||
            state.screenOpen ||
            state.hudHidden ||
            state.lastGeneratedUnpainted ||
            state.fovChanged ||
            !(state.rotationDeltaDegrees <= MAX_ROTATION_DEGREES) ||
            !(state.translationBlocks <= MAX_TRANSLATION_BLOCKS) ||
            state.captureAgeMillis > MAX_CAPTURE_AGE_MILLIS

    /** Angle in degrees between the two view directions. Translation is not involved. */
    fun rotationDeltaDegrees(captured: ViewAngles, current: ViewAngles): Float {
        val a = Basis.of(captured).forward
        val b = Basis.of(current).forward
        val dot = (a.dot(b)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(dot)).toFloat()
    }

    /**
     * Maps a normalized point of the captured frame (u right, v down, both 0..1) to where that world point
     * lands in the current frame, using only camera rotation. Returns normalized current-frame coordinates,
     * or null when the point is behind the current camera.
     */
    fun warp(u: Float, v: Float, captured: ViewAngles, current: ViewAngles, aspect: Float): FloatArray? {
        val src = Basis.of(captured)
        val dst = Basis.of(current)
        val srcTanY = tan(Math.toRadians(captured.fovDegrees.toDouble()) / 2.0)
        val ndcX = 2.0 * u - 1.0
        val ndcY = 1.0 - 2.0 * v
        val direction = src.forward
            .plus(src.right.times(ndcX * srcTanY * aspect))
            .plus(src.up.times(ndcY * srcTanY))
        val depth = direction.dot(dst.forward)
        if (depth <= MIN_DEPTH) return null
        val dstTanY = tan(Math.toRadians(current.fovDegrees.toDouble()) / 2.0)
        val screenX = direction.dot(dst.right) / (depth * dstTanY * aspect)
        val screenY = direction.dot(dst.up) / (depth * dstTanY)
        return floatArrayOf(((screenX + 1.0) / 2.0).toFloat(), ((1.0 - screenY) / 2.0).toFloat())
    }

    private class Basis(val forward: Vec, val right: Vec, val up: Vec) {
        companion object {
            /** Minecraft view vector; right is horizontal, up is the camera's own up axis. */
            fun of(angles: ViewAngles): Basis {
                val yaw = Math.toRadians(angles.yawDegrees.toDouble())
                val pitch = Math.toRadians(angles.pitchDegrees.toDouble())
                val forward = Vec(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
                val right = Vec(-cos(yaw), 0.0, -sin(yaw))
                val up = right.cross(forward)
                return Basis(forward, right, up)
            }
        }
    }

    private class Vec(val x: Double, val y: Double, val z: Double) {
        fun dot(other: Vec): Double = x * other.x + y * other.y + z * other.z
        fun plus(other: Vec): Vec = Vec(x + other.x, y + other.y, z + other.z)
        fun times(scale: Double): Vec = Vec(x * scale, y * scale, z * scale)
        fun cross(other: Vec): Vec = Vec(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x,
        )
    }
}
