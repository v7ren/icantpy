package net.icantpy.gui.customize

import net.icantpy.gui.McUi
import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.CustomRenameText
import net.icantpy.cosmetics.items.RenameTextEditor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

/** Multiline item-description editor used by the inline item customizer. */
class DescriptionPopup(
    backgroundScreen: Screen,
    item: ItemStack,
    private val onSave: (String) -> Unit,
) : PopupScreen(backgroundScreen, Component.literal("Edit Description")) {
    private val initialText = descriptionOf(item)
    private var editor = RenameTextEditor().withText(initialText)
    private var scroll = 0
    private var textX = 0
    private var textY = 0
    private var textWidth = 0
    private var textBottom = 0
    private var cancelled = false

    override fun init() {
        addRenderableWidget(Button.builder(Component.literal("Cancel")) {
            cancelled = true
            onClose()
        }.width(75).build())
        addRenderableWidget(Button.builder(Component.literal("Done")) { onClose() }.width(75).build())
        super.init()
        repositionElements()
    }

    override fun repositionElements() {
        val panelWidth = (width - 40).coerceIn(240, 420)
        val panelHeight = (height - 40).coerceIn(150, 280)
        textX = (width - panelWidth) / 2 + 10
        textY = (height - panelHeight) / 2 + 28
        textWidth = panelWidth - 20
        textBottom = (height - panelHeight) / 2 + panelHeight - 28
        val buttons = children().filterIsInstance<Button>()
        buttons.getOrNull(0)?.setPosition(width / 2 - 80, textBottom + 8)
        buttons.getOrNull(1)?.setPosition(width / 2 + 5, textBottom + 8)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)
        drawPanel(graphics, textX - 10, textY - 28, textWidth + 20, textBottom - textY + 56)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val font = Minecraft.getInstance().font
        graphics.text(font, Component.literal("Edit Description"), textX, textY - 18, 0xFFFFFFFF.toInt(), true)
        graphics.fill(textX, textY, textX + textWidth, textBottom, 0xFF08080B.toInt())
        graphics.enableScissor(textX, textY, textX + textWidth, textBottom)
        // Match the old rename tooltip editor: formatting codes are rendered, not exposed as
        // raw control characters. The stored text still keeps the codes for editing and saving.
        val lines = editor.text.split('\n')
        val visibleLines = ((textBottom - textY - 6) / font.lineHeight).coerceAtLeast(1)
        scroll = scroll.coerceIn(0, (lines.size - visibleLines).coerceAtLeast(0))
        for (row in 0 until visibleLines) {
            val line = lines.getOrNull(scroll + row) ?: break
            graphics.text(font, CustomRenameText.component(line.ifEmpty { " " }, net.minecraft.network.chat.Style.EMPTY), textX + 4, textY + 3 + row * font.lineHeight, 0xFFFFFFFF.toInt(), false)
        }
        val (cursorLine, cursorColumn) = cursorLineColumn(lines)
        if (cursorLine in scroll until scroll + visibleLines) {
            val before = lines[cursorLine].take(cursorColumn)
            val cursorX = textX + 4 + font.width(CustomRenameText.component(before, net.minecraft.network.chat.Style.EMPTY))
            val cursorY = textY + 2 + (cursorLine - scroll) * font.lineHeight
            graphics.fill(cursorX, cursorY, cursorX + 1, cursorY + font.lineHeight, 0xFFFFFFFF.toInt())
        }
        graphics.disableScissor()
    }

    override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && click.x() in textX.toDouble()..(textX + textWidth).toDouble() && click.y() in textY.toDouble()..textBottom.toDouble()) {
            val lines = editor.text.split('\n')
            val font = Minecraft.getInstance().font
            val visible = ((textBottom - textY - 6) / font.lineHeight).coerceAtLeast(1)
            val row = ((click.y().toInt() - textY - 2) / font.lineHeight).coerceIn(0, visible - 1)
            val lineIndex = (scroll + row).coerceAtMost(lines.lastIndex.coerceAtLeast(0))
            val line = lines[lineIndex]
            val targetX = click.x().toInt() - textX - 4
            var column = 0
            while (column < line.length && font.width(CustomRenameText.component(line.substring(0, column + 1), net.minecraft.network.chat.Style.EMPTY)) <= targetX) column++
            var offset = 0
            repeat(lineIndex) { offset += lines[it].length + 1 }
            editor = editor.moveTo((offset + column).coerceIn(0, editor.text.length), false)
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll = (scroll + if (verticalAmount < 0) 1 else -1).coerceAtLeast(0)
        return true
    }

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        val shift = (event.modifiers() and GLFW.GLFW_MOD_SHIFT) != 0
        editor = when (event.key()) {
            GLFW.GLFW_KEY_ESCAPE -> { cancelled = true; onClose(); return true }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> editor.insert("\n")
            GLFW.GLFW_KEY_BACKSPACE -> editor.backspace()
            GLFW.GLFW_KEY_DELETE -> editor.delete()
            GLFW.GLFW_KEY_LEFT -> editor.moveHorizontal(-1, shift)
            GLFW.GLFW_KEY_RIGHT -> editor.moveHorizontal(1, shift)
            GLFW.GLFW_KEY_HOME -> editor.moveHome(shift)
            GLFW.GLFW_KEY_END -> editor.moveEnd(shift)
            GLFW.GLFW_KEY_A -> if (control(event)) editor.selectAll() else return super.keyPressed(event)
            GLFW.GLFW_KEY_C -> if (control(event)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(editor.selectedText())
                return true
            } else return super.keyPressed(event)
            GLFW.GLFW_KEY_V -> if (control(event)) editor.insert(Minecraft.getInstance().keyboardHandler.clipboard.replace("\r", "")) else return super.keyPressed(event)
            else -> return super.keyPressed(event)
        }
        return true
    }

    override fun charTyped(event: net.minecraft.client.input.CharacterEvent): Boolean {
        if (event.codepoint() < 32) return true
        editor = editor.insert(Character.toString(event.codepoint()))
        return true
    }

    override fun onClose() {
        if (!cancelled) onSave(normalize(editor.text))
        McUi.setScreen(minecraft, backgroundScreen)
    }

    private fun cursorLineColumn(lines: List<String>): Pair<Int, Int> {
        var remaining = editor.cursor
        for ((index, line) in lines.withIndex()) {
            if (remaining <= line.length) return index to remaining
            remaining -= line.length + 1
        }
        return lines.lastIndex.coerceAtLeast(0) to (lines.lastOrNull()?.length ?: 0)
    }

    private fun control(event: net.minecraft.client.input.KeyEvent): Boolean =
        (event.modifiers() and GLFW.GLFW_MOD_CONTROL) != 0

    private fun normalize(raw: String): String = raw
        .replace("&&", "§")
        .replace("**", CustomRenameText.MASTER_STAR_GLYPH.toString())
        .replace(Regex("\\*([1-9])")) { match ->
            (CustomRenameText.MASTER_STAR_FIRST.code + (match.groupValues[1][0].code - '1'.code)).toChar().toString()
        }

    private companion object {
        fun descriptionOf(item: ItemStack): String {
            CustomRename.localAppearance(item)?.customTooltip?.takeIf { it.isNotBlank() }?.let { return it }
            return item.components.get(DataComponents.LORE)
                ?.lines?.joinToString("\n") { CustomRenameText.toLegacy(it) }.orEmpty()
        }
    }
}
