package net.icantpy.gui.customize

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.layouts.SpacerElement
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Colour popup for the name editor (single colour or gradient), ported from Skyblocker's `ColorPopup`. */
class ColorPopup private constructor(
    backgroundScreen: Screen,
    private val gradient: Boolean,
    private val onResult: (Int, Int) -> Unit,
) : PopupScreen(backgroundScreen, Component.literal("Color Popup")) {

    private val layout = GridLayout()
    private var currentColor = -1 to -1

    companion object {
        fun create(backgroundScreen: Screen, consumer: (Int) -> Unit) =
            ColorPopup(backgroundScreen, false) { start, _ -> consumer(start) }

        fun createGradient(backgroundScreen: Screen, consumer: (Int, Int) -> Unit) =
            ColorPopup(backgroundScreen, true, consumer)
    }

    override fun init() {
        val adder = layout.createRowHelper(2)
        addRenderableWidget(adder.addChild(StringWidget(Component.literal(if (gradient) "Gradient Colors" else "Custom Color"), font), 2))

        if (gradient) {
            val pickerStart = ColorPickerWidget(0, 0, 200, 100)
            val argbStart = ARGBTextInput(0, 0, font, true, hasAlpha = false)
            val pickerEnd = ColorPickerWidget(0, 0, 200, 100)
            val argbEnd = ARGBTextInput(0, 0, font, true, hasAlpha = false)
            addRenderableWidget(pickerStart)
            addRenderableWidget(argbStart)
            addRenderableWidget(pickerEnd)
            addRenderableWidget(argbEnd)
            argbStart.setOnChange { pickerStart.setARGBColor(it); currentColor = it to currentColor.second }
            pickerStart.setOnColorChange { color, _ -> argbStart.setARGBColor(color); currentColor = color to currentColor.second }
            argbEnd.setOnChange { pickerEnd.setARGBColor(it); currentColor = currentColor.first to it }
            pickerEnd.setOnColorChange { color, _ -> argbEnd.setARGBColor(color); currentColor = currentColor.first to color }
            addRenderableWidget(adder.addChild(StringWidget(Component.literal("Start"), font)))
            addRenderableWidget(adder.addChild(StringWidget(Component.literal("End"), font)))
            adder.addChild(pickerStart)
            adder.addChild(pickerEnd)
            adder.addChild(argbStart)
            adder.addChild(argbEnd)
        } else {
            val colorPicker = ColorPickerWidget(0, 0, 200, 100)
            val argb = ARGBTextInput(0, 0, font, true, hasAlpha = false)
            addRenderableWidget(colorPicker)
            addRenderableWidget(argb)
            argb.setOnChange { colorPicker.setARGBColor(it); currentColor = it to -1 }
            colorPicker.setOnColorChange { color, _ -> argb.setARGBColor(color); currentColor = color to -1 }
            adder.addChild(colorPicker, 2)
            adder.addChild(argb, 2)
        }

        adder.addChild(SpacerElement.height(15), 2)
        addRenderableWidget(adder.addChild(Button.builder(Component.literal("Cancel")) { onClose() }.build(), LayoutSettings.defaults().alignHorizontallyRight().paddingRight(2)))
        addRenderableWidget(adder.addChild(Button.builder(Component.literal("Done")) {
            onResult(currentColor.first, currentColor.second)
            onClose()
        }.build(), LayoutSettings.defaults().alignHorizontallyLeft().paddingLeft(2)))
        super.init()
        // Screen.init() registers the widgets but does not arrange this standalone layout.
        // Run the same centered pass used after a resize so the picker never opens at (0, 0).
        repositionElements()
    }

    override fun repositionElements() {
        super.repositionElements()
        layout.arrangeElements()
        layout.setPosition((width - layout.getWidth()) / 2, (height - layout.getHeight()) / 2)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractBackground(graphics, mouseX, mouseY, a)
        drawPanel(graphics, layout.getX() - 4, layout.getY() - 4, layout.getWidth() + 8, layout.getHeight() + 8)
    }
}
