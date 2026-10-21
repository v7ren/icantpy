package net.icantpy.qol.invsort

import net.icantpy.qol.invsort.InvSortLayout.ColumnTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvSortHiddenPresetLayoutTest {
    @Test fun shownPresetsKeepTheirRealIndices() {
        // Presets 0 and 2 are hidden; the column shows presets 1 and 3.
        val column = InvSortLayout.stackColumn(0, 0, listOf(30, 30), screenHeight = 200, presetIndices = listOf(1, 3))
        assertEquals(listOf(1, 3), column.presetIndices)
        assertEquals(ColumnTarget.Preset(1), column.targetAt(2, 2))
        assertEquals(ColumnTarget.Preset(3), column.targetAt(2, 20))
    }

    @Test fun allHiddenLeavesOnlySaveButton() {
        val column = InvSortLayout.stackColumn(10, 4, emptyList(), screenHeight = 200, presetIndices = emptyList())
        assertTrue(column.presets.isEmpty())
        assertTrue(column.presetIndices.isEmpty())
        assertEquals(0, column.hiddenPresetCount)
        assertEquals(ColumnTarget.Save, column.targetAt(12, 6))
        assertEquals(null, column.targetAt(0, 0))
    }

    @Test fun hiddenPresetsDoNotCountTowardsOverflow() {
        // Only two shown presets, but room for one: overflow is counted over the shown ones alone.
        val column = InvSortLayout.stackColumn(0, 4, listOf(40, 40), screenHeight = 40, presetIndices = listOf(4, 7))
        assertEquals(listOf(4), column.presetIndices)
        assertEquals(1, column.hiddenPresetCount)
    }

    @Test fun mismatchedIndexAndWidthCountsAreRejected() {
        val failure = runCatching { InvSortLayout.stackColumn(0, 0, listOf(30), 200, presetIndices = listOf(0, 1)) }
        assertTrue(failure.isFailure)
    }
}
