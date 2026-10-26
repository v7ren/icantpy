package net.icantpy.gui.config

import net.icantpy.dungeon.terminal.RubixDisplay
import net.icantpy.dungeon.terminal.TerminalFontChoice
import net.icantpy.dungeon.terminal.TerminalUserFonts
import net.icantpy.dungeon.terminal.TerminalCellStyle
import net.icantpy.dungeon.terminal.TerminalClickButton
import net.icantpy.dungeon.terminal.TerminalHeaderPosition
import net.icantpy.dungeon.terminal.TerminalOverlay
import net.icantpy.dungeon.terminal.TerminalSettings
import net.icantpy.dungeon.terminal.TerminalStyleShare
import net.icantpy.dungeon.terminal.TerminalTheme
import net.icantpy.dungeon.terminal.TerminalTitleAlign
import net.icantpy.dungeon.terminal.TerminalType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color
import kotlin.math.roundToInt

private typealias S = TerminalSettings

/**
 * The style sidebar of the terminal preview: presets, style sharing, sliders, toggles and colour pickers drawn with
 * plain GUI fills, so it works where the Skia settings window does not (macOS). Every change is saved at once and the
 * preview redraws from the saved settings, so the panel restyles live.
 */
internal class TerminalStyleEditor {
    private sealed interface Control

    private class Heading(val label: String) : Control

    private class Toggle(val label: String, val get: (S) -> Boolean, val set: (S, Boolean) -> S) : Control

    private class Cycle(val label: String, val value: (S) -> String, val next: (S) -> S) : Control

    private class Slider(
        val label: String,
        val min: Float,
        val max: Float,
        val step: Float,
        val get: (S) -> Float,
        val set: (S, Float) -> S,
        val format: (Float) -> String,
    ) : Control

    private class ColorPick(val label: String, val get: (S) -> Int, val set: (S, Int) -> S) : Control

    private object Presets : Control

    private object Sharing : Control

