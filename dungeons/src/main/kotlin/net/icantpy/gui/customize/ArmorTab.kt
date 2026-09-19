package net.icantpy.gui.customize

import net.icantpy.cosmetics.items.CustomRename
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.components.tabs.GridLayoutTab
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerModelPart
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.time.Duration

/** Armour tab, ported from Skyblocker's `ArmorTab`. */
class ArmorTab(private val parent: CustomizeScreen) : GridLayoutTab(Component.literal("Armor")) {

    private val armor = arrayOfNulls<ItemStack>(4)
    private var selectedSlot = 0
    private val trimSelectionWidget: TrimSelectionWidget
    private val colorSelectionWidget: ColorSelectionWidget
    private val headSelectionWidget: HeadSelectionWidget
    private val modelFieldContainer: ModelFieldContainer
    private var nothingCustomizable = false

    private val player: RemotePlayer = object : RemotePlayer(CLIENT.level!!, CLIENT.gameProfile) {
        override fun isInvisibleTo(player: Player) = true
        override fun onEquipItem(slot: EquipmentSlot, oldStack: ItemStack, newStack: ItemStack) {}
        override fun isModelPartShown(modelPart: PlayerModelPart): Boolean =
            modelPart != PlayerModelPart.CAPE && CLIENT.options.isModelPartEnabled(modelPart)

        override fun getId(): Int = PLACEHOLDER_ID
    }

    init {
        layout.rowSpacing(PADDING / 2).columnSpacing(PADDING)

        val list = ARMOR_SLOTS.map { CLIENT.player?.getItemBySlot(it)?.copy() ?: ItemStack.EMPTY }
        for (i in list.indices) {
            armor[3 - i] = list[i]
            player.setItemSlot(ARMOR_SLOTS[i], list[i])
        }
        while (selectedSlot < armor.size - 1 && !canEdit(armor[selectedSlot]!!)) selectedSlot++
        nothingCustomizable = !canEdit(armor[selectedSlot]!!)

        val vertical = LinearLayout.vertical().spacing(1)
        vertical.addChild(PlayerWidget(0, 0, 84, 165, player))
        vertical.addChild(PieceSelectionWidget(0, 0))
        layout.addChild(vertical, 0, 0, 2, 1, LayoutSettings::alignVerticallyMiddle)

        val width = minOf(444, parent.width) - PLAYER_WIDGET_WIDTH - PADDING * 3
        headSelectionWidget = HeadSelectionWidget(0, 0, width, 165)
        layout.addChild(headSelectionWidget, 0, 1, 2, 1, LayoutSettings::alignVerticallyMiddle)

        val layoutWidget = LinearLayout.horizontal().spacing(PADDING / 2)
        val containerWidth = (width * (1f / 3f)).toInt()
        trimSelectionWidget = TrimSelectionWidget(0, 0, width - containerWidth - PADDING / 2, 80)
        modelFieldContainer = layoutWidget.addChild(ModelFieldContainer(containerWidth, 80))
        layoutWidget.addChild(trimSelectionWidget)
        layoutWidget.arrangeElements()
        layout.addChild(layoutWidget, 0, 1)

        colorSelectionWidget = ColorSelectionWidget(0, 0, width, 100, Minecraft.getInstance().font)
        layout.addChild(colorSelectionWidget, 1, 1)

        if (nothingCustomizable) {
            layout.addChild(StringWidget(Component.literal("Nothing customizable."), Minecraft.getInstance().font), 0, 1, 2, 1) {
                it.alignVerticallyMiddle().alignHorizontallyCenter()
            }
        }
        updateWidgets()
    }

    private fun canEdit(stack: ItemStack): Boolean {
        if (CustomRename.identity(stack) == null) return false
        if (stack.`is`(Items.PLAYER_HEAD)) return true
        return stack.`is`(ItemTags.TRIMMABLE_ARMOR)
    }

    private fun updateWidgets() {
        if (nothingCustomizable) {
            headSelectionWidget.visible = false
            trimSelectionWidget.visible = false
            colorSelectionWidget.visible = false
            modelFieldContainer.visible = false
            return
        }
        val item = armor[selectedSlot]!!
        parent.backupConfigs(item)
        val isPlayerHead = item.`is`(Items.PLAYER_HEAD)
        headSelectionWidget.setCurrentItem(item)
        trimSelectionWidget.setCurrentItem(item)
        colorSelectionWidget.setCurrentItem(item)
        headSelectionWidget.visible = isPlayerHead
        trimSelectionWidget.visible = !isPlayerHead
        colorSelectionWidget.visible = !isPlayerHead
        modelFieldContainer.visible = !isPlayerHead
        val model = CustomRename.localAppearance(item)?.customArmorModel.orEmpty()
        modelFieldContainer.field.setValue(model)
    }

    fun tick() {
        player.tickCount++
        HeadTextures.tick()
    }

