package net.icantpy.qol.visual

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Translation in blocks and rotation in degrees, applied as translate, then rotate about X, Y, Z.
 * Pure data: the Minecraft-side [Viewmodel] turns it into PoseStack calls.
 */
data class SwingPose(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val tz: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val rz: Float = 0f,
) {
    /**
     * Reflection across the YZ plane, used for the left arm. X translation and the yaw/roll rotations flip sign;
     * pitch (rotation about X) does not.
     */
    fun mirrored(): SwingPose = SwingPose(-tx, ty, tz, rx, -ry, -rz)
}

object ViewmodelMath {
    /** Zoom time constant in seconds. Roughly 95% of the way to the target after three constants. */
    const val ZOOM_TIME_CONSTANT_S: Float = 0.06f
    private const val SNAP_EPSILON: Float = 1e-4f
    private const val MAX_STEP_S: Float = 0.25f

    /**
     * Swing pose for [progress] in 0..1 (vanilla attack progress). Every curve is zero at progress 0 and 1,
     * so the item rests where vanilla rests. Non-finite progress is treated as 0.
     */
    fun swingPose(mode: SwingMode, progress: Float): SwingPose {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        val bump = sin(PI.toFloat() * p)
        return when (mode) {
            SwingMode.VANILLA -> SwingPose()
            SwingMode.PUSH -> SwingPose(tz = -0.45f * bump, rx = -10f * bump)
            SwingMode.STAB -> SwingPose(tz = -0.7f * bump, rx = -20f * bump)
            // A full turn returns to the rest angle at progress 1, so the spin ends where it began.
            SwingMode.SPIN -> SwingPose(rz = (360f * p) % 360f)
            SwingMode.PUNCH -> SwingPose(tx = -0.25f * bump, tz = -0.2f * bump, ry = -25f * bump)
            SwingMode.SWING_DOWN -> SwingPose(ty = -0.15f * bump, rx = -60f * bump)
        }
    }

    /**
     * Moves [current] toward [target] by one frame. With [smooth] false it snaps. Otherwise it is an exponential
     * approach that is frame-rate independent, and it lands exactly on the target once close enough.
     * A non-positive or non-finite [dtSeconds] leaves a smooth value unchanged.
     */
    fun easeToward(current: Float, target: Float, dtSeconds: Float, smooth: Boolean): Float {
        if (!smooth) return target
        if (!dtSeconds.isFinite() || dtSeconds <= 0f) return current
        val dt = dtSeconds.coerceAtMost(MAX_STEP_S)
        val alpha = 1f - exp(-dt / ZOOM_TIME_CONSTANT_S)
        val next = current + (target - current) * alpha
        return if (kotlin.math.abs(target - next) < SNAP_EPSILON) target else next
    }
}
