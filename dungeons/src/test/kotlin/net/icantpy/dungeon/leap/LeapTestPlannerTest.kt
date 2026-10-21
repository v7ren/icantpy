package net.icantpy.dungeon.leap

import net.icantpy.qol.auto.AutoTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapTestPlannerTest {
    private val base = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START).copy(id = "card-1")

    private val leap = LeapAutoStep.leapTarget()

    private fun trigger(steps: List<LeapAutoStep>) = base.copy(steps = steps)

    @Test
    fun cardWithNoLeapAndNoStepsHasNothingToTest() {
        assertTrue(LeapTestPlanner.plan(trigger(steps = emptyList())).nothingToTest)
    }

    @Test
    fun leapOnlyCardArmsWithoutQueueingSteps() {
        val plan = LeapTestPlanner.plan(trigger(steps = listOf(leap)))
        assertTrue(plan.armLeap)
        assertTrue(plan.targets.isEmpty())
        assertTrue(plan.skipped.isEmpty(), "the leap row is applied instantly, so it is never reported as skipped")
        assertFalse(plan.nothingToTest)
    }

    @Test
    fun leapAndQueuedStepsRunTogetherInOrder() {
        val sort = LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "Combat")
        val loadout = LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 2)
        val plan = LeapTestPlanner.plan(trigger(steps = listOf(sort, leap, loadout)))
        assertTrue(plan.armLeap)
        assertEquals(listOf(AutoTarget.Sort("Combat"), AutoTarget.Loadout(2)), plan.targets)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun stepsKeepTheirOrderAndBecomeTargets() {
        val steps = listOf(
            LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "Combat"),
            LeapAutoStep(LeapAutoKind.EQUIP_STATS, statsPreset = "helm"),
            LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 2),
        )
        val plan = LeapTestPlanner.plan(trigger(steps = steps))
        assertEquals(
            listOf(AutoTarget.Sort("Combat"), AutoTarget.Stats("helm"), AutoTarget.Loadout(2)),
            plan.targets,
        )
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun incompleteStepsAreSkippedAndTheRestStillRun() {
        val good = LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 4)
        val missingPreset = LeapAutoStep(LeapAutoKind.EQUIP_STATS, statsPreset = "")
        val plan = LeapTestPlanner.plan(trigger(steps = listOf(missingPreset, good)))
        assertEquals(listOf(AutoTarget.Loadout(4)), plan.targets)
        assertEquals(listOf(missingPreset), plan.skipped)
        assertFalse(plan.nothingToTest)
    }

    @Test
    fun onlySkippedStepsStillCountAsSomethingToReport() {
        val plan = LeapTestPlanner.plan(trigger(steps = listOf(LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 0))))
        assertTrue(plan.targets.isEmpty())
        assertFalse(plan.nothingToTest)
    }
}
