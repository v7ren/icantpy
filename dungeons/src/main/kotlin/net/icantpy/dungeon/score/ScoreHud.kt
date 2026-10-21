package net.icantpy.dungeon.score

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.hud.HudText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.ceil

/** Size of the HUD box in screen pixels, after scale. */
data class ScoreHudSize(val width: Int, val height: Int)

/**
 * Draws the score box. Coordinates are GUI pixels; the caller owns the position. Hidden outside dungeons unless
 * [preview] is true, which draws a fixed sample so the HUD editor can place the box.
 */
object ScoreHud {
    private const val LINE_HEIGHT = 10
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    /** Sample inputs for preview: F7 run with some secrets missing and one failed puzzle. */
    private val previewBreakdown: ScoreBreakdown = ScoreCalculator.compute(
        ScoreInputs(
            floor = DungeonFloor.F7,
            clearedPercent = 97,
            secretsFoundPercent = 80.0,
            failedPuzzles = 1,
            deaths = 1,
            elapsedSeconds = 700,
            crypts = 3,
            mimicKilled = true,
            princeKilled = true,
        ),
    )

    fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, preview: Boolean) {
        val lines = linesFor(preview) ?: return
        val font = Minecraft.getInstance().font
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        lines.forEachIndexed { index, line ->
            graphics.text(font, line, 0, index * LINE_HEIGHT, TEXT_COLOR, true)
        }
        pose.popMatrix()
    }

    /** Box size for the HUD editor, always based on the preview lines so the box does not change size with data. */
    fun size(scale: Float): ScoreHudSize {
        val lines = previewLines()
        val font = Minecraft.getInstance().font
        val width = lines.maxOf { font.width(HudText.plain(it)) }
        val height = lines.size * LINE_HEIGHT
        return ScoreHudSize(ceil(width * scale).toInt(), ceil(height * scale).toInt())
    }

    private fun linesFor(preview: Boolean): List<String>? {
        if (preview) return previewLines()
        val settings = ScoreTracker.settings
        if (!settings.enabled) return null
        val breakdown = ScoreTracker.latest ?: return null
        return ScoreHudLines.lines(breakdown, settings.showBreakdown)
    }

    private fun previewLines(): List<String> =
        ScoreHudLines.lines(previewBreakdown, ScoreTracker.settings.showBreakdown)
}
