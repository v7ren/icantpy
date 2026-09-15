package net.icantpy.gui.neurename

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

/** Drawing helpers that reproduce NEU's dark floating panel and texture blits. */
internal object NeurenameDraw {
    fun texture(graphics: GuiGraphicsExtractor, texture: Identifier, x: Int, y: Int, width: Int, height: Int) {
        graphics.blit(texture, x, y, x + width, y + height, 0f, 1f, 0f, 1f)
    }

    /** NEU RenderUtils.drawFloatingRectDark (blurred background omitted). */
    fun floatingRectDark(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, shadow: Boolean = true) {
        val main = 0xF0202026.toInt()
        val light = 0xFF303036.toInt()
        val dark = 0xFF101016.toInt()
        graphics.fill(x, y, x + 1, y + height, light)
        graphics.fill(x + 1, y, x + width, y + 1, light)
        graphics.fill(x + width - 1, y + 1, x + width, y + height, dark)
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, dark)
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, main)
        if (shadow) {
            graphics.fill(x + width, y + 2, x + width + 2, y + height + 2, 0x70000000)
            graphics.fill(x + 2, y + height, x + width, y + height + 2, 0x70000000)
        }
    }

    fun text(graphics: GuiGraphicsExtractor, value: String, x: Int, y: Int, color: Int, shadow: Boolean = false) {
        graphics.text(Minecraft.getInstance().font, value, x, y, color, shadow)
    }

    fun text(graphics: GuiGraphicsExtractor, value: Component, x: Int, y: Int, color: Int, shadow: Boolean = false) {
        graphics.text(Minecraft.getInstance().font, value, x, y, color, shadow)
    }

    fun centered(
        graphics: GuiGraphicsExtractor,
        value: Component,
        centerX: Int,
        y: Int,
        color: Int,
        shadow: Boolean = false,
    ) {
        val font = Minecraft.getInstance().font
        graphics.text(font, value, centerX - font.width(value) / 2, y, color, shadow)
    }

    fun width(value: String): Int = Minecraft.getInstance().font.width(value)
}
