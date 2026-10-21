package net.icantpy.gui.config

import net.icantpy.dungeon.leap.LeapAutoKind
import net.icantpy.dungeon.leap.LeapCenterTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapMarkRowsTest {
    private fun shape(vararg kinds: LeapAutoKind, playerSteps: Set<Int> = emptySet()) = LeapCardShape(
        clock = false,
        leaped = false,
        chain = false,
        groupedPreset = false,
        centerTarget = LeapCenterTarget.CLASS,
        stepKinds = kinds.toList(),
        markPlayerSteps = playerSteps,
    )

    /** Action rows only: from the first step row to the end of the card. */
    private fun actionRows(shape: LeapCardShape) = LeapTriggerLayout.rows(shape).dropWhile { it.row != LeapCardRow.STEP }

    @Test
    fun classMarkShowsClassRowAndNoPlayerRow() {
        val rows = actionRows(shape(LeapAutoKind.MARK_PLAYER)).map { it.row }
        assertTrue(LeapCardRow.MARK_TARGET_CLASS in rows)
        assertFalse(LeapCardRow.MARK_TARGET_PLAYER in rows)
    }

    @Test
    fun playerMarkShowsPlayerRowAndNoClassRow() {
        val rows = actionRows(shape(LeapAutoKind.MARK_PLAYER, playerSteps = setOf(0))).map { it.row }
        assertTrue(LeapCardRow.MARK_TARGET_PLAYER in rows)
        assertFalse(LeapCardRow.MARK_TARGET_CLASS in rows)
    }

    @Test
    fun markOptionsFollowTheStepInOrder() {
        val rows = actionRows(shape(LeapAutoKind.MARK_PLAYER)).map { it.row }
        assertEquals(
            listOf(
                LeapCardRow.STEP,
                LeapCardRow.MARK_BY,
                LeapCardRow.MARK_TARGET_CLASS,
                LeapCardRow.MARK_STYLE,
                LeapCardRow.MARK_COLOR,
                LeapCardRow.MARK_FOR,
                LeapCardRow.MARK_SHOW_NAME,
                LeapCardRow.MARK_OPACITY,
                LeapCardRow.MARK_BRIGHTNESS,
                LeapCardRow.MARK_SIZE,
                LeapCardRow.MARK_HEIGHT,
                LeapCardRow.MARK_PULSE,
                LeapCardRow.MARK_PLAYER_GLOW,
                LeapCardRow.MARK_MARKER_GLOW,
                LeapCardRow.MARK_THROUGH,
                LeapCardRow.ADD_STEP,
            ),
            rows,
        )
    }

    @Test
    fun markOptionsAreIndentedAndCarryTheirStepIndex() {
        val slots = actionRows(shape(LeapAutoKind.SORT_INVENTORY, LeapAutoKind.MARK_PLAYER))
            .filter { it.step == 1 && it.row != LeapCardRow.STEP }
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it.indented })
        assertEquals(LeapCardRow.MARK_BY, slots.first().row)
    }

    @Test
    fun markRowsAreNotShownForOtherKinds() {
        val rows = actionRows(shape(LeapAutoKind.SWITCH_LOADOUT)).map { it.row }
        assertFalse(rows.any { it.name.startsWith("MARK_") })
    }

    @Test
    fun shapeRowsShowOnlyForArrowSteps() {
        val arrow = LeapTriggerLayout.rows(shape(LeapAutoKind.MARK_PLAYER).copy(markArrowSteps = setOf(0))).map { it.row }
        assertTrue(LeapCardRow.MARK_SHAPE in arrow)
        assertTrue(LeapCardRow.MARK_SPIN in arrow)
        assertTrue(LeapCardRow.MARK_BOB in arrow)
        val beam = LeapTriggerLayout.rows(shape(LeapAutoKind.MARK_PLAYER)).map { it.row }
        assertFalse(LeapCardRow.MARK_SHAPE in beam)
        assertTrue(LeapCardRow.MARK_PLAYER_GLOW in beam)
        assertTrue(LeapCardRow.MARK_MARKER_GLOW in beam)
    }
}
