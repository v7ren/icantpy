package net.icantpy.gui.customize

import net.icantpy.gui.McUi
import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.CustomRenameText
import net.icantpy.cosmetics.items.ItemIdentity
import net.icantpy.cosmetics.items.ItemCustomizeColor
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/** Item name editor, ported from Skyblocker's `CustomizeNameWidget` using icantpy's text model. */
class CustomizeNameWidget : AbstractContainerWidget(0, 0, 0, 0, Component.literal("Customize Item Name"), AbstractScrollArea.defaultSettings(4)) {

    private val textField: EditBox
    private val previewWidget: StringWidget
    private val widgets = mutableListOf<AbstractWidget>()
    private val grid = GridLayout()
    private var identity: ItemIdentity? = null
    private var currentItem: ItemStack = ItemStack.EMPTY

    init {
        textField = EditBox(Minecraft.getInstance().font, 320, 20, Component.literal("Name"))
        textField.setMaxLength(CustomRenameText.MAX_LENGTH)
        textField.setResponder { onTextChanged(it) }
        grid.addChild(textField, 1, 0, 1, 20)
        widgets.add(textField)

        addFormattingButtons()
        addColorButtons()

        val customColor = Button.builder(Component.literal("Custom Color")) {
            McUi.setScreen(Minecraft.getInstance(), ColorPopup.create(McUi.currentScreen(Minecraft.getInstance())!!, ::insertColor))
        }.size(48, 16).build()
        grid.addChild(customColor, 2, 17, 1, 3)
        widgets.add(customColor)
        val gradient = Button.builder(Component.literal("Gradient")) {
            McUi.setScreen(Minecraft.getInstance(), ColorPopup.createGradient(McUi.currentScreen(Minecraft.getInstance())!!, ::insertGradient))
        }.size(48, 16).build()
        grid.addChild(gradient, 3, 17, 1, 3)
        widgets.add(gradient)

        val description = Button.builder(Component.literal("Description")) { openDescriptionEditor() }.size(92, 16).build()
        grid.addChild(description, 6, 0, 1, 7)
        widgets.add(description)
        val glintColor = Button.builder(Component.literal("Glint Color")) { openGlintColorPicker() }.size(92, 16).build()
        grid.addChild(glintColor, 6, 7, 1, 7)
        widgets.add(glintColor)
        val resetGlintColor = Button.builder(Component.literal("Reset Glint")) { resetGlintColor() }.size(92, 16).build()
        grid.addChild(resetGlintColor, 6, 14, 1, 6)
        widgets.add(resetGlintColor)
        val resetText = Button.builder(Component.literal("Reset Name & Description")) { resetNameAndDescription() }
            .size(200, 16)
            .build()
        resetText.setTooltip(Tooltip.create(Component.literal("Clear this item's custom name and description. Other cosmetics stay.")))
        grid.addChild(resetText, 7, 0, 1, 13)
        widgets.add(resetText)

        grid.addChild(StringWidget(320, Minecraft.getInstance().font.lineHeight, Component.literal("Tip: select text then click a style/colour.").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY), Minecraft.getInstance().font), 4, 0, 1, 20, LayoutSettings.defaults().paddingTop(2))
        previewWidget = StringWidget(320, Minecraft.getInstance().font.lineHeight, Component.empty(), Minecraft.getInstance().font)
        grid.addChild(previewWidget, 5, 0, 1, 20, LayoutSettings.defaults().paddingVertical(2).alignHorizontallyCenter())
        widgets.add(previewWidget)

