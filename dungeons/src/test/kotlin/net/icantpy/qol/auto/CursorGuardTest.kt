package net.icantpy.qol.auto

import net.icantpy.qol.invsort.InvSortPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CursorGuardTest {
    private val timeout = CursorGuard.STEP_TIMEOUT_MS

    /** Clicking an empty slot 9 while carrying "sword" drops the sword there. */
    private val drop = CursorGuard.expectAfterClick(carried = "sword", slotKey = null, slot = 9)

    private fun snap(carried: String?, slot9: String? = null, stateId: Int? = null) =
        CursorGuard.Snapshot(carried, mapOf(9 to slot9), stateId)

    private fun sync(expectation: CursorGuard.Expectation = drop, armedMs: Long = 0L) =
        CursorGuard.Sync(expectation, armedMs, timeout)

    @Test fun expectationPredictsThePickupResult() {
        assertEquals(CursorGuard.Expectation(carried = null, slotKeys = mapOf(9 to "sword")), drop)
        val pickup = CursorGuard.expectAfterClick(carried = null, slotKey = "bow", slot = 10)
        assertEquals(CursorGuard.Expectation(carried = "bow", slotKeys = mapOf(10 to null)), pickup)
    }

    @Test fun proceedsOnlyAfterMatchingStateIsStableForSettleTicks() {
        val s = sync()
        assertEquals(CursorGuard.Verdict.WAITING, s.observe(50, snap(carried = null, slot9 = "sword")))
        assertEquals(CursorGuard.Verdict.SYNCED, s.observe(100, snap(carried = null, slot9 = "sword")))
    }

    @Test fun aSingleMatchingObservationIsNotEnough() {
        val s = sync()
        assertEquals(CursorGuard.Verdict.WAITING, s.observe(50, snap(carried = null, slot9 = "sword")))
        // The state changed again, so the count restarts.
        assertEquals(CursorGuard.Verdict.WAITING, s.observe(100, snap(carried = "sword", slot9 = null)))
        assertEquals(CursorGuard.Verdict.WAITING, s.observe(150, snap(carried = null, slot9 = "sword")))
        assertEquals(CursorGuard.Verdict.SYNCED, s.observe(200, snap(carried = null, slot9 = "sword")))
    }

    /** A menu that changes its state id every tick still settles once carried and watched keys agree. */
    @Test fun churningStateIdWithMatchingKeysStillSettles() {
        val s = sync()
        assertEquals(CursorGuard.Verdict.WAITING, s.observe(0, snap(carried = null, slot9 = "sword", stateId = 0)))
        assertEquals(CursorGuard.Verdict.SYNCED, s.observe(50, snap(carried = null, slot9 = "sword", stateId = 1)))
    }

    /** A churning state id does not hide a mismatch: unstable keys keep the sync waiting. */
    @Test fun churningStateIdWithMismatchedKeysKeepsTheSyncWaiting() {
        val s = sync()
        var verdict = CursorGuard.Verdict.WAITING
        for (tick in 0 until 5) {
            verdict = s.observe(tick * 50L, snap(carried = if (tick % 2 == 0) null else "sword", slot9 = null, stateId = tick))
        }
        assertEquals(CursorGuard.Verdict.WAITING, verdict)
    }

    @Test fun stableMismatchIsDivergenceNotATimeout() {
        val s = sync()
        assertEquals(CursorGuard.Verdict.WAITING, s.observe(50, snap(carried = "sword")))
        assertEquals(CursorGuard.Verdict.DIVERGED, s.observe(100, snap(carried = "sword")))
    }

    @Test fun neverConfirmedClickTimesOut() {
        val s = sync()
        var now = 0L
        var verdict = CursorGuard.Verdict.WAITING
        var n = 0
        while (verdict == CursorGuard.Verdict.WAITING) {
            // Keys keep flickering so the state is never stable.
            verdict = s.observe(now, snap(carried = if (n % 2 == 0) null else "x", slot9 = null, stateId = n))
            now += 50
            n++
            check(now < 10 * timeout) { "never timed out" }
        }
        assertEquals(CursorGuard.Verdict.TIMED_OUT, verdict)
        assertTrue(now >= timeout)
    }

    @Test fun slowPingDoublesTheTimeout() {
        assertEquals(timeout, CursorGuard.timeoutMs(null))
        assertEquals(timeout, CursorGuard.timeoutMs(CursorGuard.SLOW_PING_MS))
        assertEquals(timeout * 2, CursorGuard.timeoutMs(CursorGuard.SLOW_PING_MS + 1))
    }

    @Test fun recoveryReturnsToSourceWhenItIsStillEmpty() {
        val recovery = CursorGuard.Recovery(source = 12, candidates = CursorGuard.INVENTORY_SLOTS)
        val action = recovery.next(carriedEmpty = false) { slot -> slot != 30 }
        assertEquals(CursorGuard.RecoverAction.Click(12), action)
    }

    @Test fun recoveryUsesFirstEmptyInventorySlotWhenSourceIsFull() {
        val empty = setOf(20, 40)
        val recovery = CursorGuard.Recovery(source = 12, candidates = CursorGuard.INVENTORY_SLOTS)
        val action = recovery.next(carriedEmpty = false) { slot -> slot in empty }
        assertEquals(CursorGuard.RecoverAction.Click(20), action)
    }

    @Test fun recoveryNeverTargetsArmorCraftingOrTheStarSlot() {
        assertTrue(CursorGuard.INVENTORY_SLOTS.none { it in 0..8 || it == 44 || it >= 45 })
        // Only armor, crafting and the star are empty: none is a legal target.
        val recovery = CursorGuard.Recovery(source = 5, candidates = CursorGuard.INVENTORY_SLOTS)
        assertEquals(CursorGuard.RecoverAction.NoRoom, recovery.next(carriedEmpty = false) { it in 0..8 || it == 44 })
    }

    @Test fun noEmptySlotGivesLeaveOpenVerdict() {
        val recovery = CursorGuard.Recovery(source = 9, candidates = CursorGuard.INVENTORY_SLOTS)
        assertEquals(CursorGuard.RecoverAction.NoRoom, recovery.next(carriedEmpty = false) { false })
    }

    @Test fun recoveryStopsAfterMaxAttempts() {
        val recovery = CursorGuard.Recovery(source = 9, candidates = CursorGuard.INVENTORY_SLOTS)
        repeat(CursorGuard.MAX_RECOVER_ATTEMPTS) {
            assertIs<CursorGuard.RecoverAction.Click>(recovery.next(carriedEmpty = false) { true })
        }
        assertEquals(CursorGuard.RecoverAction.Exhausted, recovery.next(carriedEmpty = false) { true })
    }

    @Test fun recoveryIsDoneOnceTheCursorIsEmpty() {
        val recovery = CursorGuard.Recovery(source = 9, candidates = CursorGuard.INVENTORY_SLOTS)
        assertEquals(CursorGuard.RecoverAction.Done, recovery.next(carriedEmpty = true) { true })
        assertEquals(0, recovery.attempts)
    }

    @Test fun closeIsRefusedWhileAnItemIsHeld() {
        assertFalse(CursorGuard.mayClose("sword"))
        assertTrue(CursorGuard.mayClose(null))
    }

    @Test fun cancelRecoversBeforeAnythingCloses() {
        // A cancel with an item on the cursor: the put-back click comes first and closing stays refused.
        val recovery = CursorGuard.Recovery(source = 10, candidates = CursorGuard.INVENTORY_SLOTS)
        var carried: String? = "sword"
        assertFalse(CursorGuard.mayClose(carried))
        val click = recovery.next(carriedEmpty = carried == null) { true }
        assertEquals(CursorGuard.RecoverAction.Click(10), click)
        carried = null
        assertTrue(CursorGuard.mayClose(carried))
        assertEquals(CursorGuard.RecoverAction.Done, recovery.next(carriedEmpty = true) { true })
    }

    @Test fun instantModeStillWaitsForConfirmationBetweenClicks() {
        val start = mapOf(9 to "a", 10 to "b", 11 to "c")
        val target = mapOf(9 to "c", 10 to "a", 11 to "b")
        val clicks = InvSortPlanner.plan(start, target)
        assertTrue(clicks.size >= 2)

        var keys: Map<Int, String?> = start
        var carried: String? = null
        var now = 0L
        var observations = 0
        for (slot in clicks) {
            val expectation = CursorGuard.expectAfterClick(carried, keys[slot], slot)
            val sync = CursorGuard.Sync(expectation, now, CursorGuard.STEP_TIMEOUT_MS)
            val after = InvSortPlanner.applyPickup(keys, carried, slot)
            // The server confirms with a constant state; the sync must see it settle for SETTLE_TICKS ticks.
            var verdict = CursorGuard.Verdict.WAITING
            var waited = 0
            while (verdict == CursorGuard.Verdict.WAITING) {
                verdict = sync.observe(now, CursorGuard.Snapshot(after.carried, mapOf(slot to after.slots[slot]), 1))
                now += 50
                waited++
            }
            assertEquals(CursorGuard.Verdict.SYNCED, verdict)
            assertTrue(waited >= CursorGuard.SETTLE_TICKS, "click on $slot confirmed too early")
            observations += waited
            keys = after.slots
            carried = after.carried
        }
        assertTrue(observations >= clicks.size * CursorGuard.SETTLE_TICKS)
        assertEquals(null, carried)
    }
}
