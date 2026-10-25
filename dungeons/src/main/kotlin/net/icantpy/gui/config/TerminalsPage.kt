package net.icantpy.gui.config

import net.icantpy.dungeon.terminal.RubixDisplay
import net.icantpy.dungeon.terminal.TerminalFontChoice
import net.icantpy.dungeon.terminal.TerminalUserFonts
import net.icantpy.dungeon.terminal.TerminalCellStyle
import net.icantpy.dungeon.terminal.TerminalClickButton
import net.icantpy.dungeon.terminal.TerminalOverlay
import net.icantpy.dungeon.terminal.TerminalSettings
import net.icantpy.dungeon.terminal.TerminalSimulator
import net.icantpy.dungeon.terminal.TerminalTheme
import net.icantpy.dungeon.terminal.TerminalType
import kotlin.math.roundToInt

/**
 * Config body for "Terminals": which terminals the solver UI replaces, how clicks are sent, the panel's look and
 * colours, and the preview editor entry where the panel is tried and placed.
 */
internal class TerminalsPage {
    private class ColorRow(
        val title: String,
        val subtitle: String,
        val read: (TerminalSettings) -> Int,
        val write: (TerminalSettings, Int) -> TerminalSettings,
    )

    fun draw(draw: MenuDraw, m: ConfigMetrics, startY: Int, openPreview: () -> Unit): Int {
        val settings = TerminalOverlay.settings
        var y = RunPageRows.help(
            draw, m, startY,
            "Replaces the phase 3 terminal chests with a cleaner panel that shows only what to click. " +
                "Every click is still yours; the melody terminal keeps the vanilla screen.",
        )
        y = RunPageRows.button(
            draw, m, y, "Style editor",
            "Every look option with a live preview: 16 presets, colour pickers, animation, style sharing and placement.",
            "Open editor", ButtonStyle.PRIMARY, 120, openPreview,
        )
        y = RunPageRows.button(
            draw, m, y, "Terminal simulator",
            "Practise random terminals anywhere, timed, with nothing sent to the server. Also /icantpy termsim [type].",
            "Start", ButtonStyle.GHOST, 72,
        ) { TerminalSimulator.start() }
        y = RunPageRows.row(draw, m, y, "Preset", "Replace the colours and shapes with a ready-made look.") { cy ->
            var x = m.rightX(TerminalTheme.entries.sumOf { draw.measure(it.label, draw.buttonFont) + PRESET_PAD })
            TerminalTheme.entries.forEach { theme ->
                x += draw.button(theme.label, x, cy, ButtonStyle.GHOST) { TerminalOverlay.updateSettings(theme::applyTo) } + 4
            }
        }
        y = RunPageRows.heading(draw, m, y + RunPageRows.GROUP_GAP, "Terminals")
        TerminalType.entries.forEach { type ->
            y = RunPageRows.toggle(draw, m, y, type.displayName, "Use the solver panel for this terminal.", settings.typeEnabled(type)) {
                TerminalOverlay.updateSettings { it.withType(type, !it.typeEnabled(type)) }
            }
        }
        y = drawClicks(draw, m, y + RunPageRows.GROUP_GAP, settings)
        y = drawLook(draw, m, y + RunPageRows.GROUP_GAP, settings)
        y = drawColors(draw, m, y + RunPageRows.GROUP_GAP, settings)
        return y + RunPageRows.GROUP_GAP
    }

    private fun drawClicks(draw: MenuDraw, m: ConfigMetrics, startY: Int, settings: TerminalSettings): Int {
        var y = RunPageRows.heading(draw, m, startY, "Clicks")
        y = RunPageRows.button(
            draw, m, y, "Click button",
            "Mouse button sent to the server. Middle never picks the item up. The colour terminal always uses your own button.",
            settings.clickButton.label, ButtonStyle.GHOST, 80,
        ) { TerminalOverlay.updateSettings { it.copy(clickButton = next(it.clickButton)) } }
        y = RunPageRows.toggle(draw, m, y, "Block wrong clicks", "Ignore clicks on cells that are wrong, finished, or out of order.", settings.blockWrongClicks) {
            TerminalOverlay.updateSettings { it.copy(blockWrongClicks = !it.blockWrongClicks) }
        }
        y = RunPageRows.toggle(
            draw, m, y, "Show clicks instantly",
            "Mark a cell done as soon as you click it. It comes back if the server does not confirm within a moment.",
            settings.predictClicks,
        ) { TerminalOverlay.updateSettings { it.copy(predictClicks = !it.predictClicks) } }
        return RunPageRows.button(
            draw, m, y, "Colour terminal numbers",
            "Shortest: a negative number means right clicks. Left only: always count left clicks.",
            settings.rubixDisplay.label, ButtonStyle.GHOST, 96,
        ) { TerminalOverlay.updateSettings { it.copy(rubixDisplay = next(it.rubixDisplay)) } }
    }

