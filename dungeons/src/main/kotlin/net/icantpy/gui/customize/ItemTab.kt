package net.icantpy.gui.customize

import net.icantpy.gui.McUi
import net.icantpy.cosmetics.items.CustomRename
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.components.tabs.GridLayoutTab
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.layouts.SpacerElement
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.joml.Matrix3x2fStack

/** Item tab, ported from Skyblocker's `ItemTab`. */
class ItemTab(private val parentScreen: CustomizeScreen) : GridLayoutTab(Component.literal("Item")) {

    private val nameWidget = CustomizeNameWidget()
    private val glintButton: Button
    private val modelField: IdentifierTextField
    private var currentItem: ItemStack = ItemStack.EMPTY
    private var glintState = 0

    init {
        layout.spacing(5)
        glintButton = Button.builder(Component.empty()) { b ->
            glintState = (glintState + 1) % 3
            b.setMessage(getGlintText())
            val identity = CustomRename.identity(currentItem) ?: return@builder
            CustomRename.edit(identity) { data ->
                when (glintState) {
                    1 -> data.copy(overrideEnchantGlint = true, enchantGlintValue = true)
                    2 -> data.copy(overrideEnchantGlint = true, enchantGlintValue = false)
                    else -> data.copy(overrideEnchantGlint = false, enchantGlintValue = false)
                }
            }
        }.width(Button.SMALL_WIDTH).build()

        modelField = IdentifierTextField(120, 20) { identifier ->
            val identity = CustomRename.identity(currentItem) ?: return@IdentifierTextField
            CustomRename.edit(identity) { it.copy(customItemModel = identifier?.toString()) }
        }
        modelField.setHint(Component.literal("Item model override").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY))

        layout.addChild(ItemSelector(), 0, 0)
        layout.addChild(BackgroundRenderer(), 0, 1)

        val linear = layout.addChild(LinearLayout.vertical(), 0, 1) { it.alignHorizontallyRight().paddingRight(3).paddingVertical(3) }
        linear.addChild(glintButton)
        linear.addChild(modelField)

        layout.addChild(nameWidget, 1, 0, 1, 2)

        val player = Minecraft.getInstance().player
        val stack = player?.let { p ->
            sequenceOf(p.mainHandItem).plus(p.inventory.getNonEquipmentItems())
                .firstOrNull { CustomRename.identity(it) != null } ?: ItemStack.EMPTY
        } ?: ItemStack.EMPTY
        if (CustomRename.identity(stack) != null) {
            setCurrentItem(stack)
        } else {
            visitChildren { it.visible = false }
            layout.addChild(StringWidget(Component.literal("Nothing customizable."), Minecraft.getInstance().font), 0, 0, 3, 2) {
                it.alignHorizontallyCenter().alignVerticallyMiddle()
            }
        }
    }

    private fun setCurrentItem(itemStack: ItemStack) {
        currentItem = itemStack
        val empty = CustomRename.identity(itemStack) == null
        visitChildren { it.visible = !empty }
        if (empty) return
        parentScreen.backupConfigs(itemStack)
        nameWidget.setItem(itemStack)
        modelField.setValue(CustomRename.localAppearance(itemStack)?.customItemModel.orEmpty())
        glintState = when (CustomRename.customGlintOverride(itemStack)) {
            true -> 1
            false -> 2
            null -> 0
        }
        glintButton.setMessage(getGlintText())
    }

    private fun getGlintText(): Component {
        val state = when (glintState) {
            1 -> "On"
            2 -> "Off"
            else -> "Default"
        }
        return Component.literal("Glint: $state")
    }

    private inner class ItemSelector : AbstractContainerWidget(0, 20, 0, 0, Component.literal("Item Selector"), AbstractScrollArea.defaultSettings(8)) {
        private val selectItemButton: Button
        private val layoutWidget = LinearLayout.vertical().spacing(5)

        init {
            layoutWidget.addChild(SpacerElement.height(32 + ADDED_ITEM_OFFSET))
            selectItemButton = layoutWidget.addChild(Button.builder(Component.literal("Select Item")) {
                McUi.setScreen(Minecraft.getInstance(), ItemSelectPopup(parentScreen, ::setCurrentItem))
            }.width(Button.SMALL_WIDTH).build())
            layoutWidget.arrangeElements()
            layoutWidget.setPosition(PADDING, PADDING)
            setSize(layoutWidget.getWidth() + PADDING * 2, layoutWidget.getHeight() + PADDING * 2)
        }

        override fun setX(x: Int) {
            super.setX(x)
            layoutWidget.setX(getX() + PADDING)
        }

        override fun setY(y: Int) {
            super.setY(y)
            layoutWidget.setY(getY() + PADDING)
        }

        override fun children(): List<GuiEventListener> = listOf(selectItemButton)

        override fun contentHeight(): Int = 0

        override fun scrollRate(): Double = 0.0

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            CustomizeDraw.panel(graphics, getX(), getY(), getWidth(), getHeight())
            val matrices: Matrix3x2fStack = graphics.pose()
            matrices.pushMatrix()
            val x = layoutWidget.getX() + layoutWidget.getWidth() / 2f - 16
            val y = layoutWidget.getY() + ADDED_ITEM_OFFSET
            if (mouseX >= x && mouseX < x + 32 && mouseY >= y && mouseY < y + 32) {
                graphics.setTooltipForNextFrame(currentItem.hoverName, mouseX, mouseY)
            }
            matrices.translate(x, y.toFloat())
            matrices.scale(2f)
            graphics.item(currentItem, 0, 0)
            matrices.popMatrix()
            selectItemButton.extractRenderState(graphics, mouseX, mouseY, a)
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private inner class BackgroundRenderer : AbstractWidget(0, 0, 0, 0, Component.empty()) {
        init {
            active = false
        }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            val x = glintButton.getX() - 3
            val y = glintButton.getY() - 3
            CustomizeDraw.panel(graphics, x, y, modelField.getRight() + 3 - x, modelField.getBottom() + 3 - y)
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private companion object {
        const val PADDING = 3
        const val ADDED_ITEM_OFFSET = 7
    }
}
