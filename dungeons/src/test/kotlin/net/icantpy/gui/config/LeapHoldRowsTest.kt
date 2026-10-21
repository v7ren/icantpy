package net.icantpy.gui.config

import net.icantpy.dungeon.leap.LeapAutoKind
import net.icantpy.dungeon.leap.LeapCenterTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapHoldRowsTest {
    private fun shape(timedHold: Boolean, chain: Boolean = false, leap: Boolean = true) = LeapCardShape(
        clock = false,
        leaped = chain,
        chain = chain,
        groupedPreset = false,
        centerTarget = LeapCenterTarget.SENDER,
        stepKinds = if (leap) listOf(LeapAutoKind.LEAP_TARGET) else emptyList(),
        timedHold = timedHold,
    )

    private fun rowsOf(candidate: LeapCardShape): List<LeapCardRow> = LeapTriggerLayout.rows(candidate).map { it.row }

    @Test
    fun timedTargetShowsModeDurationAndLockRows() {
        val rows = rowsOf(shape(timedHold = true))
        assertTrue(LeapCardRow.HOLD_MODE in rows)
        assertTrue(LeapCardRow.HOLD_DURATION in rows)
        assertTrue(LeapCardRow.HOLD_LOCK in rows)
        assertTrue(rows.indexOf(LeapCardRow.HOLD_MODE) < rows.indexOf(LeapCardRow.HOLD_DURATION))
        assertTrue(rows.indexOf(LeapCardRow.HOLD_DURATION) < rows.indexOf(LeapCardRow.HOLD_LOCK))
    }

    @Test
    fun durationRowIsHiddenUnlessTimed() {
        val rows = rowsOf(shape(timedHold = false))
        assertTrue(LeapCardRow.HOLD_MODE in rows)
        assertTrue(LeapCardRow.HOLD_LOCK in rows)
        assertFalse(LeapCardRow.HOLD_DURATION in rows)
    }

    @Test
    fun holdRowsAppearOnlyUnderALeapTarget() {
        val rows = rowsOf(shape(timedHold = true, leap = false))
        assertFalse(LeapCardRow.HOLD_MODE in rows)
        assertFalse(LeapCardRow.HOLD_DURATION in rows)
        assertFalse(LeapCardRow.HOLD_LOCK in rows)
    }

    @Test
    fun chainDelayStaysAboveTheHoldRows() {
        val rows = rowsOf(shape(timedHold = true, chain = true))
        assertEquals(LeapCardRow.CHAIN_DELAY, rows[rows.indexOf(LeapCardRow.HOLD_MODE) - 1])
    }
}
