package net.icantpy.qol.invsort

import net.icantpy.qol.auto.CursorGuard
import net.icantpy.qol.auto.CursorGuard.SafetyLevel
import net.icantpy.qol.auto.CursorGuard.Verdict
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives [InvSortRunState] through [InvSortPacer] the way [InvSortRunner.tick] does, with a fake client that
 * applies every click at once (as vanilla does) and confirms the server state immediately. Ticks are 50 ms.
 */
class InvSortPacerTest {
    private val tickMs = 50L

    private class Trace(
        val clickTicks: List<Int>,
        val clickTimesMs: List<Long>,
        val moves: Int,
        val closeTick: Int,
        val checkpoints: Int,
    )

    private fun simulate(
        start: Map<Int, String?>,
        target: Map<Int, String>,
        level: SafetyLevel,
        instant: Boolean,
        moveCursor: Boolean,
        delayMs: Int = 100,
        maxTicks: Int = 500,
    ): Trace {
        val clicks = InvSortPlanner.plan(start, target)
        val timing = InvSortTiming(delayMs, delayMs, instant = instant, random = Random(3))
        val state = InvSortRunState(start, clicks, timing, moveCursor, startNanos = 0L, openDelayMs = 0)
        val pacer = InvSortPacer()
        var keys = start
        var carried: String? = null
        var finishing = false
        val clickTicks = mutableListOf<Int>()
        val clickTimes = mutableListOf<Long>()
        var moves = 0
        var checkpoints = 0
        var tick = 0
        while (tick < maxTicks) {
            tick++
            val nowMs = if (instant) 0L else tick * tickMs
            pacer.beginTick()
            val budget = if (instant) InvSortTiming.MAX_CLICKS_PER_TICK else 1
            for (i in 0 until budget) {
                val verdict = pacer.observe(nowMs) { watched ->
                    CursorGuard.Snapshot(carried, watched.associateWith { keys[it] }, stateId = null)
                }
                if (verdict == Verdict.WAITING) break
                if (verdict != null) {
                    assertEquals(Verdict.SYNCED, verdict, "client and plan disagree at tick $tick")
                    if (finishing) return Trace(clickTicks, clickTimes, moves, tick, checkpoints)
                }
                val step = state.poll(nowMs * 1_000_000L) ?: break
                when (step) {
                    is InvSortStep.MoveCursor -> moves++
                    is InvSortStep.Swap -> error("click-only simulation never plans a hotbar swap")
                    is InvSortStep.Click -> {
                        clickTicks += tick
                        clickTimes += nowMs
                        val expectation = CursorGuard.expectAfterClick(carried, keys[step.slot], step.slot)
                        val result = InvSortPlanner.applyPickup(keys, carried, step.slot)
                        keys = result.slots
                        carried = result.carried
                        if (carried == null) checkpoints++
                        pacer.arm(CursorGuard.Sync(expectation, nowMs, CursorGuard.STEP_TIMEOUT_MS, level.clickSettle(expectation.carried == null)))
                    }
                    InvSortStep.Finished -> {
                        // Never finish or close with an item on the cursor.
                        assertEquals(null, carried)
                        assertEquals(target, keys.filterKeys { it in target.keys })
                        pacer.arm(CursorGuard.Sync(CursorGuard.Expectation(null, emptyMap()), nowMs, CursorGuard.STEP_TIMEOUT_MS, level.closeSettle))
                        finishing = true
                        break
                    }
                }
            }
        }
        error("sort never finished")
    }

    /** Three swaps, each a pickup, a place onto the other item, and a place back: three checkpoints. */
    private val threeSwapStart = mapOf(9 to "a", 10 to "b", 11 to "c", 12 to "d", 13 to "e", 14 to "f")
    private val threeSwapTarget = mapOf(9 to "b", 10 to "a", 11 to "d", 12 to "c", 13 to "f", 14 to "e")

    @Test fun instantThreeSwapBalancedFinishesWithinSixTicks() {
        val trace = simulate(threeSwapStart, threeSwapTarget, SafetyLevel.BALANCED, instant = true, moveCursor = true)
        assertEquals(3, trace.checkpoints)
        // Each swap ends with an empty cursor and costs one tick to settle; the close needs two more observations
        // after the finish checkpoint's own tick. Derived bound: checkpoints + 3 = 6.
        assertEquals(trace.checkpoints + 3, trace.closeTick)
        assertTrue(trace.closeTick <= 6, "took ${trace.closeTick} ticks")
    }

    @Test fun instantThreeSwapStrictIsSlowerThanBalanced() {
        val balanced = simulate(threeSwapStart, threeSwapTarget, SafetyLevel.BALANCED, instant = true, moveCursor = true)
        val strict = simulate(threeSwapStart, threeSwapTarget, SafetyLevel.STRICT, instant = true, moveCursor = true)
        assertTrue(strict.closeTick > balanced.closeTick)
    }

    @Test fun nonInstantClicksAreExactlyOneDelayApartWhenSyncIsReady() {
        for (level in listOf(SafetyLevel.FAST, SafetyLevel.BALANCED)) {
            for (move in listOf(true, false)) {
                val trace = simulate(threeSwapStart, threeSwapTarget, level, instant = false, moveCursor = move)
                val gaps = trace.clickTimesMs.zipWithNext { a, b -> b - a }
                assertTrue(gaps.isNotEmpty())
                assertTrue(gaps.all { it == 100L }, "$level move=$move gaps $gaps")
            }
        }
    }

    @Test fun strictWaitsForTwoObservationsBetweenClicks() {
        val trace = simulate(threeSwapStart, threeSwapTarget, SafetyLevel.STRICT, instant = false, moveCursor = true)
        val gaps = trace.clickTimesMs.zipWithNext { a, b -> b - a }
        // Move costs one tick and two confirmation ticks follow each click; the 100 ms delay overlaps them.
        assertTrue(gaps.all { it >= 150L }, "STRICT gaps $gaps")
    }

