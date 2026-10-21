package net.icantpy.qol.auto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmedSortStateTest {
    private val t0 = 1_000_000L

    private fun armed(
        state: ArmedSortState = ArmedSortState(),
        preset: String = "Dungeon",
        timeoutMs: Int = 10_000,
        reopenMs: Int = 5_000,
        lock: Boolean = false,
        enabled: Boolean = true,
    ): ArmedSortState {
        state.arm(t0, preset, "card", timeoutMs, reopenMs, lock, enabled)
        return state
    }

    @Test
    fun armThenOpenInventoryStartsTheRequest() {
        val state = armed()
        val started = state.startIfReady(t0 + 100, inventoryOpen = true, otherRunActive = false)
        assertNotNull(started)
        assertEquals("Dungeon", started.presetName)
        assertTrue(state.isRunning())
        assertFalse(state.hasRequest())
    }

    @Test
    fun requestDoesNotStartWhileInventoryClosedOrOtherRunActive() {
        val state = armed()
        assertNull(state.startIfReady(t0, inventoryOpen = false, otherRunActive = false))
        assertNull(state.startIfReady(t0, inventoryOpen = true, otherRunActive = true))
        assertTrue(state.hasRequest())
    }

    @Test
    fun expiredRequestIsReportedOnceAndNeverStarts() {
        val state = armed(timeoutMs = 10_000)
        assertFalse(state.takeExpired(t0 + 9_999))
        assertTrue(state.takeExpired(t0 + 10_000))
        assertFalse(state.hasRequest())
        assertFalse(state.takeExpired(t0 + 20_000))
        assertNull(state.startIfReady(t0 + 20_000, inventoryOpen = true, otherRunActive = false))
    }

    @Test
    fun startAfterDeadlineWithoutTakeExpiredStillDoesNotStart() {
        val state = armed(timeoutMs = 1_000)
        assertNull(state.startIfReady(t0 + 1_000, inventoryOpen = true, otherRunActive = false))
    }

    @Test
    fun zeroTimeoutNeverExpires() {
        val state = armed(timeoutMs = 0)
        assertFalse(state.takeExpired(t0 + Long.MAX_VALUE / 2))
        assertNotNull(state.startIfReady(t0 + 999_999_999L, inventoryOpen = true, otherRunActive = false))
    }

    @Test
    fun newerRequestReplacesOlderOne() {
        val state = armed(preset = "Old")
        state.arm(t0 + 1, "New", "card2", 10_000, 0, false, true)
        val started = state.startIfReady(t0 + 2, inventoryOpen = true, otherRunActive = false)
        assertEquals("New", started?.presetName)
        assertEquals("card2", started?.cardKey)
    }

    @Test
    fun disabledSortIsNotArmed() {
        val state = armed(enabled = false)
        assertFalse(state.hasRequest())
        assertNull(state.startIfReady(t0, inventoryOpen = true, otherRunActive = false))
    }

    @Test
    fun reopenWithinWindowClosesInventoryAndAfterWindowIsAllowed() {
        val state = armed(reopenMs = 5_000)
        state.startIfReady(t0, inventoryOpen = true, otherRunActive = false)
        assertTrue(state.runFinished(t0 + 1_000))
        val within = state.reopenCheck(t0 + 2_000, inventoryOpen = true)
        assertTrue(within.close)
        assertEquals(4, within.hintSeconds)
        val after = state.reopenCheck(t0 + 6_000, inventoryOpen = true)
        assertFalse(after.close)
        assertNull(after.hintSeconds)
    }

    @Test
    fun hintIsShownOncePerWindow() {
        val state = armed(reopenMs = 5_000)
        state.startIfReady(t0, inventoryOpen = true, otherRunActive = false)
        state.runFinished(t0)
        assertNotNull(state.reopenCheck(t0 + 100, inventoryOpen = true).hintSeconds)
        assertNull(state.reopenCheck(t0 + 200, inventoryOpen = true).hintSeconds)
    }

    @Test
    fun zeroReopenBlockNeverCloses() {
        val state = armed(reopenMs = 0)
        state.startIfReady(t0, inventoryOpen = true, otherRunActive = false)
        state.runFinished(t0)
        assertFalse(state.reopenCheck(t0 + 1, inventoryOpen = true).close)
    }

    @Test
    fun otherScreensAreNeverBlockedDuringWindow() {
        val state = armed(reopenMs = 5_000)
        state.startIfReady(t0, inventoryOpen = true, otherRunActive = false)
        state.runFinished(t0)
        assertFalse(state.reopenCheck(t0 + 1_000, inventoryOpen = false).close)
    }

    @Test
    fun lockUntilDoneSwallowsEscAndKeepsDoubleEscCancel() {
        val state = armed(lock = true)
        state.startIfReady(t0, inventoryOpen = true, otherRunActive = false)
        assertTrue(state.lockActive())
        // The global lock setting is off: the armed lock still applies.
        val lock = false || state.lockActive()
        val first = AutoInputLock.decide(AutoInputEvent.ESCAPE, runActive = true, lockInput = lock, lastEscNanos = null, nowNanos = 0)
        assertEquals(AutoInputDecision.SWALLOW, first)
        val second = AutoInputLock.decide(AutoInputEvent.ESCAPE, runActive = true, lockInput = lock, lastEscNanos = 0, nowNanos = 100_000_000L)
        assertEquals(AutoInputDecision.CANCEL, second)
    }

    @Test
    fun lockUntilDoneSwallowsOtherInput() {
        val state = armed(lock = true)
        state.startIfReady(t0, inventoryOpen = true, otherRunActive = false)
        val lock = state.lockActive()
        assertEquals(AutoInputDecision.SWALLOW, AutoInputLock.decide(AutoInputEvent.OTHER, true, lock, null, 0))
        assertEquals(AutoInputDecision.SWALLOW, AutoInputLock.decide(AutoInputEvent.MOUSE_PRESS, true, lock, null, 0))
    }

    @Test
    fun withoutLockUntilDoneEscCancelsImmediately() {
        val state = armed(lock = false)
        state.startIfReady(t0, inventoryOpen = true, otherRunActive = false)
        assertFalse(state.lockActive())
        assertEquals(
            AutoInputDecision.CANCEL,
            AutoInputLock.decide(AutoInputEvent.ESCAPE, true, false || state.lockActive(), null, 0),
        )
    }

    @Test
    fun cancelClearsPendingRequestAndLock() {
        val state = armed(lock = true)
        assertTrue(state.cancelRequest())
        assertFalse(state.cancelRequest())
        state.clear()
        assertFalse(state.hasRequest())
        assertFalse(state.lockActive())
    }
}