        grid.arrangeElements()
        grid.setPosition(getX() + PADDING, getY() + PADDING)
        setSize(grid.getWidth() + PADDING * 2, grid.getHeight() + PADDING * 2)
    }

    private fun addFormattingButtons() {
        val entries = listOf(
            'l' to (ChatFormatting.BOLD to "B"),
            'o' to (ChatFormatting.ITALIC to "I"),
            'n' to (ChatFormatting.UNDERLINE to "U"),
            'm' to (ChatFormatting.STRIKETHROUGH to "S"),
            'k' to (ChatFormatting.OBFUSCATED to "|||"),
        )
        entries.forEachIndexed { i, (code, pair) ->
            val (format, label) = pair
            val button = FormatButton(Component.literal(label).withStyle(format), format) { insertCode(code) }
            grid.addChild(button, 0, i)
            widgets.add(button)
        }
    }

    private fun addColorButtons() {
        var index = 0
        for (code in "0123456789abcdef") {
            val format = ChatFormatting.getByCode(code) ?: continue
            if (TextColor.fromLegacyFormat(format) != null) {
                val button = ColorButton(format) { insertCode(code) }
                grid.addChild(button, 2, index++)
                widgets.add(button)
            }
        }
    }

    private fun onTextChanged(value: String) {
        val id = identity ?: return
        val stored = CustomRenameText.normalize(value)
        val prefix = CustomRename.editorState(currentItem)?.baseline?.originalNamePrefix.orEmpty()
        CustomRename.edit(id) { data ->
            data.copy(
                customName = stored.takeIf { it.isNotBlank() },
                customNamePrefix = if (stored.isBlank()) "" else prefix,
            )
        }
        previewWidget.setMessage(CustomRenameText.component(stored, Style.EMPTY))
    }

    private fun insertCode(code: Char) = insertAtCursor("§$code")

    private fun insertColor(rgb: Int) = insertAtCursor(hexColorCode(rgb))

    private fun insertGradient(start: Int, end: Int) {
        // Simplest: apply start colour to the selection/insert position; a full gradient is available in the NEU editor.
        insertColor(start)
    }

    private fun hexColorCode(rgb: Int): String {
        val sb = StringBuilder("§x")
        val digits = "0123456789abcdef"
        for (shift in intArrayOf(20, 16, 12, 8, 4, 0)) {
            sb.append('§').append(digits[(rgb shr shift) and 0xF])
        }
        return sb.toString()
    }

    private fun insertAtCursor(text: String) {
        val field = textField
        val pos = field.getCursorPosition()
        val current = field.getValue()
        field.setValue(current.substring(0, pos) + text + current.substring(pos))
        field.setCursorPosition(pos + text.length)
        onTextChanged(field.getValue())
    }

    override fun setX(x: Int) {
        super.setX(x)
        grid.setX(getX() + PADDING)
    }

    override fun setY(y: Int) {
        super.setY(y)
        grid.setY(getY() + PADDING)
    }

    fun setItem(stack: ItemStack) {
        currentItem = stack
        identity = CustomRename.identity(stack)
        val stored = CustomRename.localAppearance(stack)?.customName.orEmpty()
        textField.setValue(stored)
        previewWidget.setMessage(CustomRenameText.component(stored, Style.EMPTY))
    }

    private fun openDescriptionEditor() {
        val screen = McUi.currentScreen(Minecraft.getInstance()) ?: return
        McUi.setScreen(Minecraft.getInstance(), DescriptionPopup(screen, currentItem) { value ->
            val id = CustomRename.identity(currentItem) ?: identity ?: return@DescriptionPopup
            CustomRename.edit(id) { it.copy(customTooltip = value.takeIf { text -> text.isNotBlank() }) }
        })
    }

    private fun openGlintColorPicker() {
        val screen = McUi.currentScreen(Minecraft.getInstance()) ?: return
        McUi.setScreen(Minecraft.getInstance(), ColorPopup.create(screen) { color ->
            if (color < 0) return@create
            val id = identity ?: return@create
            CustomRename.edit(id) { it.copy(customGlintColor = ItemCustomizeColor(color)) }
        })
    }

    private fun resetGlintColor() {
        val id = identity ?: return
        CustomRename.edit(id) { it.copy(customGlintColor = null) }
    }

    private fun resetNameAndDescription() {
        val id = identity ?: CustomRename.identity(currentItem) ?: return
        CustomRename.resetNameAndDescription(id)
        textField.setValue("")
        previewWidget.setMessage(Component.empty())
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        CustomizeDraw.panel(graphics, getX(), getY(), getWidth(), getHeight())
        for (widget in widgets) widget.extractRenderState(graphics, mouseX, mouseY, deltaTicks)
    }

    override fun children(): List<GuiEventListener> = widgets

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    override fun contentHeight(): Int = 0

    override fun scrollRate(): Double = 0.0

    private inner class FormatButton(message: Component, private val format: ChatFormatting, private val action: () -> Unit) :
        AbstractButton(0, 0, 16, 16, message) {
        init {
            setTooltip(Tooltip.create(message))
        }

        override fun onPress(input: InputWithModifiers) = action()

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            extractDefaultSprite(graphics)
            extractDefaultLabel(graphics.textRenderer())
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private inner class ColorButton(private val format: ChatFormatting, private val action: () -> Unit) :
        AbstractButton(0, 0, 16, 16, Component.empty()) {
        private val intColor: Int = TextColor.fromLegacyFormat(format)!!.value

        init {
            setTooltip(Tooltip.create(Component.literal(format.name)))
        }

        override fun onPress(input: InputWithModifiers) = action()

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            extractDefaultSprite(graphics)
            graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2, 0xFF000000.toInt() or intColor)
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private companion object {
        const val PADDING = 3
    }
}