    private object FontFolder : Control

    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val onPress: (Int) -> Unit) {
        fun contains(mx: Int, my: Int): Boolean = mx in x until x + w && my in y until y + h
    }

    /** Hue, saturation and brightness of an open colour picker, kept so a grey colour does not lose its hue. */
    private class Hsv(var rgb: Int, val values: FloatArray)

    private val controls: List<Control> = buildControls()
    private val hits = ArrayList<Hit>()
    private val openPickers = HashMap<String, Hsv>()
    private var dragging: ((Int) -> Unit)? = null
    private var scroll = 0
    private var contentHeight = 0
    private var status: String? = null
    private var statusUntil = 0L

    fun contains(screenW: Int, mouseX: Int): Boolean = mouseX >= left(screenW)

    fun render(graphics: GuiGraphicsExtractor, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font
        val settings = TerminalOverlay.settings
        val x0 = left(screenW)
        hits.clear()
        graphics.fill(x0, 0, screenW, screenH, BACKGROUND)
        graphics.fill(x0, 0, x0 + 1, screenH, DIVIDER)
        scroll = scroll.coerceIn(0, (contentHeight - screenH + PAD * 2).coerceAtLeast(0))
        graphics.enableScissor(x0, 0, screenW, screenH)
        try {
            var y = PAD - scroll
            val x = x0 + PAD
            val w = WIDTH - PAD * 2
            controls.forEach { control ->
                val h = heightOf(control)
                if (y + h > 0 && y < screenH) drawControl(graphics, font, control, settings, x, y, w, mouseX, mouseY)
                y += h
            }
            contentHeight = y + scroll
        } finally {
            graphics.disableScissor()
        }
        drawStatus(graphics, font, x0, screenH)
    }

    fun mouseClicked(mouseX: Int, mouseY: Int): Boolean {
        val hit = hits.lastOrNull { it.contains(mouseX, mouseY) } ?: return true
        hit.onPress(mouseX)
        return true
    }

    fun mouseDragged(mouseX: Int): Boolean {
        val drag = dragging ?: return false
        drag(mouseX)
        return true
    }

    fun mouseReleased(): Boolean {
        if (dragging == null) return false
        dragging = null
        return true
    }

    fun mouseScrolled(scrollY: Double) {
        scroll -= (scrollY * SCROLL_STEP).roundToInt()
    }

    private fun heightOf(control: Control): Int = when (control) {
        is Heading -> 16
        is Toggle, is Cycle -> 14
        is Slider -> 24
        is ColorPick -> 26 + if (control.label in openPickers) PICKER_ROWS * PICKER_ROW_H else 0
        Presets -> ((TerminalTheme.entries.size + PRESETS_PER_ROW - 1) / PRESETS_PER_ROW) * (BUTTON_H + 2)
        Sharing, FontFolder -> BUTTON_H + 4
    }

    private fun drawControl(
        graphics: GuiGraphicsExtractor,
        font: Font,
        control: Control,
        settings: S,
        x: Int,
        y: Int,
        w: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        when (control) {
            is Heading -> {
                graphics.text(font, control.label, x, y + 5, ACCENT, true)
                graphics.fill(x, y + 15, x + w, y + 16, DIVIDER)
            }
            is Toggle -> {
                val on = control.get(settings)
                row(graphics, font, control.label, x, y, w, mouseX, mouseY) {
                    TerminalOverlay.updateSettings { control.set(it, !control.get(it)) }
                }
                val label = if (on) "ON" else "OFF"
                val pillW = 26
                graphics.fill(x + w - pillW, y + 2, x + w, y + 12, if (on) ON_FILL else OFF_FILL)
                graphics.text(font, label, x + w - pillW / 2 - font.width(label) / 2, y + 3, TEXT, false)
            }
            is Cycle -> {
                row(graphics, font, control.label, x, y, w, mouseX, mouseY) {
                    TerminalOverlay.updateSettings(control.next)
                }
                val value = "‹ ${control.value(settings)} ›"
                graphics.text(font, value, x + w - font.width(value), y + 3, ACCENT, false)
            }
            is Slider -> drawSlider(graphics, font, control.label, control.get(settings), control.min, control.max, x, y, w, control.format) { mx ->
                val raw = control.min + fraction(mx, x, w) * (control.max - control.min)
                val snapped = (Math.round((raw - control.min) / control.step) * control.step + control.min).coerceIn(control.min, control.max)
                val next = Math.round(snapped * 100f) / 100f
                if (next != control.get(TerminalOverlay.settings)) TerminalOverlay.updateSettings { control.set(it, next) }
            }
            is ColorPick -> drawColorPick(graphics, font, control, settings, x, y, w)
            Presets -> drawPresets(graphics, font, x, y, w, mouseX, mouseY)
            Sharing -> drawSharing(graphics, font, x, y, w, mouseX, mouseY)
            FontFolder -> drawFontFolder(graphics, font, x, y, w, mouseX, mouseY)
        }
    }

    /** A full-width clickable row with its label; the caller draws the value at the right. */
    private fun row(
        graphics: GuiGraphicsExtractor,
        font: Font,
        label: String,
        x: Int,
        y: Int,
        w: Int,
        mouseX: Int,
        mouseY: Int,
        onPress: () -> Unit,
    ) {
        val hit = Hit(x - 2, y, w + 4, 14) { onPress() }
        if (hit.contains(mouseX, mouseY)) graphics.fill(hit.x, hit.y, hit.x + hit.w, hit.y + hit.h, HOVER)
        graphics.text(font, label, x, y + 3, TEXT, false)
        hits += hit
    }

    /** Label, value and a draggable track; [apply] receives the mouse x while pressed or dragged. */
    private fun drawSlider(
        graphics: GuiGraphicsExtractor,
        font: Font,
        label: String,
        value: Float,
        min: Float,
        max: Float,
        x: Int,
        y: Int,
        w: Int,
        format: (Float) -> String,
        apply: (Int) -> Unit,
    ) {
        graphics.text(font, label, x, y + 2, TEXT, false)
        val shown = format(value)
        graphics.text(font, shown, x + w - font.width(shown), y + 2, MUTED, false)
        val trackY = y + 15
        val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
        graphics.fill(x, trackY, x + w, trackY + 3, TRACK)
        graphics.fill(x, trackY, x + (w * f).roundToInt(), trackY + 3, ACCENT)
        knob(graphics, x + (w * f).roundToInt(), trackY - 3, 9)
        hits += Hit(x - 4, trackY - 5, w + 8, 13) { mx ->
            apply(mx)
            dragging = apply
        }
    }

    private fun drawColorPick(graphics: GuiGraphicsExtractor, font: Font, control: ColorPick, settings: S, x: Int, y: Int, w: Int) {
        val current = control.get(settings)
        val open = control.label in openPickers
        graphics.text(font, control.label, x, y + 2, TEXT, false)
        val boxX = x + w - 34
        graphics.fill(boxX - 1, y, x + w + 1, y + 11, if (open) ACCENT else DIVIDER)
        graphics.fill(boxX, y + 1, x + w, y + 10, OPAQUE or current)
        val hint = if (open) "▲" else "▼"
        graphics.text(font, hint, boxX - font.width(hint) - 3, y + 2, MUTED, false)
        hits += Hit(boxX - 12, y, w - (boxX - 12 - x), 11) { togglePicker(control.label, current) }
        val swatchY = y + 13
        PALETTE.forEachIndexed { index, rgb ->
            val sx = x + index * (SWATCH + 2)
            if (sx + SWATCH > x + w) return@forEachIndexed
            if (rgb == current) graphics.fill(sx - 1, swatchY - 1, sx + SWATCH + 1, swatchY + SWATCH + 1, TEXT)
            graphics.fill(sx, swatchY, sx + SWATCH, swatchY + SWATCH, OPAQUE or rgb)
            hits += Hit(sx, swatchY, SWATCH, SWATCH) { setColor(control, rgb) }
        }
        if (open) drawPicker(graphics, font, control, current, x, y + 26, w)
    }

    /** Hue, saturation and brightness bars with gradient tracks. */
    private fun drawPicker(graphics: GuiGraphicsExtractor, font: Font, control: ColorPick, current: Int, x: Int, y: Int, w: Int) {
        val hsv = openPickers.getValue(control.label)
        if (hsv.rgb != current) {
            Color.RGBtoHSB((current shr 16) and 0xFF, (current shr 8) and 0xFF, current and 0xFF, hsv.values)
            hsv.rgb = current
        }
        val labels = listOf("H", "S", "B")
        val trackX = x + 12
        val trackW = w - 12 - 30
        labels.forEachIndexed { index, label ->
            val rowY = y + index * PICKER_ROW_H
            graphics.text(font, label, x, rowY + 3, MUTED, false)
            val trackY = rowY + 3
            for (step in 0 until trackW) {
                val t = step.toFloat() / (trackW - 1).coerceAtLeast(1)
                val rgb = when (index) {
                    0 -> Color.HSBtoRGB(t, 1f, 1f)
                    1 -> Color.HSBtoRGB(hsv.values[0], t, hsv.values[2].coerceAtLeast(0.2f))
                    else -> Color.HSBtoRGB(hsv.values[0], hsv.values[1], t)
                }
                graphics.fill(trackX + step, trackY, trackX + step + 1, trackY + 7, OPAQUE or (rgb and 0xFFFFFF))
            }
            val knobX = trackX + (hsv.values[index] * (trackW - 1)).roundToInt()
            knob(graphics, knobX, trackY - 2, 11)
            val apply: (Int) -> Unit = { mx ->
                hsv.values[index] = fraction(mx, trackX, trackW)
                val rgb = Color.HSBtoRGB(hsv.values[0], hsv.values[1], hsv.values[2]) and 0xFFFFFF
                hsv.rgb = rgb
                if (rgb != control.get(TerminalOverlay.settings)) TerminalOverlay.updateSettings { control.set(it, rgb) }
            }
            hits += Hit(trackX - 3, trackY - 3, trackW + 6, 13) { mx ->
                apply(mx)
                dragging = apply
            }
        }
        val hex = "#%06X".format(current and 0xFFFFFF)
        graphics.text(font, hex, x + w - font.width(hex), y + 3, MUTED, false)
    }

    private fun togglePicker(label: String, current: Int) {
        if (openPickers.remove(label) != null) return
        val values = FloatArray(3)
        Color.RGBtoHSB((current shr 16) and 0xFF, (current shr 8) and 0xFF, current and 0xFF, values)
        openPickers[label] = Hsv(current, values)
    }

    private fun setColor(control: ColorPick, rgb: Int) {
        openPickers[control.label]?.let { hsv ->
            Color.RGBtoHSB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, hsv.values)
            hsv.rgb = rgb
        }
        TerminalOverlay.updateSettings { control.set(it, rgb) }
    }

    private fun drawPresets(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int) {
        val buttonW = (w - (PRESETS_PER_ROW - 1) * 3) / PRESETS_PER_ROW
        TerminalTheme.entries.forEachIndexed { index, theme ->
            val bx = x + (index % PRESETS_PER_ROW) * (buttonW + 3)
            val by = y + (index / PRESETS_PER_ROW) * (BUTTON_H + 2)
            button(graphics, font, theme.label, bx, by, buttonW, mouseX, mouseY) {
                TerminalOverlay.updateSettings(theme::applyTo)
                openPickers.clear()
                show("Applied ${theme.label}")
            }
        }
    }

    private fun drawSharing(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int) {
        val buttonW = (w - 3) / 2
        button(graphics, font, "Copy style", x, y + 2, buttonW, mouseX, mouseY) {
            Minecraft.getInstance().keyboardHandler.clipboard = TerminalStyleShare.export(TerminalOverlay.settings)
            show("Style copied to the clipboard")
        }
        button(graphics, font, "Paste style", x + buttonW + 3, y + 2, buttonW, mouseX, mouseY) {
            val pasted = TerminalStyleShare.import(Minecraft.getInstance().keyboardHandler.clipboard, TerminalOverlay.settings)
            if (pasted == null) {
                show("The clipboard has no icantpy style")
            } else {
                TerminalOverlay.updateSettings { pasted }
                openPickers.clear()
                show("Style pasted")
            }
        }
    }

    /** Opens config/icantpy-fonts, and rereads it after the player adds or replaces font files. */
    private fun drawFontFolder(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int) {
        val buttonW = (w - 3) / 2
        button(graphics, font, "Open fonts folder", x, y + 2, buttonW, mouseX, mouseY) {
            TerminalUserFonts.openFolder()
            show("Add .ttf or .otf files, then Reload")
        }
        button(graphics, font, "Reload fonts", x + buttonW + 3, y + 2, buttonW, mouseX, mouseY) {
            TerminalUserFonts.refresh()
            val count = TerminalUserFonts.available().size
            show(if (count == 1) "Found 1 font" else "Found $count fonts")
        }
    }

    private fun button(
        graphics: GuiGraphicsExtractor,
        font: Font,
        label: String,
        x: Int,
        y: Int,
        w: Int,
        mouseX: Int,
        mouseY: Int,
        onPress: () -> Unit,
    ) {
        val hit = Hit(x, y, w, BUTTON_H) { onPress() }
        graphics.fill(x, y, x + w, y + BUTTON_H, if (hit.contains(mouseX, mouseY)) BUTTON_HOVER else BUTTON)
        val shown = if (font.width(label) > w - 4) font.plainSubstrByWidth(label, w - 4) else label
        graphics.text(font, shown, x + w / 2 - font.width(shown) / 2, y + 3, TEXT, false)
        hits += hit
    }

    private fun knob(graphics: GuiGraphicsExtractor, centerX: Int, top: Int, height: Int) {
        graphics.fill(centerX - 2, top, centerX + 2, top + height, KNOB_EDGE)
        graphics.fill(centerX - 1, top + 1, centerX + 1, top + height - 1, TEXT)
    }

    private fun drawStatus(graphics: GuiGraphicsExtractor, font: Font, x0: Int, screenH: Int) {
        val text = status ?: return
        if (System.currentTimeMillis() > statusUntil) {
            status = null
            return
        }
        graphics.fill(x0, screenH - 16, x0 + WIDTH, screenH, STATUS_BG)
        graphics.text(font, text, x0 + PAD, screenH - 12, TEXT, false)
    }

    private fun show(text: String) {
        status = text
        statusUntil = System.currentTimeMillis() + STATUS_MS
    }

    private fun fraction(mouseX: Int, x: Int, w: Int): Float = ((mouseX - x).toFloat() / w).coerceIn(0f, 1f)

    private fun buildControls(): List<Control> = buildList {
        add(Heading("Presets"))
        add(Presets)
        add(Sharing)
        add(Heading("Panel"))
        add(Slider("Scale", S.MIN_SCALE, S.MAX_SCALE, 0.05f, { it.scale }, { s, v -> s.copy(scale = v) }, ::percent))
        add(Slider("Opacity", 0f, 1f, 0.05f, { it.panelAlpha }, { s, v -> s.copy(panelAlpha = v) }, ::percent))
        add(Slider("Padding", 0f, S.MAX_PADDING.toFloat(), 1f, { it.padding.toFloat() }, { s, v -> s.copy(padding = v.roundToInt()) }, ::pixels))
        add(Slider("Corner radius", 0f, S.MAX_PANEL_RADIUS.toFloat(), 1f, { it.panelRadius.toFloat() }, { s, v -> s.copy(panelRadius = v.roundToInt()) }, ::pixels))
        add(ColorPick("Background", { it.panelRgb }) { s, c -> s.copy(panelRgb = c) })
        add(Toggle("Border", { it.panelBorder }) { s, v -> s.copy(panelBorder = v) })
        add(ColorPick("Border colour", { it.borderRgb }) { s, c -> s.copy(borderRgb = c) })
        add(Slider("Dim the screen", 0f, 1f, 0.05f, { it.screenDim }, { s, v -> s.copy(screenDim = v) }, ::percent))
        add(Heading("Header"))
        add(Toggle("Show title", { it.showTitle }) { s, v -> s.copy(showTitle = v) })
        add(Toggle("Show clicks left", { it.showRemaining }) { s, v -> s.copy(showRemaining = v) })
        add(Cycle("Position", { it.headerPosition.label }) { it.copy(headerPosition = next(it.headerPosition)) })
        add(Cycle("Alignment", { it.titleAlign.label }) { it.copy(titleAlign = next(it.titleAlign)) })
        add(Heading("Cells"))
        add(Cycle("Style", { it.cellStyle.label }) { it.copy(cellStyle = next(it.cellStyle)) })
        add(Slider("Gap", S.MIN_GAP.toFloat(), S.MAX_GAP.toFloat(), 1f, { it.gap.toFloat() }, { s, v -> s.copy(gap = v.roundToInt()) }, ::pixels))
        add(Slider("Corner radius", 0f, S.MAX_CELL_RADIUS.toFloat(), 1f, { it.cellRadius.toFloat() }, { s, v -> s.copy(cellRadius = v.roundToInt()) }, ::pixels))
        add(Slider("Fill opacity", 0f, 1f, 0.05f, { it.cellAlpha }, { s, v -> s.copy(cellAlpha = v) }, ::percent))
        add(Slider("Outline width", S.MIN_OUTLINE.toFloat(), S.MAX_OUTLINE.toFloat(), 1f, { it.outlineWidth.toFloat() }, { s, v -> s.copy(outlineWidth = v.roundToInt()) }, ::pixels))
        add(Toggle("Show items", { it.showItems }) { s, v -> s.copy(showItems = v) })
        add(Toggle("Hide wrong", { it.hideWrong }) { s, v -> s.copy(hideWrong = v) })
        add(Toggle("Hide finished", { it.hideDone }) { s, v -> s.copy(hideDone = v) })
        add(Toggle("Hover outline", { it.showHover }) { s, v -> s.copy(showHover = v) })
        add(ColorPick("Hover colour", { it.hoverRgb }) { s, c -> s.copy(hoverRgb = c) })
        add(Heading("Animation"))
        add(Toggle("Pulse the next cell", { it.pulseNext }) { s, v -> s.copy(pulseNext = v) })
        add(Toggle("Fade finished cells", { it.fadeDone }) { s, v -> s.copy(fadeDone = v) })
        add(Heading("Text"))
        add(Cycle("Font", ::fontLabel) { TerminalFontChoice.next(it, TerminalUserFonts.available()) })
        add(FontFolder)
        add(Slider("Your font size", S.MIN_CUSTOM_FONT_SIZE, S.MAX_CUSTOM_FONT_SIZE, 0.5f, { it.customFontSize }, { s, v -> s.copy(customFontSize = v) }, ::pixels))
        add(Slider("Your font height", -S.MAX_CUSTOM_FONT_SHIFT, S.MAX_CUSTOM_FONT_SHIFT, 0.5f, { it.customFontShift }, { s, v -> s.copy(customFontShift = v) }, ::pixels))
        add(Slider("Size", S.MIN_TEXT_SCALE, S.MAX_TEXT_SCALE, 0.05f, { it.textScale }, { s, v -> s.copy(textScale = v) }, ::percent))
        add(Toggle("Shadow", { it.textShadow }) { s, v -> s.copy(textShadow = v) })
        add(ColorPick("Colour", { it.textRgb }) { s, c -> s.copy(textRgb = c) })
        add(Heading("Cell colours"))
        add(ColorPick("Click / next", { it.targetRgb }) { s, c -> s.copy(targetRgb = c) })
        add(ColorPick("Second in order", { it.orderSecondRgb }) { s, c -> s.copy(orderSecondRgb = c) })
        add(ColorPick("Third in order", { it.orderThirdRgb }) { s, c -> s.copy(orderThirdRgb = c) })
        add(ColorPick("Finished", { it.doneRgb }) { s, c -> s.copy(doneRgb = c) })
        add(ColorPick("Wrong", { it.wrongRgb }) { s, c -> s.copy(wrongRgb = c) })
        add(ColorPick("Left-click numbers", { it.rubixLeftRgb }) { s, c -> s.copy(rubixLeftRgb = c) })
        add(ColorPick("Right-click numbers", { it.rubixRightRgb }) { s, c -> s.copy(rubixRightRgb = c) })
        add(Heading("Clicks"))
        add(Cycle("Button", { it.clickButton.label }) { it.copy(clickButton = next(it.clickButton)) })
        add(Toggle("Block wrong clicks", { it.blockWrongClicks }) { s, v -> s.copy(blockWrongClicks = v) })
        add(Toggle("Show clicks instantly", { it.predictClicks }) { s, v -> s.copy(predictClicks = v) })
        add(Slider("First click delay", 0f, S.MAX_FIRST_CLICK_DELAY_MS.toFloat(), 25f, { it.firstClickDelayMs.toFloat() }, { s, v -> s.copy(firstClickDelayMs = v.roundToInt()) }, ::millis))
        add(Toggle("Hold clicks for server lag", { it.lagProtection }) { s, v -> s.copy(lagProtection = v) })
        add(Slider("Lag protection", S.MIN_LAG_TICKS.toFloat(), S.MAX_LAG_TICKS.toFloat(), 1f, { it.lagProtectionTicks.toFloat() }, { s, v -> s.copy(lagProtectionTicks = v.roundToInt()) }, ::ticks))
        add(Slider("Resolve timeout", S.MIN_RESOLVE_TIMEOUT_MS.toFloat(), S.MAX_RESOLVE_TIMEOUT_MS.toFloat(), 10f, { it.resolveTimeoutMs.toFloat() }, { s, v -> s.copy(resolveTimeoutMs = v.roundToInt()) }, ::millis))
        add(Cycle("Colour numbers", { it.rubixDisplay.label }) { it.copy(rubixDisplay = next(it.rubixDisplay)) })
        add(Heading("Terminals"))
        TerminalType.entries.forEach { type ->
            add(Toggle(type.displayName, { it.typeEnabled(type) }) { s, v -> s.withType(type, v) })
        }
    }

    companion object {
        const val WIDTH = 196
        private const val PAD = 6
        private const val SWATCH = 10
        private const val BUTTON_H = 14
        private const val PRESETS_PER_ROW = 3
        private const val PICKER_ROWS = 3
        private const val PICKER_ROW_H = 14
        private const val SCROLL_STEP = 20
        private const val STATUS_MS = 1_800L
        private const val OPAQUE = 0xFF000000.toInt()
        private const val BACKGROUND = 0xE8101014.toInt()
        private const val DIVIDER = 0xFF2E2E38.toInt()
        private const val HOVER = 0x22FFFFFF
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFB0B0B0.toInt()
        private const val ACCENT = 0xFFC9BBFF.toInt()
        private const val TRACK = 0xFF3A3A44.toInt()
        private const val KNOB_EDGE = 0xFF000000.toInt()
        private const val ON_FILL = 0xFF3CB371.toInt()
        private const val OFF_FILL = 0xFF55555F.toInt()
        private const val BUTTON = 0xFF2A2A33.toInt()
        private const val BUTTON_HOVER = 0xFF3D3D4A.toInt()
        private const val STATUS_BG = 0xF02A2A33.toInt()

        private val PALETTE = listOf(
            0xFFFFFF, 0xBDBDBD, 0x3A3A3A, 0x1E1E1E, 0x101016, 0x55FF55, 0x3CB371, 0x00F0FF,
            0x55AAFF, 0x8A6CFF, 0xFF55FF, 0xFF5555, 0xFFAA00, 0xFFFF55,
        )

        fun left(screenW: Int): Int = screenW - WIDTH

        private fun percent(value: Float): String = "${(value * 100).roundToInt()}%"

        private fun pixels(value: Float): String =
            if (value == value.roundToInt().toFloat()) "${value.roundToInt()} px" else "$value px"

        /** The chosen font's name, flagged when its file is missing or could not be read. */
        private fun fontLabel(settings: S): String {
            val name = TerminalFontChoice.label(settings)
            return if (TerminalUserFonts.isBroken(settings)) "$name (can't load)" else name
        }

        private fun ticks(value: Float): String = "${value.roundToInt()} ticks"

        private fun millis(value: Float): String = if (value <= 0f) "Off" else "${value.roundToInt()} ms"

        private inline fun <reified E : Enum<E>> cycle(current: E): E {
            val values = enumValues<E>()
            return values[(current.ordinal + 1) % values.size]
        }

        private fun next(current: TerminalCellStyle): TerminalCellStyle = cycle(current)

        private fun next(current: TerminalClickButton): TerminalClickButton = cycle(current)

        private fun next(current: RubixDisplay): RubixDisplay = cycle(current)

        private fun next(current: TerminalHeaderPosition): TerminalHeaderPosition = cycle(current)

        private fun next(current: TerminalTitleAlign): TerminalTitleAlign = cycle(current)

    }
}
