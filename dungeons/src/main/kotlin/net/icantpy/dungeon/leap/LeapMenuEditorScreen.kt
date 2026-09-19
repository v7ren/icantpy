package net.icantpy.dungeon.leap

import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.gui.GuiHit
import net.icantpy.gui.IcantpyGui
import net.icantpy.gui.McUi
import net.icantpy.gui.SkiaContext
import net.icantpy.gui.SkiaSurface
import net.icantpy.gui.config.ButtonStyle
import net.icantpy.gui.config.GuiChrome
import net.icantpy.gui.config.MenuDraw
import net.icantpy.hud.HudBox
import net.icantpy.hud.HudHandle
import net.icantpy.hud.HudResize
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class LeapMenuEditorScreen : Screen(Component.literal("Spirit Leap layout")) {
    private val surface = SkiaSurface()
    private val history = LeapMenuEditHistory()
    private val players = listOf(
        LeapPlayer("Archer", LeapDungeonClass.ARCHER),
        LeapPlayer("Berserk", LeapDungeonClass.BERSERK),
        LeapPlayer("Healer", LeapDungeonClass.HEALER),
        LeapPlayer("Mage", LeapDungeonClass.MAGE),
    )
    private var selected = setOf<Int>()
    private var drag: Drag? = null
    private var checkpoint: LeapOrientSettings? = null
    private var hits: List<GuiHit> = emptyList()
    private val fields = HashMap<String, String>()
    private var focused: String? = null
    private var inspector = HudBox(0, 0, 0, 0)

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        McUi.releaseMouse(Minecraft.getInstance())
        SkiaContext.initialize()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (TickTimers.settings.look.backgroundBlur) {
            extractBlurredBackground(graphics)
        }
        extractTransparentBackground(graphics)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val cfg = LeapMenu.settings()
        val chrome = GuiChrome.of(TickTimers.settings.look)
        val panelW = 210
        val panelX = width - panelW - 10
        val panelY = 54
        val panelH = (height - 98).coerceAtLeast(120)
        inspector = HudBox(panelX, panelY, panelW, panelH)
        surface.paint(graphics) { canvas ->
            val draw = MenuDraw(canvas, 0, height, 0, width, mouseX, mouseY, chrome)
            draw.round(0, 0, width, height, 0f, draw.palette.scrim)
            draw.grid(20, 0xFF18181B.toInt())
            hits = draw.hits
        }
        LeapMenuRenderer.render(
            graphics, players, width, height, mouseX, mouseY, FloatArray(5),
            cfg.scale, cfg.colorStyle, cfg.onlyClass,
            LeapPlayer("Orient target", LeapDungeonClass.TANK), "Center", cfg,
        )
        surface.paint(graphics) { canvas ->
            val draw = MenuDraw(canvas, 0, height, 0, width, mouseX, mouseY, chrome)
            paintSelection(draw)
            draw.round(0, 0, width, 44, 0f, draw.palette.window)
            draw.line(0, 44, width, draw.palette.line)
            draw.text("Leap layout", 16, 14, draw.palette.text, draw.buttonFont)
            draw.button("Done (Esc)", width - 96, 11, ButtonStyle.PRIMARY) { onClose() }
            draw.round(panelX, panelY, panelW, panelH, draw.radius, draw.palette.window)
            draw.outline(panelX, panelY, panelW, panelH, draw.radius, draw.palette.line)
            paintInspector(draw, panelX, panelY, panelW)
            val dockY = height - 42
            draw.round(12, dockY, width - 24, 32, draw.radius, draw.palette.window)
            draw.outline(12, dockY, width - 24, 32, draw.radius, draw.palette.line)
            draw.text(
                "Shift-click adds. Handles grow from that edge. Click brings a card forward. Ctrl+A / Z / Y.",
                24, dockY + 9, draw.palette.secondary, draw.descFont,
            )
            hits = draw.hits
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            resetAll()
            return true
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val hit = hits.lastOrNull { it.rect.contains(mx, my) }
        if (hit != null) {
            focused = hit.fieldId
            hit.action?.invoke()
            return true
        }
        if (inspector.contains(mx, my)) {
            focused = null
            return true
        }
        focused = null
        val cfg = LeapMenu.settings()
        val boxes = LeapMenuLayout.boxes(width, height, cfg)
        val bounds = LeapMenuEditGeometry.bounds(boxes, selected)
        if (bounds != null) {
            val handle = HudResize.hit(mx, my, hudBox(bounds), body = false)
            if (handle != null) {
                beginDrag(handle, selected, boxes, bounds, mx, my, cfg)
                return true
            }
        }
        val index = LeapMenuLayout.layers(cfg).asReversed().firstOrNull { boxes[it].contains(mx, my) }
        if (index == null) {
            if (!shiftHeld()) selected = emptySet()
            return true
        }
        TickTimers.updateLeap { LeapMenuLayout.bringToFront(it, index) }
        if (shiftHeld()) {
            selected = if (index in selected) selected - index else selected + index
            return true
        }
        selected = if (index in selected) selected else setOf(index)
        val group = LeapMenuEditGeometry.bounds(boxes, selected) ?: return true
        beginDrag(HudHandle.MOVE, selected, boxes, group, mx, my, cfg)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val current = drag ?: return false
        val mx = event.x().toFloat()
        val my = event.y().toFloat()
        val next = LeapMenuEditGeometry.applyDrag(
            current.startBoxes, current.selected, current.handle, current.startBounds,
            current.grabX, current.grabY, mx, my, width, height,
        )
        if (checkpoint != null) {
            history.record(current.checkpoint)
            checkpoint = null
        }
        TickTimers.updateLeap(persist = false) {
            LeapMenuLayout.write(it, next, width, height, current.selected, current.handle != HudHandle.MOVE)
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (drag != null) {
            drag = null
            TickTimers.persist()
            return true
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val id = focused
        if (id != null) {
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> focused = null
                GLFW.GLFW_KEY_BACKSPACE -> {
                    val next = live(id).dropLast(1)
                    fields[id] = next
                    applyField(id, next)
                }
            }
            return true
        }
        if (controlHeld() && event.key() == GLFW.GLFW_KEY_A) {
            selected = setOf(0, 1, 2, 3, 4)
            return true
        }
        if (controlHeld() && event.key() == GLFW.GLFW_KEY_Z) {
            applyHistory(if (shiftHeld()) history.redo(LeapMenu.settings()) else history.undo(LeapMenu.settings()))
            return true
        }
        if (controlHeld() && event.key() == GLFW.GLFW_KEY_Y) {
            applyHistory(history.redo(LeapMenu.settings()))
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_R) {
            resetSelected()
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val id = focused ?: return super.charTyped(event)
        val ch = event.codepoint()
        if (ch < 32) return true
        val next = live(id) + Character.toString(ch)
        fields[id] = next
        applyField(id, next)
        return true
    }

    override fun removed() {
        LeapMenuRenderer.release()
        surface.close()
    }

    override fun onClose() {
        TickTimers.persist()
        IcantpyGui.showConfig()
    }

    private fun paintSelection(draw: MenuDraw) {
        val boxes = LeapMenuLayout.boxes(width, height, LeapMenu.settings())
        selected.forEach { index ->
            val box = boxes.getOrNull(index) ?: return@forEach
            draw.outline(
                box.left.roundToInt() - 1, box.top.roundToInt() - 1,
                box.width.roundToInt() + 2, box.height.roundToInt() + 2,
                0f, draw.palette.accent,
            )
        }
        val bounds = LeapMenuEditGeometry.bounds(boxes, selected) ?: return
        val hud = hudBox(bounds)
        if (selected.size > 1) {
            draw.outline(hud.x - 2, hud.y - 2, hud.w + 4, hud.h + 4, 0f, draw.palette.secondary)
        }
        HudResize.handleRects(hud).values.forEach { handle ->
            draw.round(handle.x, handle.y, handle.w, handle.h, 0f, 0xFFFFFFFF.toInt())
            draw.round(handle.x + 1, handle.y + 1, handle.w - 2, handle.h - 2, 0f, draw.palette.accent)
        }
    }

    private fun paintInspector(draw: MenuDraw, x: Int, y: Int, w: Int) {
        val pad = 12
        var cy = y + pad
        val boxes = LeapMenuLayout.boxes(width, height, LeapMenu.settings())
        val title = when {
            selected.isEmpty() -> "No cards selected"
            selected.size == 1 -> NAMES[selected.first()]
            else -> "${selected.size} cards"
        }
        draw.text(title, x + pad, cy, draw.palette.text, draw.headingFont)
        cy += 22
        draw.button("Select all", x + pad, cy, ButtonStyle.GHOST) { selected = setOf(0, 1, 2, 3, 4) }
        draw.button("Undo", x + pad + 86, cy, ButtonStyle.GHOST) {
            applyHistory(history.undo(LeapMenu.settings()))
        }
        draw.button("Redo", x + pad + 140, cy, ButtonStyle.GHOST) {
            applyHistory(history.redo(LeapMenu.settings()))
        }
        cy += 32
        if (selected.isEmpty()) {
            draw.text("Click a card. Shift-click to add.", x + pad, cy, draw.palette.secondary, draw.descFont)
            cy += 36
            draw.button("Reset all", x + pad, cy, ButtonStyle.DANGER) { resetAll() }
            return
        }
        draw.text("Width", x + pad, cy, draw.palette.secondary, draw.microFont)
        sizeRow(draw, "leap:w", x + pad, cy + 14, LeapMenuEditGeometry.sharedDimension(boxes, selected, true)) { value ->
            applySize(width = value)
        }
        cy += 48
        draw.text("Height", x + pad, cy, draw.palette.secondary, draw.microFont)
        sizeRow(draw, "leap:h", x + pad, cy + 14, LeapMenuEditGeometry.sharedDimension(boxes, selected, false)) { value ->
            applySize(height = value)
        }
        cy += 52
        draw.button("Reset selected", x + pad, cy, ButtonStyle.DANGER) { resetSelected() }
        cy += 28
        draw.button("Reset all", x + pad, cy, ButtonStyle.GHOST) { resetAll() }
    }

    private fun sizeRow(draw: MenuDraw, id: String, x: Int, y: Int, value: Float?, apply: (Float) -> Unit) {
        val shown = value?.roundToInt()?.toString().orEmpty()
        draw.button("-", x, y, ButtonStyle.GHOST) {
            val current = value ?: return@button
            apply((current - 4f).coerceAtLeast(LeapMenuEditGeometry.MIN_WIDTH))
        }
        draw.field(id, live(id, shown), "mix", x + 28, y, 56, focused == id)
        draw.button("+", x + 90, y, ButtonStyle.GHOST) {
            val current = value ?: return@button
            apply(current + 4f)
        }
    }

    private fun applyField(id: String, raw: String) {
        val value = raw.toFloatOrNull() ?: return
        when (id) {
            "leap:w" -> applySize(width = value, record = false)
            "leap:h" -> applySize(height = value, record = false)
        }
    }

    private fun applySize(width: Float? = null, height: Float? = null, record: Boolean = true) {
        if (selected.isEmpty()) return
        val current = LeapMenu.settings()
        val boxes = LeapMenuLayout.boxes(this.width, this.height, current)
        val next = LeapMenuEditGeometry.setSize(boxes, selected, width, height, this.width, this.height)
        if (record) history.record(current)
        TickTimers.updateLeap { LeapMenuLayout.write(it, next, this.width, this.height, selected, true) }
    }

    private fun resetSelected() {
        if (selected.isEmpty()) return
        history.record(LeapMenu.settings())
        TickTimers.updateLeap { LeapMenuLayout.clear(it, selected) }
    }

    private fun resetAll() {
        history.record(LeapMenu.settings())
        TickTimers.updateLeap { it.copy(boxPositions = emptyMap(), boxSizes = emptyMap(), boxOrder = emptyList()) }
        selected = emptySet()
        drag = null
    }

    private fun beginDrag(
        handle: HudHandle,
        indices: Set<Int>,
        boxes: List<LeapMenuBox>,
        bounds: LeapMenuBox,
        mx: Int,
        my: Int,
        cfg: LeapOrientSettings,
    ) {
        checkpoint = cfg
        drag = Drag(handle, indices, boxes, bounds, mx - bounds.left, my - bounds.top, cfg)
    }

    private fun applyHistory(next: LeapOrientSettings?) {
        if (next == null) return
        TickTimers.updateLeap { next }
        drag = null
        fields.clear()
    }

    private fun live(id: String, fallback: String = fields[id].orEmpty()): String {
        if (focused == id) return fields.getOrPut(id) { fallback }
        fields[id] = fallback
        return fallback
    }

    private fun hudBox(box: LeapMenuBox): HudBox =
        HudBox(box.left.roundToInt(), box.top.roundToInt(), box.width.roundToInt().coerceAtLeast(1), box.height.roundToInt().coerceAtLeast(1))

    private fun shiftHeld(): Boolean = Minecraft.getInstance().hasShiftDown()
    private fun controlHeld(): Boolean = Minecraft.getInstance().hasControlDown()

    private data class Drag(
        val handle: HudHandle,
        val selected: Set<Int>,
        val startBoxes: List<LeapMenuBox>,
        val startBounds: LeapMenuBox,
        val grabX: Float,
        val grabY: Float,
        val checkpoint: LeapOrientSettings,
    )

    companion object {
        private val NAMES = listOf("Top left", "Top right", "Bottom left", "Bottom right", "Center")
    }
}
