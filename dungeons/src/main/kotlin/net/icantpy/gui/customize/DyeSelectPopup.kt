package net.icantpy.gui.customize

import net.icantpy.gui.McUi
import net.icantpy.Icantpy
import net.icantpy.cosmetics.items.RepoDyes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ScrollableLayout
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.layouts.SpacerElement
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/** A modal popup over an existing screen, with a dim background and a return path. */
abstract class PopupScreen(val backgroundScreen: Screen, title: Component) : Screen(title) {
    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        McUi.setScreen(minecraft, backgroundScreen)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(0, 0, width, height, 0x80000000.toInt())
    }

    protected fun drawPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, 0xF0202026.toInt())
        CustomizeDraw.border(graphics, x, y, w, h, 0xFF303036.toInt())
    }
}

/** Popup listing static and animated Hypixel dyes, wired to icantpy's `RepoDyes`. */
class DyeSelectPopup(
    backgroundScreen: Screen,
    private val updateStatic: (Int) -> Unit,
    private val updateAnimated: (List<Int>, Boolean) -> Unit,
) : PopupScreen(backgroundScreen, Component.literal("Pick Dye")) {

    private var scrollableLayout: ScrollableLayout? = null
    private var titleWidget: StringWidget? = null
    private var closeButton: Button? = null

    override fun init() {
        val layout = LinearLayout.vertical()
        layout.defaultCellSetting().alignHorizontallyCenter()

        layout.addChild(StringWidget(Component.literal("Static Dyes"), font))
        val statics = (BUILT_IN_STATIC_DYES.keys + RepoDyes.names().filter { !RepoDyes.isAnimated(it) }).distinct()
        for (name in statics) {
            val color = RepoDyes.staticColor(name) ?: BUILT_IN_STATIC_DYES[name] ?: continue
            layout.addChild(dyeButton(name, 0xFF000000.toInt() or color) { updateStatic(color) })
        }
        layout.addChild(SpacerElement.height(15))
        layout.addChild(StringWidget(Component.literal("Animated Dyes"), font))
        val animated = (BUILT_IN_ANIMATED_DYES.keys + RepoDyes.names().filter { RepoDyes.isAnimated(it) }).distinct()
        for (name in animated) {
            val colors = RepoDyes.animatedColors(name) ?: BUILT_IN_ANIMATED_DYES[name] ?: continue
            val first = colors.firstOrNull() ?: 0x808080
            layout.addChild(dyeButton(name, 0xFF000000.toInt() or first) {
                val cycleBack = colors.size % 2 == 0
                updateAnimated(colors, cycleBack)
            })
        }

        Icantpy.LOGGER.info("Dye picker created {} static and {} animated buttons", statics.size, animated.size)

        // ScrollableLayout measures the content during construction. Arrange it first, otherwise
        // its initial zero-height viewport clips every button even though they were created.
        layout.arrangeElements()
        val scroll = ScrollableLayout(minecraft, layout, 0)
        scroll.visitWidgets { addRenderableWidget(it) }
        scrollableLayout = scroll
        val title = StringWidget(Component.literal("Pick Dye"), font)
        val close = Button.builder(CommonComponents.GUI_CANCEL) { onClose() }.width(75).build()
        addRenderableWidget(title)
        addRenderableWidget(close)
        titleWidget = title
        closeButton = close
        super.init()
        repositionElements()
    }

    private fun dyeButton(name: String, color: Int, action: () -> Unit): Button {
        val label = name.removePrefix("DYE_").lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        return Button.builder(Component.literal(label).withColor(color)) {
            action()
            onClose()
        }.size(150, 20).build()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractBackground(graphics, mouseX, mouseY, a)
        scrollableLayout?.let { drawPanel(graphics, it.getX() - 4, it.getY() - 4, it.getWidth() + 8, it.getHeight() + 8) }
    }

    override fun repositionElements() {
        scrollableLayout?.let {
            it.setMaxHeight(minOf(300, (height * 0.68).toInt()))
            it.arrangeElements()
            it.setPosition((width - it.getWidth()) / 2, (height - it.getHeight()) / 2)
            closeButton?.setPosition((width - (closeButton?.getWidth() ?: 0)) / 2, it.getY() + it.getHeight() + 20)
            titleWidget?.setPosition((width - (titleWidget?.getWidth() ?: 0)) / 2, it.getY() - 30)
        }
    }

    private companion object {
        val BUILT_IN_STATIC_DYES = linkedMapOf(
            "DYE_CARMINE" to 0x960018,
            "DYE_NECRON" to 0xE7413C,
            "DYE_EMERALD" to 0x50C878,
            "DYE_AQUAMARINE" to 0x7FFFD4,
            "DYE_PURE_WHITE" to 0xFFFFFF,
            "DYE_PURE_BLACK" to 0x000000,
        )
        val BUILT_IN_ANIMATED_DYES = mapOf(
            "DYE_ROSE" to listOf(0xFF5A8A, 0xFFB347, 0xFF5A8A),
        )
    }
}
