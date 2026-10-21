package net.icantpy.qol.perf

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameTimeStatsTest {
    private fun ms(value: Double): Long = (value * 1_000_000).toLong()

    @Test
    fun emptyWindowReportsZero() {
        val stats = FrameTimeStats(10)
        assertEquals(0, stats.size)
        assertEquals(0.0, stats.fps())
        assertEquals(0.0, stats.onePercentLowFps())
        assertEquals(0.0, stats.maxFrameMs())
    }

    @Test
    fun steadySixteenMillisecondFramesGiveSixtyFps() {
        val stats = FrameTimeStats(10)
        repeat(10) { stats.record(ms(16.0)) }
        assertEquals(16.0, stats.averageFrameMs(), 1e-6)
        assertEquals(62.5, stats.fps(), 1e-6)
        assertEquals(62.5, stats.onePercentLowFps(), 1e-6)
        assertEquals(16.0, stats.maxFrameMs(), 1e-6)
    }

    @Test
    fun onePercentLowUsesWorstFramesOfWindow() {
        val stats = FrameTimeStats(200)
        repeat(199) { stats.record(ms(10.0)) }
        stats.record(ms(100.0))
        // 1% of 200 frames is 2 frames: worst are 100ms and 10ms -> average 55ms -> 18.18 FPS
        assertEquals(1000.0 / 55.0, stats.onePercentLowFps(), 1e-6)
        // 0.1% of 200 frames rounds up to 1 frame: the 100ms stutter -> 10 FPS
        assertEquals(10.0, stats.pointOnePercentLowFps(), 1e-6)
        assertEquals(100.0, stats.maxFrameMs(), 1e-6)
    }

    @Test
    fun ringBufferKeepsOnlyLatestSamples() {
        val stats = FrameTimeStats(3)
        stats.record(ms(50.0))
        stats.record(ms(50.0))
        stats.record(ms(50.0))
        repeat(3) { stats.record(ms(8.0)) }
        assertEquals(3, stats.size)
        assertEquals(8.0, stats.averageFrameMs(), 1e-6)
        assertEquals(8.0, stats.maxFrameMs(), 1e-6)
    }

    @Test
    fun nonPositiveSamplesAreIgnored() {
        val stats = FrameTimeStats(4)
        stats.record(0L)
        stats.record(-5L)
        assertEquals(0, stats.size)
        stats.record(ms(20.0))
        assertEquals(1, stats.size)
    }

    @Test
    fun clearResetsWindow() {
        val stats = FrameTimeStats(4)
        stats.record(ms(20.0))
        stats.clear()
        assertEquals(0, stats.size)
        assertEquals(0.0, stats.averageFrameMs())
    }
}
