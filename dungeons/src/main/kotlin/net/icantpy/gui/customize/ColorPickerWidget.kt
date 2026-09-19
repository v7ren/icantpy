package net.icantpy.gui.customize

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.CommonColors
import java.awt.Color

/** HSV colour picker, ported from Skyblocker's `ColorPickerWidget` (no texture dependency). */
class ColorPickerWidget(x: Int, y: Int, width: Int, height: Int, private val hasAlpha: Boolean = false) :
    AbstractWidget(x, y, width, height, Component.literal("ColorPicker")) {

    private val rainbowColors = createRainbowColors(minOf(width / 20, 8))
    private val alphaMask = if (hasAlpha) 0 else 0xFF000000.toInt()

    private var aThumbX = 0.0
    private var hThumbX = 0.0
    private var svThumbX = 0.0
    private var svThumbY = 0.0
    private var svColor = 0xFFFF0000.toInt()

    private var draggingSV = false
    private var draggingH = false
    private var draggingA = false

    private var svRect = ScreenRectangle.empty()
    private var hRect = ScreenRectangle.empty()
    private var aRect = ScreenRectangle.empty()

    private var argbColor = -1
    private var onColorChange: ((Int, Boolean) -> Unit)? = null

    init {
        updateRects()
    }

    override fun onRelease(click: MouseButtonEvent) {
        super.onRelease(click)
        if ((draggingH || draggingSV || draggingA) && onColorChange != null) {
            onColorChange?.invoke(argbColor or alphaMask, true)
        }
        draggingH = false
        draggingSV = false
        draggingA = false
    }

    private fun updateRects() {
        var y = getBottom()
        if (hasAlpha) {
            aRect = ScreenRectangle(getX() + 1, getBottom() - 9, getWidth() - 2, 8)
            y = aRect.top()
        }
        hRect = ScreenRectangle(getX() + 1, y - 9 - 4, getWidth() - 2, 8)
        val previewOffset = 15
        val svY = getY() + 1
        svRect = ScreenRectangle(getX() + 1 + previewOffset, svY, getWidth() - 2 - previewOffset, hRect.top() - svY - 4)
    }

    override fun setX(x: Int) {
        super.setX(x)
        updateRects()
    }

    override fun setY(y: Int) {
        super.setY(y)
        updateRects()
    }

    override fun setWidth(width: Int) {
        super.setWidth(width)
        updateRects()
    }

    override fun setHeight(height: Int) {
        super.setHeight(height)
        updateRects()
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        updateRects()
    }

    override fun onClick(click: MouseButtonEvent, doubled: Boolean) {
        super.onClick(click, doubled)
        val i = click.x().toInt()
        val j = click.y().toInt()
        if (hRect.containsPoint(i, j)) {
            draggingH = true
            onDrag(click, 0.0, 0.0)
        }
        if (svRect.containsPoint(i, j)) {
            draggingSV = true
            onDrag(click, 0.0, 0.0)
        }
        if (hasAlpha && aRect.containsPoint(i, j)) {
            draggingA = true
            onDrag(click, 0.0, 0.0)
        }
    }

    override fun onDrag(click: MouseButtonEvent, deltaX: Double, deltaY: Double) {
        super.onDrag(click, deltaX, deltaY)
        if (draggingH) {
            hThumbX = (click.x() - hRect.left()).coerceIn(0.0, (hRect.width() - 1).toDouble())
            svColor = Color.HSBtoRGB((hThumbX / (hRect.width() - 1)).toFloat(), 1f, 1f)
        }
        if (draggingSV) {
            svThumbX = (click.x() - svRect.left()).coerceIn(0.0, (svRect.width() - 1).toDouble())
            svThumbY = (click.y() - svRect.top()).coerceIn(0.0, (svRect.height() - 1).toDouble())
        }
        if (draggingA) {
            aThumbX = (click.x() - aRect.left()).coerceIn(0.0, (aRect.width() - 1).toDouble())
        }
        if (draggingH || draggingSV || draggingA) {
            val alpha = if (hasAlpha) (aThumbX / (aRect.width() - 1)).toFloat() else 1f
            argbColor = ARGB.color(
                alpha,
                Color.HSBtoRGB(
                    (hThumbX / (hRect.width() - 1)).toFloat(),
                    (svThumbX / (svRect.width() - 1)).toFloat(),
                    (1 - svThumbY / (svRect.height() - 1)).toFloat(),
                ),
            )
            onColorChange?.invoke(argbColor, false)
        }
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val color = 0x80606060.toInt()
        graphics.fill(hRect.left() - 1, hRect.top() - 1, hRect.right() + 1, hRect.bottom() + 1, color)
        for (i in rainbowColors.indices) {
            val startColor = rainbowColors[i]
            val endColor = rainbowColors[(i + 1) % rainbowColors.size]
            val segmentLength = hRect.width().toFloat() / rainbowColors.size
            val startX = hRect.left() + segmentLength * i
            val endX = hRect.left() + segmentLength * (i + 1)
            CustomizeDraw.horizontalGradient(graphics, startX, hRect.top().toFloat(), endX, hRect.bottom().toFloat(), startColor, endColor)
        }
        drawThumb(graphics, hRect, hThumbX.toInt())

        graphics.fill(svRect.left() - 1, svRect.top() - 1, svRect.right() + 1, svRect.bottom() + 1, color)
        val pickerX = svRect.left()
        val pickerY = svRect.top()
        val pickerEndX = svRect.right()
        val pickerEndY = svRect.bottom()
        CustomizeDraw.horizontalGradient(graphics, pickerX.toFloat(), pickerY.toFloat(), pickerEndX.toFloat(), pickerEndY.toFloat(), -1, svColor)
        graphics.fillGradient(pickerX, pickerY, pickerEndX, pickerEndY, 1, 0xFF000000.toInt())

        drawThumb2(graphics, svRect.left() + svThumbX.toInt(), svRect.top() + svThumbY.toInt())

        if (hasAlpha) {
            graphics.fill(aRect.left() - 1, aRect.top() - 1, aRect.right() + 1, aRect.bottom() + 1, color)
            CustomizeDraw.horizontalGradient(graphics, aRect.left().toFloat(), aRect.top().toFloat(), aRect.right().toFloat(), aRect.bottom().toFloat(), CommonColors.BLACK, CommonColors.WHITE)
            drawThumb(graphics, aRect, aThumbX.toInt())
        }

        graphics.fill(getX(), getY(), svRect.left() - 2, svRect.bottom() + 1, color)
        graphics.fill(getX() + 1, getY() + 1, svRect.left() - 3, svRect.bottom(), argbColor)

        if (isHovered) {
            if (svRect.containsPoint(mouseX, mouseY) || hRect.containsPoint(mouseX, mouseY) || aRect.containsPoint(mouseX, mouseY)) {
                graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND)
            }
            if (draggingSV) graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.CROSSHAIR)
            else if (draggingH || draggingA) graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_EW)
        }
    }

    private fun drawThumb(graphics: GuiGraphicsExtractor, rect: ScreenRectangle, thumbX: Int) {
        graphics.fill(rect.left() + thumbX - 1, rect.top(), rect.left() + thumbX + 2, rect.bottom(), CommonColors.BLACK)
        graphics.fill(rect.left() + thumbX, rect.top() - 1, rect.left() + thumbX + 1, rect.bottom() + 1, CommonColors.BLACK)
        graphics.fill(rect.left() + thumbX, rect.top(), rect.left() + thumbX + 1, rect.bottom(), CommonColors.WHITE)
    }

    private fun drawThumb2(graphics: GuiGraphicsExtractor, cx: Int, cy: Int) {
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, CommonColors.WHITE)
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, CommonColors.BLACK)
    }

    fun getARGBColor(): Int = argbColor

    fun setARGBColor(argb: Int) {
        argbColor = argb or alphaMask
        val floats = Color.RGBtoHSB((argbColor shr 16) and 0xFF, (argbColor shr 8) and 0xFF, argbColor and 0xFF, null)
        setHSV(floats[0], floats[1], floats[2])
        setAlphaValue(ARGB.alphaFloat(argbColor))
    }

    fun setHSV(h: Float, s: Float, v: Float) {
        hThumbX = (h * (hRect.width() - 1)).toDouble()
        svThumbX = (s * (svRect.width() - 1)).toDouble()
        svThumbY = ((1 - v) * (svRect.height() - 1)).toDouble()
        svColor = Color.HSBtoRGB((hThumbX / (hRect.width() - 1)).toFloat(), 1f, 1f)
    }

    fun setAlphaValue(alpha: Float) {
        if (!hasAlpha) return
        aThumbX = (alpha * (aRect.width() - 1)).toDouble()
    }

    fun setOnColorChange(onColorChange: ((Int, Boolean) -> Unit)?) {
        this.onColorChange = onColorChange
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    private companion object {
        fun createRainbowColors(samples: Int): IntArray {
            val colors = IntArray(samples)
            for (i in 0 until samples) colors[i] = Color.HSBtoRGB(i.toFloat() / samples, 1f, 1f)
            return colors
        }
    }
}
