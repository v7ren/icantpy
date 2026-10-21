package net.icantpy.gui.config

import net.icantpy.dungeon.leap.LeapAutoKind
import net.icantpy.dungeon.leap.LeapCenterTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeapSortOptionRowsTest {
    private fun shape(vararg kinds: LeapAutoKind, armed: Set<Int> = emptySet()) = LeapCardShape(
        clock = false,
        leaped = false,
        chain = false,
        groupedPreset = false,
        centerTarget = LeapCenterTarget.CLASS,
        stepKinds = kinds.toList(),
        sortArmedSteps = armed,
    )

    private fun sortRows(shape: LeapCardShape) = LeapTriggerLayout.rows(shape).filter {
        it.row in setOf(LeapCardRow.SORT_ARMED, LeapCardRow.SORT_LOCK, LeapCardRow.SORT_REOPEN, LeapCardRow.SORT_ARM_TIMEOUT)
    }

    @Test
    fun unarmedSortShowsOnlyTheArmToggleIndented() {
        val rows = sortRows(shape(LeapAutoKind.SORT_INVENTORY))
        assertEquals(listOf(LeapCardRow.SORT_ARMED), rows.map { it.row })
        assertTrue(rows.single().indented)
        assertEquals(0, rows.single().step)
    }

    @Test
    fun armedSortAddsLockReopenAndTimeoutRowsBelowItsStep() {
        // Only the action rows: the header, timing and filter rows above them are not under test here.
        val rows = LeapTriggerLayout.rows(shape(LeapAutoKind.SORT_INVENTORY, armed = setOf(0)))
            .dropWhile { it.row != LeapCardRow.STEP }
        assertEquals(
            listOf(LeapCardRow.STEP, LeapCardRow.SORT_ARMED, LeapCardRow.SORT_LOCK, LeapCardRow.SORT_REOPEN, LeapCardRow.SORT_ARM_TIMEOUT, LeapCardRow.ADD_STEP),
            rows.map { it.row },
        )
        assertTrue(rows.drop(1).take(4).all { it.indented && it.step == 0 })
    }

    @Test
    fun armedFlagOnlyAffectsItsOwnStep() {
        val rows = LeapTriggerLayout.rows(shape(LeapAutoKind.SORT_INVENTORY, LeapAutoKind.SORT_INVENTORY, armed = setOf(1)))
        val lockRows = rows.filter { it.row == LeapCardRow.SORT_LOCK }
        assertEquals(listOf(1), lockRows.map { it.step })
    }

    @Test
    fun nonSortStepsGetNoOptionRows() {
        assertTrue(sortRows(shape(LeapAutoKind.SWITCH_LOADOUT, LeapAutoKind.EQUIP_STATS)).isEmpty())
    }

    @Test
    fun cardHeightGrowsOnlyWhileArmed() {
        val unarmed = LeapTriggerLayout.rows(shape(LeapAutoKind.SORT_INVENTORY)).size
        val armed = LeapTriggerLayout.rows(shape(LeapAutoKind.SORT_INVENTORY, armed = setOf(0))).size
        assertEquals(unarmed + 3, armed)
        assertTrue(LeapTriggerLayout.cardHeight(true, armed) > LeapTriggerLayout.cardHeight(true, unarmed))
    }
}
