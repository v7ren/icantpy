package net.icantpy.qol.path

import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In-memory grid from a char map. Row index is z, column index is x.
 * - '#' or a digit d: a solid block at (x, d, z) ('#' is 0), so the player stands at y = d + 1 on it.
 * - 'W': a 3-high wall, solid at y = 0, 1, 2.
 * - '~': water at (x, 0, z), no solid block.
 * - anything else ('.'): empty air, no floor.
 * Everything outside the map is empty air.
 */
private class CharGrid(rows: List<String>) : BlockGrid {
    private val solid = HashSet<Triple<Int, Int, Int>>()
    private val water = HashSet<Triple<Int, Int, Int>>()

    init {
        rows.forEachIndexed { z, row ->
            row.forEachIndexed { x, c ->
                when {
                    c == '#' -> solid.add(Triple(x, 0, z))
                    c.isDigit() -> solid.add(Triple(x, c - '0', z))
                    c == 'W' -> for (y in 0..2) solid.add(Triple(x, y, z))
                    c == '~' -> water.add(Triple(x, 0, z))
                }
            }
        }
    }

    override fun passable(x: Int, y: Int, z: Int): Boolean = Triple(x, y, z) !in solid
    override fun solidFloor(x: Int, y: Int, z: Int): Boolean = Triple(x, y, z) in solid
    override fun water(x: Int, y: Int, z: Int): Boolean = Triple(x, y, z) in water
}

private fun openGrid(width: Int, depth: Int): CharGrid =
    CharGrid(List(depth) { "#".repeat(width) })

class PathFinderTest {
    @Test
    fun straightCorridorSimplifiesToStartAndGoal() {
        val grid = CharGrid(listOf("#####"))
        val finder = PathFinder(grid)
        val start = PathNode(0, 1, 0)
        val goal = PathNode(4, 1, 0)

        val raw = assertNotNull(finder.findRawPath(start, goal))
        assertEquals(start, raw.first())
        assertEquals(goal, raw.last())
        assertEquals(5, raw.size)

        assertEquals(listOf(start, goal), finder.findPath(start, goal))
    }

    @Test
    fun goesAroundWall() {
        val grid = CharGrid(
            listOf(
                "#####",
                "#WWW#",
                "#####",
            ),
        )
        val finder = PathFinder(grid)
        val start = PathNode(0, 1, 1)
        val goal = PathNode(4, 1, 1)

        val raw = assertNotNull(finder.findRawPath(start, goal))
        assertEquals(goal, raw.last())
        assertTrue(raw.none { it.z == 1 && it.x in 1..3 }, "path must not enter the wall: $raw")
        assertTrue(raw.size > 5, "path must detour around the wall: $raw")
        raw.forEach { assertTrue(isStandable(grid, it), "node $it is not walkable") }
    }

    @Test
    fun noDiagonalThroughTouchingCorners() {
        // Walls at (1,0) and (0,1): the diagonal from (0,0) to (1,1) squeezes between two corners.
        val grid = CharGrid(
            listOf(
                "#W",
                "W#",
            ),
        )
        val finder = PathFinder(grid)
        val start = PathNode(0, 1, 0)
        val goal = PathNode(1, 1, 1)

        assertNull(finder.findRawPath(start, goal))
        assertNull(finder.findPath(start, goal))
    }

    @Test
    fun diagonalAllowedInOpenGrid() {
        // Control for the corner test: with open sides the diagonal is a single step.
        val finder = PathFinder(openGrid(2, 2))
        val raw = assertNotNull(finder.findRawPath(PathNode(0, 1, 0), PathNode(1, 1, 1)))
        assertEquals(2, raw.size)
    }

    @Test
    fun stepsUpOneBlock() {
        // Raised block at x=3 (top y=1). The player stands at y=2 on it.
        val grid = CharGrid(listOf("0001"))
        val finder = PathFinder(grid)
        val goal = PathNode(3, 2, 0)

        val raw = assertNotNull(finder.findRawPath(PathNode(0, 1, 0), goal))
        assertEquals(goal, raw.last())
        assertEquals(PathNode(2, 1, 0), raw[raw.size - 2], "climb must start from the lower floor: $raw")
    }

    @Test
    fun dropsDownOneBlock() {
        // Raised block at x=0 (top y=1). The player stands at y=2 on it, and must drop to y=1.
        val grid = CharGrid(listOf("1000"))
        val finder = PathFinder(grid)
        val goal = PathNode(3, 1, 0)

        val raw = assertNotNull(finder.findRawPath(PathNode(0, 2, 0), goal))
        assertEquals(goal, raw.last())
        assertTrue(raw.contains(PathNode(1, 1, 0)), "drop onto the lower floor expected: $raw")
    }

    @Test
    fun dropLimitedToFourBlocks() {
        // Floor 5 blocks down from the start is out of reach (max drop is 4).
        val grid = CharGrid(listOf("500000"))
        val finder = PathFinder(grid)

        assertNull(finder.findRawPath(PathNode(0, 6, 0), PathNode(3, 1, 0)))
    }

