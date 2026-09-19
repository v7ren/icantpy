package net.icantpy.gui.customize

import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.ItemIdentity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/** Trim pattern/material selector, ported from Skyblocker's `TrimSelectionWidget`. */
class TrimSelectionWidget(x: Int, y: Int, width: Int, height: Int) :
    AbstractContainerWidget(x, y, width, height, Component.nullToEmpty("Trim Selection"), AbstractScrollArea.defaultSettings(8)) {

    private val patternButtons = mutableListOf<TrimElementButton.Pattern>()
    private val materialButtons = mutableListOf<TrimElementButton>()
    private val children = mutableListOf<AbstractWidget>()
    private var layout: FrameLayout = FrameLayout()

    private var currentIdentity: ItemIdentity? = null
    private var selectedPattern: Identifier? = null
    private var selectedMaterial: Identifier? = null

    init {
        val lookup = Minecraft.getInstance().level?.registryAccess()
        if (lookup != null) {
            val none = TrimElementButton.Pattern(null, null, ::onClickPattern).apply { message = Component.translatable("gui.none") }
            patternButtons.add(none)
            lookup.lookupOrThrow(Registries.TRIM_PATTERN).listElements().toList()
                .sortedBy { it.value().description().string }
                .forEach { ref -> patternButtons.add(TrimElementButton.Pattern(ref.key().identifier(), ref.value(), ::onClickPattern)) }
            children.addAll(patternButtons)

            lookup.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().toList()
                .sortedBy { it.value().description().string }
                .forEach { ref -> materialButtons.add(TrimElementButton.Material(ref.key().identifier(), ref.value(), ::onClickMaterial)) }
            children.addAll(materialButtons)
        }
        positionButtons(width, height)
        layout.setPosition(x + PADDING, y + PADDING)
    }

    private fun positionButtons(width: Int, height: Int) {
        var buttonsPerRow = (width - 9) / 20
        var patternButtonsPerRow = minOf((buttonsPerRow + 1) / 2, MAX_BUTTONS_PER_ROW_PATTERN)
        var materialButtonsPerRow = minOf(buttonsPerRow / 2, MAX_BUTTONS_PER_ROW_MATERIAL)

        val maxHeight = getHeight() - PADDING * 2
        val overflow = (patternButtons.size / patternButtonsPerRow.coerceAtLeast(1) + 1) * 20 > maxHeight ||
            (materialButtons.size / materialButtonsPerRow.coerceAtLeast(1) + 1) * 20 > maxHeight
        if (overflow) {
            buttonsPerRow = (width - 15) / 20
            patternButtonsPerRow = minOf((buttonsPerRow + 1) / 2, MAX_BUTTONS_PER_ROW_PATTERN)
            materialButtonsPerRow = minOf(buttonsPerRow / 2, MAX_BUTTONS_PER_ROW_MATERIAL)
        }

        val patternsGrid = GridLayout()
        val patternAdder = patternsGrid.createRowHelper(patternButtonsPerRow.coerceAtLeast(1))
        patternButtons.forEach { patternAdder.addChild(it) }

        val materialsGrid = GridLayout()
        for (i in materialButtons.indices) {
            val row = i / materialButtonsPerRow.coerceAtLeast(1)
            val column = materialButtonsPerRow - (i % materialButtonsPerRow.coerceAtLeast(1)) - 1
            materialsGrid.addChild(materialButtons[i], row, column)
        }

        layout = FrameLayout(width - PADDING * 2 - (if (overflow) 6 else 0), height - PADDING * 2)
        layout.defaultChildLayoutSetting().alignVerticallyTop()
        layout.addChild(patternsGrid, LayoutSettings::alignHorizontallyLeft)
        layout.addChild(materialsGrid, LayoutSettings::alignHorizontallyRight)
        layout.arrangeElements()
    }

    private fun onClickPattern(button: TrimElementButton) {
        patternButtons.forEach { it.active = true }
        button.active = false
        selectedPattern = button.element
        updateConfig()
    }

    private fun onClickMaterial(button: TrimElementButton) {
        materialButtons.forEach { it.active = true }
        button.active = false
        selectedMaterial = button.element
        updateConfig()
    }

    private fun updateConfig() {
        val identity = currentIdentity ?: return
        CustomRename.edit(identity) { data ->
            if (selectedPattern == null) {
                data.copy(trimMaterial = null, trimPattern = null)
            } else {
                data.copy(
                    trimMaterial = selectedMaterial?.toString(),
                    trimPattern = selectedPattern?.toString(),
                )
            }
        }
    }

    override fun children(): List<GuiEventListener> = children

    override fun contentHeight(): Int = layout.getHeight() + PADDING * 2

    override fun setX(x: Int) {
        super.setX(x)
        layout.setX(getX() + PADDING)
    }

    override fun setY(y: Int) {
        super.setY(y)
        layout.setY(getY() + PADDING)
    }

    override fun setWidth(width: Int) {
        super.setWidth(width)
        positionButtons(getWidth(), getHeight())
    }

    override fun setHeight(height: Int) {
        super.setHeight(height)
        positionButtons(getWidth(), getHeight())
    }

    override fun scrollRate(): Double = 10.0

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean =
        super.mouseClicked(MouseButtonEvent(click.x(), click.y() + scrollAmount(), click.buttonInfo()), doubled)

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        CustomizeDraw.panel(graphics, getX(), getY(), getWidth(), getHeight())
        graphics.enableScissor(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2)
        val scrollY = scrollAmount().toInt()
        for (widget in children) {
            widget.setY(widget.getY() - scrollY)
            widget.extractRenderState(graphics, mouseX, mouseY, a)
            widget.setY(widget.getY() + scrollY)
        }
        extractScrollbar(graphics, mouseX, mouseY)
        graphics.disableScissor()
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    fun setCurrentItem(currentItem: ItemStack) {
        currentIdentity = CustomRename.identity(currentItem)
        patternButtons.forEach { it.setPreviewStack(currentItem) }
        val data = CustomRename.localAppearance(currentItem)
        if (data?.trimPattern == null || data.trimMaterial == null) {
            selectedPattern = null
            selectedMaterial = materialButtons.firstOrNull()?.element
            materialButtons.forEachIndexed { i, b -> b.active = i != 0 }
            patternButtons.forEachIndexed { i, b -> b.active = i != 0 }
        } else {
            selectedMaterial = Identifier.tryParse(data.trimMaterial)
            selectedPattern = Identifier.tryParse(data.trimPattern)
            materialButtons.forEach { it.active = selectedMaterial != it.element }
            patternButtons.forEach { it.active = selectedPattern != it.element }
        }
    }

    private companion object {
        const val PADDING = 3
        const val MAX_BUTTONS_PER_ROW_PATTERN = 7
        const val MAX_BUTTONS_PER_ROW_MATERIAL = 6
    }
}
