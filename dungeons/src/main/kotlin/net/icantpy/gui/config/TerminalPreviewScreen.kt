package net.icantpy.gui.config

import net.icantpy.dungeon.terminal.TerminalAnimations
import net.icantpy.dungeon.terminal.TerminalClickPolicy
import net.icantpy.dungeon.terminal.TerminalLayout
import net.icantpy.dungeon.terminal.TerminalOverlay
import net.icantpy.dungeon.terminal.TerminalPreviewSample
import net.icantpy.dungeon.terminal.TerminalRenderer
import net.icantpy.dungeon.terminal.TerminalSettings
import net.icantpy.dungeon.terminal.TerminalSolution
import net.icantpy.dungeon.terminal.TerminalSolver
import net.icantpy.dungeon.terminal.TerminalType
import net.icantpy.gui.IcantpyGui
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
 * Tries the terminal solver UI on a made-up terminal and places it. Clicking cells solves the sample locally with the
 * live click rules; dragging the panel outside a cell moves it. The offset is kept locally while dragging and saved on
 * release, like the run HUD editor.
 */
internal class TerminalPreviewScreen : Screen(Component.literal("icantpy terminal preview")) {
    private var typeIndex = 0
    private var sample = TerminalPreviewSample(TerminalType.entries[typeIndex])
    private var offsetX = 0
    private var offsetY = 0
    private var grabX = 0
    private var grabY = 0
    private var dragging = false
    private val editor = TerminalStyleEditor()
    private val animations = TerminalAnimations()

    /** With the editor open the panel is centred in the space beside it; Tab hides it to place the panel for real. */
    private var editorOpen = true

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        offsetX = TerminalOverlay.settings.offsetX
        offsetY = TerminalOverlay.settings.offsetY
        McUi.releaseMouse(Minecraft.getInstance())
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, SCRIM)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val settings = localSettings()
        val solution = solve()
        val layout = layout(solution, settings)
        animations.begin(sample)
        TerminalRenderer.render(
            graphics, solution, layout, settings, sample.title, width, height, mouseX, mouseY, animations,
            stackAt = sample::stackAt,
            canClick = { cell -> TerminalClickPolicy.allows(cell, sample.type, settings) },
        )
        val font = Minecraft.getInstance().font
        graphics.text(font, "Terminal preview: ${sample.type.displayName} (${typeIndex + 1}/${TerminalType.entries.size})", 10, 10, TEXT, true)
        graphics.text(font, "Click cells to solve. Left/Right: terminal. Space: refill.", 10, 22, MUTED, true)
        val placeHint = if (editorOpen) {
            "Tab: hide the style editor to move the panel."
        } else {
            "Drag the panel to move it. +/- scale, R resets. Tab: style editor."
        }
        graphics.text(font, placeHint, 10, 34, MUTED, true)
        if (solution.remaining == 0) graphics.text(font, "Solved. Press Space for a new sample.", 10, 46, ACCENT, true)
        if (editorOpen) editor.render(graphics, width, height, mouseX, mouseY)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val settings = localSettings()
        val solution = solve()
        val layout = layout(solution, settings)
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (editorOpen && editor.contains(width, mx)) return editor.mouseClicked(mx, my)
        val slot = layout.slotAt(mx, my, solution.cells.map { it.slot })
        if (slot != null) {
            val cell = solution.cellAt(slot) ?: return true
            if (!TerminalClickPolicy.allows(cell, sample.type, settings)) return true
            val click = TerminalClickPolicy.clickFor(sample.type, settings, event.button()) ?: return true
            sample.apply(cell, click)
            return true
        }
        if (editorOpen || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !layout.contains(mx, my)) {
            return super.mouseClicked(event, doubleClick)
        }
        dragging = true
        grabX = mx - layout.panelX
        grabY = my - layout.panelY
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (editor.mouseDragged(event.x().toInt())) return true
        if (!dragging) return super.mouseDragged(event, dragX, dragY)
        val settings = localSettings()
        val layout = layout(solve(), settings)
        val (x, y) = TerminalLayout.offsetFor(width, height, layout, event.x().toInt() - grabX, event.y().toInt() - grabY)
        offsetX = x
        offsetY = y
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (editor.mouseReleased()) return true
        if (!dragging) return super.mouseReleased(event)
        dragging = false
        save()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!editorOpen || !editor.contains(width, mouseX.toInt())) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        editor.mouseScrolled(scrollY)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        when (event.key()) {
            GLFW.GLFW_KEY_TAB -> editorOpen = !editorOpen
            GLFW.GLFW_KEY_RIGHT -> switchType(1)
            GLFW.GLFW_KEY_LEFT -> switchType(-1)
            GLFW.GLFW_KEY_SPACE -> sample = TerminalPreviewSample(sample.type)
            GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> adjustScale(SCALE_STEP)
            GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> adjustScale(-SCALE_STEP)
            GLFW.GLFW_KEY_R -> resetPosition()
            else -> return super.keyPressed(event)
        }
        return true
    }

    override fun onClose() {
        save()
        McUi.setScreen(Minecraft.getInstance(), if (IcantpyGui.skiaSupported()) IcantpyConfigScreen() else null)
    }

    /** Saved settings with the offset being dragged, so the panel follows the mouse before it is saved. */
    private fun localSettings(): TerminalSettings =
        if (editorOpen) TerminalOverlay.settings.copy(offsetX = 0, offsetY = 0)
        else TerminalOverlay.settings.copy(offsetX = offsetX, offsetY = offsetY)

    private fun previewWidth(): Int = if (editorOpen) width - TerminalStyleEditor.WIDTH else width

    private fun solve(): TerminalSolution = TerminalSolver.solve(sample.type, sample.snapshot())

    private fun layout(solution: TerminalSolution, settings: TerminalSettings): TerminalLayout =
        TerminalRenderer.layout(previewWidth(), height, solution, sample.title, settings)

    private fun switchType(delta: Int) {
        val count = TerminalType.entries.size
        typeIndex = ((typeIndex + delta) % count + count) % count
        sample = TerminalPreviewSample(TerminalType.entries[typeIndex])
    }

    private fun adjustScale(delta: Float) {
        TerminalOverlay.updateSettings {
            val next = ((it.scale + delta) * 100f).roundToInt() / 100f
            it.copy(scale = next.coerceIn(TerminalSettings.MIN_SCALE, TerminalSettings.MAX_SCALE))
        }
    }

    private fun resetPosition() {
        offsetX = 0
        offsetY = 0
        TerminalOverlay.updateSettings { it.copy(offsetX = 0, offsetY = 0, scale = TerminalSettings.DEFAULT_SCALE) }
    }

    private fun save() {
        if (TerminalOverlay.settings.offsetX == offsetX && TerminalOverlay.settings.offsetY == offsetY) return
        TerminalOverlay.updateSettings { it.copy(offsetX = offsetX, offsetY = offsetY) }
    }

    private companion object {
        const val SCRIM = 0x88000000.toInt()
        const val ACCENT = 0xFFC9BBFF.toInt()
        const val TEXT = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFFB0B0B0.toInt()
        const val SCALE_STEP = 0.25f
    }
}
