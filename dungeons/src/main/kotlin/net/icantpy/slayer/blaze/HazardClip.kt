package net.icantpy.slayer.blaze

import kotlin.math.floor

/**
 * Hard avoid: shortens a horizontal move so the player's hitbox does not enter a blocked column, the same way a
 * wall would stop it. Columns the hitbox already overlaps never block, so the player can always walk out of fire.
 * Axes are resolved one at a time (x, then z with the new x), like vanilla collision, so sliding along the edge of
 * a fire patch still works. Pure: coordinates in, coordinates out.
 */
object HazardClip {
    /** Gap left between the hitbox and a blocked column so float rounding never lets it touch. */
    private const val GAP = 1e-4

    /**
     * Writes the allowed (dx, dz) into [out] for a hitbox spanning [minX]..[maxX] x [minZ]..[maxZ] moving by
     * ([dx], [dz]). Returns true when the move was shortened.
     */
    fun clip(
        minX: Double, minZ: Double, maxX: Double, maxZ: Double,
        dx: Double, dz: Double,
        blocked: Set<Long>,
        out: DoubleArray,
    ): Boolean {
        val allowedX = clipAxis(minX, maxX, minZ, maxZ, dx, blocked, alongX = true)
        val allowedZ = clipAxis(minZ, maxZ, minX + allowedX, maxX + allowedX, dz, blocked, alongX = false)
        out[0] = allowedX
        out[1] = allowedZ
        return allowedX != dx || allowedZ != dz
    }

    /**
     * Movement along one axis: [lo]..[hi] is the hitbox on that axis, [crossLo]..[crossHi] on the other axis.
     * Returns how far it may move toward [delta] before entering a blocked column it is not already in.
     */
    private fun clipAxis(
        lo: Double, hi: Double, crossLo: Double, crossHi: Double,
        delta: Double, blocked: Set<Long>, alongX: Boolean,
    ): Double {
        if (delta == 0.0 || blocked.isEmpty()) return delta
        val crossFirst = floor(crossLo).toInt()
        val crossLast = floor(crossHi - GAP).toInt()
        val nowFirst = floor(lo).toInt()
        val nowLast = floor(hi - GAP).toInt()
        var allowed = delta
        if (delta > 0) {
            val reachLast = floor(hi + delta - GAP).toInt()
            for (cell in nowLast + 1..reachLast) {
                if (!anyBlocked(cell, crossFirst, crossLast, blocked, alongX)) continue
                allowed = minOf(allowed, (cell - hi - GAP).coerceAtLeast(0.0))
                break
            }
        } else {
            val reachFirst = floor(lo + delta).toInt()
            for (cell in nowFirst - 1 downTo reachFirst) {
                if (!anyBlocked(cell, crossFirst, crossLast, blocked, alongX)) continue
                allowed = maxOf(allowed, (cell + 1 - lo + GAP).coerceAtMost(0.0))
                break
            }
        }
        return allowed
    }

    private fun anyBlocked(cell: Int, crossFirst: Int, crossLast: Int, blocked: Set<Long>, alongX: Boolean): Boolean {
        for (cross in crossFirst..crossLast) {
            val key = if (alongX) packXZ(cell, cross) else packXZ(cross, cell)
            if (key in blocked) return true
        }
        return false
    }
}
