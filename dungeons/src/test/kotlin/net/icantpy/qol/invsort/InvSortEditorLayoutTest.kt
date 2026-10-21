package net.icantpy.qol.invsort

import net.icantpy.qol.invsort.InvSortEditorLayout.EditorLayout
import net.icantpy.qol.invsort.InvSortEditorLayout.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvSortEditorLayoutTest {
    /** Natural button widths (label plus padding) for the default labels. */
    private val normalWidths = listOf(29, 62, 62, 92, 72, 96, 34, 42)

    /** Deliberately wide labels, to stress wrapping. */
    private val wideWidths = listOf(41, 80, 80, 104, 84, 120, 46, 54)

    /** Compact labels whose natural widths plus gaps fit one row of the 500 px panel (472 px inside the padding). */
    private val fittingWidths = listOf(29, 50, 50, 70, 60, 80, 30, 36)

    private fun layout(w: Int, h: Int, count: Int = 12, scroll: Int = 0, widths: List<Int> = normalWidths) =
        InvSortEditorLayout.compute(w, h, count, scroll, widths)

    private fun interactive(l: EditorLayout): List<Rect> =
        listOf(l.list, l.grid, l.armor, l.renameBox, l.hotkey, l.moveUp, l.moveDown, l.status) + l.buttons

    @Test fun modeButtonGetsItsOwnRectInsidePanel() {
        val l = layout(1000, 600)
        assertEquals(normalWidths.size, l.buttons.size)
        l.buttons.forEach { assertTrue(it.inside(l.panel)) }
        val wrapped = layout(320, 240, widths = wideWidths)
        assertEquals(wideWidths.size, wrapped.buttons.size)
        wrapped.buttons.forEach { assertTrue(it.inside(wrapped.panel)) }
    }

    @Test fun panelIsCappedAndCenteredOnSmallScreens() {
        val small = layout(320, 240)
        assertEquals(Rect(8, 8, 304, 224), small.panel)
    }

    @Test fun panelUsesDesiredSizeOnLargeScreens() {
        val large = layout(1000, 600)
        assertEquals(500, large.panel.w)
        assertEquals(250, large.panel.h)
        assertEquals((1000 - 500) / 2, large.panel.x)
        assertEquals((600 - 250) / 2, large.panel.y)
    }

    @Test fun everyRectStaysInsidePanelWithoutOverlap() {
        for (w in 320..1000 step 10) {
            for (h in 240..600 step 10) {
                val l = layout(w, h)
                val rects = interactive(l)
                rects.forEach { assertTrue(it.inside(l.panel), "rect $it outside panel ${l.panel} at ${w}x$h") }
                l.scrollbar?.let { assertTrue(it.inside(l.panel), "scrollbar outside panel at ${w}x$h") }
                for (i in rects.indices) for (j in i + 1 until rects.size) {
                    assertFalse(rects[i].overlaps(rects[j]), "rects ${rects[i]} and ${rects[j]} overlap at ${w}x$h")
                }
                assertTrue(l.visibleRows >= 1, "no list rows at ${w}x$h")
            }
        }
    }

    @Test fun wideLabelsStillFitAtSmallestSize() {
        val l = layout(320, 240, widths = wideWidths)
        interactive(l).forEach { assertTrue(it.inside(l.panel)) }
        val rects = interactive(l)
        for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse(rects[i].overlaps(rects[j]))
    }

    @Test fun buttonsWrapOnNarrowPanelsAndStayOnOneRowWhenWide() {
        val narrow = layout(320, 600)
        assertTrue(narrow.buttons.map { it.y }.distinct().size >= 2)
        val wide = layout(1000, 600, widths = fittingWidths)
        assertEquals(1, wide.buttons.map { it.y }.distinct().size)
    }

    @Test fun defaultPanelShowsEightRowsAndScrollsWhenLonger() {
        val many = layout(1000, 600, count = 12, widths = fittingWidths)
        assertEquals(8, many.visibleRows)
        assertNotNull(many.scrollbar)
        val few = layout(1000, 600, count = 5, widths = fittingWidths)
        assertNull(few.scrollbar)
    }

    @Test fun scrollClampingKeepsOffsetInRange() {
        assertEquals(0, InvSortEditorLayout.clampScroll(-3, 12, 8))
        assertEquals(4, InvSortEditorLayout.clampScroll(10, 12, 8))
        assertEquals(0, InvSortEditorLayout.clampScroll(2, 5, 8))
        assertEquals(4, layout(1000, 600, count = 12, scroll = 99, widths = fittingWidths).listScroll)
    }

    @Test fun wheelMovesOneRowAndClamps() {
        assertEquals(0, InvSortEditorLayout.wheelScroll(0, 1.0, 12, 8))
        assertEquals(3, InvSortEditorLayout.wheelScroll(4, 1.0, 12, 8))
        assertEquals(4, InvSortEditorLayout.wheelScroll(3, -1.0, 12, 8))
        assertEquals(4, InvSortEditorLayout.wheelScroll(4, -1.0, 12, 8))
    }

    @Test fun scrollIntoViewMovesOnlyAsFarAsNeeded() {
        assertEquals(2, InvSortEditorLayout.scrollIntoView(0, 9, 12, 8))
        assertEquals(1, InvSortEditorLayout.scrollIntoView(4, 1, 12, 8))
        assertEquals(0, InvSortEditorLayout.scrollIntoView(0, 3, 12, 8))
        assertEquals(4, InvSortEditorLayout.scrollIntoView(3, 11, 12, 8))
        assertEquals(0, InvSortEditorLayout.scrollIntoView(0, 0, 0, 8))
    }

    @Test fun rowAtAccountsForScrollAndGaps() {
        val top = 100
        assertEquals(3, InvSortEditorLayout.rowAt(top + 5, top, 12, 8, 3))
        assertEquals(4, InvSortEditorLayout.rowAt(top + 25, top, 12, 8, 3))
        assertNull(InvSortEditorLayout.rowAt(top + 18, top, 12, 8, 3))
        assertNull(InvSortEditorLayout.rowAt(top + 8 * 20 + 5, top, 12, 8, 3))
        assertNull(InvSortEditorLayout.rowAt(top + 5 + 2 * 20, top, 2, 8, 0))
        assertNull(InvSortEditorLayout.rowAt(top - 1, top, 12, 8, 0))
    }

    @Test fun dropIndexAccountsForScrollOffset() {
        val top = 100
        // Visible row 0 is absolute row 3; top half of it drops before row 3.
        assertEquals(3, InvSortEditorLayout.dropIndex(top + 5, top, 12, 3, from = 7))
        // Bottom half of visible row 1 (absolute row 4) lands at 4 when moving down from 0.
        assertEquals(4, InvSortEditorLayout.dropIndex(top + 30, top, 12, 3, from = 0))
        // Far below the list clamps to the last preset.
        assertEquals(11, InvSortEditorLayout.dropIndex(top + 1000, top, 12, 3, from = 0))
        // Above the list clamps to the first preset.
        assertEquals(0, InvSortEditorLayout.dropIndex(0, top, 12, 3, from = 7))
    }

    @Test fun autoScrollTriggersNearListEdges() {
        assertEquals(-1, InvSortEditorLayout.autoScrollStep(105, 100, 260))
        assertEquals(1, InvSortEditorLayout.autoScrollStep(255, 100, 260))
        assertEquals(0, InvSortEditorLayout.autoScrollStep(180, 100, 260))
    }

    @Test fun thumbFollowsScrollPosition() {
        val track = Rect(0, 0, 4, 160)
        val top = InvSortEditorLayout.thumb(track, count = 20, visibleRows = 8, scroll = 0)
        assertEquals(Rect(0, 0, 4, 64), top)
        val bottom = InvSortEditorLayout.thumb(track, count = 20, visibleRows = 8, scroll = 12)
        assertEquals(96, bottom?.y)
        assertNull(InvSortEditorLayout.thumb(track, count = 8, visibleRows = 8, scroll = 0))
    }

    @Test fun fitTextShortensWithEllipsis() {
        val measure: (String) -> Int = { it.length * 6 }
        assertEquals("abc", InvSortEditorLayout.fitText("abc", 100, measure))
        assertEquals("ab...", InvSortEditorLayout.fitText("abcdefghijklmnop", 30, measure))
    }
}
