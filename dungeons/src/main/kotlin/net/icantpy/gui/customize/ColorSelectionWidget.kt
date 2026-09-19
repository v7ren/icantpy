package net.icantpy.gui.customize

import net.icantpy.gui.McUi
import net.icantpy.cosmetics.items.AnimatedDyeEvaluator
import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.HypixelItemData
import net.icantpy.cosmetics.items.ItemCustomizeColor
import net.icantpy.cosmetics.items.ItemCustomizeKeyframe
import net.icantpy.cosmetics.items.ItemIdentity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.DyedItemColor

/** Colour panel for armour, ported from Skyblocker's `ColorSelectionWidget` using icantpy's dye model. */
class ColorSelectionWidget(x: Int, y: Int, width: Int, height: Int, textRenderer: Font) :
    AbstractContainerWidget(x, y, width, height, Component.nullToEmpty("ColorSelectionWidget"), AbstractScrollArea.defaultSettings(4)) {

    private val colorPicker: ColorPickerWidget
    private val argbTextInput: ARGBTextInput
    private val timelineWidget: AnimatedDyeTimelineWidget
    private val cycleBackCheckbox: ToggleBox
    private val delaySlider: Slider
    private val durationSlider: Slider
    private val resetColorButton: Button
    private val pickDyeButton: Button
    private val animatedCheckbox: ToggleBox
    private val notCustomizableText: StringWidget

    private val children: List<AbstractWidget>
    private lateinit var layout: FrameLayout

    private var currentIdentity: ItemIdentity? = null
    private var currentItem: ItemStack = ItemStack.EMPTY
    private var animated = false

    init {
        val height1 = minOf(minOf(2 * height / 3, width / 5), height - 40)
        colorPicker = ColorPickerWidget(0, 0, height1 * 2, height1)
        colorPicker.setOnColorChange { argb, release -> onPickerColorChanged(argb, release) }
        argbTextInput = ARGBTextInput(0, 0, textRenderer, true, hasAlpha = false)
        argbTextInput.setOnChange { onTextInputColorChanged(it) }
        timelineWidget = AnimatedDyeTimelineWidget(0, 0, getWidth() - 6, 15) { color, _ ->
            argbTextInput.setARGBColor(color)
            colorPicker.setARGBColor(color)
        }
        timelineWidget.setOnDataChanged { persistAnimatedDye() }

        resetColorButton = Button.builder(Component.literal("Reset Color")) { onRemoveCustomColor() }.width(75).build()
        pickDyeButton = Button.builder(Component.literal("Pick Dye")) { onClickPickDye() }.width(75).build()
        notCustomizableText = StringWidget(Component.literal("This item cannot be customized."), textRenderer)
        FrameLayout.centerInRectangle(notCustomizableText, getX(), getY(), getWidth(), getHeight())

        animatedCheckbox = ToggleBox(Component.literal("Animated"), { animated }, { onAnimatedToggle(it) })
        cycleBackCheckbox = ToggleBox(Component.literal("Cycle Back"), { animated && cycleBack() }, { onCycleBackToggle(it) })

        val sliderWidth = (width * 0.35f).toInt()
        delaySlider = Slider(0, 0, sliderWidth, 0f, 2f, 0.02f, "Delay", { onDelayChanged(it) })
        delaySlider.setTooltip(Tooltip.create(Component.literal("Time to wait before the animation starts")))
        durationSlider = Slider(0, 0, sliderWidth, 0.1f, 10f, 0.1f, "Duration", { onDurationChanged(it) })
        durationSlider.setTooltip(Tooltip.create(Component.literal("Time for one full colour sweep")))

        children = listOf(colorPicker, argbTextInput, timelineWidget, resetColorButton, pickDyeButton, animatedCheckbox, notCustomizableText, cycleBackCheckbox, delaySlider, durationSlider)
        val w = getWidth() - PADDING * 2
        val h = getHeight() - PADDING * 2
        layout = FrameLayout(w, h)
        layout.addChild(timelineWidget, LayoutSettings::alignVerticallyBottom)

        val grid = GridLayout().spacing(3)
        grid.addChild(argbTextInput, 0, 1)
        grid.addChild(resetColorButton, 0, 1, 1, 3, LayoutSettings::alignHorizontallyRight)
        grid.addChild(pickDyeButton, 0, 2, 1, 4, LayoutSettings::alignHorizontallyRight)
        grid.addChild(animatedCheckbox, 1, 1, 1, 2)
        grid.addChild(delaySlider, 1, 3, 1, 2, LayoutSettings::alignHorizontallyRight)
        grid.addChild(cycleBackCheckbox, 2, 1, 1, 2)
        grid.addChild(durationSlider, 2, 3, 1, 2, LayoutSettings::alignHorizontallyRight)
        grid.addChild(colorPicker, 0, 0, 3, 1)
        layout.addChild(grid, LayoutSettings::alignVerticallyTop)
        layout.addChild(notCustomizableText)
        updateWidgetDimensions()
    }

    private fun updateWidgetDimensions() {
        val w = getWidth() - PADDING * 2
        val h = getHeight() - PADDING * 2
        timelineWidget.setWidth(w)
        colorPicker.setHeight(minOf(h - timelineWidget.getHeight() - 5, w / 3 / 2))
        colorPicker.setWidth(colorPicker.getHeight() * 2)
        delaySlider.setWidth((w * 0.35f).toInt())
        durationSlider.setWidth((w * 0.35f).toInt())
        layout.arrangeElements()
        layout.setPosition(getX() + PADDING, getY() + PADDING)
    }

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
        updateWidgetDimensions()
    }

    private fun onPickerColorChanged(argb: Int, release: Boolean) {
        argbTextInput.setARGBColor(argb)
        if (!animated) {
            setStaticColor(argb and 0xFFFFFF)
        } else if (release) {
            timelineWidget.setColor(argb)
        }
    }

    private fun onTextInputColorChanged(argb: Int) {
        colorPicker.setARGBColor(argb)
        if (animated) timelineWidget.setColor(argb) else setStaticColor(argb and 0xFFFFFF)
    }

    private fun setStaticColor(rgb: Int) {
        val identity = currentIdentity ?: return
        CustomRename.edit(identity) { it.copy(customLeatherColor = ItemCustomizeColor(rgb), animatedKeyframes = null, customAnimatedDye = null) }
    }

    private fun onRemoveCustomColor() {
        animated = false
        animatedCheckbox.checked = false
        changeVisibilities()
        val identity = currentIdentity ?: return
        CustomRename.edit(identity) { it.copy(customLeatherColor = null, animatedKeyframes = null, customAnimatedDye = null) }
        val color = underlyingColor(currentItem)
        argbTextInput.setARGBColor(color)
        colorPicker.setARGBColor(color)
    }

    private fun onAnimatedToggle(checked: Boolean) {
        animated = checked
        changeVisibilities()
        val identity = currentIdentity ?: return
        if (animated) {
            val frames = AnimatedDyeEvaluator.defaultKeyframes()
            CustomRename.edit(identity) {
                it.copy(
                    customLeatherColor = null,
                    customAnimatedDye = null,
                    animatedKeyframes = frames,
                    animatedCycleBack = true,
                    animatedDelay = 0f,
                    animatedDuration = 1f,
                )
            }
            timelineWidget.setKeyframes(frames)
            delaySlider.setValueValue(0f)
            durationSlider.setValueValue(1f)
            cycleBackCheckbox.checked = true
        } else {
            val color = staticColorOrUnderlying()
            colorPicker.setARGBColor(color)
            argbTextInput.setARGBColor(color)
            CustomRename.edit(identity) { it.copy(animatedKeyframes = null) }
        }
    }

    private fun onCycleBackToggle(checked: Boolean) {
        val identity = currentIdentity ?: return
        CustomRename.edit(identity) { it.copy(animatedCycleBack = checked) }
    }

    private fun onDelayChanged(value: Float) {
        val identity = currentIdentity ?: return
        CustomRename.edit(identity) { it.copy(animatedDelay = value) }
    }

    private fun onDurationChanged(value: Float) {
        val identity = currentIdentity ?: return
        CustomRename.edit(identity) { it.copy(animatedDuration = value) }
    }

    private fun cycleBack(): Boolean = CustomRename.localAppearance(currentItem)?.animatedCycleBack ?: true

    private fun persistAnimatedDye() {
        val identity = currentIdentity ?: return
        CustomRename.edit(identity) { it.copy(animatedKeyframes = timelineWidget.keyframes()) }
    }

    private fun onClickPickDye() {
        val minecraft = Minecraft.getInstance()
        val screen = McUi.currentScreen(minecraft) ?: return
        McUi.setScreen(minecraft, DyeSelectPopup(screen, ::setFromSolidDye, ::setFromAnimatedDye))
    }

    private fun setFromSolidDye(rgb: Int) {
        onRemoveCustomColor()
        setStaticColor(rgb)
        colorPicker.setARGBColor(rgb)
        argbTextInput.setARGBColor(rgb)
    }

    private fun setFromAnimatedDye(colors: List<Int>, cycleBack: Boolean) {
        onRemoveCustomColor()
        animated = true
        animatedCheckbox.checked = true
        cycleBackCheckbox.checked = cycleBack
        durationSlider.setValueValue(10f)
        changeVisibilities()
        val frames = buildKeyframes(colors, cycleBack)
        val identity = currentIdentity ?: return
        CustomRename.edit(identity) {
            it.copy(customLeatherColor = null, customAnimatedDye = null, animatedKeyframes = frames, animatedCycleBack = cycleBack, animatedDelay = 0f, animatedDuration = 10f)
        }
        timelineWidget.setKeyframes(frames)
    }

    private fun buildKeyframes(colors: List<Int>, cycleBack: Boolean): List<ItemCustomizeKeyframe> {
        if (colors.isEmpty()) return AnimatedDyeEvaluator.defaultKeyframes()
        val max = if (cycleBack) colors.size / 2 else colors.size
        val n = max.coerceAtLeast(2)
        return (0 until n).map { i ->
            ItemCustomizeKeyframe(colors[i.coerceAtMost(colors.size - 1)], i.toFloat() / (n - 1))
        }
    }

    private fun changeVisibilities() {
        colorPicker.visible = true
        argbTextInput.visible = true
        timelineWidget.visible = animated
        cycleBackCheckbox.visible = animated
        delaySlider.visible = animated
        durationSlider.visible = animated
        resetColorButton.visible = true
        pickDyeButton.visible = true
        animatedCheckbox.visible = true
        notCustomizableText.visible = false
    }

    override fun children(): List<GuiEventListener> = children

    override fun contentHeight(): Int = 0

    override fun scrollRate(): Double = 0.0

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean =
        getChildAt(mouseX, mouseY).filter { it.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) }.isPresent ||
            super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        CustomizeDraw.panel(graphics, getX(), getY(), getWidth(), getHeight())
        for (child in children) child.extractRenderState(graphics, mouseX, mouseY, a)
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!super.mouseClicked(click, doubled)) {
            setFocused(null)
            return false
        }
        return true
    }

    fun setCurrentItem(item: ItemStack) {
        currentItem = item
        currentIdentity = CustomRename.identity(item)
        refresh()
    }

    fun refresh() {
        val data = CustomRename.localAppearance(currentItem)
        changeVisibilities()
        if (data?.animatedKeyframes != null && data.animatedKeyframes.size >= 2) {
            animated = true
            cycleBackCheckbox.checked = data.animatedCycleBack
            delaySlider.setValueValue(data.animatedDelay)
            durationSlider.setValueValue(data.animatedDuration)
            timelineWidget.setKeyframes(data.animatedKeyframes)
        } else if (data?.customLeatherColor != null) {
            animated = false
            val color = data.customLeatherColor.opaqueArgb
            argbTextInput.setARGBColor(color)
            colorPicker.setARGBColor(color)
        } else {
            animated = false
            val color = underlyingColor(currentItem)
            argbTextInput.setARGBColor(color)
            colorPicker.setARGBColor(color)
        }
        animatedCheckbox.checked = animated
        changeVisibilities()
    }

    private fun staticColorOrUnderlying(): Int =
        CustomRename.localAppearance(currentItem)?.customLeatherColor?.opaqueArgb ?: underlyingColor(currentItem)

    private fun underlyingColor(stack: ItemStack): Int {
        val rgb = HypixelItemData.raw(stack, DataComponents.DYED_COLOR)?.rgb()?.and(0xFFFFFF)
            ?: (DyedItemColor.LEATHER_COLOR and 0xFFFFFF)
        return 0xFF000000.toInt() or rgb
    }

    private companion object {
        const val PADDING = 3
    }
}

