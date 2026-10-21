package net.icantpy.qol.path

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathSplineTest {
    private val corridor = (0..10).map { PathNode(it, 1, 0) }

    @Test
    fun lookAheadStaysOnPathAndKeepsPlayerHeight() {
        val v = PathSpline.lookAhead(corridor, 0, 0.5, 2.62, 0.5, lookDistance = 5.0)

        // Block centres are at z=0.5 and y=1.5. Look-ahead is 5 blocks along the corridor from index 0 (x=5.5).
        assertEquals(5.5, v.x, 1e-9)
        assertEquals(0.5, v.z, 1e-9)
        assertEquals(2.62, v.y, 1e-9)
    }

    @Test
    fun lookAheadMovesForwardAsFromIndexGrows() {
        val xs = (0..5).map { PathSpline.lookAhead(corridor, it, 0.5, 2.0, 0.5).x }

        for (i in 0 until xs.size - 1) {
            assertTrue(xs[i + 1] > xs[i], "x should increase: $xs")
        }
        assertEquals(8.5, PathSpline.lookAhead(corridor, 3, 0.5, 2.0, 0.5).x, 1e-9)
    }

    @Test
    fun lookAheadClampsToLastNode() {
        val v = PathSpline.lookAhead(corridor, 100, 0.5, 2.0, 0.5)
        assertEquals(10.5, v.x, 1e-9)
    }

    @Test
    fun lookAheadWithEmptyPathReturnsPlayerPosition() {
        assertEquals(Vec(1.0, 2.0, 3.0), PathSpline.lookAhead(emptyList(), 0, 1.0, 2.0, 3.0))
    }

    @Test
    fun smoothWithoutPreviousReturnsNext() {
        val next = Vec(1.0, 2.0, 3.0)
        assertEquals(next, PathSpline.smooth(null, next))
    }

    @Test
    fun smoothBlendsWithAlpha() {
        val prev = Vec(0.0, 0.0, 0.0)
        val next = Vec(10.0, -10.0, 4.0)
        val v = PathSpline.smooth(prev, next, alpha = 0.28)

        assertEquals(2.8, v.x, 1e-9)
        assertEquals(-2.8, v.y, 1e-9)
        assertEquals(1.12, v.z, 1e-9)
    }

    @Test
    fun smoothDefaultAlphaIs028() {
        val v = PathSpline.smooth(Vec(0.0, 0.0, 0.0), Vec(100.0, 0.0, 0.0))
        assertEquals(28.0, v.x, 1e-9)
    }
}