    @Test
    fun unreachableGoalReturnsPartialPathTowardIt() {
        // A 3-high wall at x=2 cuts the corridor. The search can only reach x=0 and x=1.
        val grid = CharGrid(listOf("##W##"))
        val finder = PathFinder(grid)
        val start = PathNode(0, 1, 0)
        val goal = PathNode(4, 1, 0)

        val raw = assertNotNull(finder.findRawPath(start, goal))
        assertEquals(start, raw.first())
        assertEquals(PathNode(1, 1, 0), raw.last(), "partial path should end at the closest reachable node: $raw")

        assertEquals(listOf(start, PathNode(1, 1, 0)), finder.findPath(start, goal))
    }

    @Test
    fun simplifiedPathStaysCollisionFree() {
        val grid = CharGrid(
            listOf(
                "#######",
                "#WWWW##",
                "#######",
                "##WWW##",
                "#######",
                "#######",
            ),
        )
        val finder = PathFinder(grid)
        val start = PathNode(1, 1, 0)
        val goal = PathNode(5, 1, 4)

        val raw = assertNotNull(finder.findRawPath(start, goal))
        val simplified = assertNotNull(finder.findPath(start, goal))

        assertTrue(simplified.size <= raw.size)
        assertEquals(start, simplified.first())
        assertEquals(goal, simplified.last())
        simplified.forEach { assertTrue(isStandable(grid, it), "node $it is not walkable") }
        for (i in 0 until simplified.size - 1) {
            assertSegmentClear(grid, simplified[i], simplified[i + 1])
        }
    }

    @Test
    fun findBestWalkableSnapsAirTargetDown() {
        val grid = CharGrid(listOf("###"))
        val finder = PathFinder(grid)

        assertEquals(PathNode(1, 1, 0), finder.findBestWalkable(PathNode(1, 5, 0)))
    }

    @Test
    fun findBestWalkableReturnsWalkableTargetUnchanged() {
        val finder = PathFinder(CharGrid(listOf("###")))
        assertEquals(PathNode(1, 1, 0), finder.findBestWalkable(PathNode(1, 1, 0)))
    }

    @Test
    fun iterationCapStopsSearchOnBigOpenGrid() {
        val grid = openGrid(60, 60)
        val goal = PathNode(59, 1, 59)

        val capped = PathFinder(grid, maxIterations = 50)
        val partial = assertNotNull(capped.findRawPath(PathNode(0, 1, 0), goal))
        assertTrue(partial.size <= 51, "at most one node per iteration: ${partial.size}")
        assertTrue(partial.last() != goal, "goal must not be reached under the cap")

        val full = PathFinder(grid)
        val reached = assertNotNull(full.findRawPath(PathNode(0, 1, 0), goal))
        assertEquals(goal, reached.last())
    }

    @Test
    fun waterNodeIsWalkable() {
        // A water cell in a row of floor: the player can wade through it at y=0.
        val grid = CharGrid(listOf("#~#"))
        val finder = PathFinder(grid)

        val raw = assertNotNull(finder.findRawPath(PathNode(0, 1, 0), PathNode(2, 1, 0)))
        assertEquals(PathNode(2, 1, 0), raw.last())
        assertTrue(raw.any { it.x == 1 }, "path should cross the water cell: $raw")
    }

    @Test
    fun climbCostsMoreThanDrop() {
        val finder = PathFinder(openGrid(1, 1))

        assertEquals(10, finder.moveCost(0, 1, 0, 1, 1, 0), "flat cardinal step")
        assertEquals(22, finder.moveCost(0, 1, 0, 1, 2, 0), "1-block climb is 10 + 12")
        assertEquals(15, finder.moveCost(0, 2, 0, 1, 1, 0), "1-block drop is 10 + 5")
        assertEquals(14, finder.moveCost(0, 1, 0, 1, 1, 1), "flat diagonal step")
        assertTrue(finder.moveCost(0, 1, 0, 1, 2, 0) > finder.moveCost(0, 2, 0, 1, 1, 0))
    }

    @Test
    fun heuristicIsOctileWithSignedVerticalTerm() {
        val finder = PathFinder(openGrid(1, 1))

        assertEquals(30, finder.heuristic(0, 0, 0, 3, 0, 0), "3 straight")
        assertEquals(38, finder.heuristic(0, 0, 0, 2, 0, 3), "2 diagonal + 1 straight")
        assertEquals(24, finder.heuristic(0, 0, 0, 0, 2, 0), "goal 2 above: climb 2 * 12")
        assertEquals(10, finder.heuristic(0, 2, 0, 0, 0, 0), "goal 2 below: drop 2 * 5")
    }