/** A checkbox-like toggle without needing an accessor mixin. */
internal class ToggleBox(
    private val label: Component,
    private val getter: () -> Boolean,
    private val onToggle: (Boolean) -> Unit,
) : AbstractWidget(0, 0, 80, 16, Component.empty()) {

    var checked: Boolean
        get() = getter()
        set(value) = onToggle(value)

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val boxX = getX()
        val boxY = getY() + 1
        graphics.fill(boxX, boxY, boxX + 12, boxY + 12, if (checked) 0xFF55FF55.toInt() else 0xFF000000.toInt())
        CustomizeDraw.border(graphics, boxX, boxY, 12, 12, 0xFFFFFFFF.toInt())
        if (checked) {
            graphics.fill(boxX + 3, boxY + 5, boxX + 5, boxY + 9, 0xFF000000.toInt())
            graphics.fill(boxX + 5, boxY + 7, boxX + 9, boxY + 9, 0xFF000000.toInt())
        }
        val font = Minecraft.getInstance().font
        graphics.text(font, label, getX() + 16, getY() + 3, 0xFFFFFFFF.toInt(), false)
    }

    override fun onClick(click: MouseButtonEvent, doubled: Boolean) {
        onToggle(!getter())
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}
}

/** A labelled slider reporting its value in the message, ported from Skyblocker's `Slider`. */
internal class Slider(
    x: Int,
    y: Int,
    width: Int,
    private val minValue: Float,
    private val maxValue: Float,
    private val step: Float,
    private val label: String,
    private val onValueChanged: (Float) -> Unit,
) : AbstractSliderButton(x, y, width, 15, Component.empty(), 0.0) {

    private var clicked = false

    init {
        updateMessage()
    }

    fun trueValue(): Float {
        val v = value
        return roundToStep((v * (maxValue - minValue)).toFloat())
    }

    override fun updateMessage() {
        setMessage(Component.literal("$label: ${String.format("%.2f", trueValue())}"))
    }

    fun setValueValue(val0: Float) {
        val v = (val0 - minValue) / (maxValue - minValue)
        value = v.toDouble()
        updateMessage()
    }

    override fun onClick(click: MouseButtonEvent, doubled: Boolean) {
        super.onClick(click, doubled)
        clicked = true
    }

    override fun onRelease(click: MouseButtonEvent) {
        super.onRelease(click)
        if (clicked) {
            onValueChanged(trueValue())
            clicked = false
        }
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (super.keyPressed(input)) {
            onValueChanged(trueValue())
            return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (verticalAmount == 0.0) return false
        val offset = if (verticalAmount > 0) step else -step
        setValueValue((trueValue() + offset).coerceIn(minValue, maxValue))
        onValueChanged(trueValue())
        return true
    }

    override fun applyValue() {}

    private fun roundToStep(v: Float): Float =
        (minValue + step * Math.round((v - minValue) / step)).coerceIn(minValue, maxValue)
}
