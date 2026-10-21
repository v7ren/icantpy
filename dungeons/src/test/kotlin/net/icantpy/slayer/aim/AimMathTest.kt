package net.icantpy.slayer.aim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AimMathTest {
    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 1e-3f) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }

    @Test
    fun wrapDegreesHandlesSeamAndMultiples() {
        assertNear(180f, AimMath.wrapDegrees(180f))
        assertNear(180f, AimMath.wrapDegrees(-180f))
        assertNear(180f, AimMath.wrapDegrees(540f))
        assertNear(170f, AimMath.wrapDegrees(-190f))
        assertNear(-170f, AimMath.wrapDegrees(190f))
        assertNear(0f, AimMath.wrapDegrees(0f))
    }

    @Test
    fun lookAtUsesMinecraftYawAndPitchConvention() {
        val plusZ = AimMath.lookAt(0.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        assertNear(0f, plusZ.yaw)
        assertNear(0f, plusZ.pitch)

        val minusX = AimMath.lookAt(0.0, 0.0, 0.0, -1.0, 0.0, 0.0)
        assertNear(90f, minusX.yaw)

        val plusX = AimMath.lookAt(0.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        assertNear(-90f, plusX.yaw)

        val up = AimMath.lookAt(0.0, 0.0, 0.0, 0.0, 5.0, 0.0)
        assertNear(-90f, up.pitch)

        val down = AimMath.lookAt(0.0, 0.0, 0.0, 0.0, -5.0, 0.0)
        assertNear(90f, down.pitch)
    }

    @Test
    fun angularDistanceCrossesYawSeamTheShortWay() {
        assertNear(2f, AimMath.angularDistance(AimPose(179f, 0f), AimPose(-179f, 0f)))
        assertNear(3f, AimMath.angularDistance(AimPose(0f, 0f), AimPose(0f, 3f)))
    }

    @Test
    fun deltaTakesTheShortYawPath() {
        val forward = AimMath.delta(AimPose(170f, 10f), AimPose(-170f, 30f))
        assertNear(20f, forward.yaw)
        assertNear(20f, forward.pitch)

        val backward = AimMath.delta(AimPose(-170f, 0f), AimPose(170f, 0f))
        assertNear(-20f, backward.yaw)
    }

    @Test
    fun easingsStartAtZeroEndAtOneAndAreMonotonic() {
        for (easing in AimEasing.values()) {
            assertEquals(0f, AimMath.ease(0f, easing), 1e-6f, "$easing start")
            assertEquals(1f, AimMath.ease(1f, easing), 1e-6f, "$easing end")
            assertEquals(0f, AimMath.ease(-1f, easing), 1e-6f, "$easing clamps low")
            assertEquals(1f, AimMath.ease(2f, easing), 1e-6f, "$easing clamps high")
            var previous = AimMath.ease(0f, easing)
            for (step in 1..100) {
                val value = AimMath.ease(step / 100f, easing)
                assertTrue(value >= previous - 1e-6f, "$easing not monotonic at step $step")
                previous = value
            }
        }
    }

    @Test
    fun easingIdsRoundTrip() {
        for (easing in AimEasing.values()) {
            assertEquals(easing, AimEasing.fromId(easing.id))
        }
        assertEquals(null, AimEasing.fromId("bogus"))
        assertEquals(null, AimEasing.fromId(null))
    }

    @Test
    fun durationClampsToBoundsAndNeverBelowMin() {
        val settings = AimSettings(speedDegPerSec = 180f, minDurationMs = 180, maxDurationMs = 900)
        assertEquals(180L, AimMath.durationMs(0f, settings))
        assertEquals(500L, AimMath.durationMs(90f, settings))
        assertEquals(900L, AimMath.durationMs(1800f, settings))

        val unsafeMin = AimSettings(minDurationMs = 0, maxDurationMs = 0)
        assertTrue(AimMath.durationMs(0f, unsafeMin) >= AimMath.MIN_DURATION_MS)
    }

    @Test
    fun bezierMatchesEndpoints() {
        assertNear(2f, AimMath.bezier(2f, 5f, -3f, 8f, 0f))
        assertNear(8f, AimMath.bezier(2f, 5f, -3f, 8f, 1f))
        assertNear(0.75f, AimMath.bezier(0f, 1f, 1f, 0f, 0.5f))
    }
}
