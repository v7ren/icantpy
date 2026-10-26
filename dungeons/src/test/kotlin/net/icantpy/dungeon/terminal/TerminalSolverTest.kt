package net.icantpy.dungeon.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalSolverTest {
    private fun item(slot: Int, id: String, count: Int = 1, name: String = id, glint: Boolean = false) =
        TerminalItem(slot, id, count, name, glint)

    private fun snapshot(title: String, vararg items: TerminalItem, rows: Int = 6) =
        TerminalSnapshot(title, rows, items.toList())

    @Test
    fun titlesMapToTypes() {
        assertEquals(TerminalType.PANES, TerminalType.fromTitle("Correct all the panes!"))
        assertEquals(TerminalType.IN_ORDER, TerminalType.fromTitle("§aClick in order!"))
        assertEquals(TerminalType.STARTS_WITH, TerminalType.fromTitle("What starts with: 'B'?"))
        assertEquals(TerminalType.SELECT_COLOR, TerminalType.fromTitle("Select all the LIGHT BLUE items!"))
        assertEquals(TerminalType.RUBIX, TerminalType.fromTitle("Change all to same color!"))
        assertEquals(TerminalType.MELODY, TerminalType.fromTitle("Click the button on time!"))
        assertNull(TerminalType.fromTitle("Spirit Leap"))
        assertEquals("B", TerminalType.startsWithLetter("What starts with: \"B\"?"))
    }

    private fun melody(pointerColumn: Int) = snapshot(
        "Click the button on time!",
        item(3, "magenta_stained_glass_pane"),
        *(1..5).map { column -> item(9 + column, if (column == pointerColumn) "lime_stained_glass_pane" else "white_stained_glass_pane") }
            .toTypedArray(),
        item(16, "lime_terracotta"),
        *(1..5).map { column -> item(18 + column, "white_stained_glass_pane") }.toTypedArray(),
        item(25, "red_terracotta"),
    )

    @Test
    fun melodyButtonIsLiveOnlyWhileThePaneIsUnderTheColumn() {
        val early = TerminalSolver.solve(TerminalType.MELODY, melody(pointerColumn = 1))
        assertEquals(TerminalCellState.WRONG, early.cellAt(16)?.state)
        assertEquals(MelodyRole.POINTER, early.cellAt(10)?.melodyRole)
        assertEquals(2, early.remaining)
        assertFalse(TerminalClickPolicy.allows(early.cellAt(16)!!, TerminalType.MELODY, TerminalSettings()))

        val onTime = TerminalSolver.solve(TerminalType.MELODY, melody(pointerColumn = 3))
        assertEquals(3, onTime.melodyColumn)
        assertEquals(TerminalCellState.TARGET, onTime.cellAt(16)?.state)
        val pointer = onTime.cellAt(12)!!
        assertTrue(TerminalClickPolicy.allows(pointer, TerminalType.MELODY, TerminalSettings()))
        assertEquals(16, TerminalClickPolicy.slotFor(pointer, onTime), "a click on the pane presses the button")
        assertEquals(MelodyRole.TRACK, onTime.cellAt(20)?.melodyRole)
    }

    @Test
    fun panelWidensToFitItsHeaderAndCentresTheGrid() {
        val slots = listOf(12, 13, 14)
        val settings = TerminalSettings()
        val fitted = TerminalLayout.compute(800, 600, slots, settings)
        val wide = TerminalLayout.compute(800, 600, slots, settings, headerTextWidth = 200)
        val headerPixels = (200 * settings.scale * settings.textScale).toInt()
        assertTrue(wide.panelW >= headerPixels + 2 * wide.padding, "header fits inside the panel")
        assertTrue(wide.panelW > fitted.panelW)
        val leftSpace = wide.cellX(12) - wide.panelX
        val rightSpace = wide.panelX + wide.panelW - (wide.cellX(14) + wide.cell)
        assertTrue(kotlin.math.abs(leftSpace - rightSpace) <= 1, "grid is centred: $leftSpace vs $rightSpace")
        val hidden = TerminalLayout.compute(800, 600, slots, settings.copy(showTitle = false, showRemaining = false), headerTextWidth = 200)
        assertEquals(fitted.panelW, hidden.panelW, "no header, no widening")
    }

    @Test
    fun fontIsSavedAndSharedWithTheLook() {
        val withFont = TerminalSettings(font = TerminalFont.VT323)
        assertEquals(TerminalFont.VT323, TerminalSettingsCodec.decode(TerminalSettingsCodec.encode(withFont))?.font)
        val imported = TerminalStyleShare.import(TerminalStyleShare.export(withFont), TerminalSettings())
        assertEquals(TerminalFont.VT323, imported?.font)
        assertEquals(TerminalFont.MINECRAFT, TerminalStyleShare.resetLook(withFont).font)
    }

    @Test
    fun fontListRunsThroughBuiltInsThenTheFolder() {
        val files = listOf("Comic.ttf", "Inter.otf")
        var settings = TerminalSettings()
        val seen = mutableListOf(TerminalFontChoice.label(settings))
        repeat(TerminalFont.entries.size - 1 + files.size) {
            settings = TerminalFontChoice.next(settings, files)
            seen += TerminalFontChoice.label(settings)
        }
        assertEquals(listOf("Minecraft", "Unicode", "Poppins", "Space Mono", "Retro terminal", "Arcade", "Comic", "Inter", "Minecraft"), seen)
        val missing = TerminalSettings(font = TerminalFont.CUSTOM, customFont = "Deleted.ttf")
        assertEquals(TerminalFont.MINECRAFT, TerminalFontChoice.next(missing, files).font, "a missing file starts the list over")
    }

    @Test
    fun ownFontIsSavedButPathsAreDropped() {
        val mine = TerminalSettings(font = TerminalFont.CUSTOM, customFont = "Inter.otf", customFontSize = 12.5f, customFontShift = -1f)
        val decoded = TerminalSettingsCodec.decode(TerminalSettingsCodec.encode(mine))
        assertEquals(mine, decoded)
        val sneaky = TerminalSettingsCodec.decode("{\"font\":\"CUSTOM\",\"customFont\":\"../../options.txt\"}")
        assertEquals("", sneaky?.customFont)
        val shared = TerminalStyleShare.import(TerminalStyleShare.export(mine), TerminalSettings())
        assertEquals("Inter.otf", shared?.customFont)
    }

    @Test
    fun panesTargetRedAndPendingCountsAsDone() {
        val solution = TerminalSolver.solve(
            TerminalType.PANES,
            snapshot(
                "Correct all the panes!",
                item(11, "red_stained_glass_pane"),
                item(12, "lime_stained_glass_pane"),
                item(13, "red_stained_glass_pane"),
                item(0, "black_stained_glass_pane"),
            ),
            pending = mapOf(13 to 1),
        )
        assertEquals(listOf(11, 12, 13), solution.cells.map { it.slot })
        assertEquals(1, solution.remaining)
        assertEquals(TerminalCellState.DONE, solution.cellAt(13)?.state)
    }

    @Test
    fun inOrderRanksRemainingPanesByNumber() {
        val solution = TerminalSolver.solve(
            TerminalType.IN_ORDER,
            snapshot(
                "Click in order!",
                item(10, "lime_stained_glass_pane", count = 1),
                item(11, "red_stained_glass_pane", count = 4),
                item(12, "red_stained_glass_pane", count = 2),
                item(13, "red_stained_glass_pane", count = 3),
                rows = 4,
            ),
            pending = mapOf(12 to 1),
        )
        assertEquals(0, solution.cellAt(13)?.order)
        assertEquals(1, solution.cellAt(11)?.order)
        assertEquals(TerminalCellState.DONE, solution.cellAt(12)?.state)
        assertEquals(4, solution.cellAt(11)?.number)
    }

    @Test
    fun selfGlintingItemsStayTargetsUntilTheServerAnswers() {
        val apple = TerminalItem(10, "enchanted_golden_apple", 1, "Apple", glint = true, naturalGlint = true)
        val board = snapshot("What starts with: 'A'?", apple)
        assertEquals(TerminalCellState.TARGET, TerminalSolver.solve(TerminalType.STARTS_WITH, board).cellAt(10)?.state)
        assertEquals(
            TerminalCellState.DONE,
            TerminalSolver.solve(TerminalType.STARTS_WITH, board, confirmed = setOf(10)).cellAt(10)?.state,
        )
        assertEquals(
            TerminalCellState.DONE,
            TerminalSolver.solve(TerminalType.STARTS_WITH, board, pending = mapOf(10 to 1)).cellAt(10)?.state,
        )
    }

    @Test
    fun colourCycleOnlyTakesTheButtonThatMatchesTheDirection() {
        val settings = TerminalSettings()
        val forward = TerminalCell(item(12, "red_stained_glass_pane"), TerminalCellState.TARGET, leftClicks = 1, rightClicks = 4)
        val backward = TerminalCell(item(13, "red_stained_glass_pane"), TerminalCellState.TARGET, leftClicks = 4, rightClicks = 1)
        val left = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
        val right = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT
        assertTrue(TerminalClickPolicy.allowsButton(forward, TerminalType.RUBIX, settings, left))
        assertFalse(TerminalClickPolicy.allowsButton(forward, TerminalType.RUBIX, settings, right))
        assertTrue(TerminalClickPolicy.allowsButton(backward, TerminalType.RUBIX, settings, right))
        assertFalse(TerminalClickPolicy.allowsButton(backward, TerminalType.RUBIX, settings, left))
        assertTrue(TerminalClickPolicy.allowsButton(backward, TerminalType.RUBIX, settings.copy(blockWrongClicks = false), left))
        assertTrue(TerminalClickPolicy.allowsButton(backward, TerminalType.PANES, settings, left))
    }

    @Test
    fun startsWithSkipsBorderAndClickedItems() {
        val solution = TerminalSolver.solve(
            TerminalType.STARTS_WITH,
            snapshot(
                "What starts with: 'B'?",
                item(10, "bread", name = "Bread"),
                item(11, "bone", name = "Bone", glint = true),
                item(12, "apple", name = "Apple"),
                item(13, "black_stained_glass_pane", name = "Blank"),
                item(1, "book", name = "Book"),
            ),
        )
        assertEquals(TerminalCellState.TARGET, solution.cellAt(10)?.state)
        assertEquals(TerminalCellState.DONE, solution.cellAt(11)?.state)
        assertEquals(TerminalCellState.WRONG, solution.cellAt(12)?.state)
        assertNull(solution.cellAt(13), "panes are border filler")
        assertNull(solution.cellAt(1), "the top row is outside the item area")
    }

    @Test
    fun selectColorMatchesModernIdsAndOldDyeNames() {
        val solution = TerminalSolver.solve(
            TerminalType.SELECT_COLOR,
            snapshot(
                "Select all the BLUE items!",
                item(10, "blue_wool"),
                item(11, "lapis_lazuli"),
                item(12, "light_blue_wool"),
                item(13, "red_wool"),
            ),
        )
        assertEquals(listOf(10, 11), solution.cells.filter { it.state == TerminalCellState.TARGET }.map { it.slot })
        assertEquals("light_gray", TerminalSolver.colorPrefix("SILVER"))
        assertTrue(TerminalSolver.matchesColor("light_gray_wool", "light_gray"))
        assertFalse(TerminalSolver.matchesColor("light_gray_wool", "gray"))
    }

    @Test
    fun rubixPicksCheapestTargetAndShortestDirection() {
        val solution = TerminalSolver.solve(
            TerminalType.RUBIX,
            snapshot(
                "Change all to same color!",
                item(12, "red_stained_glass_pane"),
                item(13, "red_stained_glass_pane"),
                item(14, "blue_stained_glass_pane"),
                item(21, "orange_stained_glass_pane"),
            ),
        )
        assertEquals(RubixColor.RED, solution.rubixTarget)
        val blue = solution.cellAt(14)!!
        assertEquals(1, blue.leftClicks, "blue wraps forward to red")
        assertEquals(4, blue.rightClicks)
        val orange = solution.cellAt(21)!!
        assertEquals(4, orange.leftClicks)
        assertEquals(1, orange.rightClicks)
        assertEquals(2, solution.remaining)
    }

    @Test
    fun rubixPendingStepsMoveThePredictedColour() {
        val solution = TerminalSolver.solve(
            TerminalType.RUBIX,
            snapshot("Change all to same color!", item(12, "orange_stained_glass_pane")),
            pending = mapOf(12 to -1),
            lockedRubixTarget = RubixColor.RED,
        )
        assertEquals(TerminalCellState.DONE, solution.cellAt(12)?.state)
    }

    @Test
    fun pendingRubixKeepsUnappliedSteps() {
        val pending = TerminalPendingClicks(timeoutMs = 1_000)
        val red = item(12, "red_stained_glass_pane")
        pending.record(red, 1, nowMs = 0)
        pending.record(red, 1, nowMs = 10)
        assertEquals(mapOf(12 to 2), pending.asMap())
        pending.prune(snapshot("t", item(12, "orange_stained_glass_pane")), nowMs = 50)
        assertEquals(mapOf(12 to 1), pending.asMap(), "one of two clicks applied")
        pending.prune(snapshot("t", item(12, "yellow_stained_glass_pane")), nowMs = 60)
        assertTrue(pending.asMap().isEmpty())
    }

    @Test
    fun pendingExpiresWhenTheServerIgnoresTheClick() {
        val pending = TerminalPendingClicks(timeoutMs = 100)
        val pane = item(11, "red_stained_glass_pane")
        pending.record(pane, 1, nowMs = 0)
        pending.prune(snapshot("t", pane), nowMs = 50)
        assertEquals(setOf(11), pending.asMap().keys)
        pending.prune(snapshot("t", pane), nowMs = 150)
        assertTrue(pending.asMap().isEmpty())
    }

    @Test
    fun blockWrongClicksOnlyAllowsTheNextPaneInOrder() {
        val settings = TerminalSettings()
        val next = TerminalCell(item(10, "red_stained_glass_pane"), TerminalCellState.TARGET, order = 0)
        val later = TerminalCell(item(11, "red_stained_glass_pane"), TerminalCellState.TARGET, order = 1)
        assertTrue(TerminalClickPolicy.allows(next, TerminalType.IN_ORDER, settings))
        assertFalse(TerminalClickPolicy.allows(later, TerminalType.IN_ORDER, settings))
        assertTrue(TerminalClickPolicy.allows(later, TerminalType.IN_ORDER, settings.copy(blockWrongClicks = false)))
    }

    @Test
    fun themesChangeOnlyTheLook() {
        val mine = TerminalSettings(scale = 2f, offsetX = 40, clickButton = TerminalClickButton.LEFT, rubix = false, cellRadius = 7)
        val neon = TerminalTheme.NEON.applyTo(mine)
        assertEquals(2f, neon.scale)
        assertEquals(40, neon.offsetX)
        assertEquals(TerminalClickButton.LEFT, neon.clickButton)
        assertFalse(neon.rubix)
        assertEquals(TerminalCellStyle.FILLED_OUTLINE, neon.cellStyle)
        assertEquals(TerminalSettings().cellRadius, TerminalTheme.DEFAULT.applyTo(mine).cellRadius)
    }

    @Test
    fun styleStringsCarryOnlyTheLook() {
        val designer = TerminalTheme.DRACULA.applyTo(TerminalSettings()).copy(pulseNext = true, headerPosition = TerminalHeaderPosition.BOTTOM)
        val friend = TerminalSettings(scale = 2.5f, offsetX = -30, clickButton = TerminalClickButton.LEFT, firstClickDelayMs = 200)
        val imported = TerminalStyleShare.import(TerminalStyleShare.export(designer), friend)!!
        assertEquals(designer.panelRgb, imported.panelRgb)
        assertTrue(imported.pulseNext)
        assertEquals(TerminalHeaderPosition.BOTTOM, imported.headerPosition)
        assertEquals(2.5f, imported.scale)
        assertEquals(-30, imported.offsetX)
        assertEquals(TerminalClickButton.LEFT, imported.clickButton)
        assertEquals(200, imported.firstClickDelayMs)
        assertNull(TerminalStyleShare.import("hello", friend))
        assertNull(TerminalStyleShare.import("icantpy-term:@@@", friend))
    }

    @Test
    fun everyThemeIsDistinctAndResetsTheLookFirst() {
        val styled = TerminalSettings(screenDim = 0.9f, padding = 1, titleAlign = TerminalTitleAlign.CENTER)
        val looks = TerminalTheme.entries.map { theme -> TerminalStyleShare.export(theme.applyTo(styled)) }
        assertEquals(looks.size, looks.toSet().size, "two presets look the same")
        val defaulted = TerminalTheme.DEFAULT.applyTo(styled)
        assertEquals(0f, defaulted.screenDim)
        assertEquals(TerminalSettings.DEFAULT_PADDING, defaulted.padding)
        assertEquals(TerminalTitleAlign.SPLIT, defaulted.titleAlign)
    }

    @Test
    fun settingsRoundTripAndRejectWrongTypes() {
        val custom = TerminalSettings(
            scale = 2.25f, gap = 4, clickButton = TerminalClickButton.LEFT, targetRgb = 0x123456, rubix = false,
            cellStyle = TerminalCellStyle.OUTLINE, cellRadius = 3, panelBorder = true, textScale = 1.25f, textShadow = false,
            hoverRgb = 0xABCDEF, pulseNext = true, fadeDone = false, headerPosition = TerminalHeaderPosition.BOTTOM,
            titleAlign = TerminalTitleAlign.CENTER, padding = 10, screenDim = 0.4f, firstClickDelayMs = 150,
        )
        assertEquals(custom, TerminalSettingsCodec.decode(TerminalSettingsCodec.encode(custom)))
        assertEquals(TerminalSettings(), TerminalSettingsCodec.decode("{}"))
        assertNull(TerminalSettingsCodec.decode("{\"scale\": \"big\"}"))
        assertEquals(TerminalSettings.MAX_SCALE, TerminalSettingsCodec.decode("{\"scale\": 99}")?.scale)
    }
}