    private fun drawLook(draw: MenuDraw, m: ConfigMetrics, startY: Int, settings: TerminalSettings): Int {
        var y = RunPageRows.heading(draw, m, startY, "Look")
        y = slider(
            draw, m, y, "Scale", "Size of the panel.", settings.scale,
            TerminalSettings.MIN_SCALE, TerminalSettings.MAX_SCALE, SCALE_STEP, { "${(it * 100).roundToInt()}%" },
        ) { value -> TerminalOverlay.updateSettings { it.copy(scale = value) } }
        y = slider(
            draw, m, y, "Cell gap", "Space between cells in pixels.", settings.gap.toFloat(),
            TerminalSettings.MIN_GAP.toFloat(), TerminalSettings.MAX_GAP.toFloat(), 1f, { "${it.roundToInt()} px" },
        ) { value -> TerminalOverlay.updateSettings { it.copy(gap = value.roundToInt()) } }
        y = slider(
            draw, m, y, "Panel opacity", "Background behind the cells.", settings.panelAlpha,
            0f, 1f, ALPHA_STEP, { "${(it * 100).roundToInt()}%" },
        ) { value -> TerminalOverlay.updateSettings { it.copy(panelAlpha = value) } }
        y = slider(
            draw, m, y, "Panel corners", "Rounding of the panel corners.", settings.panelRadius.toFloat(),
            0f, TerminalSettings.MAX_PANEL_RADIUS.toFloat(), 1f, { "${it.roundToInt()} px" },
        ) { value -> TerminalOverlay.updateSettings { it.copy(panelRadius = value.roundToInt()) } }
        y = RunPageRows.toggle(draw, m, y, "Panel border", "Draw a thin border around the panel.", settings.panelBorder) {
            TerminalOverlay.updateSettings { it.copy(panelBorder = !it.panelBorder) }
        }
        y = RunPageRows.button(
            draw, m, y, "Cell style", "Filled boxes, outlines only, or both.",
            settings.cellStyle.label, ButtonStyle.GHOST, 120,
        ) { TerminalOverlay.updateSettings { it.copy(cellStyle = next(it.cellStyle)) } }
        y = slider(
            draw, m, y, "Cell corners", "Rounding of each cell.", settings.cellRadius.toFloat(),
            0f, TerminalSettings.MAX_CELL_RADIUS.toFloat(), 1f, { "${it.roundToInt()} px" },
        ) { value -> TerminalOverlay.updateSettings { it.copy(cellRadius = value.roundToInt()) } }
        y = slider(
            draw, m, y, "Cell fill opacity", "How solid the cell colour is.", settings.cellAlpha,
            0f, 1f, ALPHA_STEP, { "${(it * 100).roundToInt()}%" },
        ) { value -> TerminalOverlay.updateSettings { it.copy(cellAlpha = value) } }
        y = slider(
            draw, m, y, "Outline width", "Thickness of cell outlines.", settings.outlineWidth.toFloat(),
            TerminalSettings.MIN_OUTLINE.toFloat(), TerminalSettings.MAX_OUTLINE.toFloat(), 1f, { "${it.roundToInt()} px" },
        ) { value -> TerminalOverlay.updateSettings { it.copy(outlineWidth = value.roundToInt()) } }
        y = slider(
            draw, m, y, "Text size", "Size of the title, counter and numbers.", settings.textScale,
            TerminalSettings.MIN_TEXT_SCALE, TerminalSettings.MAX_TEXT_SCALE, SCALE_STEP, { "${(it * 100).roundToInt()}%" },
        ) { value -> TerminalOverlay.updateSettings { it.copy(textScale = value) } }
        y = RunPageRows.button(
            draw, m, y, "Font", "Font for the title, counter and numbers.",
            TerminalFontChoice.label(settings), ButtonStyle.GHOST, 120,
        ) { TerminalOverlay.updateSettings { TerminalFontChoice.next(it, TerminalUserFonts.available()) } }
        y = RunPageRows.button(
            draw, m, y, "Your fonts", "Put .ttf or .otf files in config/icantpy-fonts; they join the font list. Size them in the style editor.",
            "Open folder", ButtonStyle.GHOST, 96,
        ) {
            TerminalUserFonts.refresh()
            TerminalUserFonts.openFolder()
        }
        y = RunPageRows.toggle(draw, m, y, "Text shadow", "Draw a shadow under the text.", settings.textShadow) {
            TerminalOverlay.updateSettings { it.copy(textShadow = !it.textShadow) }
        }
        y = RunPageRows.toggle(draw, m, y, "Show title", "Name the terminal above the cells.", settings.showTitle) {
            TerminalOverlay.updateSettings { it.copy(showTitle = !it.showTitle) }
        }
        y = RunPageRows.toggle(draw, m, y, "Show clicks left", "Count the clicks still needed.", settings.showRemaining) {
            TerminalOverlay.updateSettings { it.copy(showRemaining = !it.showRemaining) }
        }
        y = RunPageRows.toggle(draw, m, y, "Show items", "Draw the item icons in the letter and colour terminals.", settings.showItems) {
            TerminalOverlay.updateSettings { it.copy(showItems = !it.showItems) }
        }
        y = RunPageRows.toggle(draw, m, y, "Hide wrong items", "Leave cells you must not click empty.", settings.hideWrong) {
            TerminalOverlay.updateSettings { it.copy(hideWrong = !it.hideWrong) }
        }
        return RunPageRows.toggle(draw, m, y, "Hide finished cells", "Leave cells that are already done empty.", settings.hideDone) {
            TerminalOverlay.updateSettings { it.copy(hideDone = !it.hideDone) }
        }
    }

