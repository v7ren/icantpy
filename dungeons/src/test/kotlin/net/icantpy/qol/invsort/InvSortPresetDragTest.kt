package net.icantpy.qol.invsort

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvSortPresetDragTest {
    private val top = 100
    private val rowHeight = 20

    private fun dropAt(mouseY: Int, from: Int, count: Int = 4) =
        InvSortPresetDrag.dropIndex(mouseY, top, rowHeight, count, from)

    @Test fun smallVerticalMovementIsNotADrag() {
        assertFalse(InvSortPresetDrag.exceedsThreshold(pressY = 100, mouseY = 103))
        assertTrue(InvSortPresetDrag.exceedsThreshold(pressY = 100, mouseY = 104))
        assertTrue(InvSortPresetDrag.exceedsThreshold(pressY = 100, mouseY = 96))
    }

    @Test fun releasingOnTopHalfOfFirstRowDropsAtZero() {
        assertEquals(0, dropAt(mouseY = top, from = 2))
    }

    @Test fun releasingOnBottomHalfOfRowMovesDownward() {
        // Bottom half of row 1 is the boundary below it, so the preset lands at index 1.
        assertEquals(1, dropAt(mouseY = top + 30, from = 0))
    }

    @Test fun releasingBelowLastRowDropsAtLastIndex() {
        assertEquals(3, dropAt(mouseY = top + 79, from = 0))
    }

    @Test fun releasingAboveListClampsToFirst() {
        assertEquals(0, dropAt(mouseY = top - 50, from = 3))
    }

    @Test fun releasingFarBelowListClampsToLast() {
        assertEquals(3, dropAt(mouseY = top + 500, from = 0))
    }

    @Test fun emptyListDropsAtZero() {
        assertEquals(0, dropAt(mouseY = top + 40, from = 0, count = 0))
    }

    @Test fun insertionLineFollowsFinalIndex() {
        assertEquals(3, InvSortPresetDrag.insertionGap(from = 0, drop = 2))
        assertEquals(1, InvSortPresetDrag.insertionGap(from = 3, drop = 1))
        assertEquals(1, InvSortPresetDrag.insertionGap(from = 1, drop = 1))
        assertEquals(0, InvSortPresetDrag.insertionGap(from = 2, drop = 0))
    }
}