    override fun doLayout(tabArea: ScreenRectangle) {
        val width = minOf(444, tabArea.width()) - PLAYER_WIDGET_WIDTH - PADDING * 3
        headSelectionWidget.setWidth(width)
        val modelFieldWidth = (width * (1f / 3f)).toInt()
        trimSelectionWidget.setWidth(width - modelFieldWidth - PADDING / 2)
        modelFieldContainer.setWidth(modelFieldWidth)
        colorSelectionWidget.setWidth(width)
        super.doLayout(tabArea)
    }

    private inner class PieceSelectionWidget(x: Int, y: Int) : AbstractWidget(x, y, 84, 24, Component.nullToEmpty("")) {
        private val selectable = armor.map { canEdit(it!!) }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            CustomizeDraw.miniHotbar(graphics, getX() + 1, getY() + 1, 82, 22)
            val localX = mouseX - getX() - 2
            val localY = mouseY - getY() - 2
            val hoveredSlot = if (localY in 0 until 20) (localX / 20).takeIf { it in 0 until armor.size } ?: -1 else -1
            if (hoveredSlot in 0 until 4 && selectable[hoveredSlot]) {
                val i = getX() + 2 + hoveredSlot * 20
                graphics.fill(i, getY() + 2, i + 20, getY() + 22, 0x20FFFFFF)
            }
            for (i in armor.indices) {
                val stack = armor[i] ?: ItemStack.EMPTY
                if (!stack.isEmpty) graphics.item(stack, getX() + 4 + i * 20, getY() + 4)
                if (!selectable[i] && !stack.isEmpty) graphics.item(BARRIER, getX() + 4 + i * 20, getY() + 4)
            }
            CustomizeDraw.slotSelection(graphics, getX() + selectedSlot * 20, getY(), 24, 24)
            handleCursor(graphics)
        }

        override fun onClick(click: MouseButtonEvent, doubled: Boolean) {
            val localX = click.x() - getX() - 2
            val localY = click.y() - getY() - 2
            if (localY < 0 || localY >= 20) return
            val i = (localX / 20).toInt()
            if (i < 0 || i >= armor.size || !selectable[i]) return
            if (i != selectedSlot) {
                selectedSlot = i
                updateWidgets()
            }
        }

        override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean =
            active && visible && mouseX >= getX() + 2 && mouseY >= getY() + 2 && mouseX < getRight() - 2 && mouseY < getBottom() - 2

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private inner class ModelFieldContainer(width: Int, height: Int) :
        AbstractContainerWidget(0, 0, width, height, Component.empty(), AbstractScrollArea.defaultSettings(4)) {

        private val text = Component.literal("Model Override").withStyle(net.minecraft.ChatFormatting.ITALIC).withStyle(net.minecraft.ChatFormatting.GRAY)
        private val containerLayout = FrameLayout()
        val field: IdentifierTextField

        init {
            field = containerLayout.addChild(IdentifierTextField(width - 10, 20) { identifier ->
                val identity = CustomRename.identity(armor[selectedSlot]!!) ?: return@IdentifierTextField
                CustomRename.edit(identity) { it.copy(customArmorModel = identifier?.toString()) }
                colorSelectionWidget.refresh()
            })
            containerLayout.arrangeElements()
            field.setTooltip(Tooltip.create(Component.literal("Override the armour model (identifier).")))
            field.setTooltipDelay(Duration.ofMillis(400))
        }

        override fun setX(x: Int) {
            super.setX(x)
            FrameLayout.alignInDimension(getX(), getWidth(), containerLayout.getWidth(), { containerLayout.setX(it) }, 0.5f)
        }

        override fun setWidth(width: Int) {
            super.setWidth(width)
            containerLayout.setMinWidth(width)
            containerLayout.arrangeElements()
            field.setWidth(width - 10)
            setX(getX())
        }

        override fun setY(y: Int) {
            super.setY(y)
            FrameLayout.alignInDimension(getY(), getHeight(), containerLayout.getHeight(), { containerLayout.setY(it) }, 0.5f)
        }

        override fun children(): List<GuiEventListener> = if (visible) listOf(field) else emptyList()

        override fun contentHeight(): Int = 0

        override fun scrollRate(): Double = 0.0

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            if (!visible) return
            CustomizeDraw.panel(graphics, getX(), getY(), getWidth(), getHeight())
            field.extractRenderState(graphics, mouseX, mouseY, a)
            val drawer = graphics.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.NONE)
            val padding = 5
            val startY = getY() + padding
            drawer.acceptScrollingWithDefaultCenter(text, getX() + padding, getRight() - padding, startY, startY + 9)
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private companion object {
        val CLIENT: Minecraft = Minecraft.getInstance()
        const val PLAYER_WIDGET_WIDTH = 84
        const val PADDING = 10
        val ARMOR_SLOTS: List<EquipmentSlot> = EquipmentSlot.VALUES.filter { it.type == EquipmentSlot.Type.HUMANOID_ARMOR }
        val BARRIER: ItemStack = ItemStack(Items.BARRIER)
        const val PLACEHOLDER_ID = -1
    }
}
