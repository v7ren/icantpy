package net.icantpy.slayer.blaze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HazardClipTest {
    private val out = DoubleArray(2)
    private val half = 0.3 // player hitbox is 0.6 wide

    private fun clip(px: Double, pz: Double, dx: Double, dz: Double, vararg blocked: Pair<Int, Int>): Boolean =
        HazardClip.clip(
            px - half, pz - half, px + half, pz + half, dx, dz,
            blocked.map { (x, z) -> packXZ(x, z) }.toSet(), out,
        )

    @Test
    fun freeMoveIsUnchanged() {
        assertFalse(clip(0.5, 0.5, 0.2, 0.1, 5 to 5))
        assertEquals(0.2, out[0], 1e-9)
        assertEquals(0.1, out[1], 1e-9)
    }

    @Test
    fun stopsAtTheEdgeOfFireAhead() {
        // Standing in column 0 (x 0.2..0.8), fire in column 1: may move at most 0.2 before touching x = 1.
        assertTrue(clip(0.5, 0.5, 0.5, 0.0, 1 to 0))
        assertTrue(out[0] < 0.2 && out[0] > 0.19)
        assertEquals(0.0, out[1], 1e-9)
    }

    @Test
    fun stopsGoingNegativeToo() {
        assertTrue(clip(0.5, 0.5, -0.5, 0.0, -1 to 0))
        assertTrue(out[0] > -0.2 && out[0] < -0.19)
    }

    @Test
    fun slidesAlongFireWhenMovingDiagonally() {
        // Fire to the east; moving north-east keeps the north component.
        assertTrue(clip(0.5, 0.5, 0.5, -0.3, 1 to 0, 1 to -1))
        assertTrue(out[0] < 0.2)
        assertEquals(-0.3, out[1], 1e-9)
    }

    @Test
    fun alreadyInsideFireCanAlwaysLeave() {
        // Hitbox overlaps column 0, which is fire: walking out to column 1 (free) is allowed.
        assertFalse(clip(0.5, 0.5, 0.6, 0.0, 0 to 0))
        assertEquals(0.6, out[0], 1e-9)
    }

    @Test
    fun hitboxStraddlingTwoColumnsIsBlockedByEither() {
        // Hitbox spans z columns 0 and 1; fire only at (1, 1) still blocks moving east.
        assertTrue(clip(0.5, 1.0, 0.5, 0.0, 1 to 1))
        assertTrue(out[0] < 0.2)
    }
}
