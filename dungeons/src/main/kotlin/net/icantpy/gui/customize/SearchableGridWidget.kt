package net.icantpy.gui.customize

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.network.chat.Component

/**
 * A searchable grid of widgets, ported from Skyblocker's `SearchableGridWidget`. The subclass
 * supplies the full candidate list via [filterWidgets]; the search field filters and re-grids.
 */
abstract class SearchableGridWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    private val expectedWidgetWidth: Int,
    private val maxPerRow: Int = Int.MAX_VALUE,
    private val packed: Boolean = false,
    private val spaceElementsOut: Boolean = false,
) : AbstractContainerWidget(x, y, width, height, message, AbstractScrollArea.defaultSettings(8)) {
    private val filteredWidgets = mutableListOf<AbstractWidget>()
    private val layoutWidget = LinearLayout.vertical()
    private val searchField: EditBox
    private val widgetsContainer: WidgetsContainer

    init {
        searchField = EditBox(Minecraft.getInstance().font, width, TEXT_FIELD_HEIGHT, Component.translatable("gui.recipebook.search_hint"))
        searchField.setHint(Component.translatable("gui.recipebook.search_hint").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY))
        searchField.setResponder { filterInternal(it) }
        widgetsContainer = WidgetsContainer()
        layoutWidget.addChild(searchField)
        layoutWidget.addChild(widgetsContainer)
        layoutWidget.arrangeElements()
        layoutWidget.setPosition(x, y)
        setWidth(getWidth())
    }

    override fun setX(x: Int) {
        super.setX(x)
        layoutWidget.setX(x)
    }

    override fun setY(y: Int) {
        super.setY(y)
        layoutWidget.setY(y)
    }

    override fun setWidth(width: Int) {
        var w = width
        if (packed) {
            val perRow = minOf((width - AbstractScrollArea.SCROLLBAR_WIDTH) / expectedWidgetWidth, maxPerRow)
            val newWidth = perRow * expectedWidgetWidth + AbstractScrollArea.SCROLLBAR_WIDTH
            setX(getX() + (width - newWidth) / 2)
            w = newWidth
        }
        super.setWidth(w)
        searchField.setWidth(w)
        widgetsContainer.setWidth(w)
        layoutWidget.arrangeElements()
    }

    override fun setHeight(height: Int) {
        super.setHeight(height)
        widgetsContainer.setHeight(height - TEXT_FIELD_HEIGHT)
        layoutWidget.arrangeElements()
    }

    fun setSearch(search: String) = searchField.setValue(search)

    private fun filterInternal(input: String) {
        filteredWidgets.clear()
        filteredWidgets.addAll(filterWidgets(input))
        widgetsContainer.recreateGrid()
    }

    protected abstract fun filterWidgets(input: String): Collection<AbstractWidget>

    override fun children(): List<GuiEventListener> = listOf(searchField, widgetsContainer)

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        searchField.extractRenderState(context, mouseX, mouseY, deltaTicks)
        widgetsContainer.extractRenderState(context, mouseX, mouseY, deltaTicks)
    }

    private inner class WidgetsContainer : AbstractContainerWidget(
        0, 0, getWidth(), getHeight() - TEXT_FIELD_HEIGHT, Component.literal("Grid"), AbstractScrollArea.defaultSettings(8),
    ) {
        var grid: GridLayout = GridLayout()
            private set

        override fun setX(x: Int) {
            super.setX(x)
            grid.setX(x)
        }

        override fun setY(y: Int) {
            super.setY(y)
            grid.setY(y)
        }

        override fun setWidth(width: Int) {
            super.setWidth(width)
            recreateGrid()
        }

        override fun setHeight(height: Int) {
            super.setHeight(height)
            recreateGrid()
        }

        override fun children(): List<GuiEventListener> = filteredWidgets

        override fun contentHeight(): Int = grid.getHeight()

        override fun scrollRate(): Double = this@SearchableGridWidget.scrollRate()

        override fun setScrollAmount(scrollY: Double) {
            super.setScrollAmount(scrollY)
            grid.setY(getY() - scrollAmount().toInt())
        }

        private fun isVisible(widget: AbstractWidget): Boolean =
            widget.bottom >= getY() && widget.y < getBottom()

        override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            context.enableScissor(getX(), getY(), getRight(), getBottom())
            for (widget in filteredWidgets) {
                if (isVisible(widget)) widget.extractRenderState(context, mouseX, mouseY, deltaTicks)
            }
            extractScrollbar(context, mouseX, mouseY)
            context.disableScissor()
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}

        fun recreateGrid() {
            val newGrid = GridLayout()
            val columns = (getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH) / expectedWidgetWidth
            val adder = newGrid.createRowHelper(columns)
            filteredWidgets.forEach { adder.addChild(it) }
            if (spaceElementsOut && columns > 0) {
                newGrid.columnSpacing(((getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH) - columns * expectedWidgetWidth) / columns)
            }
            newGrid.arrangeElements()
            newGrid.setPosition(grid.getX(), grid.getY())
            grid = newGrid
            refreshScrollAmount()
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean =
        getChildAt(mouseX, mouseY).filter { it.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) }.isPresent

    override fun contentHeight(): Int = 0

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    private companion object {
        const val TEXT_FIELD_HEIGHT = 20
    }
}
