package net.icantpy.qol.invsort

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvSortRunStateTest {
    private val start = mapOf(9 to "a", 10 to "b", 11 to "c")
    private val target = mapOf(9 to "c", 10 to "a", 11 to "b")
    private val clicks = InvSortPlanner.plan(start, target)

    private val msNanos = 1_000_000L

    private fun newState(
        minMs: Int = 100,
        maxMs: Int = 100,
        moveCursor: Boolean = false,
        openDelayMs: Int = 0,
        instant: Boolean = false,
        keys: Map<Int, String?> = start,
        planned: List<Int> = clicks,
    ) = InvSortRunState(
        startKeys = keys,
        clicks = planned,
        timing = InvSortTiming(minMs, maxMs, instant = instant, random = Random(5)),
        moveCursor = moveCursor,
        startNanos = 0L,
        openDelayMs = openDelayMs,
    )

    /** Polls once per millisecond until the state finishes, recording when each step fired. */
    private fun drain(state: InvSortRunState): List<Pair<Long, InvSortStep>> {
        val fired = mutableListOf<Pair<Long, InvSortStep>>()
        var now = 0L
        while (now < 60_000 * msNanos) {
            val step = state.poll(now)
            if (step != null) {
                fired += now to step
                if (step == InvSortStep.Finished) return fired
            }
            now += msNanos
        }
        error("run never finished")
    }

    @Test fun clicksFollowThePlannedSequenceAndFinish() {
        val steps = drain(newState()).map { it.second }
        val clickSlots = steps.filterIsInstance<InvSortStep.Click>().map { it.slot }
        assertEquals(clicks, clickSlots)
        assertIs<InvSortStep.Finished>(steps.last())
    }

    @Test fun expectedKeysMatchPlannerStateBeforeEachClick() {
        var slots: Map<Int, String?> = start
        var carried: String? = null
        drain(newState()).map { it.second }.filterIsInstance<InvSortStep.Click>().forEach { click ->
            assertEquals(slots[click.slot], click.expectedSlotKey)
            assertEquals(carried, click.expectedCarriedKey)
            val result = InvSortPlanner.applyPickup(slots, carried, click.slot)
            slots = result.slots
            carried = result.carried
        }
        assertEquals(target, slots.filterKeys { it in target.keys })
    }

    @Test fun firstClickWaitsForOpenDelay() {
        val state = newState(openDelayMs = 220)
        assertNull(state.poll(219 * msNanos))
        assertIs<InvSortStep.Click>(state.poll(220 * msNanos))
    }

    @Test fun laterClicksWaitTheFixedDelay() {
        val fired = drain(newState(minMs = 150, maxMs = 150))
        val clickTimes = fired.filter { it.second is InvSortStep.Click }.map { it.first }
        clickTimes.zipWithNext().forEach { (previous, next) ->
            assertEquals(150 * msNanos, next - previous)
        }
    }

    @Test fun moveCursorPrecedesEveryClick() {
        val steps = drain(newState(moveCursor = true)).map { it.second }
        val moveSlots = steps.filterIsInstance<InvSortStep.MoveCursor>().map { it.slot }
        val clickSlots = steps.filterIsInstance<InvSortStep.Click>().map { it.slot }
        assertEquals(clickSlots, moveSlots)
        steps.zipWithNext().filter { it.second is InvSortStep.Click }.forEach { (before, click) ->
            assertIs<InvSortStep.MoveCursor>(before)
            assertEquals((click as InvSortStep.Click).slot, before.slot)
        }
    }

    @Test fun cursorMoveAddsOneTickNotARandomDelay() {
        val fired = drain(newState(minMs = 100, maxMs = 100, moveCursor = true))
        val firstMove = fired.first { it.second is InvSortStep.MoveCursor }.first
        val firstClick = fired.first { it.second is InvSortStep.Click }.first
        assertTrue(firstClick - firstMove <= msNanos, "click should follow the move on the next poll")
    }

    @Test fun sampledGapsStayWithinConfiguredBounds() {
        val fired = drain(newState(minMs = 90, maxMs = 170))
        val clickTimes = fired.filter { it.second is InvSortStep.Click }.map { it.first }
        clickTimes.zipWithNext().forEach { (previous, next) ->
            val gapMs = (next - previous) / msNanos
            assertTrue(gapMs in 90..171, "gap $gapMs ms outside bounds")
        }
    }

    /** Polls repeatedly at one instant, as the runner does inside a single tick. */
    private fun pollSameTick(state: InvSortRunState, nowNanos: Long, limit: Int = 1_000): List<InvSortStep> {
        val steps = mutableListOf<InvSortStep>()
        repeat(limit) {
            val step = state.poll(nowNanos) ?: return steps
            steps += step
            if (step == InvSortStep.Finished) return steps
        }
        error("instant run did not finish within one tick")
    }

    @Test fun instantRunFinishesWithinOneTickWithoutWaits() {
        val state = newState(instant = true, openDelayMs = 220, moveCursor = true, minMs = 170, maxMs = 170)
        val steps = pollSameTick(state, nowNanos = 0L)
        assertEquals(clicks, steps.filterIsInstance<InvSortStep.Click>().map { it.slot })
        assertEquals(clicks, steps.filterIsInstance<InvSortStep.MoveCursor>().map { it.slot })
        assertIs<InvSortStep.Finished>(steps.last())
    }

    @Test fun instantRunWithoutCursorMoveFinishesInOneTick() {
        val steps = pollSameTick(newState(instant = true, openDelayMs = 220), nowNanos = 0L)
        assertEquals(clicks, steps.filterIsInstance<InvSortStep.Click>().map { it.slot })
        assertTrue(steps.none { it is InvSortStep.MoveCursor })
        assertIs<InvSortStep.Finished>(steps.last())
    }

    @Test fun instantRunKeepsExpectedKeysAndSlotChecks() {
        var slots: Map<Int, String?> = start
        var carried: String? = null
        pollSameTick(newState(instant = true), nowNanos = 0L).filterIsInstance<InvSortStep.Click>().forEach { click ->
            assertEquals(slots[click.slot], click.expectedSlotKey)
            assertEquals(carried, click.expectedCarriedKey)
            val result = InvSortPlanner.applyPickup(slots, carried, click.slot)
            slots = result.slots
            carried = result.carried
        }
    }

    /** The move for the next click starts right after the previous click, inside its delay. */
    @Test fun cursorMoveOverlapsTheDelayAfterAClick() {
        val fired = drain(newState(minMs = 100, maxMs = 100, moveCursor = true))
        val firstClick = fired.indexOfFirst { it.second is InvSortStep.Click }
        val nextMove = fired.drop(firstClick + 1).first { it.second is InvSortStep.MoveCursor }
        assertTrue(nextMove.first < fired[firstClick].first + 100 * msNanos, "move waited for the delay to end")
    }

    @Test fun emptyPlanFinishesImmediately() {
        val steps = drain(newState(planned = emptyList())).map { it.second }
        assertEquals(listOf<InvSortStep>(InvSortStep.Finished), steps)
    }
}
