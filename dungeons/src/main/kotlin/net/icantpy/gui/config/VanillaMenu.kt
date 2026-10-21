package net.icantpy.gui.config

import net.icantpy.gui.GuiHit
import net.icantpy.gui.GuiRect
import net.icantpy.gui.wrapLines
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect

internal const val TAB_PADDING = 11
internal const val TOGGLE_WIDTH = 34
internal const val TOGGLE_HEIGHT = 18
private const val DROPDOWN_ITEM_HEIGHT = 22
private const val DROPDOWN_HEADER_HEIGHT = 18

/** One line of a grouped dropdown: [option] is the index of a choice, or null for a non-clickable header. */
data class DropdownLine(val label: String, val option: Int?)

internal fun dropdownLineHeight(line: DropdownLine): Int =
    if (line.option == null) DROPDOWN_HEADER_HEIGHT else DROPDOWN_ITEM_HEIGHT

internal fun dropdownLinesHeight(lines: List<DropdownLine>): Int = lines.sumOf { dropdownLineHeight(it) } + 4
private const val SELECT_PAD = 10
private const val SELECT_CHEVRON_SIZE = 8

/** Approximate text line box; a 28px field keeps the historical 6px text offset. */
private const val FIELD_TEXT_BOX = 16
private const val TOGGLE_INSET = 3
private const val TOGGLE_KNOB = 12
private const val TOGGLE_MAX_TRACK_RADIUS = 9f
private const val SCROLLBAR_WIDTH = 2
private const val FADE_HEIGHT = 14
private const val FADE_STEPS = 7

internal enum class ButtonStyle {
    PRIMARY,
    GHOST,
    DANGER,
}

