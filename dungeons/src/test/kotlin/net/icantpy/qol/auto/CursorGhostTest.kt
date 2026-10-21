package net.icantpy.qol.auto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorGhostTest {
    private val containerKeys = listOf("loadout-1", "loadout-2", "arrow")

    @Test fun clickedMenuItemOnCursorIsAGhost() {
        assertTrue(CursorGuard.isGhost(carried = "loadout-2", clickedKey = "loadout-2", containerKeys = emptyList()))
    }

    @Test fun anyContainerItemOnCursorIsAGhost() {
        assertTrue(CursorGuard.isGhost(carried = "arrow", clickedKey = null, containerKeys = containerKeys))
    }

    @Test fun realPlayerItemOnCursorIsNotAGhost() {
        assertFalse(CursorGuard.isGhost(carried = "sword", clickedKey = "loadout-1", containerKeys = containerKeys))
        assertFalse(CursorGuard.isGhost(carried = null, clickedKey = "loadout-1", containerKeys = containerKeys))
    }

    @Test fun closeIsRefusedForRealItemButAllowedOverGhost() {
        val ghosts = setOf("loadout-1")
        assertTrue(CursorGuard.mayCloseOverGhost(carried = null, ghostKeys = ghosts))
        assertTrue(CursorGuard.mayCloseOverGhost(carried = "loadout-1", ghostKeys = ghosts))
        assertFalse(CursorGuard.mayCloseOverGhost(carried = "diamond", ghostKeys = ghosts))
    }

    @Test fun ghostIsWaitedOutThenClosedOverNotRecovered() {
        assertEquals(CursorGuard.HeldItemPlan.CLOSE_NOW, CursorGuard.planForHeldItem(held = false, ghost = false, waitedMs = 0))
        assertEquals(CursorGuard.HeldItemPlan.WAIT_FOR_SERVER, CursorGuard.planForHeldItem(held = true, ghost = true, waitedMs = 0))
        assertEquals(
            CursorGuard.HeldItemPlan.WAIT_FOR_SERVER,
            CursorGuard.planForHeldItem(held = true, ghost = true, waitedMs = CursorGuard.GHOST_WAIT_MS - 1),
        )
        assertEquals(
            CursorGuard.HeldItemPlan.CLOSE_OVER_GHOST,
            CursorGuard.planForHeldItem(held = true, ghost = true, waitedMs = CursorGuard.GHOST_WAIT_MS),
        )
    }

    @Test fun realItemOnCursorStillGoesToRecoveryAndBlocksClose() {
        assertEquals(CursorGuard.HeldItemPlan.RECOVER, CursorGuard.planForHeldItem(held = true, ghost = false, waitedMs = 10_000))
    }

    @Test fun serverReplacedMenuAfterClickCountsAsCompletion() {
        assertTrue(CursorGuard.menuReplacedAfterClick(clickedContainerId = 3, currentContainerId = 4))
        assertFalse(CursorGuard.menuReplacedAfterClick(clickedContainerId = 3, currentContainerId = 3))
        assertFalse(CursorGuard.menuReplacedAfterClick(clickedContainerId = null, currentContainerId = 4))
    }

    @Test fun cursorPinHoldsOnlyWhileRunLockAndScreenMatch() {
        assertTrue(AutoActionRules.holdsCursorPin(screenOwnsPin = true, runActive = true, lockInput = true))
        assertFalse(AutoActionRules.holdsCursorPin(screenOwnsPin = true, runActive = false, lockInput = true))
        assertFalse(AutoActionRules.holdsCursorPin(screenOwnsPin = true, runActive = true, lockInput = false))
        assertFalse(AutoActionRules.holdsCursorPin(screenOwnsPin = false, runActive = true, lockInput = true))
    }

    @Test fun watchdogFiresAfterFiveSecondsWithoutClickProgress() {
        val lastClick = 1_000L
        assertFalse(AutoActionRules.isStalled(running = true, lastProgressMs = lastClick, nowMs = lastClick + 4_999))
        assertTrue(AutoActionRules.isStalled(running = true, lastProgressMs = lastClick, nowMs = lastClick + 5_000))
    }
}
