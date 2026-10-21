package net.icantpy.slayer.aim

import kotlin.math.sqrt

/**
 * Stateful curve planner for camera aim. The curve is defined relative to start -> end, and the end is
 * re-read on every sample, so a moving target bends the path without restarting it.
 *
 * During the curve, pose(t) = start + D * e + N * |D| * v(e), where D = delta(start, end), e = ease(t) and
 * v is a Bezier with control values (0, side*c, side*c*0.5, 0), c = curve * 0.5. N * |D| is D rotated 90 degrees.
 *
 * After the curve ends the pose follows the end at no more than speedDegPerSec, so it never snaps.
 */
class AimMotion {
    private var started = false
    private var start = AimPose(0f, 0f)
    private var end = AimPose(0f, 0f)
    private var current = AimPose(0f, 0f)
    private var startMs = 0L
    private var durationMs = 0L
    private var lastSampleMs = 0L
    private var bulge = 0f
    private var side = 1f
    private var speedDegPerSec = 0f
    private var easing = AimEasing.EASE_IN_OUT

    /** True from the first [retarget] until [reset]. */
    val active: Boolean
        get() = started

    /** Starts a new curve from [from] to [to]. The duration is fixed from the initial angular distance. */
    fun retarget(nowMs: Long, from: AimPose, to: AimPose, settings: AimSettings, seed: Int) {
        started = true
        start = from
        end = clampPitch(to)
        current = from
        startMs = nowMs
        lastSampleMs = nowMs
        durationMs = AimMath.durationMs(AimMath.angularDistance(from, to), settings)
        bulge = settings.curve * 0.5f
        side = if ((seed and 1) == 0) 1f else -1f
        speedDegPerSec = settings.speedDegPerSec
        easing = settings.easing
    }

    /** Moves the end point without restarting the curve or changing its duration. */
    fun updateTarget(to: AimPose) {
        end = clampPitch(to)
    }

    /** Pose the camera should have at [nowMs], or null before the first [retarget]. */
    fun sample(nowMs: Long): AimPose? {
        if (!started) return null
        if (nowMs <= startMs + durationMs) {
            current = curvePose(curveProgress(nowMs))
        } else {
            val dtSeconds = ((nowMs - lastSampleMs) / 1000f).coerceIn(0f, MAX_FRAME_DT_SECONDS)
            current = stepToward(current, end, speedDegPerSec * dtSeconds)
        }
        lastSampleMs = nowMs
        return current
    }

    /** True once the curve has finished and the last sampled pose is within [toleranceDeg] of the end. */
    fun onTarget(nowMs: Long, toleranceDeg: Float): Boolean {
        if (!started || nowMs < startMs + durationMs) return false
        return AimMath.angularDistance(current, end) <= toleranceDeg
    }

    fun reset() {
        started = false
        start = AimPose(0f, 0f)
        end = start
        current = start
        startMs = 0L
        durationMs = 0L
        lastSampleMs = 0L
        bulge = 0f
        side = 1f
        speedDegPerSec = 0f
        easing = AimEasing.EASE_IN_OUT
    }

    private fun curveProgress(nowMs: Long): Float {
        if (durationMs <= 0L) return 1f
        val t = (nowMs - startMs).toFloat() / durationMs.toFloat()
        return AimMath.ease(t, easing)
    }

    private fun curvePose(e: Float): AimPose {
        val d = AimMath.delta(start, end)
        val v = AimMath.bezier(0f, side * bulge, side * bulge * 0.5f, 0f, e)
        // N * |D| is D rotated 90 degrees: (yaw, pitch) -> (-pitch, yaw).
        val yaw = AimMath.wrapDegrees(start.yaw + d.yaw * e - d.pitch * v)
        val pitch = (start.pitch + d.pitch * e + d.yaw * v).coerceIn(-90f, 90f)
        return AimPose(yaw, pitch)
    }

    private fun stepToward(from: AimPose, target: AimPose, maxStepDeg: Float): AimPose {
        val d = AimMath.delta(from, target)
        val distance = sqrt(d.yaw * d.yaw + d.pitch * d.pitch)
        if (distance <= maxStepDeg || distance == 0f) return target
        val fraction = maxStepDeg / distance
        return AimPose(
            AimMath.wrapDegrees(from.yaw + d.yaw * fraction),
            (from.pitch + d.pitch * fraction).coerceIn(-90f, 90f),
        )
    }

    private fun clampPitch(pose: AimPose): AimPose = AimPose(pose.yaw, pose.pitch.coerceIn(-90f, 90f))

    private companion object {
        /** Caps the time step used by the follow phase so a stalled frame cannot jump the camera. */
        const val MAX_FRAME_DT_SECONDS = 0.1f
    }
}
