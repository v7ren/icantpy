package net.icantpy.slayer.blaze

/**
 * Predicts where an airborne player lands. Simulates vanilla air physics a tick at a time (position first, then
 * gravity and drag), optionally with a constant horizontal acceleration [nudgeX]/[nudgeZ] added to the velocity
 * before each tick's move. That is what held movement keys do in the air (vanilla moveRelative), which is how the
 * air strafe steers.
 */
object AirLanding {
    class Landing(val x: Double, val z: Double, val ticks: Int)

    /** Horizontal acceleration of one tick. */
    class Accel(val x: Double, val z: Double)

    /** Vanilla air acceleration of the movement keys, standing and sprinting. */
    const val AIR_SPEED = 0.02
    const val AIR_SPEED_SPRINT = 0.026

    /**
     * Acceleration held keys give per tick at camera [yawDeg], as vanilla moveRelative: the key vector is
     * normalised, forward is (-sin yaw, cos yaw) and left is (cos yaw, sin yaw). Opposing keys cancel.
     */
    fun inputAccel(forward: Boolean, back: Boolean, left: Boolean, right: Boolean, yawDeg: Double, speed: Double): Accel {
        val f = (if (forward) 1.0 else 0.0) - (if (back) 1.0 else 0.0)
        val l = (if (left) 1.0 else 0.0) - (if (right) 1.0 else 0.0)
        val len = Math.sqrt(f * f + l * l)
        if (len < 1.0E-4) return Accel(0.0, 0.0)
        val yaw = Math.toRadians(yawDeg)
        val s = Math.sin(yaw)
        val c = Math.cos(yaw)
        val scale = speed / len
        return Accel((l * c - f * s) * scale, (f * c + l * s) * scale)
    }

    const val MAX_TICKS = 40
    private const val GRAVITY = 0.08
    private const val VERTICAL_DRAG = 0.98
    private const val HORIZONTAL_DRAG = 0.91

    /**
     * [floorTop] returns the top of the highest solid block under (x, z) at or just below the given height, or NaN
     * when there is none. Returns null when the player does not land within [MAX_TICKS] ticks.
     */
    fun predict(
        x: Double,
        y: Double,
        z: Double,
        vx: Double,
        vy: Double,
        vz: Double,
        nudgeX: Double,
        nudgeZ: Double,
        floorTop: (Int, Int, Double) -> Double,
    ): Landing? {
        var px = x
        var py = y
        var pz = z
        var mx = vx
        var my = vy
        var mz = vz
        for (tick in 1..MAX_TICKS) {
            mx += nudgeX
            mz += nudgeZ
            val nx = px + mx
            val nz = pz + mz
            val ny = py + my
            if (my <= 0.0) {
                val top = floorTop(Math.floor(nx).toInt(), Math.floor(nz).toInt(), py)
                if (!top.isNaN() && ny <= top) return Landing(nx, nz, tick)
            }
            px = nx
            py = ny
            pz = nz
            my = (my - GRAVITY) * VERTICAL_DRAG
            mx *= HORIZONTAL_DRAG
            mz *= HORIZONTAL_DRAG
        }
        return null
    }
}
