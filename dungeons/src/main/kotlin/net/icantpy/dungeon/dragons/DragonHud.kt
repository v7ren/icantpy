package net.icantpy.dungeon.dragons

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Draws the dragon timer HUD and the spawn title. Line content comes from [DragonHudText]. */
object DragonHud {
    private const val LINE_HEIGHT = 10
    private const val TITLE_SCALE = 2f

    fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, preview: Boolean) {
        val settings = DragonController.settings()
        if (!preview && (!settings.enabled || !settings.showTimerHud)) return
        val lines = linesFor(preview, settings.timerStyle)
        if (lines.isEmpty()) return
        val font = Minecraft.getInstance().font
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        lines.forEachIndexed { index, line ->
            graphics.text(font, line.text, 0, index * LINE_HEIGHT, line.colorArgb, true)
        }
        pose.popMatrix()
    }

    /** Box size for the HUD editor, based on preview lines so the box stays stable while the match runs. */
    fun size(scale: Float, style: DragonTimerStyle): Pair<Int, Int> {
        val lines = DragonHudText.previewLines(style)
        val font = Minecraft.getInstance().font
        val width = lines.maxOf { font.width(it.text) }
        val height = lines.size * LINE_HEIGHT
        return kotlin.math.ceil(width * scale).toInt() to kotlin.math.ceil(height * scale).toInt()
    }

    /** Spawn title, centred near the top third of the screen. Draws nothing once the title has expired. */
    fun renderTitle(graphics: GuiGraphicsExtractor, guiWidth: Int, guiHeight: Int) {
        val title = DragonController.activeTitle() ?: return
        val font = Minecraft.getInstance().font
        val width = font.width(title.text)
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(guiWidth / 2f, guiHeight / 3f)
        pose.scale(TITLE_SCALE, TITLE_SCALE)
        graphics.text(font, title.text, -width / 2, 0, title.colorArgb, true)
        pose.popMatrix()
    }

    private fun linesFor(preview: Boolean, style: DragonTimerStyle): List<DragonHudLine> =
        if (preview) {
            DragonHudText.previewLines(style)
        } else {
            DragonHudText.lines(
                states = DragonController.states(),
                priority = DragonController.currentPriority,
                nowTick = DragonController.currentTick(),
                style = style,
            )
        }
}