    private fun drawColors(draw: MenuDraw, m: ConfigMetrics, startY: Int, settings: TerminalSettings): Int {
        var y = RunPageRows.heading(draw, m, startY, "Colours")
        COLOR_ROWS.forEach { row -> y = swatchRow(draw, m, y, row, row.read(settings)) }
        return RunPageRows.button(draw, m, y, "Reset colours", "Restore every terminal colour to its default.", "Reset", ButtonStyle.GHOST, 72) {
            val d = TerminalSettings()
            TerminalOverlay.updateSettings { current -> COLOR_ROWS.fold(current) { acc, row -> row.write(acc, row.read(d)) } }
        }
    }

    private fun swatchRow(draw: MenuDraw, m: ConfigMetrics, y: Int, row: ColorRow, current: Int): Int =
        RunPageRows.row(draw, m, y, row.title, row.subtitle) { cy ->
            val left = m.rightX(PRESETS.size * SWATCH_STEP)
            PRESETS.forEachIndexed { index, rgb ->
                draw.swatch(left + index * SWATCH_STEP, cy + 6, rgb, rgb == current) {
                    TerminalOverlay.updateSettings { row.write(it, rgb) }
                }
            }
        }

    private fun slider(
        draw: MenuDraw,
        m: ConfigMetrics,
        y: Int,
        title: String,
        subtitle: String,
        value: Float,
        min: Float,
        max: Float,
        step: Float,
        format: (Float) -> String,
        onChange: (Float) -> Unit,
    ): Int = RunPageRows.row(draw, m, y, title, subtitle) { cy ->
        val x = m.rightX(SLIDER_WIDTH)
        val label = format(value)
        draw.text(label, x - 12 - draw.measure(label, draw.buttonFont), cy + 6, draw.palette.secondary, draw.buttonFont)
        draw.slider(x, cy, SLIDER_WIDTH, (value - min) / (max - min)) { fraction ->
            val snapped = (Math.round((fraction * (max - min)) / step) * step + min).coerceIn(min, max)
            val rounded = Math.round(snapped * 100f) / 100f
            // A drag reports every mouse move; only a new snapped value is saved.
            if (rounded != value) onChange(rounded)
        }
    }

    private fun next(current: TerminalClickButton): TerminalClickButton =
        TerminalClickButton.entries[(current.ordinal + 1) % TerminalClickButton.entries.size]

    private fun next(current: TerminalCellStyle): TerminalCellStyle =
        TerminalCellStyle.entries[(current.ordinal + 1) % TerminalCellStyle.entries.size]

    private fun next(current: RubixDisplay): RubixDisplay =
        RubixDisplay.entries[(current.ordinal + 1) % RubixDisplay.entries.size]

    private companion object {
        const val SLIDER_WIDTH = 140
        const val SWATCH_STEP = 20
        const val SCALE_STEP = 0.05f
        const val ALPHA_STEP = 0.05f
        const val PRESET_PAD = 32

        val PRESETS = listOf(
            0x55FF55, 0x00C8A0, 0x55AAFF, 0x8A6CFF, 0xFF55FF, 0xFF5555,
            0xFFAA00, 0xFFFF55, 0xFFFFFF, 0x3A3A3A, 0x1E1E1E, 0x101016,
        )

        val COLOR_ROWS = listOf(
            ColorRow("Click", "Cells to click, and the next pane in order.", { it.targetRgb }) { s, c -> s.copy(targetRgb = c) },
            ColorRow("Second in order", "The pane after the next one.", { it.orderSecondRgb }) { s, c -> s.copy(orderSecondRgb = c) },
            ColorRow("Third in order", "The pane two after the next one.", { it.orderThirdRgb }) { s, c -> s.copy(orderThirdRgb = c) },
            ColorRow("Finished", "Cells that are already done.", { it.doneRgb }) { s, c -> s.copy(doneRgb = c) },
            ColorRow("Wrong", "Cells you must not click.", { it.wrongRgb }) { s, c -> s.copy(wrongRgb = c) },
            ColorRow("Panel", "Background of the panel.", { it.panelRgb }) { s, c -> s.copy(panelRgb = c) },
            ColorRow("Border", "Panel border, when it is on.", { it.borderRgb }) { s, c -> s.copy(borderRgb = c) },
            ColorRow("Text", "Title, counter and pane numbers.", { it.textRgb }) { s, c -> s.copy(textRgb = c) },
            ColorRow("Left clicks", "Colour terminal numbers for left clicks.", { it.rubixLeftRgb }) { s, c -> s.copy(rubixLeftRgb = c) },
            ColorRow("Right clicks", "Colour terminal numbers for right clicks.", { it.rubixRightRgb }) { s, c -> s.copy(rubixRightRgb = c) },
        )
    }
}
