package net.icantpy.qol.path

import kotlin.math.sqrt

/** Plain 3D point, so the spline code does not depend on Minecraft's Vec3. */
data class Vec(val x: Double, val y: Double, val z: Double) {
    fun lerp(other: Vec, t: Double): Vec =
        Vec(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t)

    fun distanceTo(other: Vec): Double {
        val dx = other.x - x
        val dy = other.y - y
        val dz = other.z - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * Pure port of senura's PathSplineUtil. Path nodes are sampled at block centres (x+0.5, y+0.5, z+0.5), as
 * Vec3.atCenterOf does, and a Catmull-Rom curve is walked from [fromIndex] for [lookDistance] blocks.
 */
object PathSpline {
    const val SMOOTH_ALPHA = 0.28

    /**
     * Look-target point [lookDistance] blocks ahead along the spline from path index [fromIndex].
     * The returned y is [py]. Pass eye-level y as [py], as senura does with `playerPos.y + eyeHeight`.
     * With an empty path the player position itself is returned.
     */
    fun lookAhead(
        path: List<PathNode>,
        fromIndex: Int,
        px: Double,
        py: Double,
        pz: Double,
        lookDistance: Double = 5.0,
    ): Vec {
        if (path.isEmpty()) return Vec(px, py, pz)

        val points = path.map { Vec(it.x + 0.5, it.y + 0.5, it.z + 0.5) }
        val start = fromIndex.coerceIn(0, points.lastIndex)
        val raw = sampleDistanceAlong(points, start, lookDistance)
        return Vec(raw.x, py, raw.z)
    }

    /** senura's smoothing: exponential blend from [previous] toward [next]. With no previous value, [next] is returned. */
    fun smooth(previous: Vec?, next: Vec, alpha: Double = SMOOTH_ALPHA): Vec =
        previous?.lerp(next, alpha) ?: next

    private fun sampleDistanceAlong(points: List<Vec>, startIndex: Int, distance: Double): Vec {
        if (points.size == 1 || startIndex >= points.lastIndex) {
            return points.last()
        }

        var remaining = distance.coerceAtLeast(0.0)
        var i = startIndex
        while (i < points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val seg = a.distanceTo(b)
            if (seg >= remaining) {
                val t = if (seg <= 1e-6) 0.0 else remaining / seg
                return catmullOnSegment(points, i, t)
            }
            remaining -= seg
            i++
        }
        return points.last()
    }

    private fun catmullOnSegment(points: List<Vec>, i: Int, t: Double): Vec {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[(i + 1).coerceAtMost(points.lastIndex)]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
        return catmull(p0, p1, p2, p3, t)
    }

    private fun catmull(p0: Vec, p1: Vec, p2: Vec, p3: Vec, t: Double): Vec {
        val t2 = t * t
        val t3 = t2 * t
        return Vec(
            0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
            0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
            0.5 * ((2 * p1.z) + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3),
        )
    }
}
