package net.icantpy.modules.impl.dungeon.leaporient

import net.icantpy.modules.impl.timer.ClockSnapshot
import net.icantpy.modules.impl.timer.TimerClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapClockWatcherTest {
    @Test
    fun pyLightningAtZeroTargetsTankWhenZeroIsSkipped() {
        val trigger = LeapRoutePreset.rows(LeapStormRoute.PY).first { it.id == "mage-py-lightning-tank" }
        val watcher = LeapClockWatcher()
        assertEquals("tank", trigger.targetValue)
        assertEquals(ClockSpec.remaining(TimerClock.LIGHTNING, 0.0), trigger.clock)
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, 2), listOf(trigger)).isEmpty())
        assertEquals(listOf(trigger), watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)))
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)).isEmpty())
    }

    @Test
    fun skippedZeroCrossingRequiresAnObservedPositiveCountdown() {
        for (clock in TimerClock.entries.filterNot { it.countsUp() }) {
            val spec = ClockSpec.remaining(clock, 0.0)
            assertTrue(LeapClocks.crossed(2, -1, spec), clock.name)
            assertFalse(LeapClocks.crossed(-1, -1, spec), clock.name)
            assertFalse(LeapClocks.crossed(-1, 0, spec), clock.name)
            assertFalse(LeapClocks.crossed(0, -1, spec), clock.name)
        }
    }

    @Test
    fun skippedPositiveThresholdCrossesOnCountdownCompletion() {
        val spec = ClockSpec.remaining(TimerClock.LIGHTNING, 0.1)
        assertTrue(LeapClocks.crossed(4, -1, spec))
        assertFalse(LeapClocks.crossed(1, -1, spec))
    }

    @Test
    fun elapsedAndRisingSpecsDoNotTreatInactivityAsZero() {
        assertFalse(LeapClocks.crossed(2, -1, ClockSpec.stormElapsed(0.0)))
        assertFalse(LeapClocks.crossed(2, -1, ClockSpec.remaining(TimerClock.LIGHTNING, 0.0).copy(
            crossing = ClockCrossing.RISING,
        )))
        assertFalse(LeapClocks.crossed(2, -1, ClockSpec.remaining(TimerClock.LIGHTNING, 0.0).copy(
            metric = ClockMetric.ELAPSED,
        )))
    }

    @Test
    fun afterZeroStartsWhenPositiveCountdownSkipsToInactive() {
        val trigger = trigger(TimerClock.LIGHTNING, -0.1)
        val watcher = LeapClockWatcher()
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, 2), listOf(trigger)).isEmpty())
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)).isEmpty())
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)).isEmpty())
        assertEquals(listOf(trigger), watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)))
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)).isEmpty())
    }

    @Test
    fun afterZeroSurvivesObservedZeroBecomingInactive() {
        val trigger = trigger(TimerClock.LIGHTNING, -0.1)
        val watcher = LeapClockWatcher()
        watcher.evaluate(snapshot(TimerClock.LIGHTNING, 1), listOf(trigger))
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, 0), listOf(trigger)).isEmpty())
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)).isEmpty())
        assertEquals(listOf(trigger), watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)))
    }

    @Test
    fun initialInactiveSnapshotsNeverFireZeroOrAfterZero() {
        val triggers = listOf(trigger(TimerClock.LIGHTNING, 0.0), trigger(TimerClock.LIGHTNING, -0.1))
        val watcher = LeapClockWatcher()
        repeat(5) {
            assertTrue(watcher.evaluate(ClockSnapshot(emptyMap()), triggers).isEmpty())
        }
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, 0), triggers).isEmpty())
        repeat(5) {
            assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), triggers).isEmpty())
        }
    }

    @Test
    fun restartRearmsZeroAndClearsPendingAfterZero() {
        val atZero = trigger(TimerClock.LIGHTNING, 0.0)
        val afterZero = trigger(TimerClock.LIGHTNING, -0.1)
        val triggers = listOf(atZero, afterZero)
        val watcher = LeapClockWatcher()
        repeat(2) {
            assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, 2), triggers).isEmpty())
            assertEquals(listOf(atZero), watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), triggers))
            assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), triggers).isEmpty())
        }
        assertEquals(listOf(afterZero), watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), triggers))
        assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), triggers).isEmpty())
    }

    @Test
    fun restartRearmsAfterZeroAfterItHasFired() {
        val trigger = trigger(TimerClock.LIGHTNING, -0.1)
        val watcher = LeapClockWatcher()
        repeat(2) {
            assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, 2), listOf(trigger)).isEmpty())
            assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)).isEmpty())
            assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)).isEmpty())
            assertEquals(listOf(trigger), watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)))
            assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), listOf(trigger)).isEmpty())
        }
    }

    @Test
    fun resetDoesNotTurnPreviousCountdownIntoAZeroCrossing() {
        val triggers = listOf(trigger(TimerClock.LIGHTNING, 0.0), trigger(TimerClock.LIGHTNING, -0.1))
        val watcher = LeapClockWatcher()
        watcher.evaluate(snapshot(TimerClock.LIGHTNING, 1), triggers)
        watcher.reset()
        repeat(5) {
            assertTrue(watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), triggers).isEmpty())
        }
        watcher.evaluate(snapshot(TimerClock.LIGHTNING, 1), triggers)
        assertEquals(listOf(triggers.first()), watcher.evaluate(snapshot(TimerClock.LIGHTNING, -1), triggers))
    }

    @Test
    fun loopingPadAfterZeroKeepsItsDelayAndRearms() {
        val trigger = trigger(TimerClock.PAD, -1.0)
        val watcher = LeapClockWatcher()
        watcher.evaluate(snapshot(TimerClock.PAD, 1), listOf(trigger))
        assertTrue(watcher.evaluate(snapshot(TimerClock.PAD, 0), listOf(trigger)).isEmpty())
        repeat(2) {
            for (remaining in 19 downTo 1) {
                assertTrue(watcher.evaluate(snapshot(TimerClock.PAD, remaining), listOf(trigger)).isEmpty())
            }
            assertEquals(listOf(trigger), watcher.evaluate(snapshot(TimerClock.PAD, 0), listOf(trigger)))
        }
    }

    @Test
    fun elapsedAfterInactiveStillFiresOnceAndRearms() {
        val trigger = trigger(TimerClock.STORM_ELAPSED, -0.1)
        val watcher = LeapClockWatcher()
        repeat(2) {
            assertTrue(watcher.evaluate(snapshot(TimerClock.STORM_ELAPSED, 5), listOf(trigger)).isEmpty())
            assertTrue(watcher.evaluate(snapshot(TimerClock.STORM_ELAPSED, -1), listOf(trigger)).isEmpty())
            assertTrue(watcher.evaluate(snapshot(TimerClock.STORM_ELAPSED, -1), listOf(trigger)).isEmpty())
            assertEquals(listOf(trigger), watcher.evaluate(snapshot(TimerClock.STORM_ELAPSED, -1), listOf(trigger)))
            assertTrue(watcher.evaluate(snapshot(TimerClock.STORM_ELAPSED, -1), listOf(trigger)).isEmpty())
        }
    }

    private fun trigger(clock: TimerClock, seconds: Double): LeapOrientTrigger = LeapOrientTrigger(
        id = "${clock.name}-$seconds",
        event = LeapTriggerEvent.CLOCK,
        clock = ClockSpec.forSeconds(clock, seconds),
    )

    private fun snapshot(clock: TimerClock, value: Int): ClockSnapshot = ClockSnapshot(mapOf(clock to value))
}
