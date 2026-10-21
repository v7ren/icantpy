package net.icantpy.qol.path

import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Render-rate camera easing ported from senura's LookAtTask. Pure math with no Minecraft access: the caller reads
 * the player's rotation, calls [step] and applies the difference through Entity.turn.
 */
class SmoothLook {
    private var lastFrameNanos = 0L

    /** Forgets the previous frame so the next [frameSeconds] call starts a fresh timing window. */
    fun reset() {
        lastFrameNanos = 0L
    }

    /** Seconds since the previous frame, clamped to 1-50 ms. The first frame after [reset] uses 16 ms. */
    fun frameSeconds(nowNanos: Long): Float {
        val previous = lastFrameNanos
        lastFrameNanos = nowNanos
        if (previous == 0L) return FIRST_FRAME_SECONDS
        return ((nowNanos - previous) / 1_000_000_000.0f).coerceIn(MIN_FRAME_SECONDS, MAX_FRAME_SECONDS)
    }

    /**
     * Returns the new `[yaw, pitch]` after one frame of easing toward the target. Yaw takes the shortest way round.
     * Pitch target is clamped to the vanilla range. Returns the current rotation unchanged when already aligned.
     */
    fun step(
        currentYaw: Float,
        currentPitch: Float,
        targetYaw: Float,
        targetPitch: Float,
        dtSeconds: Float,
    ): FloatArray {
        val yawDiff = wrapDegrees(targetYaw - currentYaw)
        val pitchDiff = targetPitch.coerceIn(-90f, 90f) - currentPitch
        val totalAngle = hypot(yawDiff.toDouble(), pitchDiff.toDouble())
        if (totalAngle < MIN_ANGLE_DEG) return floatArrayOf(currentYaw, currentPitch)

        val turnDecay = 1f - exp(-DECAY_PER_SECOND * dtSeconds)
        val maxYaw = MAX_YAW_DEG_PER_SECOND * dtSeconds
        val maxPitch = MAX_PITCH_DEG_PER_SECOND * dtSeconds
        val stepYaw = (yawDiff * turnDecay).coerceIn(-maxYaw, maxYaw)
        val stepPitch = (pitchDiff * turnDecay).coerceIn(-maxPitch, maxPitch)
        return floatArrayOf(currentYaw + stepYaw, (currentPitch + stepPitch).coerceIn(-90f, 90f))
    }

    companion object {
        private const val MIN_ANGLE_DEG = 0.05
        private const val DECAY_PER_SECOND = 8.5f
        private const val MAX_YAW_DEG_PER_SECOND = 340f
        private const val MAX_PITCH_DEG_PER_SECOND = 220f
        private const val FIRST_FRAME_SECONDS = 0.016f
        private const val MIN_FRAME_SECONDS = 0.001f
        private const val MAX_FRAME_SECONDS = 0.05f

        /** Vanilla-style yaw/pitch that looks from a point along the offset `(dx, dy, dz)`. Returns `[yaw, pitch]`. */
        fun rotationTo(dx: Double, dy: Double, dz: Double): FloatArray {
            val horizontal = sqrt(dx * dx + dz * dz)
            val yaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
            val pitch = (-Math.toDegrees(atan2(dy, horizontal))).toFloat().coerceIn(-90f, 90f)
            return floatArrayOf(wrapDegrees(yaw), pitch)
        }

        private fun wrapDegrees(degrees: Float): Float {
            var wrapped = degrees
            while (wrapped > 180f) wrapped -= 360f
            while (wrapped < -180f) wrapped += 360f
            return wrapped
        }
    }
}
