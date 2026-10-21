package net.icantpy.slayer.blaze

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Movement keys, relative to where the player looks, that move toward a bearing. */
data class KeyCombo(val forward: Boolean, val back: Boolean, val left: Boolean, val right: Boolean)

enum class RelativeDirection(val label: String, val arrow: String) {
    FORWARD("forward", "↑"), FORWARD_RIGHT("forward-right", "↗"), RIGHT("right", "→"), BACK_RIGHT("back-right", "↘"),
    BACK("back", "↓"), BACK_LEFT("back-left", "↙"), LEFT("left", "←"), FORWARD_LEFT("forward-left", "↖"),
}

object FirePitEscape {
    /** A safe point to move to. [bearingDeg] uses Minecraft yaw convention: 0 = +Z (south), 90 = -X (west). */
    data class Escape(val targetX: Double, val targetZ: Double, val distance: Double, val bearingDeg: Float)

    private const val BEARING_COUNT = 32
    private const val STEP = 0.25
    private const val MIN_DISTANCE = 0.5
    private const val EPSILON = 1e-9

    /** True when the player's footprint (point inflated by [margin]) overlaps any hazard column [x, x+1) x [z, z+1). */
    fun isInside(px: Double, pz: Double, hazards: Set<Long>, margin: Double): Boolean {
        if (hazards.isEmpty()) return false
        val minX = px - margin
        val maxX = px + margin
        val minZ = pz - margin
        val maxZ = pz + margin
        for (x in floor(minX).toInt()..floor(maxX).toInt()) {
            if (!(x < maxX && x + 1 > minX)) continue
            for (z in floor(minZ).toInt()..floor(maxZ).toInt()) {
                if (z < maxZ && z + 1 > minZ && packXZ(x, z) in hazards) return true
            }
        }
        return false
    }

    /**
     * Nearest safe point. Distances grow in [STEP] increments; at the first distance with a safe candidate, the
     * bearing whose straight path crosses the fewest hazard columns wins.
     */
    fun solve(
        px: Double,
        pz: Double,
        hazards: Set<Long>,
        margin: Double,
        maxDistance: Double,
        standable: (Int, Int) -> Boolean,
    ): Escape? {
        if (!isInside(px, pz, hazards, margin)) return null
        val steps = floor((maxDistance - MIN_DISTANCE) / STEP + EPSILON).toInt()
        for (k in 0..steps) {
            val distance = MIN_DISTANCE + k * STEP
            val candidates = safeCandidates(px, pz, distance, hazards, margin, standable)
            if (candidates.isNotEmpty()) return candidates.minBy { crossedColumns(px, pz, it, hazards) }
        }
        return null
    }

    private fun safeCandidates(
        px: Double,
        pz: Double,
        distance: Double,
        hazards: Set<Long>,
        margin: Double,
        standable: (Int, Int) -> Boolean,
    ): List<Escape> {
        val result = ArrayList<Escape>(BEARING_COUNT)
        for (i in 0 until BEARING_COUNT) {
            val yaw = i * 360.0 / BEARING_COUNT
            val tx = px + directionX(yaw) * distance
            val tz = pz + directionZ(yaw) * distance
            if (isInside(tx, tz, hazards, margin)) continue
            if (!standable(floor(tx).toInt(), floor(tz).toInt())) continue
            result += Escape(tx, tz, distance, bearing(px, pz, tx, tz))
        }
        return result
    }

    /** Distinct hazard columns touched by the straight segment from the player to the escape, sampled every [STEP]. */
    private fun crossedColumns(px: Double, pz: Double, escape: Escape, hazards: Set<Long>): Int {
        val crossed = HashSet<Long>()
        var s = 0.0
        while (s <= escape.distance + EPSILON) {
            val fraction = s / escape.distance
            val x = floor(px + (escape.targetX - px) * fraction).toInt()
            val z = floor(pz + (escape.targetZ - pz) * fraction).toInt()
            val key = packXZ(x, z)
            if (key in hazards) crossed += key
            s += STEP
        }
        return crossed.size
    }

    /** Bearing (yaw convention) from (fromX, fromZ) to (toX, toZ). */
    fun bearing(fromX: Double, fromZ: Double, toX: Double, toZ: Double): Float {
        // Inverse of (dx, dz) = (-sin yaw, cos yaw).
        val dx = toX - fromX
        val dz = toZ - fromZ
        return normalizeDegrees(Math.toDegrees(atan2(-dx, dz))).toFloat()
    }

    /**
     * Which of 8 directions [bearingDeg] is relative to the player's [playerYaw]. A positive relative angle is the
     * player's right: the right vector for yaw t is (-cos t, -sin t), which is the bearing t + 90.
     */
    fun relativeDirection(playerYaw: Float, bearingDeg: Float): RelativeDirection {
        val relative = normalizeDegrees((bearingDeg - playerYaw).toDouble())
        val sector = floor((relative + 22.5) / 45.0).toInt().mod(8)
        return RelativeDirection.entries[sector]
    }

    /** W/A/S/D combination that moves toward [bearingDeg] while looking at [playerYaw]. */
    fun movementKeys(playerYaw: Float, bearingDeg: Float): KeyCombo {
        return when (relativeDirection(playerYaw, bearingDeg)) {
            RelativeDirection.FORWARD -> KeyCombo(forward = true, back = false, left = false, right = false)
            RelativeDirection.FORWARD_RIGHT -> KeyCombo(forward = true, back = false, left = false, right = true)
            RelativeDirection.RIGHT -> KeyCombo(forward = false, back = false, left = false, right = true)
            RelativeDirection.BACK_RIGHT -> KeyCombo(forward = false, back = true, left = false, right = true)
            RelativeDirection.BACK -> KeyCombo(forward = false, back = true, left = false, right = false)
            RelativeDirection.BACK_LEFT -> KeyCombo(forward = false, back = true, left = true, right = false)
            RelativeDirection.LEFT -> KeyCombo(forward = false, back = false, left = true, right = false)
            RelativeDirection.FORWARD_LEFT -> KeyCombo(forward = true, back = false, left = true, right = false)
        }
    }

    private fun directionX(yawDeg: Double): Double = -sin(Math.toRadians(yawDeg))

    private fun directionZ(yawDeg: Double): Double = cos(Math.toRadians(yawDeg))

    /** Wraps any angle into (-180, 180]. */
    private fun normalizeDegrees(angle: Double): Double {
        val wrapped = ((angle + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return if (wrapped <= -180.0) wrapped + 360.0 else wrapped
    }
}
