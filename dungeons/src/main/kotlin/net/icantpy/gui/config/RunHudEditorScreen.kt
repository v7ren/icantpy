package net.icantpy.gui.config

import net.icantpy.dungeon.dragons.DragonController
import net.icantpy.dungeon.dragons.DragonHud
import net.icantpy.dungeon.score.ScoreHud
import net.icantpy.dungeon.score.ScoreTracker
import net.icantpy.dungeon.splits.RunSplitsController
import net.icantpy.dungeon.splits.RunSplitsHud
import net.icantpy.gui.McUi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * Places the Splits or Score box. The box position and scale live in the feature's own settings; this screen keeps a
 * local copy while dragging and writes once on release, so a drag does not rewrite the config file every frame.
 */
internal class RunHudEditorScreen(private val target: Target) : Screen(Component.literal("icantpy run HUD")) {
    enum class Target { SPLITS, SCORE, DRAGON }

    private var x = 0
    private var y = 0
    private var scale = 1f
    private var grabX = 0
    private var grabY = 0
    private var dragging = false

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        val start = load()
        x = start.x
        y = start.y
        scale = start.scale
        McUi.releaseMouse(Minecraft.getInstance())
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, SCRIM)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        drawPreview(graphics)
        val (boxW, boxH) = boxSize()
        drawOutline(graphics, x - 1, y - 1, boxW + 2, boxH + 2)
        val font = Minecraft.getInstance().font
        graphics.text(font, title(), 10, 10, TEXT, true)
        graphics.text(font, "Drag to move. + / - scale. R resets. Esc saves and returns.", 10, 22, MUTED, true)
        graphics.text(font, "Scale ${(scale * 100).roundToInt()}%", 10, 34, MUTED, true)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val (boxW, boxH) = boxSize()
        if (mx !in x..(x + boxW) || my !in y..(y + boxH)) return super.mouseClicked(event, doubleClick)
        dragging = true
        grabX = mx - x
        grabY = my - y
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (!dragging) return super.mouseDragged(event, dragX, dragY)
        val (boxW, boxH) = boxSize()
        x = clampX(event.x().toInt() - grabX, boxW)
        y = clampY(event.y().toInt() - grabY, boxH)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!dragging) return super.mouseReleased(event)
        dragging = false
        save()
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        when (event.key()) {
            GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> adjustScale(SCALE_STEP)
            GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> adjustScale(-SCALE_STEP)
            GLFW.GLFW_KEY_R -> resetPosition()
            else -> return super.keyPressed(event)
        }
        return true
    }

    override fun onClose() {
        save()
        McUi.setScreen(Minecraft.getInstance(), IcantpyConfigScreen())
    }

    private fun title(): String = when (target) {
        Target.SPLITS -> "Splits HUD"
        Target.SCORE -> "Score HUD"
        Target.DRAGON -> "Wither dragon timer HUD"
    }

    private fun drawPreview(graphics: GuiGraphicsExtractor) {
        when (target) {
            Target.SPLITS -> RunSplitsHud.render(graphics, x, y, scale, preview = true)
            Target.SCORE -> ScoreHud.render(graphics, x, y, scale, preview = true)
            Target.DRAGON -> DragonHud.render(graphics, x, y, scale, preview = true)
        }
    }

    private fun drawOutline(graphics: GuiGraphicsExtractor, left: Int, top: Int, w: Int, h: Int) {
        graphics.fill(left, top, left + w, top + 1, ACCENT)
        graphics.fill(left, top + h - 1, left + w, top + h, ACCENT)
        graphics.fill(left, top, left + 1, top + h, ACCENT)
        graphics.fill(left + w - 1, top, left + w, top + h, ACCENT)
    }

    /** Box size in GUI pixels at the current local scale. */
    private fun boxSize(): Pair<Int, Int> = when (target) {
        Target.SPLITS -> {
            val (w, h) = RunSplitsHud.size()
            (w * scale).roundToInt() to (h * scale).roundToInt()
        }
        Target.SCORE -> ScoreHud.size(scale).let { it.width to it.height }
        Target.DRAGON -> DragonHud.size(scale, DragonController.settings().timerStyle)
    }

    private fun clampX(value: Int, boxW: Int): Int = value.coerceIn(0, (width - boxW).coerceAtLeast(0))

    private fun clampY(value: Int, boxH: Int): Int = value.coerceIn(0, (height - boxH).coerceAtLeast(0))

    private fun adjustScale(delta: Float) {
        scale = ((scale + delta) * 10f).roundToInt().coerceIn(
            (MIN_SCALE * 10).roundToInt(),
            (MAX_SCALE * 10).roundToInt(),
        ) / 10f
        val (boxW, boxH) = boxSize()
        x = clampX(x, boxW)
        y = clampY(y, boxH)
        save()
    }

    private fun resetPosition() {
        val start = defaults()
        x = start.x
        y = start.y
        scale = start.scale
        save()
    }

    private fun load(): Placement = when (target) {
        Target.SPLITS -> RunSplitsController.settings.let { Placement(it.hudX, it.hudY, it.hudScale) }
        Target.SCORE -> ScoreTracker.settings.let { Placement(it.hudX, it.hudY, it.scale) }
        Target.DRAGON -> DragonController.settings().let { Placement(it.hudX, it.hudY, it.scale) }
    }

    private fun defaults(): Placement = when (target) {
        Target.SPLITS -> Placement(DEFAULT_SPLITS_X, DEFAULT_SPLITS_Y, 1f)
        Target.SCORE -> Placement(DEFAULT_SCORE_X, DEFAULT_SCORE_Y, 1f)
        Target.DRAGON -> Placement(DRAGON_DEFAULT_X, DRAGON_DEFAULT_Y, 1f)
    }

    private fun save() {
        val (boxW, boxH) = boxSize()
        val placed = Placement(clampX(x, boxW), clampY(y, boxH), scale)
        when (target) {
            Target.SPLITS -> RunSplitsController.updateSettings {
                it.copy(hudX = placed.x, hudY = placed.y, hudScale = placed.scale)
            }
            Target.SCORE -> ScoreTracker.updateSettings {
                it.copy(hudX = placed.x, hudY = placed.y, scale = placed.scale)
            }
            Target.DRAGON -> DragonController.updateSettings {
                it.copy(hudX = placed.x, hudY = placed.y, scale = placed.scale)
            }
        }
    }

    private data class Placement(val x: Int, val y: Int, val scale: Float)

    private companion object {
        const val SCRIM = 0x88000000.toInt()
        const val ACCENT = 0xFFC9BBFF.toInt()
        const val TEXT = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFFB0B0B0.toInt()
        // Same as DragonSettings defaults; the editor reset returns the box here.
        const val DRAGON_DEFAULT_X = 10
        const val DRAGON_DEFAULT_Y = 60
        const val SCALE_STEP = 0.1f
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 3f
        const val DEFAULT_SPLITS_X = 4
        const val DEFAULT_SPLITS_Y = 60
        const val DEFAULT_SCORE_X = 10
        const val DEFAULT_SCORE_Y = 60
    }
}