    @Test fun safetyLevelsMapToTheDocumentedSettleTicks() {
        assertEquals(0, SafetyLevel.FAST.sequenceSettle)
        assertEquals(1, SafetyLevel.FAST.closeSettle)
        assertEquals(1, SafetyLevel.BALANCED.sequenceSettle)
        assertEquals(2, SafetyLevel.BALANCED.closeSettle)
        assertEquals(2, SafetyLevel.STRICT.sequenceSettle)
        assertEquals(3, SafetyLevel.STRICT.closeSettle)
        // Mid-sequence clicks need no settle except in strict mode, which keeps the old per-click confirmation.
        assertEquals(0, SafetyLevel.BALANCED.clickSettle(endsEmpty = false))
        assertEquals(1, SafetyLevel.BALANCED.clickSettle(endsEmpty = true))
        assertEquals(0, SafetyLevel.FAST.clickSettle(endsEmpty = false))
        assertEquals(CursorGuard.SETTLE_TICKS, SafetyLevel.STRICT.clickSettle(endsEmpty = false))
        assertEquals(CursorGuard.SETTLE_TICKS, SafetyLevel.STRICT.clickSettle(endsEmpty = true))
    }

    @Test fun safetyLevelParsesNamesAndDefaultsToBalanced() {
        assertEquals(SafetyLevel.STRICT, SafetyLevel.parse("strict"))
        assertEquals(SafetyLevel.FAST, SafetyLevel.parse("FAST"))
        assertEquals(SafetyLevel.BALANCED, SafetyLevel.parse("bogus"))
        assertEquals(SafetyLevel.BALANCED, SafetyLevel.parse(null))
        assertEquals(SafetyLevel.STRICT, SafetyLevel.BALANCED.next())
        assertEquals(SafetyLevel.FAST, SafetyLevel.STRICT.next())
    }

    @Test fun checkpointIsNotObservedOnTheTickItWasArmed() {
        val pacer = InvSortPacer()
        pacer.beginTick()
        pacer.arm(CursorGuard.Sync(CursorGuard.Expectation(null, mapOf(9 to "a")), 0, CursorGuard.STEP_TIMEOUT_MS, 1))
        val matching = { _: Set<Int> -> CursorGuard.Snapshot(null, mapOf(9 to "a"), null) }
        // Even a matching view does not release a checkpoint on the tick of its click.
        assertEquals(Verdict.WAITING, pacer.observe(0, matching))
        pacer.beginTick()
        assertEquals(Verdict.SYNCED, pacer.observe(50, matching))
        assertEquals(null, pacer.observe(100, matching), "nothing pending once settled")
    }

    @Test fun fastCheckpointSettlesAtOnceAndIsNeverPendingAfter() {
        val pacer = InvSortPacer()
        pacer.beginTick()
        pacer.arm(CursorGuard.Sync(CursorGuard.Expectation(null, mapOf(9 to "a")), 0, CursorGuard.STEP_TIMEOUT_MS, 0))
        assertEquals(Verdict.SYNCED, pacer.observe(0) { CursorGuard.Snapshot(null, mapOf(9 to "a"), null) })
        assertTrue(!pacer.pending)
    }

    @Test fun closeNeedsItsSettleTicksAfterFinishing() {
        val pacer = InvSortPacer()
        val close = CursorGuard.Expectation(null, emptyMap())
        val empty = { _: Set<Int> -> CursorGuard.Snapshot(null, emptyMap(), null) }
        pacer.beginTick()
        pacer.arm(CursorGuard.Sync(close, 0, CursorGuard.STEP_TIMEOUT_MS, SafetyLevel.BALANCED.closeSettle))
        assertEquals(Verdict.WAITING, pacer.observe(0, empty))
        pacer.beginTick()
        assertEquals(Verdict.WAITING, pacer.observe(50, empty))
        pacer.beginTick()
        assertEquals(Verdict.SYNCED, pacer.observe(100, empty))
    }

    @Test fun divergenceMidSequenceIsReportedAfterTheSecondStableMismatch() {
        val pacer = InvSortPacer()
        // A mid-sequence click leaves "a" on the cursor; the server instead shows an empty cursor.
        val expectation = CursorGuard.Expectation("a", mapOf(10 to null))
        pacer.beginTick()
        pacer.arm(CursorGuard.Sync(expectation, 0, CursorGuard.STEP_TIMEOUT_MS, 0))
        val wrong = { _: Set<Int> -> CursorGuard.Snapshot(null, mapOf(10 to null), null) }
        assertEquals(Verdict.WAITING, pacer.observe(0, wrong))
        pacer.beginTick()
        assertEquals(Verdict.DIVERGED, pacer.observe(50, wrong))
        assertTrue(!pacer.pending)
    }

    @Test fun mismatchThatNeverSettlesTimesOutInsteadOfProceeding() {
        val pacer = InvSortPacer()
        pacer.beginTick()
        // Settle 2 means alternating matching and mismatching views never agree twice in a row.
        pacer.arm(CursorGuard.Sync(CursorGuard.Expectation("a", mapOf(10 to null)), 0, 100, 2))
        var verdict: Verdict? = null
        var now = 0L
        var n = 0
        while (verdict == null || verdict == Verdict.WAITING) {
            pacer.beginTick()
            verdict = pacer.observe(now) { CursorGuard.Snapshot(if (n++ % 2 == 0) "a" else null, mapOf(10 to null), null) }
            now += 50
        }
        assertEquals(Verdict.TIMED_OUT, verdict)
    }
}