    @Test
    fun equalCostSearchIsDeterministic() {
        val finder = PathFinder(openGrid(8, 8))
        val first = assertNotNull(finder.findRawPath(PathNode(0, 1, 0), PathNode(7, 1, 7)))
        assertEquals(PathNode(7, 1, 7), first.last())
        repeat(3) {
            assertEquals(first, finder.findRawPath(PathNode(0, 1, 0), PathNode(7, 1, 7)))
        }
    }

    @Test
    fun timeLimitStopsSearchAtFirstCheck() {
        // Clock reads 0 for the deadline, then 1000 ms. With a 1 ms budget the first check (iteration 1024) stops the search.
        // The goal lies off the grid, so the search can never finish early: an open-grid goal is reached in ~300
        // iterations, before the first time check.
        var calls = 0
        val clock: () -> Long = { if (calls++ == 0) 0L else 1_000L }
        val goal = PathNode(1000, 1, 1000)
        val finder = PathFinder(openGrid(300, 300), maxIterations = 200_000, maxSearchMillis = 1L, clock = clock)

        val partial = assertNotNull(finder.findRawPath(PathNode(0, 1, 0), goal))
        assertTrue(partial.last() != goal, "search must stop before reaching the goal")
        assertTrue(partial.size <= 1025, "at most one new node per iteration: ${partial.size}")
        assertEquals(2, calls, "one deadline read plus one check at iteration 1024")
    }

    @Test
    fun tinyTimeBudgetReturnsQuicklyOnHugeGrid() {
        // The goal is one block above the floor, so it is unreachable and the search would otherwise run to the cap.
        val goal = PathNode(399, 50, 399)
        val finder = PathFinder(openGrid(400, 400), maxSearchMillis = 1L)

        val started = System.currentTimeMillis()
        val result = finder.findRawPath(PathNode(0, 1, 0), goal)
        val elapsed = System.currentTimeMillis() - started

        assertTrue(result == null || result.last() != goal, "unreachable goal must not be reached")
        assertTrue(elapsed < 5_000, "search took ${elapsed}ms")
    }

    @Test
    fun findPathSnapsStartDownToFloor() {
        val finder = PathFinder(CharGrid(listOf("###")))

        val path = assertNotNull(finder.findPath(PathNode(1, 3, 0), PathNode(2, 1, 0)))
        assertEquals(listOf(PathNode(1, 1, 0), PathNode(2, 1, 0)), path)
    }

    @Test
    fun findBestWalkableFallsBackToShellSearch() {
        // x=3 has no floor, so the column search fails and the radius-1 shell finds (2,1,0).
        val finder = PathFinder(CharGrid(listOf("###")))
        assertEquals(PathNode(2, 1, 0), finder.findBestWalkable(PathNode(3, 1, 0)))
    }

    // Helpers: a standable node has floor (or water) under it and body/head space.
    private fun isStandable(grid: BlockGrid, n: PathNode): Boolean =
        (grid.solidFloor(n.x, n.y - 1, n.z) || grid.water(n.x, n.y, n.z)) &&
            grid.passable(n.x, n.y, n.z) && grid.passable(n.x, n.y + 1, n.z)

    // Samples the segment between block centres and checks the body stays in passable space.
    private fun assertSegmentClear(grid: BlockGrid, a: PathNode, b: PathNode) {
        val steps = 40
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val x = floor(a.x + 0.5 + (b.x - a.x) * t).toInt()
            val y = floor(a.y + (b.y - a.y) * t + 0.1).toInt()
            val z = floor(a.z + 0.5 + (b.z - a.z) * t).toInt()
            assertTrue(grid.passable(x, y, z) && grid.passable(x, y + 1, z), "segment $a -> $b blocked at ($x,$y,$z)")
        }
    }

    @Test
    fun outcomeIsFoundWhenTheGoalIsReached() {
        val finder = PathFinder(openGrid(6, 6))
        assertNotNull(finder.findPath(PathNode(0, 1, 0), PathNode(5, 1, 5)))
        assertEquals(SearchOutcome.FOUND, finder.lastOutcome)
    }

    @Test
    fun outcomeIsGoalUnknownWhenNothingWalkableIsNearTheGoal() {
        // Far outside the grid: no floor anywhere near the goal, as with an unloaded, uncached chunk.
        val finder = PathFinder(openGrid(6, 6))
        assertNull(finder.findPath(PathNode(0, 1, 0), PathNode(500, 1, 500)))
        assertEquals(SearchOutcome.GOAL_UNKNOWN, finder.lastOutcome)
    }

    @Test
    fun outcomeIsTimeLimitWhenTheBudgetRunsOut() {
        var calls = 0
        val clock: () -> Long = { if (calls++ == 0) 0L else 1_000L }
        val finder = PathFinder(openGrid(300, 300), maxSearchMillis = 1L, clock = clock)
        finder.findRawPath(PathNode(0, 1, 0), PathNode(1000, 1, 1000))
        assertEquals(SearchOutcome.TIME_LIMIT, finder.lastOutcome)
    }

}
