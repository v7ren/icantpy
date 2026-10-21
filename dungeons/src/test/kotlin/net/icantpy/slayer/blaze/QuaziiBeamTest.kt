package net.icantpy.slayer.blaze

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuaziiBeamTest {
    private fun near(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }

    @Test
    fun bearingFollowsMinecraftYawConventionWithNorthUp() {
        near(0.0, QuaziiBeam.bearing(0.0, 0.0, 0.0, 1.0))   // south (+Z)
        near(90.0, QuaziiBeam.bearing(0.0, 0.0, -1.0, 0.0)) // west (-X)
        near(180.0, QuaziiBeam.bearing(0.0, 0.0, 0.0, -1.0)) // north (-Z)
        near(270.0, QuaziiBeam.bearing(0.0, 0.0, 1.0, 0.0)) // east (+X)
    }

    @Test
    fun bearingIsRelativeToBodyAndStaysInRange() {
        near(90.0, QuaziiBeam.bearing(10.0, -4.0, 9.0, -4.0))
        val b = QuaziiBeam.bearing(0.0, 0.0, 0.5, -0.5)
        assertTrue(b >= 0.0 && b < 360.0, "bearing out of range: $b")
    }

    @Test
    fun wrapMapsIntoHalfOpenRange() {
        near(0.0, QuaziiBeam.wrap(360.0))
        near(180.0, QuaziiBeam.wrap(-180.0))
        near(180.0, QuaziiBeam.wrap(180.0))
        near(-170.0, QuaziiBeam.wrap(190.0))
        near(10.0, QuaziiBeam.wrap(-350.0))
    }

    @Test
    fun rateOfSteadyOrbitIsTheStep() {
        val history = DoubleArray(10) { 100.0 + 2.87 * it }
        near(2.87, assertNotNull(QuaziiBeam.rate(history, 10)), 1e-9)
    }

    @Test
    fun rateAcrossTheSeamKeepsTheStep() {
        val history = DoubleArray(6) { ((350.0 + 2.87 * it) % 360.0) }
        near(2.87, assertNotNull(QuaziiBeam.rate(history, 6)), 1e-6)
    }

    @Test
    fun rateIsTheSweepAndATeleportIsDropped() {
        // Quantised 1.4 degree steps around a ~2.8 orbit, plus one 90 degree body swap that is dropped.
        val history = doubleArrayOf(0.0, 2.8, 5.6, 7.0, 9.8, 99.8, 102.6, 105.4)
        near(15.4 / 6.0, assertNotNull(QuaziiBeam.rate(history, history.size)), 1e-9)
    }

    @Test
    fun updatesEveryOtherTickStillGiveTheOrbitRate() {
        // The real pattern: 0, ~6, 0, ~6 ... The zero steps are missed updates, not a reversal.
        val history = doubleArrayOf(0.0, 0.0, 5.6, 5.6, 11.2, 11.2, 17.0, 17.0, 22.6, 22.6)
        near(22.6 / 9.0, assertNotNull(QuaziiBeam.rate(history, history.size)), 1e-9)
    }

    @Test
    fun rateNeedsEnoughUsableSamples() {
        assertNull(QuaziiBeam.rate(doubleArrayOf(0.0, 3.0, 6.0, 9.0), 4))
        assertNull(QuaziiBeam.rate(doubleArrayOf(0.0, 3.0, 60.0, 63.0, 120.0), 5))
    }

    @Test
    fun counterclockwiseRateIsNegative() {
        val history = DoubleArray(8) { 200.0 - 3.0 * it }
        near(-3.0, assertNotNull(QuaziiBeam.rate(history, 8)), 1e-9)
    }

    @Test
    fun approachingEndCrossesAfterDistanceOverRate() {
        // Player 10 degrees clockwise (ahead) of the arm, arm moving clockwise at 2.87 deg/tick.
        near(10.0 / 2.87, assertNotNull(QuaziiBeam.ticksUntilCrossing(100.0, 2.87, 110.0, 5.0)), 1e-9)
    }

    @Test
    fun counterclockwiseRateUsesTheSameFormula() {
        near(5.0, assertNotNull(QuaziiBeam.ticksUntilCrossing(100.0, -2.0, 90.0, 5.0)), 1e-9)
    }

    @Test
    fun onlyTheGuardianEndIsTimedNotTheFarSide() {
        // Arm at 100 turning clockwise, player at 290: only the far side of a line would be 10 away. Not timed.
        assertNull(QuaziiBeam.ticksUntilCrossing(100.0, 1.0, 290.0, 5.0))
        // The guardian end itself still gets there after 170 degrees.
        near(170.0, assertNotNull(QuaziiBeam.ticksUntilCrossing(100.0, 1.0, 270.0, 5.0)), 1e-9)
    }

    @Test
    fun beamThatJustPassedIsNotPredicted() {
        assertNull(QuaziiBeam.ticksUntilCrossing(110.0, 2.0, 100.0, 5.0))
    }

    @Test
    fun reversingArmHasNoRate() {
        // Half the steps clockwise, half counterclockwise: no direction to trust.
        val history = doubleArrayOf(0.0, 3.0, 0.0, 3.0, 0.0, 3.0, 0.0)
        assertNull(QuaziiBeam.rate(history, history.size))
    }

    @Test
    fun implausiblyFastMedianHasNoRate() {
        val history = DoubleArray(8) { 12.0 * it }
        assertNull(QuaziiBeam.rate(history, 8))
    }

    @Test
    fun keepOutLeavesMovesThatStayOutside() {
        val out = DoubleArray(2)
        assertFalse(QuaziiBeam.keepOut(2.0, 0.0, 0.2, 0.0, 0.0, 0.0, 1.0, 0.15, out))
        assertFalse(QuaziiBeam.keepOut(2.0, 0.0, -0.5, 0.0, 0.0, 0.0, 1.0, 0.15, out))
    }

    @Test
    fun keepOutStopsAWalkIntoTheBodyAtTheEdge() {
        val out = DoubleArray(2)
        assertTrue(QuaziiBeam.keepOut(1.2, 0.0, -0.5, 0.0, 0.0, 0.0, 1.0, 0.15, out))
        near(-0.2, out[0], 1e-9)
        near(0.0, out[1], 1e-9)
    }

    @Test
    fun keepOutSlidesAroundTheBody() {
        val out = DoubleArray(2)
        // Walking diagonally past the body: the end point is put back on the circle, still moving sideways.
        assertTrue(QuaziiBeam.keepOut(1.0, -0.3, -0.3, 0.3, 0.0, 0.0, 1.0, 0.15, out))
        near(1.0, kotlin.math.hypot(1.0 + out[0], -0.3 + out[1]), 1e-9)
        assertTrue(out[1] > 0.0)
    }

    @Test
    fun keepOutEasesThePlayerOutWhenInside() {
        val out = DoubleArray(2)
        // Standing still 0.5 inside a radius of 1: pushed outward by the push limit.
        assertTrue(QuaziiBeam.keepOut(0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.15, out))
        near(0.15, out[0], 1e-9)
        near(0.0, out[1], 1e-9)
        // Walking further in: the inward part is dropped, sideways kept.
        assertTrue(QuaziiBeam.keepOut(0.5, 0.0, -0.2, 0.1, 0.0, 0.0, 1.0, 0.15, out))
        near(0.15, out[0], 1e-9)
        near(0.1, out[1], 1e-9)
        // Never pushed past the edge.
        assertTrue(QuaziiBeam.keepOut(0.95, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.15, out))
        near(0.05, out[0], 1e-9)
    }

    @Test
    fun playerOutOfReachReturnsNull() {
        assertNull(QuaziiBeam.ticksUntilCrossing(100.0, 2.87, 110.0, QuaziiBeam.REACH + 0.1))
        assertNotNull(QuaziiBeam.ticksUntilCrossing(100.0, 2.87, 110.0, QuaziiBeam.REACH))
    }

    @Test
    fun stalledArmReturnsNull() {
        assertNull(QuaziiBeam.ticksUntilCrossing(100.0, 0.0, 110.0, 5.0))
        assertNull(QuaziiBeam.ticksUntilCrossing(100.0, 0.5, 110.0, 5.0))
    }

    @Test
    fun impactIsCrossingMinusLead() {
        near(16.0, QuaziiBeam.impactTicks(20.0))
        near(-1.0, QuaziiBeam.impactTicks(3.0))
    }

    @Test
    fun jumpWindowIsSixToOneTicksBeforeImpact() {
        assertTrue(QuaziiBeam.jumpWindow(6.0))
        assertTrue(QuaziiBeam.jumpWindow(2.5))
        assertTrue(QuaziiBeam.jumpWindow(1.0))
        assertFalse(QuaziiBeam.jumpWindow(6.1))
        assertFalse(QuaziiBeam.jumpWindow(0.9))
    }

    @Test
    fun earlyTicksMoveOnlyTheStartEarlier() {
        assertTrue(QuaziiBeam.jumpWindow(8.0, 2.0))
        assertTrue(QuaziiBeam.jumpWindow(1.5, 2.0))
        assertFalse(QuaziiBeam.jumpWindow(8.1, 2.0))
        assertFalse(QuaziiBeam.jumpWindow(0.5, 2.0))
    }
}
