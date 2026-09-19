package net.icantpy.gui.customize

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.CommonColors
import org.lwjgl.glfw.GLFW
import java.util.Locale

/** Hex ARGB text input, ported from Skyblocker's `ARGBTextInput`. */
class ARGBTextInput(x: Int, y: Int, private val textRenderer: Font, private val drawBackground: Boolean, private val hasAlpha: Boolean = false) :
    AbstractWidget(
        x, y,
        textRenderer.width(if (hasAlpha) "AAAAAAAA" else "AAAAAA") + (if (drawBackground) 6 else 0),
        10 + (if (drawBackground) 4 else 0),
        Component.nullToEmpty("ARGBTextInput"),
    ) {

    private val length = if (hasAlpha) 8 else 6
    private val alphaMask = if (hasAlpha) 0 else 0xFF000000.toInt()
    private var input = if (hasAlpha) "FFFFFFFF" else "FFFFFF"
    private var index = 0
    private var onChange: ((Int) -> Unit)? = null

    fun getARGBColor(): Int = parse(input) ?: CommonColors.WHITE

    fun setARGBColor(argb: Int) {
        input = String.format(if (hasAlpha) "%08X" else "%06X", argb and alphaMask.inv())
    }

    fun setOnChange(onChange: ((Int) -> Unit)?) {
        this.onChange = onChange
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val selectionStart = textRenderer.width(input.substring(0, index))
        val selectionEnd = textRenderer.width(input.substring(0, index + 1))
        val textX = getX() + (if (drawBackground) 3 else 0)
        val textY = getY() + (getHeight() - textRenderer.lineHeight) / 2
        if (drawBackground) {
            context.fill(getX(), getY(), getRight(), getBottom(), if (isFocused) CommonColors.WHITE else CommonColors.GRAY)
            context.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, CommonColors.BLACK)
        }
        if (isFocused) {
            context.fill(textX + selectionStart, textY, textX + selectionEnd, textY + textRenderer.lineHeight, 0xFF00BBFF.toInt())
            context.fill(textX + selectionStart, textY + textRenderer.lineHeight - 1, textX + selectionEnd, textY + textRenderer.lineHeight, CommonColors.WHITE)
        }
        context.text(textRenderer, input, textX, textY, CommonColors.WHITE, true)
        if (isHovered) context.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.IBEAM)
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    override fun keyPressed(keyInput: KeyEvent): Boolean {
        if (!isFocused) return false
        val handled = when (keyInput.key()) {
            GLFW.GLFW_KEY_DELETE -> {
                input = input.replaceRange(index, index + 1, "0")
                true
            }
            GLFW.GLFW_KEY_BACKSPACE -> {
                input = input.replaceRange(index, index + 1, "0")
                index = (index - 1).coerceAtLeast(0)
                true
            }
            GLFW.GLFW_KEY_LEFT -> {
                index = (index - 1).coerceAtLeast(0)
                true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                index = (index + 1).coerceAtMost(length - 1)
                true
            }
            else -> false
        }
        if (handled) {
            callOnChange()
            return true
        }
        if (keyInput.isCopy) {
            Minecraft.getInstance().keyboardHandler.setClipboard(input)
            return true
        }
        if (keyInput.isPaste) {
            var clipboard = Minecraft.getInstance().keyboardHandler.getClipboard()
            if (clipboard.startsWith("#")) clipboard = clipboard.substring(1)
            val s = clipboard.substring(0, minOf(if (hasAlpha) 8 else 6, clipboard.length))
            parse(s.uppercase(Locale.ENGLISH))?.let {
                setARGBColor(it)
                callOnChange()
            }
            return true
        }
        return super.keyPressed(keyInput)
    }

    override fun charTyped(inputEvent: CharacterEvent): Boolean {
        if (!isFocused) return false
        val s = inputEvent.codepointAsString()
        if (s.length == 1 && s[0].lowercaseChar() in HEXADECIMAL_CHARS) {
            input = input.replaceRange(index, index + 1, s.uppercase(Locale.ENGLISH))
            index = (index + 1).coerceAtMost(length - 1)
            callOnChange()
            return true
        }
        return super.charTyped(inputEvent)
    }

    private fun callOnChange() = onChange?.invoke(getARGBColor())

    override fun onClick(click: MouseButtonEvent, doubled: Boolean) {
        super.onClick(click, doubled)
        index = findClickedChar(click.x().toInt())
    }

    override fun onDrag(click: MouseButtonEvent, deltaX: Double, deltaY: Double) {
        super.onDrag(click, deltaX, deltaY)
        index = findClickedChar(click.x().toInt())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        val i = findClickedChar(mouseX.toInt()) / 2
        val begin = i * 2
        val parsed = input.substring(begin, begin + 2).toIntOrNull(16)
        if (parsed != null) {
            val prev = parsed
            val newInt = (prev + (if (verticalAmount > 0) 1 else -1)).coerceIn(0, 255)
            if (newInt != prev) {
                input = input.replaceRange(begin, begin + 2, String.format("%02X", newInt))
                callOnChange()
            }
        }
        return true
    }

    private fun findClickedChar(mouseX: Int): Int =
        textRenderer.plainSubstrByWidth(input, mouseX - getX() - (if (drawBackground) 3 else 0)).length.coerceIn(0, length - 1)

    private fun parse(value: String): Int? = try {
        Integer.parseUnsignedInt(value, 16) or alphaMask
    } catch (_: NumberFormatException) {
        null
    }

    private companion object {
        val FORMATTINGS = arrayOf(ChatFormatting.WHITE, ChatFormatting.RED, ChatFormatting.GREEN, ChatFormatting.BLUE)
        const val HEXADECIMAL_CHARS = "0123456789abcdef"
    }
}
