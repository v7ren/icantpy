package net.icantpy.dungeon.leap

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapTriggerLockStateTest {
    @AfterTest
    fun cleanup() = LeapTriggerLockState.reset()

    @Test
    fun unresolvedTriggerWaitsUntilRequiredClassIsActuallyLeaped() {
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.CLOCK, "healer")
            .copy(id = "healer-after-tank", requiresTargetClass = LeapDungeonClass.TANK)
        assertFalse(LeapTriggerLockState.isSatisfied(trigger))
        LeapTriggerLockState.defer(DeferredLeapTrigger(trigger, LeapTriggerEvent.CLOCK, "clock", null, "35s"))
        assertEquals(1, LeapTriggerLockState.deferredCount())

        assertTrue(LeapTriggerLockState.markCompleted(LeapDungeonClass.BERSERK).isEmpty())
        assertEquals(1, LeapTriggerLockState.deferredCount())
        val ready = LeapTriggerLockState.markCompleted(LeapDungeonClass.TANK)
        assertEquals(listOf("healer-after-tank"), ready.map { it.trigger.id })
        assertTrue(LeapTriggerLockState.isSatisfied(trigger))
        assertEquals(0, LeapTriggerLockState.deferredCount())
    }

    @Test
    fun rowRequiringHealerIsNotSatisfiedByALeapToTankEvenAfterAnEarlierHealerLeap() {
        val step = LeapAutoStep.leapTarget().copy(requires = LeapDungeonClass.HEALER)
        LeapTriggerLockState.markCompleted(LeapDungeonClass.HEALER)
        assertTrue(LeapTriggerLockState.isSatisfied(step))
        assertFalse(LeapTriggerLockState.isSatisfiedByLeap(step, LeapDungeonClass.TANK))
        assertFalse(LeapTriggerLockState.isSatisfiedByLeap(step, null))
        assertTrue(LeapTriggerLockState.isSatisfiedByLeap(step, LeapDungeonClass.HEALER))
        assertTrue(LeapTriggerLockState.isSatisfiedByLeap(step.copy(requires = LeapDungeonClass.EMPTY), LeapDungeonClass.TANK))
    }

    @Test
    fun noRequirementPreservesImmediateBehavior() {
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.CLOCK, "healer")
        assertTrue(LeapTriggerLockState.isSatisfied(trigger))
    }

    @Test
    fun defaultPyHealerRuleRequiresTankFirst() {
        val trigger = LeapRoutePreset.rows(LeapStormRoute.PY)
            .first { it.id == "mage-py-35s-healer" }
        assertEquals(LeapDungeonClass.TANK, trigger.requiresTargetClass)
    }

    @Test
    fun aPhaseChangeKeepsRowsParkedJustNowButDropsOldOnes() {
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.CLOCK, "healer").copy(id = "p")
        val fresh = LeapAutoStep.leapTarget().copy(requires = LeapDungeonClass.HEALER)
        val old = LeapAutoStep.leapTarget().copy(requires = LeapDungeonClass.TANK)
        LeapTriggerLockState.deferStep(DeferredLeapStep(trigger, fresh, "x", "m", atMs = 9_500))
        LeapTriggerLockState.deferStep(DeferredLeapStep(trigger, old, "x", "m", atMs = 1_000))
        LeapTriggerLockState.markCompleted(LeapDungeonClass.MAGE)

        LeapTriggerLockState.resetForPhase(nowMs = 10_000)

        assertEquals(1, LeapTriggerLockState.deferredCount())
        assertEquals(1, LeapTriggerLockState.takeSteps(LeapDungeonClass.HEALER).size)
        assertTrue(LeapTriggerLockState.completed().isEmpty())
    }
}
