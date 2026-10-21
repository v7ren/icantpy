package net.icantpy.qol.auto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoInputLockTest {
    private val start = 5_000_000_000L
    private val window = AutoInputLock.DOUBLE_ESC_WINDOW_NANOS

    private fun decide(
        event: AutoInputEvent,
        runActive: Boolean = true,
        lock: Boolean = true,
        lastEsc: Long? = null,
        now: Long = start,
    ) = AutoInputLock.decide(event, runActive, lock, lastEsc, now)

    @Test
    fun noRunActiveLetsEverythingThrough() {
        AutoInputEvent.entries.forEach { event ->
            assertEquals(AutoInputDecision.PASS, decide(event, runActive = false, lock = true))
            assertEquals(AutoInputDecision.PASS, decide(event, runActive = false, lock = false))
        }
    }

    @Test
    fun singleEscIsSwallowedWhileLocked() {
        assertEquals(AutoInputDecision.SWALLOW, decide(AutoInputEvent.ESCAPE))
    }

    @Test
    fun secondEscWithinWindowCancelsWhileLocked() {
        assertEquals(AutoInputDecision.CANCEL, decide(AutoInputEvent.ESCAPE, lastEsc = start, now = start + window - 1))
    }

    @Test
    fun secondEscExactlyAtWindowEdgeCancels() {
        assertEquals(AutoInputDecision.CANCEL, decide(AutoInputEvent.ESCAPE, lastEsc = start, now = start + window))
    }

    @Test
    fun escAfterWindowIsSwallowedAgain() {
        assertEquals(AutoInputDecision.SWALLOW, decide(AutoInputEvent.ESCAPE, lastEsc = start, now = start + window + 1))
    }

    @Test
    fun clicksScrollsReleasesAndOtherKeysAreSwallowedWhileLocked() {
        listOf(AutoInputEvent.MOUSE_PRESS, AutoInputEvent.OTHER).forEach { event ->
            assertEquals(AutoInputDecision.SWALLOW, decide(event), "$event should be swallowed")
        }
    }

    @Test
    fun otherInputDoesNotCancelEvenAfterAnEsc() {
        assertEquals(AutoInputDecision.SWALLOW, decide(AutoInputEvent.MOUSE_PRESS, lastEsc = start, now = start + 1))
        assertEquals(AutoInputDecision.SWALLOW, decide(AutoInputEvent.OTHER, lastEsc = start, now = start + 1))
    }

    @Test
    fun unlockedClickAndEscCancelLikeBefore() {
        assertEquals(AutoInputDecision.CANCEL, decide(AutoInputEvent.MOUSE_PRESS, lock = false))
        assertEquals(AutoInputDecision.CANCEL, decide(AutoInputEvent.ESCAPE, lock = false))
    }

    @Test
    fun unlockedOtherInputPassesWithoutCancelling() {
        assertEquals(AutoInputDecision.PASS, decide(AutoInputEvent.OTHER, lock = false))
    }

    @Test
    fun unlockedEscIgnoresEarlierEscTiming() {
        assertEquals(AutoInputDecision.CANCEL, decide(AutoInputEvent.ESCAPE, lock = false, lastEsc = null))
    }

    @Test
    fun sameEventInstanceIsOnePress() {
        val first = AutoInputLock.Press(AutoInputEvent.ESCAPE, start)
        assertTrue(AutoInputLock.isDuplicatePress(first, AutoInputEvent.ESCAPE, start + 1, sameInstance = true))
    }

    @Test
    fun escFromBothLayersCountsOnce() {
        // Fabric layer decides the first Esc (swallowed, recorded), the mixin layer reports the same press a few ms later.
        val gate = AutoInputLock.Press(AutoInputEvent.ESCAPE, start)
        val mixinAt = start + 5_000_000L
        assertTrue(AutoInputLock.isDuplicatePress(gate, AutoInputEvent.ESCAPE, mixinAt, sameInstance = false))
        // A genuine second Esc, well after the duplicate window, is not a duplicate and cancels.
        val secondAt = start + 300_000_000L
        assertFalse(AutoInputLock.isDuplicatePress(gate, AutoInputEvent.ESCAPE, secondAt, sameInstance = false))
        assertEquals(
            AutoInputDecision.CANCEL,
            decide(AutoInputEvent.ESCAPE, lastEsc = start, now = secondAt),
        )
    }

    @Test
    fun clicksAreNotDuplicatesWithoutSameInstance() {
        val press = AutoInputLock.Press(AutoInputEvent.MOUSE_PRESS, start)
        assertFalse(AutoInputLock.isDuplicatePress(press, AutoInputEvent.MOUSE_PRESS, start + 1, sameInstance = false))
        assertFalse(AutoInputLock.isDuplicatePress(null, AutoInputEvent.ESCAPE, start, sameInstance = false))
    }
}
