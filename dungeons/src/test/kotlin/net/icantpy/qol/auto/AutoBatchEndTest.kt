package net.icantpy.qol.auto

import net.icantpy.qol.invsort.InvSortPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoBatchEndTest {
    private val drop = CursorGuard.expectAfterClick(carried = "sword", slotKey = null, slot = 9)

    private fun snap(carried: String?, slot9: String?, stateId: Int?) =
        CursorGuard.Snapshot(carried, mapOf(9 to slot9), stateId)

    @Test fun stateIdChurningWithMatchingKeysStillSettles() {
        val sync = CursorGuard.Sync(drop, 0L, CursorGuard.STEP_TIMEOUT_MS)
        assertEquals(CursorGuard.Verdict.WAITING, sync.observe(50, snap(null, "sword", stateId = 1)))
        assertEquals(CursorGuard.Verdict.SYNCED, sync.observe(100, snap(null, "sword", stateId = 2)))
    }

    @Test fun stateIdChurningOnFinishSettleStillFinishes() {
        val finish = CursorGuard.Expectation(carried = null, slotKeys = emptyMap())
        val sync = CursorGuard.Sync(finish, 0L, CursorGuard.STEP_TIMEOUT_MS)
        assertEquals(CursorGuard.Verdict.WAITING, sync.observe(50, CursorGuard.Snapshot(null, emptyMap(), 7)))
        assertEquals(CursorGuard.Verdict.SYNCED, sync.observe(100, CursorGuard.Snapshot(null, emptyMap(), 8)))
    }

    @Test fun stateIdChurnWithMismatchedKeysDiverges() {
        val sync = CursorGuard.Sync(drop, 0L, CursorGuard.STEP_TIMEOUT_MS)
        assertEquals(CursorGuard.Verdict.WAITING, sync.observe(50, snap("sword", null, stateId = 1)))
        assertEquals(CursorGuard.Verdict.DIVERGED, sync.observe(100, snap("sword", null, stateId = 2)))
    }

    @Test fun watchdogFiresOnlyAfterFiveSecondsWithoutProgress() {
        assertFalse(AutoActionRules.isStalled(running = true, lastProgressMs = 0L, nowMs = 4_999L))
        assertTrue(AutoActionRules.isStalled(running = true, lastProgressMs = 0L, nowMs = 5_000L))
        assertFalse(AutoActionRules.isStalled(running = false, lastProgressMs = 0L, nowMs = 60_000L))
    }

    /** stats (already worn, skipped) + loadout + sort (already sorted, no clicks): the batch ends and the lock releases. */
    @Test fun batchWithSkipLoadoutAndNoOpSortEndsWithCloseAndUnlock() {
        val queue = AutoBatchQueue()
        queue.enqueue(listOf(AutoTarget.Stats("p"), AutoTarget.Loadout(2), AutoTarget.Sort("main")), "trigger")
        var wasActive = false
        val ended = mutableListOf<Boolean>()
        fun observe(activeRun: Boolean, activeSort: Boolean, sortBusy: Boolean) {
            val nowActive = AutoActionRules.holdsInput(activeRun, activeSort, sortBusy, queue.size)
            ended += AutoActionRules.batchEnded(wasActive, nowActive)
            wasActive = nowActive
        }
        queue.pop()                                  // stats skipped: no run, step consumed
        observe(false, false, false)                 // loadout and sort still waiting: lock held
        assertTrue(wasActive)
        queue.pop()
        observe(activeRun = true, activeSort = false, sortBusy = false)   // loadout running
        queue.pop()
        observe(activeRun = false, activeSort = true, sortBusy = false)   // sort job started (no-op)
        observe(activeRun = false, activeSort = false, sortBusy = false)  // sort finished
        assertFalse(wasActive)
        assertEquals(listOf(false, false, false, true), ended)
        assertTrue(AutoActionRules.closesAtBatchEnd(ownedScreen = true, batchEnded = ended.last()))
    }

    @Test fun alreadyOpenInventoryClosesAtBatchEnd() {
        assertTrue(AutoActionRules.closesAtBatchEnd(ownedScreen = true, batchEnded = AutoActionRules.batchEnded(true, false)))
        assertFalse(AutoActionRules.closesAtBatchEnd(ownedScreen = true, batchEnded = AutoActionRules.batchEnded(true, true)))
    }

    @Test fun playerOwnedScreensAreNotClosedAtBatchEnd() {
        assertFalse(AutoActionRules.closesAtBatchEnd(ownedScreen = false, batchEnded = true))
    }

    @Test fun lockReleasesOnceNothingRunsOrWaits() {
        assertFalse(AutoActionRules.holdsInput(activeRun = false, activeSort = false, sortBusy = false, waitingSteps = 0))
        assertTrue(AutoActionRules.holdsInput(activeRun = false, activeSort = false, sortBusy = true, waitingSteps = 0))
    }

    @Test fun zeroPlannedClicksMeansNothingToWaitFor() {
        assertTrue(InvSortPlanner.plan(mapOf(9 to "a"), mapOf(9 to "a")).isEmpty())
    }
}
