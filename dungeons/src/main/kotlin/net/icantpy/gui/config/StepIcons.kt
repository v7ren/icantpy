package net.icantpy.gui.config

import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path

/** Vector glyphs for the leap action row: move up, move down and remove. Drawn, not font glyphs. */
internal enum class StepGlyph { UP, DOWN, REMOVE }

/** Half the width or height of a glyph inside its button. */
private const val GLYPH_HALF = 4f
private const val GLYPH_STROKE = 1.6f
private const val INLINE_BUTTON_PAD = 14

private val glyphFill = Paint().apply { isAntiAlias = true }
private val glyphStroke = Paint().apply {
    setStroke(true)
    strokeWidth = GLYPH_STROKE
    isAntiAlias = true
}

/** A square icon button; disabled buttons are drawn dim and do not register a click. */
internal fun MenuDraw.stepIconButton(rect: LayoutRect, glyph: StepGlyph, enabled: Boolean, action: () -> Unit) {
    val hot = enabled && hovered(rect.x, rect.y, rect.w, rect.h)
    val corner = chrome.controlRadius.coerceAtMost(6f)
    round(rect.x, rect.y, rect.w, rect.h, corner, if (hot) palette.rowHover else palette.group)
    outline(rect.x, rect.y, rect.w, rect.h, corner, if (hot) palette.secondary else palette.line)
    drawStepGlyph(glyph, rect, if (enabled) palette.text else palette.line)
    if (enabled) clickable(rect.x, rect.y, rect.w, rect.h, action)
}

/** A full-width ghost button used for "+ Add action". */
internal fun MenuDraw.inlineButton(label: String, rect: LayoutRect, action: () -> Unit) {
    val hot = hovered(rect.x, rect.y, rect.w, rect.h)
    val corner = chrome.controlRadius.coerceAtMost(8f)
    round(rect.x, rect.y, rect.w, rect.h, corner, if (hot) palette.rowHover else palette.group)
    outline(rect.x, rect.y, rect.w, rect.h, corner, if (hot) palette.secondary else palette.line)
    val textY = rect.y + (rect.h - lineHeight(buttonFont)) / 2
    text(fit(label, rect.w - 2 * INLINE_BUTTON_PAD, buttonFont), rect.x + INLINE_BUTTON_PAD, textY, palette.text, buttonFont)
    clickable(rect.x, rect.y, rect.w, rect.h, action)
}

private fun MenuDraw.drawStepGlyph(glyph: StepGlyph, rect: LayoutRect, color: Int) {
    val cx = rect.x + rect.w / 2f
    val cy = rect.y + rect.h / 2f
    when (glyph) {
        StepGlyph.UP -> canvas.drawPath(triangle(cx, cy, pointingUp = true, color = color), glyphFill)
        StepGlyph.DOWN -> canvas.drawPath(triangle(cx, cy, pointingUp = false, color = color), glyphFill)
        StepGlyph.REMOVE -> {
            glyphStroke.color = color
            canvas.drawLine(cx - GLYPH_HALF, cy - GLYPH_HALF, cx + GLYPH_HALF, cy + GLYPH_HALF, glyphStroke)
            canvas.drawLine(cx + GLYPH_HALF, cy - GLYPH_HALF, cx - GLYPH_HALF, cy + GLYPH_HALF, glyphStroke)
        }
    }
}

/** Filled isosceles triangle centred on ([cx], [cy]). */
private fun triangle(cx: Float, cy: Float, pointingUp: Boolean, color: Int): Path {
    glyphFill.color = color
    val tip = if (pointingUp) cy - GLYPH_HALF else cy + GLYPH_HALF
    val base = if (pointingUp) cy + GLYPH_HALF / 2 else cy - GLYPH_HALF / 2
    return Path().apply {
        moveTo(cx, tip)
        lineTo(cx + GLYPH_HALF, base)
        lineTo(cx - GLYPH_HALF, base)
        closePath()
    }
}
