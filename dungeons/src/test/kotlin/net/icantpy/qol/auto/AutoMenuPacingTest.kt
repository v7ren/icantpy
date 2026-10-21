package net.icantpy.qol.auto

import net.icantpy.qol.auto.AutoMenuPacing.StepOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoMenuPacingTest {
    @Test fun nonInstantRunDoesOneStepPerTick() {
        var calls = 0
        val ended = AutoMenuPacing.runSteps(instant = false) { calls++; StepOutcome.PROGRESSED }
        assertEquals(1, calls)
        assertFalse(ended)
    }

    /** No idle tick: instant steps that make progress chain until one has to wait for a checkpoint. */
    @Test fun instantRunChainsStepsWithinOneTick() {
        var calls = 0
        val ended = AutoMenuPacing.runSteps(instant = true) {
            calls++
            if (calls <= 3) StepOutcome.PROGRESSED else StepOutcome.WAITING
        }
        assertEquals(4, calls)
        assertFalse(ended)
    }

    @Test fun instantRunStopsAtTheChainCap() {
        var calls = 0
        AutoMenuPacing.runSteps(instant = true) { calls++; StepOutcome.PROGRESSED }
        assertEquals(AutoMenuPacing.MAX_STEPS_PER_TICK, calls)
    }

    @Test fun endedStopsTheChainAndReportsIt() {
        var calls = 0
        val ended = AutoMenuPacing.runSteps(instant = true) {
            calls++
            if (calls == 2) StepOutcome.ENDED else StepOutcome.PROGRESSED
        }
        assertEquals(2, calls)
        assertTrue(ended)
    }

    @Test fun instantRunWaitsForPopulatedSlotOnlyUpToTheLimit() {
        assertTrue(AutoMenuPacing.keepWaitingForPopulate(instant = true, waitedMs = 0))
        assertTrue(AutoMenuPacing.keepWaitingForPopulate(instant = true, waitedMs = AutoMenuPacing.POPULATE_WAIT_MS - 1))
        assertFalse(AutoMenuPacing.keepWaitingForPopulate(instant = true, waitedMs = AutoMenuPacing.POPULATE_WAIT_MS))
        assertFalse(AutoMenuPacing.keepWaitingForPopulate(instant = false, waitedMs = 0))
    }

    @Test fun instantClicksSkipTheHoverSettleAndPollsAreImmediate() {
        assertEquals(0L, AutoMenuPacing.settleBeforeClickMs(instant = true, settleMs = AutoMenuRun.SETTLE_MS))
        assertEquals(AutoMenuRun.SETTLE_MS, AutoMenuPacing.settleBeforeClickMs(instant = false, settleMs = AutoMenuRun.SETTLE_MS))
        assertEquals(0L, AutoMenuPacing.pollMs(instant = true, pollMs = AutoMenuRun.POLL_MS))
        assertEquals(AutoMenuRun.POLL_MS, AutoMenuPacing.pollMs(instant = false, pollMs = AutoMenuRun.POLL_MS))
    }
}
