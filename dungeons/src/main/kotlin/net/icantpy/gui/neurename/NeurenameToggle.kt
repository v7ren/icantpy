package net.icantpy.gui.neurename

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import kotlin.math.exp

/** 1:1 port of NEU's GuiElementBoolean (48x14 toggle with the sigmoid knob animation). */
internal class NeurenameToggle(
    var x: Int,
    var y: Int,
    value: Boolean,
    private val onToggle: (Boolean) -> Unit,
) {
    private var value: Boolean = value
    private var previewValue: Boolean = value
    private var animation: Int = if (value) 36 else 0
    private var lastMillis: Long = System.currentTimeMillis()

    fun setValue(next: Boolean) {
        value = next
        previewValue = next
    }

    fun render(graphics: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()
        val deltaMillis = now - lastMillis
        lastMillis = now
        var passedLimit = false
        if (previewValue != value) {
            if ((previewValue && animation > 12) || (!previewValue && animation < 24)) passedLimit = true
        }
        if (previewValue != passedLimit) {
            animation += (deltaMillis / 10).toInt()
        } else {
            animation -= (deltaMillis / 10).toInt()
        }
        lastMillis -= deltaMillis % 10

        if (previewValue == value) {
            animation = animation.coerceIn(0, 36)
        } else if (!passedLimit) {
            animation = if (previewValue) animation.coerceIn(0, 12) else animation.coerceIn(24, 36)
        } else {
            animation = if (previewValue) maxOf(12, animation) else minOf(24, animation)
        }

        val eased = (sigmoidZeroOne(animation / 36f) * 36f).toInt()
        val (button, bar) = buttonAndBar(eased)
        NeurenameDraw.texture(graphics, bar, x, y, WIDTH, HEIGHT)
        NeurenameDraw.texture(graphics, button, x + eased, y, 12, HEIGHT)
    }

    fun mousePressed(mouseX: Int, mouseY: Int, button: Int) {
        if (button != 0) return
        if (contains(mouseX, mouseY)) previewValue = !value
    }

    fun mouseReleased(mouseX: Int, mouseY: Int, button: Int) {
        if (button != 0) return
        if (contains(mouseX, mouseY)) {
            if (previewValue == !value) {
                value = !value
                onToggle(value)
            }
        } else {
            previewValue = value
        }
    }

    private fun contains(mouseX: Int, mouseY: Int): Boolean =
        mouseX > x && mouseX < x + WIDTH && mouseY > y && mouseY < y + HEIGHT

    private fun buttonAndBar(eased: Int): Pair<Identifier, Identifier> = when {
        eased < 3 -> NeurenameAssets.toggleOff to NeurenameAssets.bar
        eased < 13 -> NeurenameAssets.toggleOne to NeurenameAssets.barOne
        eased < 23 -> NeurenameAssets.toggleTwo to NeurenameAssets.barTwo
        eased < 33 -> NeurenameAssets.toggleThree to NeurenameAssets.barThree
        else -> NeurenameAssets.toggleOn to NeurenameAssets.barOn
    }

    companion object {
        const val WIDTH = 48
        const val HEIGHT = 14

        // NEU LerpUtils.sigmoidZeroOne
        private const val SIGMOID_STR = 8f
        private val SIGMOID_A = -1f / (sigmoid(-0.5f * SIGMOID_STR) - sigmoid(0.5f * SIGMOID_STR))
        private val SIGMOID_B = SIGMOID_A * sigmoid(-0.5f * SIGMOID_STR)

        private fun sigmoid(value: Float): Float = (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()

        fun sigmoidZeroOne(f: Float): Float {
            val clamped = f.coerceIn(0f, 1f)
            return SIGMOID_A * sigmoid(SIGMOID_STR * (clamped - 0.5f)) - SIGMOID_B
        }
    }
}
