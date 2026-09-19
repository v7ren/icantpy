package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LeapAutoLeapStateTest {
    private fun request(delayMs: Long = 100) =
        LeapAutoLeapState.request("Alice", "m7p3s1", delayMs, 1_000)!!

    private fun frame(
        now: Long,
        menu: Int? = null,
        screen: Int? = null,
        slot: Int? = null,
        valid: Boolean = menu != null,
        enabled: Boolean = true,
        itemUnchanged: Boolean = true,
        scope: String = "m7p3s1",
        connected: Boolean = true,
    ) = LeapAutoLeapFrame(now, connected, enabled, itemUnchanged, scope, screen, menu, valid, slot)

    @Test
    fun delayBeginsWhenServerMenuOpensAndCapturesTargetName() {
        val waiting = LeapAutoLeapState.advance(request(), frame(1_800, menu = 7, screen = 40)).pending!!
        assertEquals("Alice", waiting.targetName)
        assertNull(LeapAutoLeapState.advance(waiting, frame(1_899, 7, 40, 12)).clickSlot)
        val due = LeapAutoLeapState.advance(waiting, frame(1_900, 7, 40, 12))
        assertEquals(12, due.clickSlot)
        assertNull(due.pending)
        assertNull(LeapAutoLeapState.advance(due.pending, frame(2_000, 7, 40, 12)).clickSlot)
    }

    @Test
    fun waitsForActualNamedHeadInsteadOfSelectingAnotherClass() {
        val waiting = LeapAutoLeapState.advance(request(0), frame(1_050, 7, 40)).pending!!
        assertNotNull(LeapAutoLeapState.advance(waiting, frame(2_000, 7, 40)).pending)
        assertEquals(14, LeapAutoLeapState.advance(waiting, frame(2_100, 7, 40, 14)).clickSlot)
        assertNull(LeapAutoLeapState.advance(waiting, frame(3_050, 7, 40)).pending)
    }

    @Test
    fun boundsOpeningTimeoutAndRejectsUnrelatedScreensAndMenus() {
        assertNotNull(LeapAutoLeapState.advance(request(), frame(2_999)).pending)
        assertNull(LeapAutoLeapState.advance(request(), frame(3_000)).pending)
        assertNull(LeapAutoLeapState.advance(request(), frame(1_010, screen = 44)).pending)
        assertNull(LeapAutoLeapState.advance(request(), frame(1_010, 7, 44, 12, valid = false)).clickSlot)
    }

    @Test
    fun closingOrSwitchingAnOpenedMenuCancels() {
        val waiting = LeapAutoLeapState.advance(request(), frame(1_010, 7, 40)).pending!!
        assertNull(LeapAutoLeapState.advance(waiting, frame(1_200)).pending)
        assertNull(LeapAutoLeapState.advance(waiting, frame(1_200, 8, 40, 12)).pending)
        assertNull(LeapAutoLeapState.advance(waiting, frame(1_200, 7, 41, 12)).pending)
    }

    @Test
    fun cancellationAlwaysPrecedesClick() {
        val waiting = LeapAutoLeapState.advance(request(), frame(1_010, 7, 40)).pending!!
        val cancellations = listOf(
            frame(1_500, 7, 40, 12, enabled = false),
            frame(1_500, 7, 40, 12, itemUnchanged = false),
            frame(1_500, 7, 40, 12, scope = "m7p3s2"),
            frame(1_500, 7, 40, 12, connected = false),
            frame(1_500, 7, 40, 12, valid = false),
        )
        cancellations.forEach {
            val result = LeapAutoLeapState.advance(waiting, it)
            assertNull(result.pending)
            assertNull(result.clickSlot)
        }
    }

    @Test
    fun validatesNamesAndClampsDelay() {
        assertNull(LeapAutoLeapState.request("", "scope", 100, 0))
        assertNull(LeapAutoLeapState.request("not a player", "scope", 100, 0))
        assertEquals(0L, request(-100).delayMs)
        assertEquals(2_000L, request(9_000).delayMs)
        assertNull(LeapAutoLeapState.advance(null, frame(1_100, 7, 40, 12)).clickSlot)
    }

    @Test
    fun maximumDelayToleratesTicksAfterConfiguredDelay() {
        val waiting = LeapAutoLeapState.advance(request(2_000), frame(1_100, 7, 40)).pending!!
        assertEquals(12, LeapAutoLeapState.advance(waiting, frame(3_125, 7, 40, 12)).clickSlot)
        assertNotNull(LeapAutoLeapState.advance(waiting, frame(5_099, 7, 40)).pending)
        assertNull(LeapAutoLeapState.advance(waiting, frame(5_100, 7, 40)).pending)
        assertNull(LeapAutoLeapState.advance(waiting, frame(5_101, 7, 40, 12)).clickSlot)
    }
}
