package net.icantpy.slayer.aim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimMotionTest {
    private val frameMs = 16L

    private fun assertPoseNear(expected: AimPose, actual: AimPose?, tolerance: Float = 0.01f) {
        assertNotNull(actual)
        assertTrue(abs(expected.yaw - actual.yaw) <= tolerance, "yaw expected ${expected.yaw} was ${actual.yaw}")
        assertTrue(abs(expected.pitch - actual.pitch) <= tolerance, "pitch expected ${expected.pitch} was ${actual.pitch}")
    }

    private fun straightPose(from: AimPose, to: AimPose, settings: AimSettings, progress: Float): AimPose {
        val d = AimMath.delta(from, to)
        val e = AimMath.ease(progress, settings.easing)
        return AimPose(AimMath.wrapDegrees(from.yaw + d.yaw * e), from.pitch + d.pitch * e)
    }

    @Test
    fun sampleIsNullBeforeRetargetAndAfterReset() {
        val motion = AimMotion()
        assertFalse(motion.active)
        assertNull(motion.sample(0L))

        motion.retarget(0L, AimPose(0f, 0f), AimPose(40f, 0f), AimSettings(), seed = 0)
        assertTrue(motion.active)
        assertNotNull(motion.sample(0L))

        motion.reset()
        assertFalse(motion.active)
        assertNull(motion.sample(100L))
    }

    @Test
    fun startAndDurationEndpointsMatchStartAndEnd() {
        val settings = AimSettings()
        val start = AimPose(0f, 0f)
        val end = AimPose(90f, 10f)
        val motion = AimMotion()
        motion.retarget(1000L, start, end, settings, seed = 0)

        assertPoseNear(start, motion.sample(1000L), tolerance = 1e-3f)
        val duration = AimMath.durationMs(AimMath.angularDistance(start, end), settings)
        assertPoseNear(end, motion.sample(1000L + duration))
    }

    @Test
    fun midwayLeavesStraightLineOnlyWhenCurved() {
        val settings = AimSettings(curve = 0.35f)
        val start = AimPose(0f, 0f)
        val end = AimPose(90f, 10f)
        val duration = AimMath.durationMs(AimMath.angularDistance(start, end), settings)
        val midTime = duration / 2
        val progress = midTime.toFloat() / duration.toFloat()

        val curved = AimMotion()
        curved.retarget(0L, start, end, settings, seed = 0)
        val curvedMid = curved.sample(midTime)
        val straight = straightPose(start, end, settings, progress)
        assertNotNull(curvedMid)
        assertTrue(abs(curvedMid.pitch - straight.pitch) > 0.1f, "curved path should bulge off the line")

        val flat = AimMotion()
        flat.retarget(0L, start, end, settings.copy(curve = 0f), seed = 0)
        assertPoseNear(straight, flat.sample(midTime), tolerance = 1e-3f)
    }

    @Test
    fun seedParityFlipsBendSide() {
        val settings = AimSettings(curve = 0.35f)
        val start = AimPose(0f, 0f)
        val end = AimPose(90f, 10f)
        val duration = AimMath.durationMs(AimMath.angularDistance(start, end), settings)
        val midTime = duration / 2
        val straight = straightPose(start, end, settings, midTime.toFloat() / duration.toFloat())

        val even = AimMotion().apply { retarget(0L, start, end, settings, seed = 0) }
        val odd = AimMotion().apply { retarget(0L, start, end, settings, seed = 1) }
        val evenOffset = even.sample(midTime)!!.pitch - straight.pitch
        val oddOffset = odd.sample(midTime)!!.pitch - straight.pitch

        assertTrue(abs(evenOffset) > 0.1f)
        assertEquals(-evenOffset, oddOffset, 1e-3f)
    }

    @Test
    fun crossingYawSeamGoesTheShortWay() {
        val settings = AimSettings(curve = 0f)
        val motion = AimMotion()
        motion.retarget(0L, AimPose(170f, 0f), AimPose(-170f, 0f), settings, seed = 0)

        val duration = AimMath.durationMs(20f, settings)
        val mid = motion.sample(duration / 2)
        assertNotNull(mid)
        assertTrue(abs(mid.yaw) > 170f, "should pass 180, not 0, but was ${mid.yaw}")
    }

    @Test
    fun followAfterCurveNeverExceedsSpeed() {
        val settings = AimSettings(speedDegPerSec = 180f, curve = 0.35f)
        val motion = AimMotion()
        motion.retarget(0L, AimPose(0f, 0f), AimPose(30f, 0f), settings, seed = 0)
        val duration = AimMath.durationMs(30f, settings)

        var now = duration
        var previous = motion.sample(now)!!
        val maxStep = settings.speedDegPerSec * frameMs / 1000f + 1e-3f
        repeat(120) {
            now += frameMs
            motion.updateTarget(AimPose(30f + (now - duration) / 100f, 0f))
            val next = motion.sample(now)!!
            val step = AimMath.angularDistance(previous, next)
            assertTrue(step <= maxStep, "step $step exceeded $maxStep at $now")
            previous = next
        }
    }

    @Test
    fun endMovedDuringCurveIsReachedWithMatchingFinalPose() {
        val settings = AimSettings()
        val motion = AimMotion()
        motion.retarget(0L, AimPose(0f, 0f), AimPose(60f, 0f), settings, seed = 0)
        val duration = AimMath.durationMs(60f, settings)

        motion.sample(duration / 2)
        val moved = AimPose(30f, 20f)
        motion.updateTarget(moved)

        var now = duration / 2
        while (now < duration + 5_000L) {
            now += frameMs
            motion.sample(now)
        }
        assertPoseNear(moved, motion.sample(now))
        assertTrue(motion.onTarget(now, toleranceDeg = 0.05f))
    }

    @Test
    fun onTargetIsFalseBeforeCurveFinishes() {
        val settings = AimSettings()
        val motion = AimMotion()
        motion.retarget(0L, AimPose(0f, 0f), AimPose(60f, 0f), settings, seed = 0)
        val duration = AimMath.durationMs(60f, settings)

        motion.sample(duration / 2)
        assertFalse(motion.onTarget(duration / 2, toleranceDeg = 0.5f))
    }
}