internal class MenuDraw(
    val canvas: Canvas,
    var clipTop: Int,
    var clipBottom: Int,
    var clipLeft: Int,
    var clipRight: Int,
    val mouseX: Int = Int.MIN_VALUE,
    val mouseY: Int = Int.MIN_VALUE,
    val chrome: GuiChrome,
) {
    val hits = mutableListOf<GuiHit>()
    private val chevronPaint = Paint().apply {
        setStroke(true)
        strokeWidth = 1.6f
        isAntiAlias = true
    }
    val palette: GuiPalette = chrome.palette
    val radius: Float = chrome.radius
    val titleFont = sized(chrome.typefaces.semibold, 12f)
    val headingFont = sized(chrome.typefaces.semibold, 9.5f)
    val bodyFont = sized(chrome.typefaces.semibold, 10.5f)
    val descFont = sized(chrome.typefaces.regular, 9.5f)
    val buttonFont = sized(chrome.typefaces.regular, 10f)
    val smallFont = sized(chrome.typefaces.regular, 9.5f)
    val microFont = sized(chrome.typefaces.mono, 8.5f)
    val captionFont = sized(chrome.typefaces.regular, 9f)
    val displayFont = sized(chrome.typefaces.semibold, 15f)
    private var ignoreClip = false
    private var tooltipText: String? = null
    private var tooltipMaxWidth = 260
    private val fill = Paint()
    private val stroke = Paint().apply {
        setStroke(true)
        strokeWidth = 1f
    }

    fun unclipped(block: () -> Unit) {
        val previous = ignoreClip
        ignoreClip = true
        block()
        ignoreClip = previous
    }

    fun visible(y: Int, h: Int): Boolean = ignoreClip || (y + h > clipTop && y < clipBottom)

    fun round(x: Int, y: Int, w: Int, h: Int, corner: Float, color: Int) {
        if (w <= 0 || h <= 0 || !visible(y, h)) return
        fill.color = color
        fill.isAntiAlias = corner > 0.5f
        if (corner <= 0.5f) {
            canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()), fill)
        } else {
            canvas.drawRRect(RRect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), corner), fill)
        }
    }

    fun outline(x: Int, y: Int, w: Int, h: Int, corner: Float, color: Int) {
        if (w <= 0 || h <= 0 || !visible(y, h)) return
        stroke.color = color
        stroke.isAntiAlias = corner > 0.5f
        if (corner <= 0.5f) {
            canvas.drawRect(Rect.makeXYWH(x.toFloat() + 0.5f, y.toFloat() + 0.5f, w - 1f, h - 1f), stroke)
        } else {
            canvas.drawRRect(RRect.makeXYWH(x.toFloat() + 0.5f, y.toFloat() + 0.5f, w - 1f, h - 1f, corner), stroke)
        }
    }

    fun line(x: Int, y: Int, w: Int, color: Int = palette.line) {
        if (w <= 0 || !visible(y - 1, 2)) return
        fill.color = color
        fill.isAntiAlias = false
        canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), 1f), fill)
    }

    fun grid(step: Int, color: Int) {
        if (step <= 0) return
        fill.color = color
        fill.isAntiAlias = false
        var x = 0
        while (x <= clipRight) {
            var y = clipTop
            while (y <= clipBottom) {
                canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 1f, 1f), fill)
                y += step
            }
            x += step
        }
    }

    fun text(value: String, x: Int, y: Int, color: Int, font: Font = bodyFont) {
        if (value.isEmpty() || !visible(y, lineHeight(font))) return
        fill.color = color
        fill.isAntiAlias = true
        canvas.drawString(value, x.toFloat(), y - font.metrics.ascent, font, fill)
    }

    fun heading(label: String, x: Int, y: Int) {
        text(label.uppercase(), x, y, palette.accent, headingFont)
    }

    fun measure(value: String, font: Font = bodyFont): Int = font.measureText(value).width.toInt()

    fun lineHeight(font: Font): Int =
        (-font.metrics.ascent + font.metrics.descent + font.metrics.leading).toInt().coerceAtLeast(12)

    fun wrap(value: String, maxWidth: Int, font: Font = descFont): List<String> =
        wrapLines(value, maxWidth) { measure(it, font) }

    fun hovered(x: Int, y: Int, w: Int, h: Int): Boolean =
        visible(y, h) && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h

    fun circle(x: Float, y: Float, radius: Float, color: Int) {
        fill.color = color
        fill.isAntiAlias = true
        canvas.drawCircle(x, y, radius, fill)
    }

    fun clickable(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        if (w <= 0 || h <= 0) return
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }





    fun navTab(label: String, x: Int, y: Int, w: Int, h: Int, selected: Boolean, action: () -> Unit) {
        if (selected) {
            round(x + TAB_PADDING, y + h - 3, w - TAB_PADDING * 2, 2, 0f, palette.accent)
        } else if (hovered(x, y, w, h)) {
            round(x, y + 4, w, h - 8, chrome.controlRadius, withAlpha(palette.accentSoft, 0x1A))
        }
        text(label, x + TAB_PADDING, y + 10, if (selected) palette.accent else palette.text, buttonFont)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun button(label: String, x: Int, y: Int, style: ButtonStyle, action: () -> Unit): Int {
        val w = measure(label, buttonFont) + 28
        val h = 28
        val hot = hovered(x, y, w, h)
        val corner = chrome.controlRadius.coerceAtMost(12f)
        when (style) {
            ButtonStyle.PRIMARY -> {
                round(x, y, w, h, corner, if (hot) palette.accent else palette.activeAccent)
                text(label, x + 14, y + 6, palette.accentInk, buttonFont)
            }
            ButtonStyle.GHOST -> {
                round(x, y, w, h, corner, if (hot) palette.rowHover else palette.group)
                outline(x, y, w, h, corner, palette.line)
                text(label, x + 14, y + 6, palette.text, buttonFont)
            }
            ButtonStyle.DANGER -> {
                round(x, y, w, h, corner, if (hot) palette.dangerSoft else palette.group)
                outline(x, y, w, h, corner, palette.danger)
                text(label, x + 14, y + 6, palette.danger, buttonFont)
            }
        }
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
        return w
    }

    fun dropdownHeight(optionCount: Int): Int = optionCount * DROPDOWN_ITEM_HEIGHT + 4

    /**
     * Grouped dropdown that paints a [viewHeight] window onto [lines], starting [scroll] pixels down.
     * Only lines that fit whole are drawn and hit. Returns the scroll offset used, clamped to the content.
     */
    fun dropdownScrollable(
        x: Int,
        menuY: Int,
        width: Int,
        viewHeight: Int,
        lines: List<DropdownLine>,
        selected: Int,
        scroll: Int,
        onSelect: (Int) -> Unit,
    ): Int {
        val maxScroll = (dropdownLinesHeight(lines) - viewHeight).coerceAtLeast(0)
        val offset = scroll.coerceIn(0, maxScroll)
        val radius = chrome.controlRadius.coerceAtMost(12f)
        val top = menuY + 2
        val bottom = menuY + viewHeight - 2
        unclipped {
            round(x, menuY, width, viewHeight, radius, palette.window)
            outline(x, menuY, width, viewHeight, radius, palette.accent)
            var y = top - offset
            lines.forEach { line ->
                val h = dropdownLineHeight(line)
                if (y >= top && y + h <= bottom) {
                    val option = line.option
                    if (option == null) {
                        text(fit(line.label, width - 18, captionFont), x + 9, y + 3, palette.secondary, captionFont)
                    } else {
                        val hot = hovered(x + 2, y, width - 4, h)
                        if (hot || option == selected) {
                            round(
                                x + 2,
                                y,
                                width - 4,
                                h,
                                chrome.controlRadius.coerceAtMost(8f),
                                if (option == selected) palette.accentSoft else palette.rowHover,
                            )
                        }
                        text(
                            fit(line.label, width - 18, buttonFont),
                            x + 9,
                            y + 4,
                            if (option == selected) palette.accent else palette.text,
                            buttonFont,
                        )
                        hitRect(x + 2, y, width - 4, h)?.let {
                            hits += GuiHit(it, action = { onSelect(option) })
                        }
                    }
                }
                y += h
            }
        }
        return offset
    }

    /** Dropdown that flips above its anchor when it would pass the bottom of the clip area. */
    fun dropdownMenu(
        x: Int,
        anchorY: Int,
        width: Int,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        if (options.isEmpty()) return
        val menuHeight = dropdownHeight(options.size)
        val menuY = if (anchorY + menuHeight <= clipBottom) anchorY else anchorY - menuHeight
        dropdownMenuAt(x, menuY, width, options, selected, onSelect)
    }

    /** Paints a dropdown list whose top edge is already placed, without clip limits. */
    fun dropdownMenuAt(
        x: Int,
        menuY: Int,
        width: Int,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        if (options.isEmpty()) return
        val menuHeight = dropdownHeight(options.size)
        unclipped {
            round(x, menuY, width, menuHeight, chrome.controlRadius.coerceAtMost(12f), palette.window)
            outline(x, menuY, width, menuHeight, chrome.controlRadius.coerceAtMost(12f), palette.accent)
            options.forEachIndexed { index, option ->
                val itemY = menuY + 2 + index * DROPDOWN_ITEM_HEIGHT
                val hot = hovered(x + 2, itemY, width - 4, DROPDOWN_ITEM_HEIGHT)
                if (hot || index == selected) {
                    round(
                        x + 2,
                        itemY,
                        width - 4,
                        DROPDOWN_ITEM_HEIGHT,
                        chrome.controlRadius.coerceAtMost(8f),
                        if (index == selected) palette.accentSoft else palette.rowHover,
                    )
                }
                text(
                    fit(option, width - 18, buttonFont),
                    x + 9,
                    itemY + 4,
                    if (index == selected) palette.accent else palette.text,
                    buttonFont,
                )
                hitRect(x + 2, itemY, width - 4, DROPDOWN_ITEM_HEIGHT)?.let {
                    hits += GuiHit(it, action = { onSelect(index) })
                }
            }
        }
    }

    /**
     * Vector chevron centred on ([centerX], [centerY]). [expanded] points down, otherwise right.
     * Drawn with lines instead of font glyphs so it never renders as a missing-glyph box.
     */
    fun chevron(centerX: Int, centerY: Int, size: Int, expanded: Boolean, color: Int) {
        if (!visible(centerY - size, size * 2)) return
        chevronPaint.color = color
        val half = size / 2f
        val quarter = size / 4f
        val cx = centerX.toFloat()
        val cy = centerY.toFloat()
        if (expanded) {
            canvas.drawLine(cx - half, cy - quarter, cx, cy + quarter, chevronPaint)
            canvas.drawLine(cx, cy + quarter, cx + half, cy - quarter, chevronPaint)
        } else {
            canvas.drawLine(cx - quarter, cy - half, cx + quarter, cy, chevronPaint)
            canvas.drawLine(cx + quarter, cy, cx - quarter, cy + half, chevronPaint)
        }
    }

    /** Ghost-style dropdown button: [label] on the left and a drawn down-chevron at the right edge. */
    fun selectButton(label: String, x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        if (w <= 0 || h <= 0) return
        val hot = hovered(x, y, w, h)
        val corner = chrome.controlRadius.coerceAtMost(8f)
        round(x, y, w, h, corner, if (hot) palette.rowHover else palette.group)
        outline(x, y, w, h, corner, if (hot) palette.secondary else palette.line)
        val chevronCenterX = x + w - SELECT_PAD - SELECT_CHEVRON_SIZE / 2
        val textX = x + SELECT_PAD
        val textMax = chevronCenterX - SELECT_CHEVRON_SIZE / 2 - SELECT_PAD - textX
        val textY = y + (h - lineHeight(buttonFont)) / 2
        text(fit(label, textMax.coerceAtLeast(20), buttonFont), textX, textY, palette.text, buttonFont)
        chevron(chevronCenterX, y + h / 2, SELECT_CHEVRON_SIZE, true, palette.tertiary)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun toggle(x: Int, y: Int, on: Boolean, action: () -> Unit) {
        val w = TOGGLE_WIDTH
        val h = TOGGLE_HEIGHT
        val hot = hovered(x, y, w, h)
        val trackCorner = chrome.controlRadius.coerceAtMost(TOGGLE_MAX_TRACK_RADIUS)
        if (on) {
            round(x, y, w, h, trackCorner, palette.activeAccent)
        } else {
            round(x, y, w, h, trackCorner, palette.trackOff)
            outline(x, y, w, h, trackCorner, if (hot) palette.secondary else palette.line)
        }
        val knobX = if (on) x + w - TOGGLE_INSET - TOGGLE_KNOB else x + TOGGLE_INSET
        round(knobX, y + TOGGLE_INSET, TOGGLE_KNOB, TOGGLE_KNOB, TOGGLE_KNOB / 2f, if (on) palette.accentInk else palette.knob)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun field(id: String, value: String, placeholder: String, x: Int, y: Int, w: Int, focused: Boolean, h: Int = 28) {
        val hot = focused || hovered(x, y, w, h)
        val corner = chrome.controlRadius.coerceAtMost(8f)
        round(x, y, w, h, corner, palette.field)
        outline(x, y, w, h, corner, if (focused) palette.accent else if (hot) palette.secondary else palette.line)
        val shown = value.ifEmpty { placeholder }
        val color = when {
            focused -> palette.accent
            value.isEmpty() -> palette.tertiary
            else -> palette.text
        }
        text(fit(shown, w - 14, smallFont), x + 10, y + (h - FIELD_TEXT_BOX) / 2, color, smallFont)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, fieldId = id) }
    }

    fun requestTooltip(value: String, maxWidth: Int = 260) {
        if (value.isNotBlank()) {
            tooltipText = value
            tooltipMaxWidth = maxWidth.coerceAtLeast(120)
        }
    }

    fun paintTooltip() {
        val value = tooltipText ?: return
        val maxWidth = tooltipMaxWidth.coerceAtMost((clipRight - clipLeft - 24).coerceAtLeast(120))
        val lines = wrap(value, maxWidth, descFont)
        val textWidth = lines.maxOfOrNull { measure(it, descFont) } ?: return
        val boxW = (textWidth + 20).coerceAtMost(clipRight - clipLeft - 12)
        val boxH = lines.size * lineHeight(descFont) + 14
        val boxX = (mouseX + 12).coerceIn(clipLeft + 6, clipRight - boxW - 6)
        val belowY = mouseY + 12
        val boxY = if (belowY + boxH <= clipBottom - 4) belowY else mouseY - boxH - 8
        unclipped {
            round(boxX, boxY, boxW, boxH, chrome.controlRadius.coerceAtMost(8f), palette.window)
            outline(boxX, boxY, boxW, boxH, chrome.controlRadius.coerceAtMost(8f), palette.accent)
            lines.forEachIndexed { index, line ->
                text(line, boxX + 10, boxY + 7 + index * lineHeight(descFont), palette.secondary, descFont)
            }
        }
    }

    fun swatch(x: Int, y: Int, rgb: Int, selected: Boolean, action: () -> Unit) {
        val size = 16
        round(x, y, size, size, chrome.controlRadius.coerceAtMost(6f), rgb or 0xFF000000.toInt())
        outline(x, y, size, size, chrome.controlRadius.coerceAtMost(6f), if (selected) palette.accent else palette.line)
        if (visible(y, size) && inClipX(x, size)) hitRect(x, y, size, size)?.let { hits += GuiHit(it, action = action) }
    }

    /**
     * Horizontal slider [w] wide at [fraction] (0..1). Pressing or dragging anywhere on it calls [onDrag] with the
     * fraction under the mouse; the knob may hang a little past either end, so the hit area does too.
     */
    fun slider(x: Int, y: Int, w: Int, fraction: Float, onDrag: (Float) -> Unit) {
        val h = 28
        val f = fraction.coerceIn(0f, 1f)
        val trackY = y + h / 2 - SLIDER_TRACK / 2
        val hot = hovered(x - SLIDER_KNOB, y, w + SLIDER_KNOB * 2, h)
        round(x, trackY, w, SLIDER_TRACK, SLIDER_TRACK / 2f, palette.trackOff)
        val filled = (w * f).toInt()
        if (filled > 0) round(x, trackY, filled, SLIDER_TRACK, SLIDER_TRACK / 2f, palette.activeAccent)
        circle(x + w * f, y + h / 2f, if (hot) SLIDER_KNOB + 1f else SLIDER_KNOB.toFloat(), palette.accent)
        val hx = x - SLIDER_KNOB
        val hw = w + SLIDER_KNOB * 2
        if (visible(y, h) && inClipX(hx, hw)) {
            hitRect(hx, y, hw, h)?.let { rect ->
                hits += GuiHit(rect, drag = { mx -> onDrag(((mx - x).toFloat() / w).coerceIn(0f, 1f)) })
            }
        }
    }

    fun chip(label: String, x: Int, y: Int, selected: Boolean, action: () -> Unit): Int {
        val w = measure(label, buttonFont) + 22
        val h = 28
        val corner = chrome.controlRadius.coerceAtMost(8f)
        round(x, y, w, h, corner, if (selected) palette.accentSoft else palette.group)
        outline(x, y, w, h, corner, if (selected) palette.accent else palette.line)
        text(label, x + 11, y + 6, if (selected) palette.accent else palette.text, buttonFont)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
        return w
    }

    fun pickCard(
        title: String,
        caption: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        selected: Boolean,
        action: () -> Unit,
    ) {
        val corner = chrome.cardRadius
        round(x, y, w, h, corner, if (selected) palette.accentSoft else palette.group)
        outline(x, y, w, h, corner, if (selected) palette.accent else palette.line)
        text(title, x + 10, y + 10, if (selected) palette.accent else palette.text, bodyFont)
        var ty = y + 28
        wrap(caption, w - 20).forEach { line ->
            text(line, x + 10, ty, palette.tertiary, microFont)
            ty += lineHeight(microFont)
        }
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun scrollbar(x: Int, y: Int, h: Int, contentH: Int, scroll: Int) {
        if (contentH <= h) return
        round(x, y, SCROLLBAR_WIDTH, h, 1f, palette.line)
        val thumbH = (h.toFloat() * h / contentH).toInt().coerceIn(18, h)
        val max = (contentH - h).coerceAtLeast(1)
        val thumbY = y + ((h - thumbH) * scroll / max)
        round(x, thumbY, SCROLLBAR_WIDTH, thumbH, 1f, withAlpha(palette.accent, 0x99))
    }

    /** Soft fade of [color] over the bottom [FADE_HEIGHT] pixels, signalling more content below. */
    fun fadeBottom(x: Int, bottom: Int, w: Int, color: Int) {
        val stepH = FADE_HEIGHT / FADE_STEPS
        val alphaMax = color ushr 24
        repeat(FADE_STEPS) { step ->
            val alpha = alphaMax * (step + 1) / FADE_STEPS
            round(x, bottom - FADE_HEIGHT + step * stepH, w, stepH, 0f, withAlpha(color, alpha))
        }
    }

    fun fit(value: String, maxWidth: Int, font: Font): String {
        if (measure(value, font) <= maxWidth) return value
        var text = value
        while (text.isNotEmpty() && measure("$text...", font) > maxWidth) {
            text = text.dropLast(1)
        }
        return if (text.isEmpty()) "..." else "$text..."
    }


    private fun hitRect(x: Int, y: Int, w: Int, h: Int): GuiRect? {
        if (ignoreClip) return GuiRect(x, y, w, h)
        val x1 = maxOf(x, clipLeft)
        val y1 = maxOf(y, clipTop)
        val x2 = minOf(x + w, clipRight)
        val y2 = minOf(y + h, clipBottom)
        if (x2 <= x1 || y2 <= y1) return null
        return GuiRect(x1, y1, x2 - x1, y2 - y1)
    }

    private fun inClipX(x: Int, w: Int): Boolean = ignoreClip || (x + w > clipLeft && x < clipRight)

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun sized(typeface: org.jetbrains.skia.Typeface, size: Float): Font = Font(typeface, size).apply {
        edging = FontEdging.SUBPIXEL_ANTI_ALIAS
        hinting = FontHinting.SLIGHT
        isSubpixel = true
    }
}

private const val SLIDER_TRACK = 4
private const val SLIDER_KNOB = 6
