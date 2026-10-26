package net.icantpy.qol.camera

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Which fly keys are held this frame. */
data class FreecamKeys(
    val forward: Boolean = false,
    val back: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val up: Boolean = false,
    val down: Boolean = false,
    val boost: Boolean = false,
)

data class FreecamPos(val x: Double, val y: Double, val z: Double)

/** Pure fly-camera movement, in Minecraft's yaw convention (yaw 0 faces +Z, 90 faces -X; pitch +90 looks down). */
internal object FreecamMath {
    const val BOOST: Double = 3.0
    const val MAX_STEP_SECONDS: Double = 0.1

    fun step(from: FreecamPos, yawDeg: Float, pitchDeg: Float, keys: FreecamKeys, speed: Double, seconds: Double): FreecamPos {
        val dt = seconds.coerceIn(0.0, MAX_STEP_SECONDS)
        val yaw = Math.toRadians(yawDeg.toDouble())
        val pitch = Math.toRadians(pitchDeg.toDouble())
        // Forward follows the full look direction, like spectator flight; strafe stays level.
        val forwardX = -sin(yaw) * cos(pitch)
        val forwardY = -sin(pitch)
        val forwardZ = cos(yaw) * cos(pitch)
        val rightX = -cos(yaw)
        val rightZ = -sin(yaw)
        val f = axis(keys.forward, keys.back)
        val r = axis(keys.right, keys.left)
        val u = axis(keys.up, keys.down)
        var dx = forwardX * f + rightX * r
        var dy = forwardY * f + u
        var dz = forwardZ * f + rightZ * r
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 1e-9) return from
        val distance = speed * (if (keys.boost) BOOST else 1.0) * dt / length
        dx *= distance
        dy *= distance
        dz *= distance
        return FreecamPos(from.x + dx, from.y + dy, from.z + dz)
    }

    private fun axis(positive: Boolean, negative: Boolean): Double = when {
        positive == negative -> 0.0
        positive -> 1.0
        else -> -1.0
    }
}
