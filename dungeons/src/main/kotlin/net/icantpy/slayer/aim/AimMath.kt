package net.icantpy.slayer.aim

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Easing curves applied to the normalized curve parameter. */
enum class AimEasing(val id: String, val label: String) {
    LINEAR("linear", "Linear"),
    EASE_IN_OUT("ease_in_out", "Ease in-out"),
    EASE_OUT("ease_out", "Ease out"),
    SMOOTHSTEP("smoothstep", "Smooth");

    companion object {
        fun fromId(raw: String?): AimEasing? {
            if (raw == null) return null
            val id = raw.trim()
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}

/** Yaw/pitch in degrees, Minecraft convention (yaw 0 = +Z, pitch +90 = straight down). */
data class AimPose(val yaw: Float, val pitch: Float)

/**
 * speedDegPerSec: angular speed the curve covers, also the max follow speed after the curve ends.
 * minDurationMs/maxDurationMs clamp each curve's duration (min is never below [AimMath.MIN_DURATION_MS]).
 * curve: 0 = straight line, 1 = strong arc (lateral bulge up to curve * 0.5 * distance).
 */
data class AimSettings(
    val speedDegPerSec: Float = 180f,
    val minDurationMs: Int = 180,
    val maxDurationMs: Int = 900,
    val curve: Float = 0.35f,
    val easing: AimEasing = AimEasing.EASE_IN_OUT,
)

object AimMath {
    /** Lower bound for any curve duration so an aim is never instant. */
    const val MIN_DURATION_MS = 60L

    private const val RAD_TO_DEG = 180.0 / Math.PI

    /** Wraps to (-180, 180]. */
    fun wrapDegrees(deg: Float): Float {
        var result = deg % 360f
        if (result <= -180f) result += 360f
        if (result > 180f) result -= 360f
        return result
    }

    /** Yaw/pitch that looks from the eye position to the target point. */
    fun lookAt(
        eyeX: Double, eyeY: Double, eyeZ: Double,
        targetX: Double, targetY: Double, targetZ: Double,
    ): AimPose {
        val dx = targetX - eyeX
        val dy = targetY - eyeY
        val dz = targetZ - eyeZ
        val yaw = wrapDegrees((atan2(-dx, dz) * RAD_TO_DEG).toFloat())
        val horizontal = sqrt(dx * dx + dz * dz)
        val pitch = (-atan2(dy, horizontal) * RAD_TO_DEG).toFloat().coerceIn(-90f, 90f)
        return AimPose(yaw, pitch)
    }

    /** Euclidean distance in yaw/pitch space, using the wrapped yaw difference. */
    fun angularDistance(a: AimPose, b: AimPose): Float {
        val d = delta(a, b)
        return sqrt(d.yaw * d.yaw + d.pitch * d.pitch)
    }

    /** Shortest-path difference [to] - [from]. */
    fun delta(from: AimPose, to: AimPose): AimPose =
        AimPose(wrapDegrees(to.yaw - from.yaw), to.pitch - from.pitch)

    /** Eases [t] (clamped to 0..1). ease(0) = 0, ease(1) = 1, monotonic. */
    fun ease(t: Float, easing: AimEasing): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            AimEasing.LINEAR -> x
            AimEasing.EASE_IN_OUT ->
                if (x < 0.5f) 4f * x * x * x
                else 1f - (-2f * x + 2f).let { it * it * it } / 2f
            AimEasing.EASE_OUT -> 1f - (1f - x) * (1f - x) * (1f - x)
            AimEasing.SMOOTHSTEP -> x * x * (3f - 2f * x)
        }
    }

    /** Curve duration for [distanceDeg] at the configured speed, clamped to the min/max bounds. */
    fun durationMs(distanceDeg: Float, settings: AimSettings): Long {
        val speed = settings.speedDegPerSec.coerceAtLeast(0.001f)
        val lower = maxOf(settings.minDurationMs.toLong(), MIN_DURATION_MS)
        val upper = maxOf(settings.maxDurationMs.toLong(), lower)
        val raw = abs(distanceDeg) / speed * 1000f
        return raw.toLong().coerceIn(lower, upper)
    }

    /** Cubic Bezier evaluated in one dimension. */
    fun bezier(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val u = 1f - t
        return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
    }
}
