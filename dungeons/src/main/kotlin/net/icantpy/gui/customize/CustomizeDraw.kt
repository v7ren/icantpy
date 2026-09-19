package net.icantpy.gui.customize

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.ARGB

/** Small drawing helpers reproduced from Skyblocker's GuiHelper, scoped to the customizer. */
internal object CustomizeDraw {
    fun panel(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        graphics.fill(x, y, x + width, y + height, 0xE016161B.toInt())
        border(graphics, x, y, width, height, 0xFF3A3A42.toInt())
    }

    fun miniHotbar(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        panel(graphics, x, y, width, height)
        val slotWidth = width / 4
        for (slot in 1 until 4) {
            graphics.fill(x + slot * slotWidth, y + 2, x + slot * slotWidth + 1, y + height - 2, 0xFF303038.toInt())
        }
    }

    fun slotSelection(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        graphics.fill(x, y, x + width, y + height, 0x3050A0D0)
        border(graphics, x, y, width, height, 0xFF78B8E8.toInt())
    }

    fun border(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        graphics.fill(x, y, x + width, y + 1, color)
        graphics.fill(x, y + height - 1, x + width, y + height, color)
        graphics.fill(x, y + 1, x + 1, y + height - 1, color)
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color)
    }

    /** Horizontal gradient between two ARGB colours, drawn one pixel column at a time. */
    fun horizontalGradient(graphics: GuiGraphicsExtractor, x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int) {
        val start = x0.toInt()
        val end = x1.toInt()
        val width = (end - start).coerceAtLeast(1)
        val a0 = ARGB.alpha(from)
        val r0 = ARGB.red(from)
        val g0 = ARGB.green(from)
        val b0 = ARGB.blue(from)
        for (i in 0 until width) {
            val amount = i.toFloat() / width
            val a = a0 + ((ARGB.alpha(to) - a0) * amount).toInt()
            val r = r0 + ((ARGB.red(to) - r0) * amount).toInt()
            val g = g0 + ((ARGB.green(to) - g0) * amount).toInt()
            val b = b0 + ((ARGB.blue(to) - b0) * amount).toInt()
            graphics.fill(start + i, y0.toInt(), start + i + 1, y1.toInt(), ARGB.color(a, r, g, b))
        }
    }
}
