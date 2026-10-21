package net.icantpy.qol.path

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Ground A* ported from senura's current `PathFinder` (finder/PathFinder.kt). Pure: the world is read only through [BlockGrid].
 *
 * Mapping of senura's WorldCache calls onto [BlockGrid]:
 * - isWalkable(pos): `(solidFloor(below) || water(here)) && passable(here) && passable(above)`. Water nodes are walkable.
 * - hasValidGround(pos) (wall/corner checks, no water): `solidFloor(below) && passable(here) && passable(above)`.
 * - isPassable(pos): `passable(pos)`. hasPlayerClearance(pos): `passable(pos) && passable(above)`.
 * - senura's isLoaded, decorative-passable, doors and collision-shape checks are not modelled. The grid must encode them
 *   in passable()/solidFloor(). Unloaded chunks should report not passable.
 * - WorldUtils.isWalkable(a, b, true) (simplifyPath's segment test) is ported as [segmentWalkable]; it uses floor ground only.
 *
 * Node keys are packed into a Long (x: 26 bits, y: 12 bits, z: 26 bits, signed). Coordinates outside
 * x/z in +-2^25 or y in +-2048 alias each other. Minecraft never gets near those limits.
 *
 * @param maxIterations  cap on expanded nodes per search.
 * @param maxSearchMillis wall-clock budget per search, checked every 1024 iterations. Partial routes are returned when it runs out.
 * @param clock millisecond clock used for the budget (injectable for tests).
 */
/** Why a search ended. Every outcome except [FOUND] and [GOAL_UNKNOWN] may still return a partial route. */
enum class SearchOutcome {
    /** The goal was reached. */
    FOUND,

    /** No walkable spot near the goal: its chunk is neither loaded nor cached, or the goal is inside solid ground. */
    GOAL_UNKNOWN,

    /** Nothing around the start can be walked to. */
    START_STUCK,

    /** Every reachable node was explored without reaching the goal. */
    EXHAUSTED,

    /** The search hit its node cap. */
    ITERATION_LIMIT,

    /** The search hit its time budget. */
    TIME_LIMIT,
}

class PathFinder(
    private val grid: BlockGrid,
    private val maxIterations: Int = 200_000,
    private val maxSearchMillis: Long = 1500L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Nearest walkable node to [target]: the target itself, then the first walkable node straight down or up (1..32),
     * then the lowest-score walkable node on the nearest shell of cubes (senura's findBestWalkablePosition).
     */
    fun findBestWalkable(target: PathNode): PathNode? {
        if (isWalkable(target.x, target.y, target.z)) return target

        for (offset in 1..32) {
            val down = target.y - offset
            if (isWalkable(target.x, down, target.z)) return PathNode(target.x, down, target.z)
            val up = target.y + offset
            if (isWalkable(target.x, up, target.z)) return PathNode(target.x, up, target.z)
        }

        return findShellWalkable(target)
    }

    /**
     * Simplified waypoints from start to goal. The start is snapped with senura's findStartPosition, the goal with
     * [findBestWalkable]. If the goal is not reached, the path ends at the closest node the search reached. Returns null
     * when the goal cannot be snapped or no node beyond the start is reachable.
     */
    fun findPath(start: PathNode, goal: PathNode): List<PathNode>? {
        val from = findStartPosition(start) ?: start
        val to = findBestWalkable(goal)
        if (to == null) {
            lastOutcome = SearchOutcome.GOAL_UNKNOWN
            lastIterations = 0
            return null
        }
        val raw = search(from, to)
        if (raw.isEmpty()) return null
        return simplify(raw)
    }

    /** How the most recent [findPath] / [findRawPath] ended, for player-facing diagnostics. */
    var lastOutcome: SearchOutcome = SearchOutcome.FOUND
        private set

    /** Nodes expanded by the most recent search. */
    var lastIterations: Int = 0
        private set

    /** Raw (unsimplified) A* path between the given nodes, without snapping. Null if no node beyond the start is reached. */
    fun findRawPath(start: PathNode, goal: PathNode): List<PathNode>? {
        val raw = search(start, goal)
        return if (raw.isEmpty()) null else raw
    }

    private class Node(val key: Long, val f: Int, val h: Int)

    /** senura's ff(start, goal). Returns the path, the partial path to the closest node, or empty. */
    private fun search(start: PathNode, goal: PathNode): List<PathNode> {
        val startKey = pack(start.x, start.y, start.z)
        val goalKey = pack(goal.x, goal.y, goal.z)

        val openSet = PriorityQueue<Node>(compareBy<Node> { it.f }.thenBy { it.h })
        val cameFrom = HashMap<Long, Long>()
        val gScore = HashMap<Long, Int>()
        val closedSet = HashSet<Long>()
        val penaltyCache = HashMap<Long, Int>()
        val deadline = clock() + maxSearchMillis

        gScore[startKey] = 0
        val startH = heuristic(start.x, start.y, start.z, goal.x, goal.y, goal.z)
        openSet.add(Node(startKey, startH, startH))

        var iterations = 0
        var bestFallback = startKey
        var bestHeuristic = fallbackScore(start.x, start.y, start.z, goal.x, goal.y, goal.z)
        var timedOut = false

        while (openSet.isNotEmpty() && iterations < maxIterations) {
            val current = openSet.poll().key
            iterations++
            if ((iterations and 1023) == 0 && clock() > deadline) {
                timedOut = true
                break
            }

            if (!closedSet.add(current)) continue

            val cx = unpackX(current)
            val cy = unpackY(current)
            val cz = unpackZ(current)

            val h = fallbackScore(cx, cy, cz, goal.x, goal.y, goal.z)
            if (h < bestHeuristic) {
                bestHeuristic = h
                bestFallback = current
            }

            if (current == goalKey) {
                lastOutcome = SearchOutcome.FOUND
                lastIterations = iterations
                return reconstructPath(cameFrom, current)
            }

            val currentG = gScore.getValue(current)
            for (neighbor in neighbors(cx, cy, cz)) {
                if (neighbor in closedSet) continue
                val nx = unpackX(neighbor)
                val ny = unpackY(neighbor)
                val nz = unpackZ(neighbor)
                if (!isWalkable(nx, ny, nz)) continue

                val moveCost = moveCost(cx, cy, cz, nx, ny, nz)
                val wallPenalty = penaltyCache.getOrPut(neighbor) { wallAvoidancePenalty(nx, ny, nz) }
                val tentativeG = currentG + moveCost + wallPenalty

                if (tentativeG < (gScore[neighbor] ?: Int.MAX_VALUE)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeG
                    val nh = heuristic(nx, ny, nz, goal.x, goal.y, goal.z)
                    openSet.add(Node(neighbor, tentativeG + nh, nh))
                }
            }
        }

        lastIterations = iterations
        lastOutcome = when {
            timedOut -> SearchOutcome.TIME_LIMIT
            iterations >= maxIterations -> SearchOutcome.ITERATION_LIMIT
            bestFallback == startKey -> SearchOutcome.START_STUCK
            else -> SearchOutcome.EXHAUSTED
        }
        if (bestFallback != startKey) return reconstructPath(cameFrom, bestFallback)
        return emptyList()
    }

    /** senura's wallAvoidancePenalty, with its WorldCache checks mapped onto the grid. */
    private fun wallAvoidancePenalty(x: Int, y: Int, z: Int): Int {
        var penalty = 0

        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue

                val nx = x + dx
                val nz = z + dz
                val isCardinal = dx == 0 || dz == 0
                val feetBlocked = !grid.passable(nx, y, nz)
                val headBlocked = !grid.passable(nx, y + 1, nz)

                if (feetBlocked || headBlocked) {
                    penalty += if (isCardinal) 4 else 2
                }
                if (!hasGround(nx, y, nz)) {
                    penalty += if (isCardinal) 3 else 2
                }
            }
        }

        for (dir in OUTER_OFFSETS) {
            val ox = x + dir[0]
            val oz = z + dir[1]
            if (!grid.passable(ox, y, oz) || !grid.passable(ox, y + 1, oz)) {
                penalty += 1
            }
        }

        return penalty
    }

    /**
     * senura's calculateMoveCost with signed dy: a climb costs dy * 12 and a drop costs -dy * 5, on top of a base of
     * 10 (cardinal) or 14 (diagonal). A 1-block climb (22) therefore costs more than a 1-block drop (15).
     */
    internal fun moveCost(fx: Int, fy: Int, fz: Int, tx: Int, ty: Int, tz: Int): Int {
        val dx = abs(tx - fx)
        val dz = abs(tz - fz)
        val dy = ty - fy

        val baseCost = if (dx != 0 && dz != 0) 14 else 10
        val verticalCost = if (dy > 0) dy * 12 else -dy * 5

        return baseCost + verticalCost
    }

    /**
     * senura's heuristic used for the open-set priority: octile horizontal distance (14 diagonal, 10 straight), plus the
     * same signed vertical term as [moveCost] (climb dy * 12 towards a higher goal, drop -dy * 5 towards a lower one).
     */
    internal fun heuristic(ax: Int, ay: Int, az: Int, bx: Int, by: Int, bz: Int): Int {
        val dx = abs(ax - bx)
        val dz = abs(az - bz)
        val dy = by - ay
        val diag = min(dx, dz)
        val straight = max(dx, dz) - diag
        val vertical = if (dy > 0) dy * 12 else -dy * 5
        return diag * 14 + straight * 10 + vertical
    }

    /** senura's fallbackScore: Euclidean distance (height weighted 1.5) times 10. Used to pick the closest node on partial routes. */
    private fun fallbackScore(ax: Int, ay: Int, az: Int, bx: Int, by: Int, bz: Int): Int {
        val dx = abs(ax - bx).toDouble()
        val dy = abs(ay - by).toDouble()
        val dz = abs(az - bz).toDouble()
        return (sqrt(dx * dx + dy * dy * 1.5 + dz * dz) * 10).toInt()
    }

    /** A node the player can stand on: solid floor or water under/at it, with body and head space. */
    private fun isWalkable(x: Int, y: Int, z: Int): Boolean {
        val floorOrWater = grid.solidFloor(x, y - 1, z) || grid.water(x, y, z)
        return floorOrWater && grid.passable(x, y, z) && grid.passable(x, y + 1, z)
    }

    /** senura's hasValidGround: solid floor below, body and head space. Used for wall/corner checks (no water). */
    private fun hasGround(x: Int, y: Int, z: Int): Boolean =
        grid.solidFloor(x, y - 1, z) && grid.passable(x, y, z) && grid.passable(x, y + 1, z)

    /** senura's hasPlayerClearance. */
    private fun hasClearance(x: Int, y: Int, z: Int): Boolean =
        grid.passable(x, y, z) && grid.passable(x, y + 1, z)

    /**
     * senura's getNeighbors. Each of the 8 directions yields at most one node: flat, else a 1-block climb (cardinal only),
     * else a drop of 1..4 blocks (cardinal only). Diagonals need both orthogonal sides clear and at least one with ground.
     */
    private fun neighbors(x: Int, y: Int, z: Int): List<Long> {
        val result = ArrayList<Long>(8)
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue

                val diagonal = dx != 0 && dz != 0
                if (diagonal) {
                    val sideAx = x + dx
                    val sideBz = z + dz
                    if (!hasClearance(sideAx, y, z) || !hasClearance(x, y, sideBz)) continue
                    if (!hasGround(sideAx, y, z) && !hasGround(x, y, sideBz)) continue
                }

                addValidNeighborsAtPosition(result, x, y, z, dx, dz, !diagonal)
            }
        }
        return result
    }

    private fun addValidNeighborsAtPosition(
        result: MutableList<Long>,
        x: Int,
        y: Int,
        z: Int,
        dx: Int,
        dz: Int,
        allowVertical: Boolean,
    ) {
        val cx = x + dx
        val cz = z + dz
        if (isWalkable(cx, y, cz)) {
            result.add(pack(cx, y, cz))
            return
        }

        if (allowVertical && grid.passable(x, y + 2, z) && isWalkable(cx, y + 1, cz)) {
            result.add(pack(cx, y + 1, cz))
            return
        }

        if (!allowVertical) return
        if (!grid.passable(cx, y, cz) || !grid.passable(cx, y + 1, cz)) return

        for (dy in -1 downTo -MAX_FALL_DISTANCE) {
            if (dy < -1 && !grid.passable(cx, y + dy + 1, cz)) break
            if (isWalkable(cx, y + dy, cz)) {
                result.add(pack(cx, y + dy, cz))
                return
            }
        }
    }

    private fun reconstructPath(cameFrom: Map<Long, Long>, current: Long): List<PathNode> {
        val path = ArrayList<PathNode>()
        var curr: Long? = current
        while (curr != null) {
            path.add(PathNode(unpackX(curr), unpackY(curr), unpackZ(curr)))
            curr = cameFrom[curr]
        }
        path.reverse()
        return path
    }

    /**
     * senura's simplifyPath: from each waypoint jump to the farthest node within 48 steps that a clearance-checked
     * segment reaches. Look-ahead stops before the first node higher than the current waypoint.
     */
    private fun simplify(path: List<PathNode>): List<PathNode> {
        if (path.size < 3) return ArrayList(path)

        val simplified = ArrayList<PathNode>()
        var i = 0
        simplified.add(path[0])

        while (i < path.size - 1) {
            var farthest = i + 1
            val limit = min(path.size - 1, i + 48)

            var k = i + 1
            while (k <= limit && path[k].y <= path[i].y) k++
            val maxLookAhead = k - 1

            for (j in maxLookAhead downTo (i + 2)) {
                if (segmentWalkable(path[i], path[j])) {
                    farthest = j
                    break
                }
            }

            simplified.add(path[farthest])
            i = farthest
        }

        return simplified
    }

    /**
     * senura's findStartPosition: the raw position, then one block down, then one block up, then the shell search.
     */
    private fun findStartPosition(raw: PathNode): PathNode? {
        if (isWalkable(raw.x, raw.y, raw.z)) return raw

        if (isWalkable(raw.x, raw.y - 1, raw.z)) return PathNode(raw.x, raw.y - 1, raw.z)

        if (isWalkable(raw.x, raw.y + 1, raw.z)) return PathNode(raw.x, raw.y + 1, raw.z)

        return findShellWalkable(raw)
    }

    /**
     * senura's findShellWalkable: for radius 1..5, scan the cube shell (Chebyshev distance == radius) and keep the walkable
     * node with the lowest score dx^2 + 2*dy^2 + dz^2. The first strictly better candidate in scan order wins ties.
     */
    private fun findShellWalkable(target: PathNode): PathNode? {
        for (radius in 1..SEARCH_RADIUS) {
            var best: PathNode? = null
            var bestScore = Int.MAX_VALUE

            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (max(max(abs(dx), abs(dy)), abs(dz)) != radius) continue

                        val x = target.x + dx
                        val y = target.y + dy
                        val z = target.z + dz
                        if (!isWalkable(x, y, z)) continue

                        val score = dx * dx + dy * dy * 2 + dz * dz
                        if (score < bestScore) {
                            bestScore = score
                            best = PathNode(x, y, z)
                        }
                    }
                }
            }

            if (best != null) return best
        }

        return null
    }

    /**
     * Port of senura's WorldUtils.isWalkable(start, end, checkClearance = true). Samples the segment between block
     * centres at 0.25-block steps. Each sample checks a 9-point body footprint for feet/head clearance, and needs floor
     * under it. The height may not change by more than one block from the start.
     */
    private fun segmentWalkable(a: PathNode, b: PathNode): Boolean {
        if (a == b) return true

        val sx = a.x + 0.5
        val sy = a.y.toDouble()
        val sz = a.z + 0.5
        val ex = b.x + 0.5
        val ey = b.y.toDouble()
        val ez = b.z + 0.5
        val distance = sqrt((ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) + (ez - sz) * (ez - sz))
        val steps = max(2, (distance * 4.0).toInt())

        for (step in 0..steps) {
            val progress = step.toDouble() / steps
            val cx = sx + (ex - sx) * progress
            val cy = sy + (ey - sy) * progress
            val cz = sz + (ez - sz) * progress

            for (offset in FOOTPRINT) {
                val px = floor(cx + offset[0]).toInt()
                val pz = floor(cz + offset[1]).toInt()
                val feetY = floor(cy + 0.1).toInt()
                val headY = floor(cy + 1.1).toInt()
                if (!grid.passable(px, feetY, pz) || !grid.passable(px, headY, pz)) return false
            }

            val gx = floor(cx).toInt()
            val gy = floor(cy).toInt()
            val gz = floor(cz).toInt()
            val hasFloor = hasGround(gx, gy, gz) || hasGround(gx, gy - 1, gz)
            if (!hasFloor) return false

            if (abs(gy - a.y) > 1) return false
        }
        return true
    }

    companion object {
        private const val MAX_FALL_DISTANCE = 4
        private const val SEARCH_RADIUS = 5

        private val OUTER_OFFSETS = arrayOf(
            intArrayOf(2, 0), intArrayOf(-2, 0), intArrayOf(0, 2), intArrayOf(0, -2),
        )

        private const val RADIUS = 0.38
        private const val DIAG = RADIUS * 0.707
        private val FOOTPRINT = arrayOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(RADIUS, 0.0),
            doubleArrayOf(-RADIUS, 0.0),
            doubleArrayOf(0.0, RADIUS),
            doubleArrayOf(0.0, -RADIUS),
            doubleArrayOf(DIAG, DIAG),
            doubleArrayOf(DIAG, -DIAG),
            doubleArrayOf(-DIAG, DIAG),
            doubleArrayOf(-DIAG, -DIAG),
        )

        private const val MASK26 = (1L shl 26) - 1
        private const val MASK12 = (1L shl 12) - 1

        internal fun pack(x: Int, y: Int, z: Int): Long =
            ((x.toLong() and MASK26) shl 38) or ((y.toLong() and MASK12) shl 26) or (z.toLong() and MASK26)

        internal fun unpackX(key: Long): Int = ((key ushr 38).toInt() shl 6) shr 6
        internal fun unpackY(key: Long): Int = (((key ushr 26).toInt() and 0xFFF) shl 20) shr 20
        internal fun unpackZ(key: Long): Int = (key.toInt() shl 6) shr 6
    }
}
