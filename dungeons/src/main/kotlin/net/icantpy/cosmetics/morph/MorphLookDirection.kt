package net.icantpy.cosmetics.morph

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

internal data class MorphLookAngles(val yaw: Float, val pitch: Float)

/** Pure render-only direction; inputs are relative to the cosmetic eye position. */
internal object MorphLookDirection {
    private const val LOOK_REFERENCE_DISTANCE = 4.0

    fun fromVanillaLook(yaw: Float, pitch: Float, eyeHeightDifference: Double): MorphLookAngles? {
        if (!yaw.isFinite() || !pitch.isFinite() || pitch !in -90f..90f
            || !eyeHeightDifference.isFinite()) return null
        val radians = pitch.toDouble() * PI / 180
        val horizontal = abs(cos(radians)) * LOOK_REFERENCE_DISTANCE
        val vertical = -sin(radians) * LOOK_REFERENCE_DISTANCE + eyeHeightDifference
        if (hypot(horizontal, vertical) < 0.0001) return null
        // Preserve vanilla yaw exactly, including unwrapped angles. No hit result is involved.
        return MorphLookAngles(yaw, (-atan2(vertical, horizontal) * 180 / PI).toFloat())
    }

    fun toward(x: Double, y: Double, z: Double, fallbackYaw: Float): MorphLookAngles? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !fallbackYaw.isFinite()) return null
        val horizontal = hypot(x, z)
        if (hypot(horizontal, y) < 0.0001) return null
        val yaw = if (horizontal < 0.0001) fallbackYaw else (atan2(-x, z) * 180 / PI).toFloat()
        val pitch = (-atan2(y, horizontal) * 180 / PI).toFloat()
        return MorphLookAngles(yaw, pitch)
    }
}
