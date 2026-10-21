package net.icantpy.qol.invsort

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvSortTimingTest {
    @Test fun seededDelaysStayWithinBoundsAndCoverBothEnds() {
        val timing = InvSortTiming(minMs = 90, maxMs = 170, random = Random(42))
        val samples = List(10_000) { timing.sampleDelayMs() }
        assertTrue(samples.all { it in 90..170 })
        assertTrue(90 in samples)
        assertTrue(170 in samples)
    }

    @Test fun equalBoundsAlwaysReturnThatValue() {
        val timing = InvSortTiming(minMs = 120, maxMs = 120, random = Random(7))
        assertTrue(List(100) { timing.sampleDelayMs() }.all { it == 120 })
    }

    @Test fun reversedBoundsAreOrderedBeforeSampling() {
        val timing = InvSortTiming(minMs = 300, maxMs = 100, random = Random(1))
        assertEquals(100, timing.minDelayMs)
        assertEquals(300, timing.maxDelayMs)
    }

    @Test fun outOfRangeBoundsAreClamped() {
        val timing = InvSortTiming(minMs = -1, maxMs = 50_000, random = Random(3))
        assertEquals(InvSortLimits.MIN_CLICK_DELAY_MS, timing.minDelayMs)
        assertEquals(InvSortLimits.MAX_CLICK_DELAY_MS, timing.maxDelayMs)
    }

    @Test fun zeroIsAValidFastDelay() {
        val timing = InvSortTiming(minMs = 0, maxMs = 0, random = Random(3))
        assertEquals(0, InvSortLimits.MIN_CLICK_DELAY_MS)
        assertTrue(List(50) { timing.sampleDelayMs() }.all { it == 0 })
    }

    @Test fun instantTimingYieldsZeroDelays() {
        val timing = InvSortTiming(minMs = 90, maxMs = 170, instant = true, random = Random(9))
        assertTrue(List(100) { timing.sampleDelayMs() }.all { it == 0 })
        assertEquals(0L, timing.openDelayNanos(requestedMs = 220))
    }

    @Test fun nonInstantOpenDelayUsesRequestedMilliseconds() {
        val timing = InvSortTiming(minMs = 90, maxMs = 170, random = Random(9))
        assertEquals(220L * 1_000_000L, timing.openDelayNanos(requestedMs = 220))
    }
}
