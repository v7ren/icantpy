package net.icantpy.slayer.aim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyAimPointTest {
    private val out = FloatArray(3)

    @Test
    fun startsAtCenterAndStaysInsideTheHitbox() {
        val point = LazyAimPoint(seed = 7)
        point.sample(0, 1f, out)
        assertEquals(0f, out[0]); assertEquals(0f, out[1]); assertEquals(0f, out[2])
        for (t in 1..2000) {
            point.sample(t * 16L, 1f, out)
            assertTrue(abs(out[0]) <= 1f && abs(out[1]) <= 0.6f && abs(out[2]) <= 1f)
        }
    }

    @Test
    fun zeroStrengthAlwaysAimsAtTheCenter() {
        val point = LazyAimPoint(seed = 3)
        for (t in 0..500) {
            point.sample(t * 16L, 0f, out)
            assertEquals(0f, out[0]); assertEquals(0f, out[1]); assertEquals(0f, out[2])
        }
    }

    @Test
    fun driftsInsteadOfJumping() {
        val point = LazyAimPoint(seed = 11)
        var prev = 0f
        point.sample(0, 1f, out)
        for (t in 1..1000) {
            point.sample(t * 16L, 1f, out)
            // One 16 ms frame with a 350 ms time constant moves at most ~5% of the 2-unit range.
            assertTrue(abs(out[0] - prev) < 0.1f, "jumped from $prev to ${out[0]}")
            prev = out[0]
        }
    }

    @Test
    fun actuallyMovesAwayFromTheCenter() {
        val point = LazyAimPoint(seed = 5)
        var maxOffset = 0f
        for (t in 0..400) {
            point.sample(t * 16L, 1f, out)
            maxOffset = maxOf(maxOffset, abs(out[0]), abs(out[2]))
        }
        assertTrue(maxOffset > 0.2f)
    }

    @Test
    fun resetReturnsToCenter() {
        val point = LazyAimPoint(seed = 9)
        for (t in 0..200) point.sample(t * 16L, 1f, out)
        point.reset()
        point.sample(5000, 1f, out)
        assertEquals(0f, out[0]); assertEquals(0f, out[1]); assertEquals(0f, out[2])
    }
}
