package net.icantpy.qol.fishing

import kotlin.test.Test
import kotlin.test.assertEquals

class FishingCycleTest {
    private val timing = FishingTiming(reelMinMs = 100, reelMaxMs = 100, castMinMs = 200, castMaxMs = 200, noBiteMs = 10_000L)

    private fun FishingCycle.at(now: Long, hook: Boolean, bite: Boolean = false) = step(hook, bite, now, timing)

    @Test
    fun castsAfterTheCastDelayAndOnlyOnce() {
        val cycle = FishingCycle()
        assertEquals(FishingAction.NONE, cycle.at(1_000, hook = false).action)
        assertEquals(FishingAction.NONE, cycle.at(1_199, hook = false).action)
        assertEquals(FishingAction.CAST, cycle.at(1_200, hook = false).action)
        assertEquals(FishingAction.NONE, cycle.at(1_300, hook = false).action)
    }

    @Test
    fun reelsAfterTheReelDelayOnceTheBiteShows() {
        val cycle = FishingCycle()
        cycle.at(0, hook = false)
        cycle.at(200, hook = false)
        assertEquals(FishingAction.NONE, cycle.at(300, hook = true).action)
        assertEquals(FishingAction.NONE, cycle.at(2_000, hook = true).action)
        assertEquals(FishingAction.NONE, cycle.at(2_050, hook = true, bite = true).action)
        assertEquals(FishingAction.REEL, cycle.at(2_150, hook = true, bite = true).action)
    }

    @Test
    fun castsAgainAfterTheHookIsGone() {
        val cycle = FishingCycle()
        cycle.at(0, hook = false)
        cycle.at(200, hook = false)
        cycle.at(300, hook = true)
        cycle.at(1_000, hook = true, bite = true)
        assertEquals(FishingAction.REEL, cycle.at(1_100, hook = true, bite = true).action)
        assertEquals(FishingAction.NONE, cycle.at(1_200, hook = false).action)
        assertEquals(FishingAction.NONE, cycle.at(1_300, hook = false).action)
        assertEquals(FishingAction.CAST, cycle.at(1_500, hook = false).action)
    }

    @Test
    fun stopsAfterThreeCastsThatNeverShowAHook() {
        val cycle = FishingCycle()
        var now = 0L
        var stop = FishingStop.NONE
        repeat(40) {
            now += 1_000
            val step = cycle.at(now, hook = false)
            if (step.stop != FishingStop.NONE) stop = step.stop
        }
        assertEquals(FishingStop.CAST_FAILED, stop)
    }

    @Test
    fun stopsAfterThreeQuietTimeoutsInARow() {
        val cycle = FishingCycle()
        var now = 0L
        var hook = false
        var stop = FishingStop.NONE
        var reels = 0
        repeat(2_000) {
            now += 100
            val step = cycle.at(now, hook = hook)
            // A hook that never bites: it appears once cast and goes away once reeled.
            if (step.action == FishingAction.CAST) hook = true
            if (step.action == FishingAction.REEL) {
                reels++
                hook = false
            }
            if (step.stop != FishingStop.NONE) stop = step.stop
        }
        assertEquals(FishingStop.NO_BITES, stop)
        assertEquals(2, reels)
    }

    @Test
    fun reelsAndRecastsWhenNothingBitesForTheTimeout() {
        val cycle = FishingCycle()
        cycle.at(0, hook = false)
        cycle.at(200, hook = false)
        cycle.at(300, hook = true)
        assertEquals(FishingAction.NONE, cycle.at(10_300, hook = true).action)
        val timeout = cycle.at(10_301, hook = true)
        assertEquals(FishingAction.REEL, timeout.action)
        assertEquals(true, timeout.quiet)
    }

    @Test
    fun aReelForABiteIsNotQuiet() {
        val cycle = FishingCycle()
        cycle.at(0, hook = false)
        cycle.at(200, hook = false)
        cycle.at(300, hook = true)
        cycle.at(1_000, hook = true, bite = true)
        val reel = cycle.at(1_100, hook = true, bite = true)
        assertEquals(FishingAction.REEL, reel.action)
        assertEquals(false, reel.quiet)
    }
}
