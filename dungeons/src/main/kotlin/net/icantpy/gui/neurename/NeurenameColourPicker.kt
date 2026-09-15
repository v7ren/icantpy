package net.icantpy.gui.neurename

import net.icantpy.modules.impl.appearance.ItemCustomizeColor
import net.icantpy.modules.impl.appearance.ItemCustomizeColorEvaluator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class NeurenameColourTarget { GLINT, LEATHER }

/**
 * 1:1 port of NEU's GuiElementColour: 64x64 hue/saturation wheel (radius^1.5 saturation), value
 * slider, optional opacity slider, chroma speed slider and hex RGB field.
 */
internal class NeurenameColourPicker(
    anchorX: Int,
    anchorY: Int,
    screenWidth: Int,
    screenHeight: Int,
    initial: ItemCustomizeColor,
    val target: NeurenameColourTarget,
    private val onChange: (ItemCustomizeColor) -> Unit,
) {
    private val opacitySlider = target == NeurenameColourTarget.GLINT
    private val valueSlider = true

    val xSize: Int = 119 - (if (valueSlider) 0 else 15) - (if (opacitySlider) 0 else 15)
    val ySize: Int = 89

    val x: Int = anchorX.coerceIn(10, (screenWidth - xSize - 10).coerceAtLeast(10))
    val y: Int = anchorY.coerceIn(10, (screenHeight - ySize - 10).coerceAtLeast(10))

    private var color: ItemCustomizeColor = initial
    private var hue: Float
    private var saturation: Float
    private var brightness: Float

    private var hexFocused = false
    private var hexBuffer: String = "%06X".format(initial.rgb)
    private var clickedComponent = -1

    init {
        val hsb = ItemCustomizeColorEvaluator.rgbToHsb(initial.rgb)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
    }

    fun render(graphics: GuiGraphicsExtractor) {
        NeurenameDraw.floatingRectDark(graphics, x, y, xSize, ySize)

        val hsb = ItemCustomizeColorEvaluator.rgbToHsb(color.rgb)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]

        paintWheel(graphics, x + 1, y + 1)
        paintValueBar(graphics)

        var valueOffset = 0
        var opacityOffset = 0
        if (valueSlider) valueOffset = 15
        if (opacitySlider) opacityOffset = 15

        if (opacitySlider) {
            val opacityX = x + 5 + 64 + 5 + valueOffset
            NeurenameDraw.texture(graphics, NeurenameAssets.selectBarAlpha, opacityX, y + 5, 10, 64)
            paintOpacityBar(graphics, opacityX)
        }

        val chromaX = x + 5 + 64 + valueOffset + opacityOffset + 5
        if (color.chromaSpeed > 0) {
            val chromaRgb = chromaHue()
            graphics.fill(chromaX + 1, y + 5 + 1, chromaX + 9, y + 5 + 63, opaque(ItemCustomizeColorEvaluator.hsbToRgb(chromaRgb, 0.8f, 0.8f)))
        } else {
            val animated = (hsvHueOfChroma() + System.currentTimeMillis() / 1000f) % 1f
            graphics.fill(chromaX + 1, y + 5 + 28, chromaX + 9, y + 5 + 36, opaque(ItemCustomizeColorEvaluator.hsbToRgb(animated, 0.8f, 0.8f)))
        }

        if (valueSlider) NeurenameDraw.texture(graphics, NeurenameAssets.selectBar, x + 5 + 64 + 5, y + 5, 10, 64)
        if (opacitySlider) {
            NeurenameDraw.texture(graphics, NeurenameAssets.selectBar, x + 5 + 64 + 5 + valueOffset, y + 5, 10, 64)
        }
        if (color.chromaSpeed > 0) {
            NeurenameDraw.texture(graphics, NeurenameAssets.selectBar, chromaX, y + 5, 10, 64)
        } else {
            NeurenameDraw.texture(graphics, NeurenameAssets.selectChroma, chromaX, y + 5 + 27, 10, 10)
        }

        if (valueSlider) {
            graphics.fill(x + 5 + 64 + 5, y + 5 + 64 - (64 * brightness).toInt(), x + 5 + 64 + 5 + 10, y + 5 + 64 - (64 * brightness).toInt() + 1, 0xFF000000.toInt())
        }
        if (opacitySlider) {
            graphics.fill(
                x + 5 + 64 + 5 + valueOffset,
                y + 5 + 64 - color.alpha / 4,
                x + 5 + 64 + 5 + valueOffset + 10,
                y + 5 + 64 - color.alpha / 4 - 1,
                0xFF000000.toInt(),
            )
        }
        if (color.chromaSpeed > 0) {
            graphics.fill(chromaX, y + 5 + 64 - (color.chromaSpeed / 255f * 64).toInt(), chromaX + 10, y + 5 + 64 - (color.chromaSpeed / 255f * 64).toInt() + 1, 0xFF000000.toInt())
        }

        val selRadius = saturation.pow(1f / 1.5f) * 32
        val selX = (cos(Math.toRadians(hue.toDouble())) * selRadius).toInt()
        val selY = (sin(Math.toRadians(hue.toDouble())) * selRadius).toInt()
        NeurenameDraw.texture(graphics, NeurenameAssets.selectDot, x + 5 + 32 + selX - 4, y + 5 + 32 + selY - 4, 8, 8)

        NeurenameDraw.text(
            graphics,
            Math.round(brightness * 100).toString(),
            x + 5 + 64 + 5 + 5 - (if (Math.round(brightness * 100) == 100) 1 else 0),
            y + 5 + 64 + 5,
            0xFF808080.toInt(),
        )
        if (opacitySlider) {
            NeurenameDraw.text(graphics, Math.round(color.alpha / 255f * 100).toString(), x + 5 + 64 + 5 + valueOffset + 5, y + 5 + 64 + 5, 0xFF808080.toInt())
        }
        if (color.chromaSpeed > 0) {
            NeurenameDraw.text(
                graphics,
                "${ItemCustomizeColorEvaluator.secondsForSpeed(color.chromaSpeed).toInt()}s",
                x + 5 + 64 + 5 + valueOffset + opacityOffset + 6,
                y + 5 + 64 + 5,
                0xFF808080.toInt(),
            )
        }

        paintHex(graphics)
    }

    private fun paintHex(graphics: GuiGraphicsExtractor) {
        val hx = x + 5 + 8
        val hy = y + 5 + 64 + 5
        if (!hexFocused) hexBuffer = "%06X".format(color.rgb)
        val prefix = "#" + "0".repeat((6 - hexBuffer.length).coerceAtLeast(0))
        NeurenameDraw.text(graphics, prefix, hx - NeurenameDraw.width(prefix), hy, 0xFF808080.toInt())
        NeurenameDraw.text(graphics, hexBuffer, hx, hy, 0xFFFFFFFF.toInt())
        if (hexFocused && System.currentTimeMillis() % 1000 > 500) {
            graphics.fill(hx + NeurenameDraw.width(hexBuffer), hy - 1, hx + NeurenameDraw.width(hexBuffer) + 1, hy + 9, 0xFFFFFFFF.toInt())
        }
    }

    fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): Boolean {
        if (mouseX < x || mouseX > x + xSize || mouseY < y || mouseY > y + ySize) return false
        if (button != 0) return true

        val valueOffset = if (valueSlider) 15 else 0
        val opacityOffset = if (opacitySlider) 15 else 0

        // Hex field hit.
        if (mouseX > x + 5 + 8 && mouseX < x + 5 + 8 + 48 && mouseY > y + 5 + 64 + 5 && mouseY < y + 5 + 64 + 5 + 10) {
            hexFocused = true
            clickedComponent = -1
            return true
        }
        hexFocused = false

        val wheelX = mouseX - x - 5
        val wheelY = mouseY - y - 5
        if (wheelX in 1 until 64 && wheelY in 1 until 64) clickedComponent = 0

        val yComp = mouseY - y - 5
        if (yComp > -5 && yComp <= 69) {
            val xValue = mouseX - (x + 5 + 64 + 5)
            if (valueSlider && xValue > 0 && xValue < 10) clickedComponent = 1
            if (opacitySlider) {
                val xOpacity = mouseX - (x + 5 + 64 + 5 + valueOffset)
                if (xOpacity > 0 && xOpacity < 10) clickedComponent = 2
            }
        }

        val xChroma = mouseX - (x + 5 + 64 + valueOffset + opacityOffset + 5)
        if (xChroma > 0 && xChroma < 10) {
            if (color.chromaSpeed > 0) {
                if (yComp > -5 && yComp <= 69) clickedComponent = 3
            } else if (mouseY > y + 5 + 27 && mouseY < y + 5 + 37) {
                commit(color.copy(chromaSpeed = DEFAULT_CHROMA))
            }
        }
        if (clickedComponent >= 0) applyDrag(mouseX, mouseY)
        return true
    }

    fun mouseDragged(mouseX: Int, mouseY: Int): Boolean {
        if (clickedComponent < 0) return false
        applyDrag(mouseX, mouseY)
        return true
    }

    fun mouseReleased() {
        clickedComponent = -1
    }

    fun keyPressed(key: Int): Boolean {
        if (!hexFocused) return false
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            hexFocused = false
            return true
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            updateHex(hexBuffer.dropLast(1))
            return true
        }
        return false
    }

    fun charTyped(codepoint: Int): Boolean {
        if (!hexFocused) return false
        val character = Character.toString(codepoint)
        if (!character.matches(Regex("[0-9a-fA-F]"))) return true
        if (hexBuffer.length >= 6) return true
        updateHex(hexBuffer + character.uppercase())
        return true
    }

    private fun applyDrag(mouseX: Int, mouseY: Int) {
        when (clickedComponent) {
            0 -> {
                val wheelX = mouseX - x - 5
                val wheelY = mouseY - y - 5
                var angle = Math.toDegrees(atan((32 - wheelX) / (wheelY - 32 + 1e-5)) + Math.PI / 2).toFloat()
                val cx = wheelX.coerceIn(0, 64)
                val cy = wheelY.coerceIn(0, 64)
                val radius = sqrt(((cx - 32) * (cx - 32) + (cy - 32) * (cy - 32)) / 1024f)
                if (cy < 32) angle += 180f
                val rgb = ItemCustomizeColorEvaluator.hsbToRgb(angle / 360f, minOf(1f, radius).pow(1.5f), brightness)
                commit(color.copy(rgb = rgb))
            }
            1 -> {
                val yComp = (mouseY - y - 5).coerceIn(0, 64)
                val rgb = ItemCustomizeColorEvaluator.hsbToRgb(hue, saturation, 1f - yComp / 64f)
                commit(color.copy(rgb = rgb))
            }
            2 -> {
                val yComp = (mouseY - y - 5).coerceIn(0, 64)
                commit(color.copy(alpha = (255 - Math.round(yComp / 64f * 255)).coerceIn(0, 255)))
            }
            3 -> {
                val yComp = (mouseY - y - 5).coerceIn(0, 64)
                commit(color.copy(chromaSpeed = (255 - Math.round(yComp / 64f * 255)).coerceIn(0, 255)))
            }
        }
    }

    private fun commit(next: ItemCustomizeColor) {
        color = next
        val hsb = ItemCustomizeColorEvaluator.rgbToHsb(next.rgb)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
        onChange(next)
    }

    private fun updateHex(text: String) {
        hexBuffer = text.uppercase()
        if (hexBuffer.length == 6) {
            val rgb = hexBuffer.toIntOrNull(16) ?: return
            commit(color.copy(rgb = rgb))
        }
    }

    private fun paintWheel(graphics: GuiGraphicsExtractor, ox: Int, oy: Int) {
        val borderRadius = 0.05f
        for (tj in 0 until 72) {
            for (ti in 0 until 72) {
                val px = (ti * 4 - 16).toFloat()
                val py = (tj * 4 - 16).toFloat()
                val radius = sqrt(((px - 128) * (px - 128) + (py - 128) * (py - 128)) / 16384f)
                var angle = Math.toDegrees(atan(((128 - px) / (py - 128 + 1e-5)).toDouble()) + Math.PI / 2).toFloat()
                if (py < 128) angle += 180f
                val argb = when {
                    radius <= 1f -> {
                        val rgb = ItemCustomizeColorEvaluator.hsbToRgb(angle / 360f, radius.pow(1.5f), brightness)
                        opaque(rgb)
                    }
                    radius <= 1f + borderRadius -> {
                        val invBlackAlpha = abs(radius - 1 - borderRadius / 2) / borderRadius * 2
                        val blackAlpha = 1 - invBlackAlpha
                        if (radius > 1 + borderRadius / 2) {
                            ((blackAlpha * 255).toInt() and 0xFF) shl 24
                        } else {
                            val rgb = ItemCustomizeColorEvaluator.hsbToRgb(angle / 360f, 1f, brightness)
                            val r = ((rgb shr 16 and 0xFF) * invBlackAlpha).toInt()
                            val g = ((rgb shr 8 and 0xFF) * invBlackAlpha).toInt()
                            val b = ((rgb and 0xFF) * invBlackAlpha).toInt()
                            0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                        }
                    }
                    else -> continue
                }
                graphics.fill(ox + ti, oy + tj, ox + ti + 1, oy + tj + 1, argb)
            }
        }
    }

    private fun paintValueBar(graphics: GuiGraphicsExtractor) {
        val sx = x + 5 + 64 + 5
        val sy = y + 5
        for (row in 0 until 64) {
            val rgb = ItemCustomizeColorEvaluator.hsbToRgb(hue, saturation, (64 - row) / 64f)
            graphics.fill(sx, sy + row, sx + 10, sy + row + 1, opaque(rgb))
        }
    }

    private fun paintOpacityBar(graphics: GuiGraphicsExtractor, sx: Int) {
        val sy = y + 5
        val rgb = color.rgb
        for (row in 0 until 64) {
            val alpha = minOf(255, (64 - row) * 4)
            graphics.fill(sx, sy + row, sx + 10, sy + row + 1, (alpha shl 24) or rgb)
        }
    }

    private fun chromaHue(): Float {
        var h = hue + System.currentTimeMillis() % 100_000L / 100_000f
        h %= 1f
        if (h < 0) h += 1f
        return h
    }

    private fun hsvHueOfChroma(): Float = ItemCustomizeColorEvaluator.rgbToHsb(color.rgb)[0]

    private fun opaque(rgb: Int): Int = 0xFF000000.toInt() or rgb

    companion object {
        private const val DEFAULT_CHROMA = 200
    }
}
