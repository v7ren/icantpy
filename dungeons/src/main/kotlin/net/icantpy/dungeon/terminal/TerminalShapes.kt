package net.icantpy.dungeon.terminal

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rounded rectangles and rings drawn from horizontal fills, since GUI graphics only fill axis-aligned rectangles.
 * A rounded shape costs one fill per row; at terminal cell sizes that is a few hundred fills a frame.
 */
object TerminalShapes {
    fun fill(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, argb: Int) {
        if (w <= 0 || h <= 0 || (argb ushr 24) == 0) return
        val r = clampRadius(radius, w, h)
        if (r == 0) {
            graphics.fill(x, y, x + w, y + h, argb)
            return
        }
        graphics.fill(x, y + r, x + w, y + h - r, argb)
        for (row in 0 until r) {
            val inset = inset(row, r)
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, argb)
            graphics.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, argb)
        }
    }

    /** A border [thickness] pixels wide that follows the same rounded outline as [fill]. */
    fun ring(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, thickness: Int, argb: Int) {
        if (w <= 0 || h <= 0 || thickness <= 0 || (argb ushr 24) == 0) return
        val t = thickness.coerceAtMost(minOf(w, h) / 2)
        val r = clampRadius(radius, w, h)
        val innerR = (r - t).coerceAtLeast(0)
        for (row in 0 until h) {
            val outer = rowInset(row, h, r)
            if (row < t || row >= h - t) {
                graphics.fill(x + outer, y + row, x + w - outer, y + row + 1, argb)
                continue
            }
            val inner = t + rowInset(row - t, h - 2 * t, innerR)
            graphics.fill(x + outer, y + row, x + inner, y + row + 1, argb)
            graphics.fill(x + w - inner, y + row, x + w - outer, y + row + 1, argb)
        }
    }

    private fun rowInset(row: Int, h: Int, r: Int): Int = when {
        r == 0 -> 0
        row < r -> inset(row, r)
        row >= h - r -> inset(h - 1 - row, r)
        else -> 0
    }

    /** Horizontal inset of [row] (0 = top edge) inside a corner of radius [r]. */
    private fun inset(row: Int, r: Int): Int {
        val dy = r - row - 0.5
        return (r - sqrt((r * r - dy * dy).coerceAtLeast(0.0))).roundToInt()
    }

    private fun clampRadius(radius: Int, w: Int, h: Int): Int = radius.coerceIn(0, minOf(w, h) / 2)
}
