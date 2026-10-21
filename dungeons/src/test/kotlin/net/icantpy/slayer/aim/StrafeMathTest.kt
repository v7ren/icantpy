package net.icantpy.slayer.aim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrafeMathTest {
    private fun near(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-9, "expected $expected but was $actual")

    @Test
    fun backOfFacingSouthIsNorth() {
        val out = DoubleArray(2)
        StrafeMath.backward(0f, out)
        near(0.0, out[0])
        near(-1.0, out[1])
    }

    @Test
    fun backOfFacingWestIsEast() {
        // Yaw 90 faces -X (west), so S moves toward +X.
        val out = DoubleArray(2)
        StrafeMath.backward(90f, out)
        near(1.0, out[0])
        near(0.0, out[1])
    }

    @Test
    fun tapFollowsAnAttackAfterTheGap() {
        assertTrue(StrafeMath.tapDue(now = 20, lastAttackTick = 18, attackHeld = false, lastTapEndTick = 10, gapTicks = 2))
        assertFalse(StrafeMath.tapDue(now = 11, lastAttackTick = 11, attackHeld = false, lastTapEndTick = 10, gapTicks = 2))
    }

    @Test
    fun noTapWithoutANewAttack() {
        assertFalse(StrafeMath.tapDue(now = 20, lastAttackTick = 9, attackHeld = false, lastTapEndTick = 10, gapTicks = 2))
        assertTrue(StrafeMath.tapDue(now = 20, lastAttackTick = 9, attackHeld = true, lastTapEndTick = 10, gapTicks = 2))
    }
}
