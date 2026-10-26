package net.icantpy.qol.auto

import net.icantpy.qol.invsort.InvSortTiming
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoActionRulesTest {
    private val reaction = 400L

    @Test
    fun waitsForReactionDelayForEveryKindAndScreen() {
        for (kind in AutoActionKind.entries) {
            for (screen in AutoScreenState.entries) {
                assertEquals(AutoDecision.WAIT, AutoActionRules.decide(kind, 399L, reaction, screen, busy = false))
            }
        }
    }

    @Test
    fun busyQueueBlocksEveryKindEvenAfterLongWaits() {
        for (kind in AutoActionKind.entries) {
            assertEquals(
                AutoDecision.WAIT,
                AutoActionRules.decide(kind, AutoActionRules.SCREEN_WAIT_LIMIT_MS * 2, reaction, AutoScreenState.NONE, busy = true),
            )
        }
    }

    @Test
    fun sortStartsWithNoScreenOrInsideOpenInventory() {
        assertEquals(
            AutoDecision.START,
            AutoActionRules.decide(AutoActionKind.SORT_INVENTORY, reaction, reaction, AutoScreenState.NONE, busy = false),
        )
        assertEquals(
            AutoDecision.START,
            AutoActionRules.decide(
                AutoActionKind.SORT_INVENTORY,
                reaction,
                reaction,
                AutoScreenState.PLAYER_INVENTORY,
                busy = false,
            ),
        )
    }

    @Test
    fun statsAndLoadoutNeverStartWhileAnyScreenIsOpen() {
        for (kind in listOf(AutoActionKind.EQUIP_STATS, AutoActionKind.SWITCH_LOADOUT, AutoActionKind.SWAP_PET)) {
            assertEquals(
                AutoDecision.WAIT,
                AutoActionRules.decide(kind, reaction, reaction, AutoScreenState.PLAYER_INVENTORY, busy = false),
            )
            assertEquals(
                AutoDecision.START,
                AutoActionRules.decide(kind, reaction, reaction, AutoScreenState.NONE, busy = false),
            )
        }
    }

    @Test
    fun givesUpOnlyAfterScreenWaitLimit() {
        assertEquals(
            AutoDecision.WAIT,
            AutoActionRules.decide(AutoActionKind.EQUIP_STATS, 7_999L, reaction, AutoScreenState.OTHER, busy = false),
        )
        assertEquals(
            AutoDecision.GIVE_UP,
            AutoActionRules.decide(
                AutoActionKind.SWITCH_LOADOUT,
                AutoActionRules.SCREEN_WAIT_LIMIT_MS,
                reaction,
                AutoScreenState.OTHER,
                busy = false,
            ),
        )
    }

    @Test
    fun sameActionForSameTriggerWithinWindowIsDuplicate() {
        assertFalse(AutoActionRules.isDuplicate(null, 10_000L))
        assertTrue(AutoActionRules.isDuplicate(10_000L, 12_999L))
        assertFalse(AutoActionRules.isDuplicate(10_000L, 13_000L))
    }

    @Test
    fun batchDedupeIsPerTriggerNotPerStep() {
        val dedupe = AutoRequestDedupe(windowMs = AutoActionRules.DUPLICATE_WINDOW_MS)
        assertTrue(dedupe.accept("batch:card-a", nowMs = 0L))
        assertFalse(dedupe.accept("batch:card-a", nowMs = 1_000L), "a second firing of the same card is one batch")
        assertTrue(dedupe.accept("batch:card-b", nowMs = 1_000L), "another card is its own batch")
    }

    @Test
    fun reactionDelayIsSampledWithinHumanRange() {
        val timing = InvSortTiming(
            AutoActionRules.REACTION_MIN_MS,
            AutoActionRules.REACTION_MAX_MS,
            random = Random(7),
        )
        repeat(500) {
            val delay = timing.sampleDelayMs()
            assertTrue(delay in AutoActionRules.REACTION_MIN_MS..AutoActionRules.REACTION_MAX_MS, "delay $delay")
        }
    }

    @Test
    fun inputIsHeldFromQueueingUntilTheBatchFinishes() {
        assertFalse(AutoActionRules.holdsInput(activeRun = false, activeSort = false, sortBusy = false, waitingSteps = 0))
        // A waiting step (including one waiting for a menu to open) holds input before anything starts.
        assertTrue(AutoActionRules.holdsInput(activeRun = false, activeSort = false, sortBusy = false, waitingSteps = 1))
        assertTrue(AutoActionRules.holdsInput(activeRun = true, activeSort = false, sortBusy = false, waitingSteps = 0))
        assertTrue(AutoActionRules.holdsInput(activeRun = false, activeSort = true, sortBusy = false, waitingSteps = 0))
        assertTrue(AutoActionRules.holdsInput(activeRun = false, activeSort = false, sortBusy = true, waitingSteps = 0))
    }

    // Instant mode: no reaction delay and same-tick chaining.
    @Test fun instantModeHasNoReactionDelay() {
        assertEquals(0L, AutoActionRules.reactionDelayMs(instant = true, sampledMs = 400L))
        assertEquals(400L, AutoActionRules.reactionDelayMs(instant = false, sampledMs = 400L))
        assertEquals(AutoDecision.START, AutoActionRules.decide(AutoActionKind.EQUIP_STATS, 0L, 0L, AutoScreenState.NONE, busy = false))
    }

    @Test fun instantModeChainsTheNextStepInTheSameTick() {
        assertTrue(AutoActionRules.chainsSameTick(instant = true, previousFinished = true))
        assertFalse(AutoActionRules.chainsSameTick(instant = true, previousFinished = false))
        assertFalse(AutoActionRules.chainsSameTick(instant = false, previousFinished = true))
    }
}
