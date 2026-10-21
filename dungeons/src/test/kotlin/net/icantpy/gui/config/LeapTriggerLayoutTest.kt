package net.icantpy.gui.config

import net.icantpy.dungeon.leap.LeapAutoKind
import net.icantpy.dungeon.leap.LeapAutoStep
import net.icantpy.dungeon.leap.LeapCenterTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapTriggerLayoutTest {
    private val cardX = 40
    private val cardY = 100
    private val cardW = 420

    /** [steps] queued actions after the optional leap-target row, which comes first when [armLeap] is set. */
    private fun shape(
        clock: Boolean = false,
        leaped: Boolean = false,
        chain: Boolean = false,
        armLeap: Boolean = true,
        grouped: Boolean = false,
        target: LeapCenterTarget = LeapCenterTarget.CLASS,
        steps: Int = 0,
        named: Boolean = false,
    ): LeapCardShape {
        val leap = if (armLeap) listOf(LeapAutoKind.LEAP_TARGET) else emptyList()
        // Loadout rows have no nested options; sort rows get their own tests in LeapSortOptionRowsTest.
        val queued = List(steps) { LeapAutoKind.SWITCH_LOADOUT }
        return LeapCardShape(
            clock = clock,
            leaped = leaped,
            chain = chain,
            groupedPreset = grouped,
            centerTarget = target,
            stepKinds = leap + queued,
            namedMatch = named,
        )
    }

    private fun rowsOf(candidate: LeapCardShape): List<LeapCardRow> = LeapTriggerLayout.rows(candidate).map { it.row }

    private fun expandedRects(rowCount: Int): List<LayoutRect> = (0 until rowCount).map { index ->
        LeapTriggerLayout.rowRect(cardX, cardY, cardW, index)
    }

    @Test
    fun rowRectsDoNotOverlapAndStayInsideCard() {
        val shapes = listOf(
            shape(),
            shape(clock = true),
            shape(leaped = true, chain = true),
            shape(armLeap = false, steps = 3),
            shape(target = LeapCenterTarget.NAME, steps = 6),
            shape(grouped = true, target = LeapCenterTarget.SENDER, steps = 1),
        )
        for (candidate in shapes) {
            val rects = expandedRects(LeapTriggerLayout.rows(candidate).size)
            for (i in rects.indices) {
                val row = rects[i]
                assertTrue(row.x >= cardX && row.right <= cardX + cardW, "row $i leaves card width for $candidate")
                for (j in i + 1 until rects.size) {
                    assertFalse(row.overlaps(rects[j]), "rows $i and $j overlap for $candidate")
                }
            }
        }
    }

    @Test
    fun labelAndControlFitWithinRowAndCardWidth() {
        val row = LeapTriggerLayout.rowRect(cardX, cardY, cardW, 0)
        val label = LeapTriggerLayout.labelRect(row)
        val control = LeapTriggerLayout.controlRect(row)
        assertEquals(LeapTriggerLayout.LABEL_WIDTH, label.w)
        assertTrue(label.right <= control.x, "label must not touch control")
        assertTrue(control.right <= cardX + cardW - LeapTriggerLayout.SIDE_PAD, "control leaves card padding")
        assertEquals(row.right, control.right)
    }

    @Test
    fun cardHeightEqualsSumOfVisibleRows() {
        val rows = LeapTriggerLayout.rows(shape(clock = true, leaped = true, chain = true, steps = 2))
        val rects = expandedRects(rows.size)
        val lastRowBottom = rects.last().bottom
        val height = LeapTriggerLayout.cardHeight(expanded = true, rowCount = rows.size)
        assertEquals(lastRowBottom - cardY + LeapTriggerLayout.BOTTOM_PAD, height)
    }

    @Test
    fun collapsedCardHasFixedHeight() {
        assertEquals(LeapTriggerLayout.COLLAPSED_HEIGHT, LeapTriggerLayout.cardHeight(expanded = false, rowCount = 12))
    }

    @Test
    fun playerRowShowsOnlyForNamedEvents() {
        assertTrue(LeapCardRow.PLAYER in rowsOf(shape(named = true)))
        assertFalse(LeapCardRow.PLAYER in rowsOf(shape(named = false)))
        assertFalse(LeapCardRow.PLAYER in rowsOf(shape(clock = true)))
    }

    @Test
    fun leapSettingsAppearOnlyUnderALeapTargetRow() {
        val on = LeapTriggerLayout.rows(shape(armLeap = true, steps = 1))
        val leapRow = on.indexOfFirst { it.row == LeapCardRow.STEP && it.step == 0 }
        val nested = on.withIndex().filter { it.value.indented }
        assertEquals(
            listOf(
                LeapCardRow.HOLD_MODE,
                LeapCardRow.HOLD_DURATION,
                LeapCardRow.HOLD_LOCK,
                LeapCardRow.CENTER_ON,
                LeapCardRow.CLASS,
                LeapCardRow.REQUIRES,
                LeapCardRow.ZONE,
                LeapCardRow.REASON,
            ),
            nested.map { it.value.row },
        )
        assertTrue(nested.all { it.index > leapRow }, "leap settings sit under their action row")
        assertFalse(on.filter { !it.indented }.any { it.row == LeapCardRow.CENTER_ON }, "leap settings are never top-level")
    }

    @Test
    fun noLeapTargetRowMeansNoLeapSettings() {
        val off = LeapTriggerLayout.rows(shape(armLeap = false, steps = 2))
        assertTrue(off.none { it.indented }, "no nested rows without a leap target")
        val offRows = off.map { it.row }
        assertFalse(LeapCardRow.CENTER_ON in offRows)
        assertFalse(LeapCardRow.CLASS in offRows)
        assertFalse(LeapCardRow.REQUIRES in offRows)
        assertFalse(LeapCardRow.ZONE in offRows)
        assertFalse(LeapCardRow.REASON in offRows)
        assertFalse(LeapCardRow.TARGET_NAME in offRows)
    }

    @Test
    fun onlyTheLeapTargetRowGetsNestedSettingsWhenMixedWithOtherActions() {
        val rows = LeapTriggerLayout.rows(shape(armLeap = true, steps = 2))
        val stepIndexOfLeap = rows.indexOfFirst { it.row == LeapCardRow.STEP && it.step == 0 }
        val afterLeap = rows.drop(stepIndexOfLeap + 1)
        val nestedEnd = afterLeap.indexOfFirst { !it.indented }
        assertTrue(nestedEnd > 0, "the leap settings run as one block")
        assertEquals(LeapCardRow.STEP, afterLeap[nestedEnd].row)
        assertEquals(1, afterLeap[nestedEnd].step)
    }

    @Test
    fun emptyActionListShowsOnlyTheAddRow() {
        val rows = LeapTriggerLayout.rows(shape(armLeap = false, steps = 0))
        assertEquals(LeapCardSlot(LeapCardRow.ADD_STEP, 0), rows.last())
        assertFalse(rows.any { it.row == LeapCardRow.STEP }, "no action rows on an empty card")
        assertFalse(rows.any { it.indented })
    }

    @Test
    fun targetNameRowFollowsNameTargetWhileArmed() {
        assertTrue(LeapCardRow.TARGET_NAME in rowsOf(shape(target = LeapCenterTarget.NAME)))
        assertFalse(LeapCardRow.TARGET_NAME in rowsOf(shape(target = LeapCenterTarget.NAME, armLeap = false)))
    }

    @Test
    fun oneActionRowPerStepThenAddRowWhileRoomRemains() {
        for (count in 0..LeapAutoStep.MAX_STEPS) {
            val rows = LeapTriggerLayout.rows(shape(armLeap = false, steps = count))
            val stepSlots = rows.filter { it.row == LeapCardRow.STEP }
            assertEquals(count, stepSlots.size, "step rows for $count steps")
            assertEquals((0 until count).toList(), stepSlots.map { it.step })
            val addSlots = rows.filter { it.row == LeapCardRow.ADD_STEP }
            if (count < LeapAutoStep.MAX_STEPS) {
                assertEquals(1, addSlots.size, "add row for $count steps")
                assertEquals(count, addSlots.single().step)
            } else {
                assertTrue(addSlots.isEmpty(), "add row hidden at the cap")
            }
        }
    }

    @Test
    fun actionRowsComeLastInRunOrder() {
        val rows = LeapTriggerLayout.rows(shape(armLeap = false, steps = 2))
        val tail = rows.takeLast(3).map { it.row to it.step }
        assertEquals(listOf(LeapCardRow.STEP to 0, LeapCardRow.STEP to 1, LeapCardRow.ADD_STEP to 2), tail)
    }

    @Test
    fun stepControlsStayInsideRowAndDoNotOverlap() {
        val control = LeapTriggerLayout.controlRect(LeapTriggerLayout.rowRect(cardX, cardY, cardW, 0))
        val parts = LeapTriggerLayout.stepControls(control)
        val all = listOf(parts.kind, parts.value, parts.up, parts.down, parts.remove)
        for (i in all.indices) {
            assertTrue(all[i].x >= control.x && all[i].right <= control.right, "part $i leaves the control")
            for (j in i + 1 until all.size) {
                assertFalse(all[i].overlaps(all[j]), "parts $i and $j overlap")
            }
        }
        assertTrue(parts.value.w >= 60, "value picker keeps a usable width")
        assertEquals(LeapTriggerLayout.STEP_BUTTON_SIZE, parts.remove.w)
    }

    @Test
    fun stepIconsAreOrderedUpDownRemove() {
        val control = LeapTriggerLayout.controlRect(LeapTriggerLayout.rowRect(cardX, cardY, cardW, 0))
        val parts = LeapTriggerLayout.stepControls(control)
        assertTrue(parts.up.right <= parts.down.x)
        assertTrue(parts.down.right <= parts.remove.x)
    }

    @Test
    fun chainDelayRowsAppearOnlyForLeapChains() {
        assertTrue(LeapCardRow.CHAIN_DELAY in rowsOf(shape(leaped = true, chain = true)))
        assertFalse(LeapCardRow.CHAIN_DELAY in rowsOf(shape(leaped = true, chain = true, armLeap = false)))
    }

    @Test
    fun clockRowsReplaceMatchRow() {
        val rows = rowsOf(shape(clock = true))
        assertTrue(LeapCardRow.TIMER in rows)
        assertTrue(LeapCardRow.SECONDS in rows)
        assertFalse(LeapCardRow.MATCH in rows)
    }

    @Test
    fun headerControlsDoNotOverlapAndStayInsideCard() {
        val header = LeapTriggerLayout.header(cardX, cardY, cardW, deleteWidth = 64)
        val parts = listOf(header.chevron, header.toggle, header.delete)
        val titleRect = LayoutRect(header.titleX, header.chevron.y, header.titleWidth, header.chevron.h)
        for (i in parts.indices) {
            assertTrue(parts[i].x >= cardX && parts[i].right <= cardX + cardW, "part $i leaves card")
            for (j in i + 1 until parts.size) {
                assertFalse(parts[i].overlaps(parts[j]), "header parts $i and $j overlap")
            }
            assertFalse(titleRect.overlaps(parts[i]), "title overlaps header part $i")
        }
        assertTrue(header.delete.right <= cardX + cardW)
    }

    @Test
    fun dropdownOpensBelowWhenItFits() {
        assertEquals(100 + 24 + LeapTriggerLayout.DROPDOWN_GAP, LeapTriggerLayout.dropdownTop(anchorTop = 100, anchorHeight = 24, menuHeight = 100, bottomLimit = 400))
    }

    @Test
    fun dropdownFlipsAboveWhenItWouldPassBottom() {
        val top = LeapTriggerLayout.dropdownTop(anchorTop = 350, anchorHeight = 24, menuHeight = 100, bottomLimit = 400)
        assertEquals(350 - LeapTriggerLayout.DROPDOWN_GAP - 100, top)
        assertTrue(top + 100 <= 350, "flipped menu must not cover its anchor")
    }

    @Test
    fun revealScrollPutsCardTopAtViewMargin() {
        assertEquals(500 - LeapTriggerLayout.REVEAL_MARGIN, LeapTriggerLayout.revealScroll(500))
        assertEquals(0, LeapTriggerLayout.revealScroll(4))
    }

    @Test
    fun plainTextReplacesArrowGlyph() {
        assertEquals("Maxor dies -> Sort: a", LeapTriggerLayout.plainText("Maxor dies → Sort: a"))
    }


    @Test
    fun headerWithOffAndTestButtonsDoesNotOverlapAtNarrowWidths() {
        // Card widths down to 300 px; the smallest config panel leaves at least this much for a card.
        for (width in listOf(300, 340, 420)) {
            val header = LeapTriggerLayout.header(cardX, cardY, width, deleteWidth = 64, offWidth = 60, testWidth = 60)
            val test = requireNotNull(header.test)
            val off = requireNotNull(header.off)
            val parts = listOf(header.chevron, header.toggle, off, test, header.delete)
            for (i in parts.indices) {
                assertTrue(parts[i].x >= cardX && parts[i].right <= cardX + width, "part $i leaves card at width $width")
                for (j in i + 1 until parts.size) {
                    assertFalse(parts[i].overlaps(parts[j]), "parts $i and $j overlap at width $width")
                }
            }
            val titleRect = LayoutRect(header.titleX, header.chevron.y, header.titleWidth, header.chevron.h)
            parts.forEach { part -> assertFalse(titleRect.overlaps(part), "title overlaps a control at width $width") }
        }
    }

    @Test
    fun testButtonSitsImmediatelyLeftOfDelete() {
        val header = LeapTriggerLayout.header(cardX, cardY, cardW, deleteWidth = 64, offWidth = 60, testWidth = 52)
        val test = requireNotNull(header.test)
        assertEquals(header.delete.x - LeapTriggerLayout.BUTTON_GAP, test.right)
        assertEquals(test.x - LeapTriggerLayout.BUTTON_GAP, requireNotNull(header.off).right)
    }

    @Test
    fun headerWithoutTestButtonHasNoTestRect() {
        assertEquals(null, LeapTriggerLayout.header(cardX, cardY, cardW, deleteWidth = 64).test)
    }
}
