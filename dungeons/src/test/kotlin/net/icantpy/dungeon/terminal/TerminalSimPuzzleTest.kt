package net.icantpy.dungeon.terminal

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalSimPuzzleTest {
    private fun snapshotOf(puzzle: TerminalSimPuzzle): TerminalSnapshot = TerminalSnapshot(
        puzzle.title,
        puzzle.rows,
        puzzle.snapshot().entries.sortedBy { it.key }.map { (slot, sim) ->
            TerminalItem(slot, sim.itemId, sim.count, sim.customName ?: sim.itemId, sim.glint)
        },
    )

    /** Follows the solver's advice until the puzzle reports solved; returns the clicks it took. */
    private fun solveWithSolver(puzzle: TerminalSimPuzzle): Int {
        var clicks = 0
        var waited = 0
        while (!puzzle.solved()) {
            val solution = TerminalSolver.solve(puzzle.type, snapshotOf(puzzle))
            val cell = solution.cells.firstOrNull { it.state == TerminalCellState.TARGET && (it.order == null || it.order == 0) }
            if (cell == null && puzzle.type == TerminalType.MELODY) {
                // Melody only has something to click while the pane is under the column; wait for it.
                puzzle.advance()
                check(++waited < 1_000) { "melody pane never lined up" }
                continue
            }
            cell ?: error("solver found nothing to click in an unsolved ${puzzle.type}: ${puzzle.title}")
            val button = when {
                puzzle.type != TerminalType.RUBIX -> 2
                cell.rightClicks < cell.leftClicks -> 1
                else -> 0
            }
            val slot = TerminalClickPolicy.slotFor(cell, solution) ?: error("no slot for ${cell.slot}")
            assertEquals(SimClickResult.APPLIED, puzzle.click(slot, button), "${puzzle.type} slot $slot")
            clicks++
            check(clicks < 200) { "no progress on ${puzzle.type}" }
        }
        return clicks
    }

    @Test
    fun solverSolvesEverySimulatedTerminalWithoutMisclicks() {
        val random = Random(42)
        TerminalType.entries.forEach { type ->
            repeat(50) {
                val puzzle = TerminalSimPuzzle.random(type, random)
                assertFalse(puzzle.solved(), "$type starts unsolved")
                assertEquals(type, TerminalType.fromTitle(puzzle.title))
                assertTrue(solveWithSolver(puzzle) > 0)
                assertEquals(0, puzzle.misclicks, "$type ${puzzle.title}")
            }
        }
    }

    @Test
    fun wrongClicksCountAsMisclicksAndChangeNothing() {
        val puzzle = TerminalSimPuzzle.random(TerminalType.IN_ORDER, Random(7))
        val highest = puzzle.snapshot().entries.first { it.value.count == 14 }.key
        assertEquals(SimClickResult.MISCLICK, puzzle.click(highest, 2))
        assertEquals(TerminalSimPuzzle.RED, puzzle.slot(highest)?.itemId)
        assertEquals(1, puzzle.misclicks)
        assertEquals(SimClickResult.IGNORED, puzzle.click(0, 2), "border panes do nothing")
    }

    @Test
    fun rubixButtonsTurnOppositeWays() {
        val puzzle = TerminalSimPuzzle.random(TerminalType.RUBIX, Random(3))
        val slot = 12
        val before = RubixColor.fromItemId(puzzle.slot(slot)!!.itemId)!!
        puzzle.click(slot, 2)
        puzzle.click(slot, 1)
        assertEquals(before, RubixColor.fromItemId(puzzle.slot(slot)!!.itemId))
        puzzle.click(slot, 0)
        puzzle.click(slot, 1)
        assertEquals(before, RubixColor.fromItemId(puzzle.slot(slot)!!.itemId), "left and middle both turn forward")
    }
}
