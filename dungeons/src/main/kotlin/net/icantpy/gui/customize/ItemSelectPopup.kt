package net.icantpy.gui.customize

import net.icantpy.cosmetics.items.CustomRename
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.layouts.SpacerElement
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Inventory popup for choosing which item to customize, ported from Skyblocker's `ItemSelectPopup`. */
class ItemSelectPopup(backgroundScreen: Screen, private val callback: (ItemStack) -> Unit) :
    PopupScreen(backgroundScreen, Component.literal("Select Item")) {

    private val mainGrid = GridLayout()
    private val equipmentLayout = LinearLayout.horizontal()
    private var x = 0
    private var y = 0

    override fun init() {
        super.init()
        val player = minecraft.player ?: return
        val adder = mainGrid.createRowHelper(9)
        val stacks = player.inventory.getNonEquipmentItems()
        for (i in 9 until stacks.size + 9) {
            val stack = stacks[i % stacks.size]
            if (stack.isEmpty) adder.addChild(SpacerElement(18, 18)) else adder.addChild(ItemWidget(stack))
        }
        listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).forEach { slot ->
            val stack = player.getItemBySlot(slot)
            if (stack.isEmpty) equipmentLayout.addChild(SpacerElement(18, 18)) else equipmentLayout.addChild(ItemWidget(stack))
        }
        equipmentLayout.addChild(SpacerElement(18, 18))
        mainGrid.visitWidgets { addRenderableWidget(it) }
        mainGrid.arrangeElements()
        equipmentLayout.visitWidgets { addRenderableWidget(it) }
        equipmentLayout.arrangeElements()
        repositionElements()
    }

    override fun repositionElements() {
        x = (width - TEXTURE_WIDTH) / 2
        y = (height - TEXTURE_HEIGHT) / 2
        mainGrid.setPosition(x + 7, y + 53)
        equipmentLayout.setPosition(x + 7, y + 17)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractBackground(graphics, mouseX, mouseY, a)
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x, y, 0f, 0f, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT)
    }

    private inner class ItemWidget(item: ItemStack) : AbstractWidget(0, 0, 18, 18, item.hoverName) {
        private val selectable = CustomRename.identity(item) != null
        private val itemStack = item

        init {
            val message = if (selectable) getMessage() else getMessage().copy().append("\n").append(Component.literal("Cannot customize this item.").withStyle(ChatFormatting.RED))
            setTooltip(Tooltip.create(message))
        }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            val ix = getX() + 1
            val iy = getY() + 1
            if (isHovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK, getX() - 3, getY() - 3, 24, 24)
            graphics.item(itemStack, ix, iy)
            if (isHovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT, getX() - 3, getY() - 3, 24, 24)
            if (!selectable) graphics.item(BARRIER, ix, iy)
        }

        override fun onClick(click: MouseButtonEvent, doubled: Boolean) {
            if (selectable) {
                callback(itemStack)
                onClose()
            }
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private companion object {
        val BACKGROUND_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("icantpy", "custom/inventory_item_selection")
        val SLOT_HIGHLIGHT_BACK: Identifier = Identifier.withDefaultNamespace("container/slot_highlight_back")
        val SLOT_HIGHLIGHT_FRONT: Identifier = Identifier.withDefaultNamespace("container/slot_highlight_front")
        val BARRIER: ItemStack = ItemStack(Items.BARRIER)
        const val TEXTURE_WIDTH = 176
        const val TEXTURE_HEIGHT = 132
    }
}
