package net.icantpy.qol.camera

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreecamTest {
    private val origin = FreecamPos(0.0, 0.0, 0.0)

    private fun near(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-6, "expected $expected, was $actual")

    @Test
    fun forwardAtYawZeroGoesSouthAtTheConfiguredSpeed() {
        val next = FreecamMath.step(origin, 0f, 0f, FreecamKeys(forward = true), speed = 10.0, seconds = 0.1)
        near(0.0, next.x)
        near(0.0, next.y)
        near(1.0, next.z)
    }

    @Test
    fun forwardFollowsThePitch() {
        val next = FreecamMath.step(origin, 0f, 90f, FreecamKeys(forward = true), speed = 10.0, seconds = 0.1)
        near(-1.0, next.y)
    }

    @Test
    fun strafeRightAtYawZeroGoesTowardsMinusX() {
        val next = FreecamMath.step(origin, 0f, 0f, FreecamKeys(right = true), speed = 10.0, seconds = 0.1)
        near(-1.0, next.x)
    }

    @Test
    fun diagonalMovementIsNotFasterThanStraight() {
        val next = FreecamMath.step(origin, 0f, 0f, FreecamKeys(forward = true, right = true), speed = 10.0, seconds = 0.1)
        near(1.0, kotlin.math.sqrt(next.x * next.x + next.y * next.y + next.z * next.z))
    }

    @Test
    fun opposingKeysCancelAndBoostTriplesSpeed() {
        assertEquals(origin, FreecamMath.step(origin, 0f, 0f, FreecamKeys(forward = true, back = true), 10.0, 0.1))
        val boosted = FreecamMath.step(origin, 0f, 0f, FreecamKeys(up = true, boost = true), speed = 10.0, seconds = 0.1)
        near(3.0, boosted.y)
    }

    @Test
    fun aLongFrameIsClampedSoAStallDoesNotTeleport() {
        val next = FreecamMath.step(origin, 0f, 0f, FreecamKeys(forward = true), speed = 10.0, seconds = 5.0)
        near(1.0, next.z)
    }

    @Test
    fun configClampsAndIgnoresBadValues() {
        val parsed = FreecamConfig.parse("""{"enabled":true,"key":70,"speed":9999,"blockClicks":false}""")
        assertTrue(parsed.enabled)
        assertEquals(70, parsed.key)
        assertEquals(FreecamConfig.MAX_SPEED, parsed.speed)
        assertEquals(false, parsed.blockClicks)
        assertEquals(FreecamConfig(), FreecamConfig.parse("not json"))
        assertEquals(FreecamConfig.UNBOUND, FreecamConfig.parse("""{"key":0}""").key)
    }
}
