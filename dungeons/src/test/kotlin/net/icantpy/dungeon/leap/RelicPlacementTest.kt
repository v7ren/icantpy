package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelicPlacementTest {
    private val red = RelicPlacement.cauldronFor("Red")!!

    @Test
    fun colorFromRelicIdAndName() {
        assertEquals("Red", RelicPlacement.colorOfRelicId("RED_KING_RELIC"))
        assertEquals("Purple", RelicPlacement.colorOfRelicId("purple_king_relic"))
        assertNull(RelicPlacement.colorOfRelicId("WITHER_KING_RELIC"))
        assertNull(RelicPlacement.colorOfRelicId(null))
        assertEquals("Orange", RelicPlacement.colorOfRelicName("§6Corrupted Orange Relic"))
        assertEquals("Blue", RelicPlacement.colorOfRelicName("Corrupted blue Relic"))
        assertNull(RelicPlacement.colorOfRelicName("Corrupted Relic"))
        assertNull(RelicPlacement.colorOfRelicName("Corrupted Teal Relic"))
    }

    @Test
    fun cauldronCenterIsBlockCenter() {
        assertEquals(51.5, red.centerX)
        assertEquals(42.5, red.centerZ)
        assertNull(RelicPlacement.cauldronFor("Teal"))
    }

    @Test
    fun selfPlacedNearCauldronFiresOnceWithColor() {
        val tracker = SelfRelicPlacementTracker()
        assertNull(tracker.update("Red", 51.5, 40.0))
        assertEquals("Red", tracker.update(null, 51.5, 42.5))
        assertNull(tracker.update(null, 51.5, 42.5))
    }

    @Test
    fun relicDisappearingFarAwayDoesNotFire() {
        val tracker = SelfRelicPlacementTracker()
        tracker.update("Red", 0.0, 0.0)
        assertNull(tracker.update(null, 0.0, 0.0))
        assertNull(tracker.update(null, 51.5, 42.5))
    }

    @Test
    fun relicLeavingJustOutsideRadiusDoesNotFire() {
        val tracker = SelfRelicPlacementTracker()
        tracker.update("Red", 0.0, 0.0)
        assertNull(tracker.update(null, 51.5 + 4.6, 42.5))
    }

    @Test
    fun wrongCauldronDoesNotFire() {
        val tracker = SelfRelicPlacementTracker()
        tracker.update("Blue", 0.0, 0.0)
        assertNull(tracker.update(null, 51.5, 42.5))
    }

    @Test
    fun repickupAfterPlacementFiresAgain() {
        val tracker = SelfRelicPlacementTracker()
        tracker.update("Green", 49.5, 44.5)
        assertEquals("Green", tracker.update(null, 49.5, 44.5))
        tracker.update("Green", 49.5, 44.5)
        assertEquals("Green", tracker.update(null, 49.5, 44.5))
    }

    @Test
    fun resetClearsHeldRelic() {
        val tracker = SelfRelicPlacementTracker()
        tracker.update("Red", 51.5, 42.5)
        tracker.reset()
        assertNull(tracker.update(null, 51.5, 42.5))
    }

    @Test
    fun anyPlacedFiresOncePerColorPerRun() {
        val tracker = AnyRelicPlacementTracker()
        val stand = RelicStandSample(x = 59.5, z = 44.5, color = "Blue")
        assertEquals(listOf("Blue"), tracker.scan(listOf(stand)))
        assertEquals(emptyList(), tracker.scan(listOf(stand)))
        assertEquals(listOf("Purple"), tracker.scan(listOf(RelicStandSample(54.5, 41.5, "Purple"))))
        tracker.reset()
        assertEquals(listOf("Blue"), tracker.scan(listOf(stand)))
    }

    @Test
    fun anyPlacedIgnoresStandsFarOrWithoutRelicColor() {
        val tracker = AnyRelicPlacementTracker()
        assertEquals(emptyList(), tracker.scan(listOf(RelicStandSample(59.5, 50.0, "Blue"))))
        assertEquals(emptyList(), tracker.scan(listOf(RelicStandSample(59.5, 44.5, null))))
        assertEquals(emptyList(), tracker.scan(listOf(RelicStandSample(59.5, 44.5, "Teal"))))
    }

    @Test
    fun anyPlacedReportsEachColorInScanOnce() {
        val tracker = AnyRelicPlacementTracker()
        val samples = listOf(
            RelicStandSample(51.5, 42.5, "Red"),
            RelicStandSample(51.5, 42.5, "Red"),
            RelicStandSample(57.5, 42.5, "Orange"),
        )
        assertEquals(listOf("Red", "Orange"), tracker.scan(samples))
    }

    @Test
    fun colorFilterMatchesCaseInsensitively() {
        val any = LeapOrientTrigger.create(LeapTriggerEvent.RELIC_PLACED_SELF).copy(match = "any")
        val blue = any.copy(match = "blue")
        assertTrue(LeapOrientEngine.namedMatchHits(any, "Red", null))
        assertTrue(LeapOrientEngine.namedMatchHits(blue, "Blue", null))
        assertTrue(LeapOrientEngine.namedMatchHits(blue.copy(match = "BLUE"), "Blue", null))
        assertFalse(LeapOrientEngine.namedMatchHits(blue, "Red", null))
        assertFalse(LeapOrientEngine.namedMatchHits(blue.copy(match = "self"), "Blue", null))
    }

    @Test
    fun throttleAllowsOncePer3Seconds() {
        val throttle = LeapEventThrottle(3_000L)
        assertTrue(throttle.allow(10_000L))
        assertFalse(throttle.allow(12_999L))
        assertTrue(throttle.allow(13_000L))
        throttle.reset()
        assertTrue(throttle.allow(13_001L))
    }
}
