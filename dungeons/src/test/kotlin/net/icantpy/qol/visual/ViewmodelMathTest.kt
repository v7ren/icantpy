package net.icantpy.qol.visual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewmodelMathTest {
    private val modes = SwingMode.entries
    private val tolerance = 1e-4f

    private fun assertPoseNear(expected: SwingPose, actual: SwingPose) {
        assertEquals(expected.tx, actual.tx, tolerance)
        assertEquals(expected.ty, actual.ty, tolerance)
        assertEquals(expected.tz, actual.tz, tolerance)
        assertEquals(expected.rx, actual.rx, tolerance)
        assertEquals(expected.ry, actual.ry, tolerance)
        assertEquals(expected.rz, actual.rz, tolerance)
    }

    @Test
    fun everyModeRestsAtProgressZeroAndOne() {
        for (mode in modes) {
            assertPoseNear(SwingPose(), ViewmodelMath.swingPose(mode, 0f))
            assertPoseNear(SwingPose(), ViewmodelMath.swingPose(mode, 1f))
        }
    }

    @Test
    fun vanillaIsAlwaysIdentity() {
        for (step in 0..10) {
            assertPoseNear(SwingPose(), ViewmodelMath.swingPose(SwingMode.VANILLA, step / 10f))
        }
    }

    @Test
    fun poseValuesStayFiniteAndBoundedAcrossProgress() {
        for (mode in modes) {
            for (step in 0..100) {
                val pose = ViewmodelMath.swingPose(mode, step / 100f)
                listOf(pose.tx, pose.ty, pose.tz, pose.rx, pose.ry, pose.rz).forEach {
                    assertTrue(it.isFinite(), "$mode at ${step / 100f} produced $it")
                    assertTrue(kotlin.math.abs(it) <= 360f, "$mode produced out-of-range $it")
                }
            }
        }
    }

    @Test
    fun nonFiniteOrOutOfRangeProgressIsClamped() {
        assertPoseNear(SwingPose(), ViewmodelMath.swingPose(SwingMode.PUSH, Float.NaN))
        assertPoseNear(ViewmodelMath.swingPose(SwingMode.STAB, 1f), ViewmodelMath.swingPose(SwingMode.STAB, 7f))
        assertPoseNear(ViewmodelMath.swingPose(SwingMode.STAB, 0f), ViewmodelMath.swingPose(SwingMode.STAB, -3f))
    }

    @Test
    fun peakMovesForwardForPushAndStab() {
        val push = ViewmodelMath.swingPose(SwingMode.PUSH, 0.5f)
        assertTrue(push.tz < -0.4f)
        val stab = ViewmodelMath.swingPose(SwingMode.STAB, 0.5f)
        assertTrue(stab.tz < push.tz)
    }

    @Test
    fun spinCompletesOneTurn() {
        assertEquals(180f, ViewmodelMath.swingPose(SwingMode.SPIN, 0.5f).rz, tolerance)
    }

    @Test
    fun mirroringTwiceRestoresThePose() {
        val pose = ViewmodelMath.swingPose(SwingMode.PUNCH, 0.3f)
        assertPoseNear(pose, pose.mirrored().mirrored())
    }

    @Test
    fun mirroringFlipsXAndYawRollOnly() {
        val pose = SwingPose(tx = 0.2f, ty = 0.1f, tz = -0.3f, rx = 15f, ry = 20f, rz = 30f)
        assertPoseNear(SwingPose(-0.2f, 0.1f, -0.3f, 15f, -20f, -30f), pose.mirrored())
    }

    @Test
    fun easingWithZeroDeltaDoesNotMove() {
        assertEquals(1f, ViewmodelMath.easeToward(1f, 0.25f, 0f, smooth = true))
        assertEquals(1f, ViewmodelMath.easeToward(1f, 0.25f, Float.NaN, smooth = true))
    }

    @Test
    fun easingWithoutSmoothingSnaps() {
        assertEquals(0.25f, ViewmodelMath.easeToward(1f, 0.25f, 0f, smooth = false))
    }

    @Test
    fun easingConvergesAndLandsExactlyOnTarget() {
        var zoom = 1f
        val target = 0.25f
        var previous = zoom
        repeat(120) {
            zoom = ViewmodelMath.easeToward(zoom, target, 1f / 60f, smooth = true)
            assertTrue(zoom <= previous + 1e-6f && zoom >= target, "zoom moved away from target: $zoom")
            previous = zoom
        }
        assertEquals(target, zoom)
    }

    @Test
    fun easingIsFrameRateIndependentForSameElapsedTime() {
        var fine = 1f
        repeat(60) { fine = ViewmodelMath.easeToward(fine, 0.25f, 1f / 60f, smooth = true) }
        var coarse = 1f
        repeat(15) { coarse = ViewmodelMath.easeToward(coarse, 0.25f, 4f / 60f, smooth = true) }
        assertEquals(fine, coarse, 1e-3f)
    }

    @Test
    fun easingClampsHugeFrameGaps() {
        val afterPause = ViewmodelMath.easeToward(1f, 0.25f, 30f, smooth = true)
        assertEquals(0.25f, afterPause, 0.05f)
        assertTrue(afterPause > 0.25f)
    }
}
