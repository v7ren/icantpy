package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeapAutoStepListTest {
    private val sort = LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "P1")
    private val stats = LeapAutoStep(LeapAutoKind.EQUIP_STATS, statsPreset = "helm")
    private val loadout = LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 3)

    @Test
    fun movedReordersAndKeepsOtherItems() {
        assertEquals(listOf(stats, loadout, sort), LeapAutoStepList.moved(listOf(sort, stats, loadout), from = 0, to = 2))
        assertEquals(listOf(loadout, sort, stats), LeapAutoStepList.moved(listOf(sort, stats, loadout), from = 2, to = 0))
    }

    @Test
    fun movedIgnoresOutOfRangeAndNoOpMoves() {
        val steps = listOf(sort, stats)
        assertEquals(steps, LeapAutoStepList.moved(steps, from = 0, to = 5))
        assertEquals(steps, LeapAutoStepList.moved(steps, from = -1, to = 1))
        assertEquals(steps, LeapAutoStepList.moved(steps, from = 1, to = 1))
    }

    @Test
    fun addedAppendsUntilTheCap() {
        var steps: List<LeapAutoStep> = emptyList()
        repeat(LeapAutoStep.MAX_STEPS + 3) { steps = LeapAutoStepList.added(steps, sort) }
        assertEquals(LeapAutoStep.MAX_STEPS, steps.size)
    }

    @Test
    fun removedDropsOnlyTheIndexedItem() {
        assertEquals(listOf(sort, loadout), LeapAutoStepList.removed(listOf(sort, stats, loadout), index = 1))
        assertEquals(listOf(sort, stats), LeapAutoStepList.removed(listOf(sort, stats), index = 9))
    }

    @Test
    fun replacedChangesOnlyTheIndexedItem() {
        val changed = LeapAutoStepList.replaced(listOf(sort, stats), index = 0) { it.copy(sortPreset = "P2") }
        assertEquals(listOf(sort.copy(sortPreset = "P2"), stats), changed)
    }

    @Test
    fun kindCyclesThroughAllSixAndBack() {
        val cycle = generateSequence(LeapAutoKind.SORT_INVENTORY) { it.next() }.take(7).toList()
        assertEquals(
            listOf(
                LeapAutoKind.SORT_INVENTORY,
                LeapAutoKind.EQUIP_STATS,
                LeapAutoKind.SWITCH_LOADOUT,
                LeapAutoKind.LEAP_TARGET,
                LeapAutoKind.FOLLOWUP_LEAP,
                LeapAutoKind.MARK_PLAYER,
                LeapAutoKind.SORT_INVENTORY,
            ),
            cycle,
        )
    }

    @Test
    fun kindSelectorSkipsLeapTargetWhenAnotherRowHoldsIt() {
        val sort = LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "P1")
        val steps = listOf(LeapAutoStep.leapTarget(), sort)
        // Row 1 is a loadout: cycling it must not land on a second leap target. The next legal kind is
        // Follow-up, which the card allows because row 0 holds its leap target.
        assertEquals(LeapAutoKind.FOLLOWUP_LEAP, LeapAutoStep.nextKind(LeapAutoKind.SWITCH_LOADOUT, index = 1, steps = steps))
        // The leap row itself may cycle away from leap target.
        // Follow-up is not allowed here, so the cycle moves on to Mark player.
        assertEquals(LeapAutoKind.MARK_PLAYER, LeapAutoStep.nextKind(LeapAutoKind.LEAP_TARGET, index = 0, steps = steps))
    }

    @Test
    fun kindSelectorOffersLeapTargetWhenNoRowHoldsIt() {
        val steps = listOf(LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 1))
        assertEquals(LeapAutoKind.LEAP_TARGET, LeapAutoStep.nextKind(LeapAutoKind.SWITCH_LOADOUT, index = 0, steps = steps))
    }

    @Test
    fun addingASecondLeapTargetIsRefused() {
        val steps = listOf(LeapAutoStep.leapTarget())
        assertEquals(steps, LeapAutoStepList.added(steps, LeapAutoStep.leapTarget()))
        val withSort = listOf(steps.first(), sort)
        assertEquals(withSort, LeapAutoStepList.replaced(withSort, index = 1) { LeapAutoStep.leapTarget() })
    }

    @Test
    fun switchingARowToLeapTargetDropsItsOldValue() {
        val changed = sort.withKind(LeapAutoKind.LEAP_TARGET, defaultSortPreset = "P9", defaultStatsPreset = "boots")
        assertEquals(LeapAutoStep.leapTarget(), changed)
    }

    @Test
    fun leapTargetCountsAsARowButNotAQueuedAction() {
        assertEquals(null, LeapAutoStep.target(LeapAutoStep.leapTarget()))
    }

    @Test
    fun switchingKindFillsDefaultsButKeepsTypedValues() {
        val switched = sort.withKind(LeapAutoKind.EQUIP_STATS, defaultSortPreset = "P9", defaultStatsPreset = "boots")
        assertEquals("boots", switched.statsPreset)
        assertEquals("P1", switched.sortPreset)
        val loadoutStep = stats.withKind(LeapAutoKind.SWITCH_LOADOUT, defaultSortPreset = "P9", defaultStatsPreset = "boots")
        assertEquals(1, loadoutStep.loadout)
    }

    @Test
    fun stepLabelsDescribeEachKind() {
        val labels = mapOf("helm" to "Helmet: Aurora")
        assertEquals("Sort: P1", sort.label(labels))
        assertEquals("Stats: Helmet: Aurora", stats.label(labels))
        assertEquals("Stats: (missing)", stats.copy(statsPreset = "gone").label(labels))
        assertEquals("Loadout 3", loadout.label())
        assertEquals("no loadout", loadout.copy(loadout = 0).label())
    }

    @Test
    fun incompleteStepHasNoRunnableTargetAndAProblem() {
        assertNull(LeapAutoStep.target(LeapAutoStep(LeapAutoKind.SORT_INVENTORY)))
        assertNull(LeapAutoStep.target(LeapAutoStep(LeapAutoKind.EQUIP_STATS)))
        assertNull(LeapAutoStep.target(LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT)))
        assertEquals("no sort preset chosen", LeapAutoStep.problem(LeapAutoStep(LeapAutoKind.SORT_INVENTORY)))
    }

    @Test
    fun triggerHelpersReorderAddAndRemoveSteps() {
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START).copy(steps = listOf(sort, stats))
        assertEquals(listOf(stats, sort), trigger.withStepMoved(0, 1).steps)
        assertEquals(listOf(sort), trigger.withStepRemoved(1).steps)
        assertEquals(listOf(sort, stats, loadout), trigger.withStepAdded(loadout).steps)
        assertEquals(listOf(sort.copy(sortPreset = "P5"), stats), trigger.withStepChanged(0) { it.copy(sortPreset = "P5") }.steps)
        assertTrue(trigger.steps.size == 2, "helpers return copies")
    }

    @Test
    fun triggerAddStopsAtSixSteps() {
        var trigger = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START)
        repeat(9) { trigger = trigger.withStepAdded(sort) }
        assertEquals(LeapAutoStep.MAX_STEPS, trigger.steps.size)
    }
}
