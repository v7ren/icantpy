package net.icantpy.gui.customize

import net.icantpy.cosmetics.items.AnimatedDyeEvaluator
import net.icantpy.cosmetics.items.ItemCustomizeKeyframe
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors
import org.lwjgl.glfw.GLFW

/**
 * Keyframe timeline for custom animated dyes. Renders the colour gradient and editable keyframes
 * inline (no generated texture), keeping icantpy's plain RGB interpolation.
 */
class AnimatedDyeTimelineWidget(x: Int, y: Int, width: Int, height: Int, private val frameCallback: (Int, Float) -> Unit) :
    AbstractContainerWidget(x, y, width, height, Component.literal("Animated Dye Timeline"), AbstractScrollArea.defaultSettings(4)) {

    private val keyframes = mutableListOf<KeyframeWidget>()
    private var focusedFrame: KeyframeWidget? = null
    private var onDataChanged: (() -> Unit)? = null

    fun setOnDataChanged(callback: () -> Unit) {
        onDataChanged = callback
    }

    fun setKeyframes(frames: List<ItemCustomizeKeyframe>) {
        keyframes.clear()
        frames.forEach { keyframes.add(KeyframeWidget(it.color, it.time, true)) }
        if (keyframes.isNotEmpty()) setFocusedWidget(keyframes.first())
    }

    fun keyframes(): List<ItemCustomizeKeyframe> = keyframes.map { ItemCustomizeKeyframe(it.color, it.time) }

    override fun children(): List<GuiEventListener> = keyframes

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        // Gradient between each adjacent keyframe pair.
        val sorted = keyframes.sortedBy { it.time }
        for (i in 0 until sorted.size - 1) {
            val frame = sorted[i]
            val next = sorted[i + 1]
            val startX = getX() + HORIZONTAL_MARGIN + (frame.time * (getWidth() - HORIZONTAL_MARGIN * 2 - 1)).toInt()
            val endX = getX() + HORIZONTAL_MARGIN + (next.time * (getWidth() - HORIZONTAL_MARGIN * 2 - 1)).toInt()
            val size = (endX - startX).coerceAtLeast(1)
            for (px in 0..size) {
                val color = AnimatedDyeEvaluator.lerpRgb(frame.color, next.color, px.toFloat() / size)
                graphics.fill(startX + px, getY() + VERTICAL_MARGIN, startX + px + 1, getY() + getHeight() - VERTICAL_MARGIN, color)
            }
        }
        for (frame in keyframes) frame.extractRenderState(graphics, mouseX, mouseY, a)
    }

    private fun setFocusedWidget(frame: KeyframeWidget) {
        focusedFrame = frame
        frameCallback(frame.color, frame.time)
    }

    override fun setFocused(focused: GuiEventListener?) {
        super.setFocused(focused)
        if (focused is KeyframeWidget) setFocusedWidget(focused)
    }

    fun setColor(argb: Int) {
        val frame = focusedFrame ?: return
        frame.color = argb
        dataChanged()
    }

    private fun dataChanged() {
        keyframes.sortBy { it.time }
        onDataChanged?.invoke()
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val handled = super.mouseClicked(click, doubled)
        if (handled) return true
        if (isMouseOver(click.x(), click.y())) {
            val localX = click.x() - getX() + HORIZONTAL_MARGIN
            val time = (localX / (getWidth() - HORIZONTAL_MARGIN * 2 - 1)).toFloat().coerceIn(0f, 1f)
            val frame = KeyframeWidget(0xFFFF0000.toInt(), time, true)
            keyframes.add(frame)
            setFocusedWidget(frame)
            dataChanged()
            return true
        }
        return false
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    override fun contentHeight(): Int = getHeight()

    override fun scrollRate(): Double = 0.0

    private inner class KeyframeWidget(var color: Int, var time: Float, private val draggable: Boolean) :
        AbstractWidget(0, 0, 7, getHeight(), Component.literal("Keyframe")) {

        private var dragging = false

        override fun getX(): Int =
            (this@AnimatedDyeTimelineWidget.getX() + HORIZONTAL_MARGIN + time * (this@AnimatedDyeTimelineWidget.getWidth() - HORIZONTAL_MARGIN * 2 - 1)).toInt() - 3

        override fun getY(): Int = this@AnimatedDyeTimelineWidget.getY()

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color)
            CustomizeDraw.border(graphics, getX(), getY(), getWidth(), getHeight(), if (isFocused) -1 else CommonColors.GRAY)
        }

        override fun onDrag(click: MouseButtonEvent, offsetX: Double, offsetY: Double) {
            super.onDrag(click, offsetX, offsetY)
            if (!draggable) return
            val parent = this@AnimatedDyeTimelineWidget
            val localX = click.x() - parent.getX() + HORIZONTAL_MARGIN
            time = (localX / (parent.getWidth() - HORIZONTAL_MARGIN * 2 - 1)).toFloat().coerceIn(0f, 1f)
            dragging = true
        }

        override fun onRelease(click: MouseButtonEvent) {
            super.onRelease(click)
            if (dragging) dataChanged()
            dragging = false
        }

        override fun keyPressed(input: KeyEvent): Boolean {
            if (input.key() == GLFW.GLFW_KEY_DELETE) {
                deleteThis()
                return true
            }
            return super.keyPressed(input)
        }

        override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isMouseOver(click.x(), click.y())) {
                deleteThis()
                return true
            }
            return super.mouseClicked(click, doubled)
        }

        private fun deleteThis() {
            if (!draggable) return
            val i = keyframes.indexOf(this)
            keyframes.remove(this)
            if (keyframes.isNotEmpty()) setFocusedWidget(keyframes[minOf(i, keyframes.size - 1)])
            dataChanged()
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private companion object {
        const val HORIZONTAL_MARGIN = 3
        const val VERTICAL_MARGIN = 1
    }
}
