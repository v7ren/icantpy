package net.icantpy.slayer.aim

import kotlin.math.cos
import kotlin.math.sin

/** Pure geometry for the boss S-tap. No Minecraft types: yaw in degrees (Minecraft convention, 0 = facing +Z). */
object StrafeMath {
    /** Unit horizontal direction the back key moves the player while facing [yawDeg], written into [out] as (x, z). */
    fun backward(yawDeg: Float, out: DoubleArray) {
        val r = Math.toRadians(yawDeg.toDouble())
        out[0] = sin(r)
        out[1] = -cos(r)
    }

    /**
     * True when a new tap may start: an attack happened after the last tap ended (or the attack button is held), and
     * at least [gapTicks] passed since then.
     */
    fun tapDue(now: Long, lastAttackTick: Long, attackHeld: Boolean, lastTapEndTick: Long, gapTicks: Int): Boolean {
        if (now - lastTapEndTick < gapTicks) return false
        return attackHeld || lastAttackTick > lastTapEndTick
    }
}
