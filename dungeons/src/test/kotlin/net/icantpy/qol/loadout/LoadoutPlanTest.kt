package net.icantpy.qol.loadout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadoutPlanTest {
    /** First page of a menu: loadouts 1..3 at container slots 5..7. */
    private val firstPage = listOf(5 to 1, 6 to 2, 7 to 3)

    @Test
    fun picksSlotForEachVisibleNumber() {
        assertEquals(5, LoadoutPlan.pickByNumber(firstPage, 1))
        assertEquals(6, LoadoutPlan.pickByNumber(firstPage, 2))
        assertEquals(7, LoadoutPlan.pickByNumber(firstPage, 3))
    }

    @Test
    fun missingNumberHasNoSlot() {
        assertNull(LoadoutPlan.pickByNumber(firstPage, 4))
        assertNull(LoadoutPlan.pickByNumber(emptyList(), 1))
    }

    @Test
    fun visibleNumberIsPressedWithoutTurningPage() {
        assertEquals(LoadoutStep.PRESS, LoadoutPlan.pageDirection(listOf(1, 2, 3), 2, hasNext = true, hasPrev = false))
    }

    @Test
    fun loadoutThirteenIsReachedThroughNextPage() {
        val firstPageNumbers = (1..12).toList()
        assertEquals(LoadoutStep.NEXT_PAGE, LoadoutPlan.pageDirection(firstPageNumbers, 13, hasNext = true, hasPrev = false))
        assertEquals(LoadoutStep.PRESS, LoadoutPlan.pageDirection((13..18).toList(), 13, hasNext = false, hasPrev = true))
    }

    @Test
    fun numberBelowVisibleRangeTurnsPreviousPage() {
        assertEquals(LoadoutStep.PREVIOUS_PAGE, LoadoutPlan.pageDirection((13..18).toList(), 4, hasNext = false, hasPrev = true))
    }

    @Test
    fun missingNumberWithoutArrowInThatDirectionIsMissing() {
        assertEquals(LoadoutStep.MISSING, LoadoutPlan.pageDirection(listOf(1, 2, 3), 5, hasNext = false, hasPrev = true))
        assertEquals(LoadoutStep.MISSING, LoadoutPlan.pageDirection(listOf(4, 5, 6), 2, hasNext = true, hasPrev = false))
        assertEquals(LoadoutStep.MISSING, LoadoutPlan.pageDirection(listOf(1, 3), 2, hasNext = true, hasPrev = true))
    }

    @Test
    fun emptyPageFollowsAvailableArrow() {
        assertEquals(LoadoutStep.NEXT_PAGE, LoadoutPlan.pageDirection(emptyList(), 1, hasNext = true, hasPrev = false))
        assertEquals(LoadoutStep.PREVIOUS_PAGE, LoadoutPlan.pageDirection(emptyList(), 1, hasNext = false, hasPrev = true))
        assertEquals(LoadoutStep.MISSING, LoadoutPlan.pageDirection(emptyList(), 1, hasNext = false, hasPrev = false))
    }

    @Test
    fun readsLoadoutNumberFromHoverName() {
        assertEquals(6, LoadoutPlan.numberFromIconName("Loadout 6"))
        assertEquals(1, LoadoutPlan.numberFromIconName("§aLoadout 1"))
        assertEquals(12, LoadoutPlan.numberFromIconName("§aLoadout 12"))
        assertEquals(18, LoadoutPlan.numberFromIconName("loadout 18"))
        assertNull(LoadoutPlan.numberFromIconName("Loadout"))
        assertNull(LoadoutPlan.numberFromIconName("Loadout Slot"))
        assertNull(LoadoutPlan.numberFromIconName("Empty Loadout"))
        assertNull(LoadoutPlan.numberFromIconName("Loadout 2 extra"))
        assertNull(LoadoutPlan.numberFromIconName("Next Page"))
    }

    @Test
    fun readsPageFromTitle() {
        assertEquals(1 to 3, LoadoutPlan.pageOf("(1/3) Loadouts"))
        assertEquals(2 to 3, LoadoutPlan.pageOf("§7(2 / 3) Loadouts"))
        assertNull(LoadoutPlan.pageOf("Stats & Equipment"))
    }
}
