package net.icantpy.slayer.blaze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FirePitTrackerTest {
    @Test
    fun packRoundTripsNegativeAndLargeCoordinates() {
        val cases = listOf(0 to 0, -5 to 7, -1 to -1, 30_000_000 to -30_000_000, Int.MIN_VALUE to Int.MAX_VALUE)
        for ((x, z) in cases) {
            val key = packXZ(x, z)
            assertEquals(x, unpackX(key), "x of ($x, $z)")
            assertEquals(z, unpackZ(key), "z of ($x, $z)")
        }
    }

    @Test
    fun pitBlocksPresentAtFirstSightingAreIgnored() {
        val tracker = FirePitTracker()
        tracker.observe(0, 1, 1, isPitBlock = true)
        tracker.observe(1, 1, 1, isPitBlock = true)
        assertTrue(tracker.hazards(1).isEmpty())
        assertEquals(0, tracker.hazardCount(1))
    }

    @Test
    fun falseToTrueTransitionCreatesHazard() {
        val tracker = FirePitTracker()
        tracker.observe(0, 1, 1, isPitBlock = false)
        tracker.observe(5, 1, 1, isPitBlock = true)
        assertTrue(packXZ(1, 1) in tracker.hazards(5))
        assertEquals(1, tracker.hazardCount(5))
    }

    @Test
    fun hazardExpiresAfterWindowEvenIfStillPit() {
        val tracker = FirePitTracker(windowTicks = 60)
        tracker.observe(0, 1, 1, isPitBlock = false)
        tracker.observe(5, 1, 1, isPitBlock = true)
        tracker.observe(30, 1, 1, isPitBlock = true)
        assertTrue(packXZ(1, 1) in tracker.hazards(64))
        assertFalse(packXZ(1, 1) in tracker.hazards(65))
    }

    @Test
    fun observedFalseClearsHazardImmediately() {
        val tracker = FirePitTracker()
        tracker.observe(0, 1, 1, isPitBlock = false)
        tracker.observe(5, 1, 1, isPitBlock = true)
        tracker.observe(10, 1, 1, isPitBlock = false)
        assertTrue(tracker.hazards(11).isEmpty())
    }

    @Test
    fun newTransitionAfterClearStartsNewWindow() {
        val tracker = FirePitTracker(windowTicks = 60)
        tracker.observe(0, 1, 1, isPitBlock = false)
        tracker.observe(5, 1, 1, isPitBlock = true)
        tracker.observe(10, 1, 1, isPitBlock = false)
        tracker.observe(20, 1, 1, isPitBlock = true)
        assertTrue(packXZ(1, 1) in tracker.hazards(79))
        assertFalse(packXZ(1, 1) in tracker.hazards(80))
    }

    @Test
    fun hazardsReturnsReusedSet() {
        val tracker = FirePitTracker()
        val first = tracker.hazards(0)
        val second = tracker.hazards(1)
        assertSame(first, second)
    }

    @Test
    fun pruneForgetsColumnsNotObservedRecently() {
        val tracker = FirePitTracker()
        tracker.observe(0, 5, 5, isPitBlock = false)
        tracker.prune(300)
        // The pruned column is unknown again, so a pit block seen now is first sighting, not a transition.
        tracker.observe(301, 5, 5, isPitBlock = true)
        assertTrue(tracker.hazards(301).isEmpty())
    }

    @Test
    fun pruneKeepsRecentlyObservedColumns() {
        val tracker = FirePitTracker()
        tracker.observe(0, 5, 5, isPitBlock = false)
        tracker.observe(150, 5, 5, isPitBlock = false)
        tracker.prune(300)
        tracker.observe(301, 5, 5, isPitBlock = true)
        assertTrue(packXZ(5, 5) in tracker.hazards(301))
    }

    @Test
    fun clearDropsAllState() {
        val tracker = FirePitTracker()
        tracker.observe(0, 1, 1, isPitBlock = false)
        tracker.observe(1, 1, 1, isPitBlock = true)
        tracker.clear()
        assertEquals(0, tracker.hazardCount(2))
        tracker.observe(3, 1, 1, isPitBlock = true)
        assertTrue(tracker.hazards(3).isEmpty())
    }
}
