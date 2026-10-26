package net.icantpy.slayer.blaze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AirLandingDelayTest {
    private val flat = { _: Int, _: Int, _: Double -> 64.0 }

    @Test
    fun landsOnFlatGroundAheadOfTheJump() {
        val landing = assertNotNull(AirLanding.predict(0.0, 65.0, 0.0, 0.2, 0.42, 0.0, 0.0, 0.0, flat))
        assertTrue(landing.x > 0.5)
        assertTrue(landing.ticks in 5..15)
    }

    @Test
    fun nudgeShiftsTheLanding() {
        val plain = assertNotNull(AirLanding.predict(0.0, 65.0, 0.0, 0.0, 0.42, 0.0, 0.0, 0.0, flat))
        val nudged = assertNotNull(AirLanding.predict(0.0, 65.0, 0.0, 0.0, 0.42, 0.0, 0.0, 0.1, flat))
        assertTrue(nudged.z > plain.z + 0.1)
    }

    @Test
    fun inputAccelFollowsTheKeys() {
        val speed = 0.02
        val fwd = AirLanding.inputAccel(true, false, false, false, 0.0, speed)
        assertEquals(0.0, fwd.x, 1e-9)
        assertEquals(speed, fwd.z, 1e-9)
        val left = AirLanding.inputAccel(false, false, true, false, 0.0, speed)
        assertEquals(speed, left.x, 1e-9)
        val diag = AirLanding.inputAccel(true, false, true, false, 0.0, speed)
        assertEquals(speed, Math.hypot(diag.x, diag.z), 1e-9)
        val cancelled = AirLanding.inputAccel(true, true, false, false, 0.0, speed)
        assertEquals(0.0, Math.hypot(cancelled.x, cancelled.z), 1e-9)
        val east = AirLanding.inputAccel(true, false, false, false, -90.0, speed)
        assertEquals(speed, east.x, 1e-9)
    }

    @Test
    fun noFloorMeansNoLanding() {
        assertNull(AirLanding.predict(0.0, 65.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) { _, _, _ -> Double.NaN })
    }

    @Test
    fun delayHoldsBackNewColumnsOnly() {
        val delay = DetectDelay()
        val out = HashSet<Long>()
        val column = packXZ(1, 2)
        delay.filter(setOf(column), 10, 3, out)
        assertTrue(out.isEmpty())
        delay.filter(setOf(column), 12, 3, out)
        assertTrue(out.isEmpty())
        delay.filter(setOf(column), 13, 3, out)
        assertEquals(setOf(column), out)
    }

    @Test
    fun delayForgetsColumnsThatVanish() {
        val delay = DetectDelay()
        val out = HashSet<Long>()
        val column = packXZ(1, 2)
        delay.filter(setOf(column), 10, 2, out)
        delay.filter(emptySet(), 11, 2, out)
        delay.filter(setOf(column), 12, 2, out)
        assertTrue(out.isEmpty())
    }

    @Test
    fun zeroDelayPassesEverything() {
        val out = HashSet<Long>()
        DetectDelay().filter(setOf(packXZ(0, 0)), 5, 0, out)
        assertEquals(1, out.size)
        assertEquals(0, DetectDelay.ticksOf(0))
        assertEquals(1, DetectDelay.ticksOf(1))
        assertEquals(2, DetectDelay.ticksOf(100))
    }

    @Test
    fun defaultPrioritiesRankYellowPitOverBeamOverFire() {
        val c = FirePitConfig()
        assertTrue(c.pitPriority(1) > c.priorityBeam)
        assertTrue(c.priorityBeam > c.priorityFire)
        assertEquals(c.priorityPitBlack, c.pitPriority(2))
        val custom = FirePitConfig(priorityBeam = 9, priorityPitYellow = 2)
        assertEquals(custom, FirePitConfig.fromJsonObject(custom.toJsonObject()))
        assertEquals(10, FirePitConfig.parse("""{"priorityFire":99}""").priorityFire)
    }

    @Test
    fun configRoundTripsNewFields() {
        val config = FirePitConfig(pitPhase = PitPhase.BLACK, pitDelayMs = 150, fireDelayMs = 50, airAvoid = true)
        assertEquals(config, FirePitConfig.fromJsonObject(config.toJsonObject()))
        assertEquals(PitPhase.RED, FirePitConfig.parse("""{"pitPhase":"nope"}""").pitPhase)
        assertEquals(1000, FirePitConfig.parse("""{"pitDelayMs":99999}""").pitDelayMs)
    }
}
