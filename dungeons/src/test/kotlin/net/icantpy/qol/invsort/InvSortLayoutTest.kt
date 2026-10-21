package net.icantpy.qol.invsort

import net.icantpy.qol.invsort.InvSortLayout.ColumnTarget
import net.icantpy.qol.invsort.InvSortLayout.GuiRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvSortLayoutTest {
    @Test fun presetsStackWithFixedGapAndSaveButtonBelowThem() {
        val column = InvSortLayout.stackColumn(originX = 10, originY = 4, labelWidths = listOf(30, 40, 20), screenHeight = 200)
        assertEquals(listOf(4, 22, 40), column.presets.map { it.y })
        assertTrue(column.presets.all { it.x == 10 && it.height == 16 })
        assertEquals(GuiRect(10, 58, 16, 16), column.save)
        assertEquals(0, column.hiddenPresetCount)
    }

    @Test fun columnWidthIsWidestLabelPlusPaddingClampedToRange() {
        assertEquals(60, InvSortLayout.buttonWidth(listOf(5)))
        assertEquals(60, InvSortLayout.buttonWidth(emptyList()))
        assertEquals(70, InvSortLayout.buttonWidth(listOf(30, 60)))
        assertEquals(110, InvSortLayout.buttonWidth(listOf(200)))
        val column = InvSortLayout.stackColumn(0, 0, listOf(30, 60), screenHeight = 200)
        assertTrue(column.presets.all { it.width == 70 })
    }

    @Test fun longLabelsAreTruncatedWithEllipsisToTheLabelBudget() {
        // Six pixels per character: the 100 px label budget holds 16 characters including the ellipsis.
        val measure: (String) -> Int = { it.length * 6 }
        assertEquals("Short", InvSortLayout.fitLabel("Short", measure))
        assertEquals("A".repeat(15) + "…", InvSortLayout.fitLabel("A".repeat(30), measure))
    }

    @Test fun presetCountIsCappedByScreenHeightAndSaveButtonStaysOnScreen() {
        val labels = List(10) { 40 }
        // originY 4 + 16 for the save button leaves 54 px: room for three 18 px rows.
        val column = InvSortLayout.stackColumn(0, 4, labels, screenHeight = 74)
        assertEquals(3, column.presets.size)
        assertEquals(7, column.hiddenPresetCount)
        assertTrue(column.save.y + column.save.height <= 74)

        val shorter = InvSortLayout.stackColumn(0, 4, labels, screenHeight = 73)
        assertEquals(2, shorter.presets.size)
    }

    @Test fun saveButtonShowsAloneWhenNoPresetsOrNoRoomForPresets() {
        val empty = InvSortLayout.stackColumn(10, 4, emptyList(), screenHeight = 200)
        assertTrue(empty.presets.isEmpty())
        assertEquals(GuiRect(10, 4, 16, 16), empty.save)

        val noRoom = InvSortLayout.stackColumn(10, 4, listOf(40, 40), screenHeight = 10)
        assertTrue(noRoom.presets.isEmpty())
        assertEquals(2, noRoom.hiddenPresetCount)
        assertEquals(GuiRect(10, 4, 16, 16), noRoom.save)
    }

    @Test fun hitTestingFindsPresetsSaveButtonAndMissesGaps() {
        val column = InvSortLayout.stackColumn(10, 4, listOf(30, 30), screenHeight = 200)
        assertEquals(ColumnTarget.Preset(0), column.targetAt(12, 10))
        assertEquals(ColumnTarget.Preset(1), column.targetAt(69, 30))
        assertEquals(ColumnTarget.Save, column.targetAt(18, 40))
        assertNull(column.targetAt(12, 20), "gap between the first two presets")
        assertNull(column.targetAt(9, 10), "left of the column")
        assertNull(column.targetAt(70, 10), "right edge is exclusive")
    }
}
