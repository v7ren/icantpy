package net.icantpy.gui.config

import net.icantpy.gui.GuiHit
import net.icantpy.gui.McUi
import net.icantpy.gui.SkiaContext
import net.icantpy.gui.SkiaSurface
import net.icantpy.util.HexColor
import net.icantpy.hud.HudBox
import net.icantpy.hud.HudDrag
import net.icantpy.hud.HudHandle
import net.icantpy.hud.HudResize
import net.icantpy.hud.IcantpyHud
import net.icantpy.dungeon.timer.HudPiece
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.dungeon.timer.TimerHud
import net.icantpy.dungeon.timer.TimerSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class IcantpyHudEditorScreen(
    private val initial: TimerHud? = null,
) : Screen(Component.literal("icantpy HUD")) {
    private val surface = SkiaSurface()
    private var selected: TimerHud = initial ?: TimerHud.NOTIFICATION
    private var drag: HudDrag? = null
    private var grabX = 0
    private var grabY = 0
    private var hits: List<GuiHit> = emptyList()
    private val fields = HashMap<String, String>()
    private var focused: String? = null
    private var inspectorRect = HudBox(0, 0, 0, 0)

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        SkiaContext.initialize()
        McUi.releaseMouse(Minecraft.getInstance())
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (TickTimers.settings.look.backgroundBlur) {
            extractBlurredBackground(graphics)
        }
        extractTransparentBackground(graphics)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val chrome = GuiChrome.of(TickTimers.settings.look)
        val pieces = placedPieces()
        val panelW = 210
        val panelX = width - panelW - 10
        val panelY = 54
        val panelH = (height - 98).coerceAtLeast(120)
        inspectorRect = HudBox(panelX, panelY, panelW, panelH)
        surface.paint(graphics) { canvas ->
            val draw = MenuDraw(canvas, 0, height, 0, width, mouseX, mouseY, chrome)
            draw.round(0, 0, width, height, 0f, draw.palette.scrim)
            draw.grid(20, 0xFF18181B.toInt())
            IcantpyHud.drawSkiaPieces(canvas, pieces)
            pieces.forEach { piece ->
                if (piece.id != selected) return@forEach
                val box = IcantpyHud.metrics(piece)
                draw.outline(piece.x - 1, piece.y - 1, box.screenW + 2, box.screenH + 2, 0f, draw.palette.accent)
                HudResize.handleRects(HudBox(piece.x, piece.y, box.screenW, box.screenH)).values.forEach { handle ->
                    draw.round(handle.x, handle.y, handle.w, handle.h, 0f, 0xFFFFFFFF.toInt())
                    draw.round(handle.x + 1, handle.y + 1, handle.w - 2, handle.h - 2, 0f, draw.palette.accent)
                }
            }
            draw.round(0, 0, width, 44, 0f, draw.palette.window)
            draw.line(0, 44, width, draw.palette.line)
            draw.text("HUD Canvas Alignment (Snap 4px)", 16, 14, draw.palette.text, draw.buttonFont)
            draw.button("Done (Esc)", width - 96, 11, ButtonStyle.PRIMARY) {
                onClose()
            }
            draw.round(panelX, panelY, panelW, panelH, draw.radius, draw.palette.window)
            draw.outline(panelX, panelY, panelW, panelH, draw.radius, draw.palette.line)
            paintInspector(draw, panelX, panelY, panelW)
            val dockH = 32
            val dockY = height - dockH - 10
            draw.round(12, dockY, width - 24, dockH, draw.radius, draw.palette.window)
            draw.outline(12, dockY, width - 24, dockH, draw.radius, draw.palette.line)
            draw.round(24, dockY + 12, 8, 8, 0f, draw.palette.accent)
            draw.text(
                "Drag the box to move. Corners scale. Left/right edges set wrap width. Esc saves.",
                40,
                dockY + 9,
                draw.palette.secondary,
                draw.descFont,
            )
            hits = draw.hits
        }
        // Draw Minecraft-font HUD pieces after the editor scrim so they remain readable on the canvas.
        IcantpyHud.drawVanillaPieces(graphics, pieces)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (!inspectorRect.contains(mx, my)) {
                hitPiece(mx, my)?.let { TickTimers.resetHudPosition(it.id) }
            }
            drag = null
            return true
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick)
        }
        if (inspectorRect.contains(mx, my)) {
            val hit = hits.lastOrNull { it.rect.contains(mx, my) }
            focused = hit?.fieldId
            hit?.action?.invoke()
            return true
        }
        focused = null
        val piece = hitPiece(mx, my)
        if (piece != null) {
            selected = piece.id
            val box = IcantpyHud.metrics(piece)
            val hudBox = HudBox(piece.x, piece.y, box.screenW, box.screenH)
            val handle = HudResize.hit(mx, my, hudBox, body = true) ?: HudHandle.MOVE
            val style = TickTimers.settings.hud
            drag = HudDrag(
                handle = handle,
                startX = piece.x,
                startY = piece.y,
                startW = box.screenW,
                startH = box.screenH,
                scale = style.scalePercent(piece.id),
                width = style.notifyWidth,
            )
            grabX = mx - piece.x
            grabY = my - piece.y
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val current = drag ?: return super.mouseDragged(event, dragX, dragY)
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val notify = selected == TimerHud.NOTIFICATION
        if (current.handle == HudHandle.MOVE) {
            val piece = placedPieces().firstOrNull { it.id == selected } ?: return true
            val box = IcantpyHud.metrics(piece)
            val next = TimerSettings.clamp(mx - grabX, my - grabY, box.screenW, box.screenH, width, height)
            TickTimers.setHudPosition(selected, next.first, next.second)
            return true
        }
        val result = HudResize.apply(current, mx, my, notify)
        TickTimers.updateHud(persist = false) { hud ->
            val scaled = hud.withScale(selected, result.scale)
            if (notify) scaled.withNotifyWidth(result.width) else scaled
        }
        val piece = placedPieces().firstOrNull { it.id == selected } ?: return true
        val box = IcantpyHud.metrics(piece)
        val x = when (current.handle) {
            HudHandle.NW, HudHandle.W, HudHandle.SW -> current.startX + current.startW - box.screenW
            else -> current.startX
        }
        val y = when (current.handle) {
            HudHandle.NW, HudHandle.N, HudHandle.NE -> current.startY + current.startH - box.screenH
            else -> current.startY
        }
        val next = TimerSettings.clamp(x, y, box.screenW, box.screenH, width, height)
        TickTimers.setHudPosition(selected, next.first, next.second)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (drag != null) {
            drag = null
            TickTimers.persist()
            return true
        }
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val id = focused
        if (id != null) {
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    focused = null
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    focused = null
                    return true
                }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    val next = live(id).dropLast(1)
                    fields[id] = next
                    applyField(id, next)
                    return true
                }
            }
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_R) {
            TickTimers.resetHudPosition(selected)
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

    override fun onClose() {
        TickTimers.persist()
        McUi.setScreen(Minecraft.getInstance(), IcantpyConfigScreen())
    }

    private fun paintInspector(draw: MenuDraw, x: Int, y: Int, w: Int) {
        val style = TickTimers.settings.hud
        val notify = selected == TimerHud.NOTIFICATION
        val pad = 12
        var cy = y + pad
        draw.text(if (notify) "Alert HUD" else selected.name.lowercase(), x + pad, cy, draw.palette.text, draw.headingFont)
        cy += 18
        draw.text(style.font(selected).label(), x + pad, cy, draw.palette.secondary, draw.smallFont)
        val nextW = draw.button("Next", x + w - pad - 52, cy - 4, ButtonStyle.PRIMARY) {
            TickTimers.updateHud { it.withFont(selected, it.font(selected).next()) }
        }
        draw.button("Prev", x + w - pad - 52 - nextW - 6, cy - 4, ButtonStyle.GHOST) {
            TickTimers.updateHud { it.withFont(selected, it.font(selected).previous()) }
        }
        cy += 28
        draw.text("Font size", x + pad, cy, draw.palette.secondary, draw.microFont)
        val sizeId = "hud:size"
        draw.button("-", x + pad, cy + 14, ButtonStyle.GHOST) {
            TickTimers.updateHud { it.withFontSize(selected, it.fontSize(selected) - 1) }
            fields[sizeId] = TickTimers.settings.hud.fontSize(selected).toString()
        }
        draw.field(sizeId, live(sizeId, style.fontSize(selected).toString()), "12", x + pad + 28, cy + 14, 48, focused == sizeId)
        draw.button("+", x + pad + 82, cy + 14, ButtonStyle.GHOST) {
            TickTimers.updateHud { it.withFontSize(selected, it.fontSize(selected) + 1) }
            fields[sizeId] = TickTimers.settings.hud.fontSize(selected).toString()
        }
        cy += 44
        draw.text("Scale ${style.scalePercent(selected)}%", x + pad, cy, draw.palette.secondary, draw.microFont)
        cy += 16
        if (notify) {
            val widthId = "hud:width"
            draw.text("Box width", x + pad, cy, draw.palette.secondary, draw.microFont)
            draw.field(
                widthId,
                live(widthId, style.notifyWidth.toString()),
                "200",
                x + w - pad - 56,
                cy - 2,
                56,
                focused == widthId,
            )
            cy += 24
            draw.text("Text", x + pad, cy, draw.palette.secondary, draw.microFont)
            val textId = "hud:text"
            draw.field(
                textId,
                live(textId, "%06X".format(style.notifyTextRgb)),
                "FFFF55",
                x + w - pad - 72,
                cy - 2,
                72,
                focused == textId,
            )
            cy += 22
            HudOverlaySettings.TEXT_PRESETS.forEachIndexed { index, rgb ->
                draw.swatch(x + pad + index * 22, cy, 0xFF000000.toInt() or rgb, style.notifyTextRgb == rgb) {
                    TickTimers.updateHud { it.copy(notifyTextRgb = rgb) }
                    fields[textId] = "%06X".format(rgb)
                }
            }
            cy += 26
            draw.text("Box", x + pad, cy, draw.palette.secondary, draw.microFont)
            val boxId = "hud:box"
            draw.field(
                boxId,
                live(boxId, "%06X".format(style.notifyBoxRgb)),
                "000000",
                x + w - pad - 72,
                cy - 2,
                72,
                focused == boxId,
            )
            cy += 22
            HudOverlaySettings.BOX_PRESETS.forEachIndexed { index, rgb ->
                draw.swatch(x + pad + index * 22, cy, 0xFF000000.toInt() or rgb, style.notifyBoxRgb == rgb) {
                    TickTimers.updateHud { it.copy(notifyBoxRgb = rgb) }
                    fields[boxId] = "%06X".format(rgb)
                }
            }
            cy += 26
            draw.text("Box opacity ${alphaPercent(style.notifyBoxAlpha)}%", x + pad, cy, draw.palette.secondary, draw.microFont)
            cy += 16
            draw.button("-", x + pad, cy, ButtonStyle.GHOST) {
                TickTimers.updateHud { it.copy(notifyBoxAlpha = (it.notifyBoxAlpha - 16).coerceIn(0, 255)) }
            }
            draw.button("+", x + pad + 32, cy, ButtonStyle.GHOST) {
                TickTimers.updateHud { it.copy(notifyBoxAlpha = (it.notifyBoxAlpha + 16).coerceIn(0, 255)) }
            }
            cy += 28
            draw.text("Bold", x + pad, cy + 2, draw.palette.text, draw.smallFont)
            draw.toggle(x + w - pad - 38, cy, style.notifyBold) {
                TickTimers.updateHud { it.copy(notifyBold = !it.notifyBold) }
            }
            cy += 24
            draw.text("Shadow", x + pad, cy + 2, draw.palette.text, draw.smallFont)
            draw.toggle(x + w - pad - 38, cy, style.notifyShadow) {
                TickTimers.updateHud { it.copy(notifyShadow = !it.notifyShadow) }
            }
            cy += 24
            draw.text("Lock color", x + pad, cy + 2, draw.palette.text, draw.smallFont)
            draw.toggle(x + w - pad - 38, cy, style.lockNotifyColor) {
                TickTimers.updateHud { it.copy(lockNotifyColor = !it.lockNotifyColor) }
            }
        } else {
            draw.text("Text", x + pad, cy, draw.palette.secondary, draw.microFont)
            val textId = "hud:timerText"
            draw.field(
                textId,
                live(textId, "%06X".format(style.timerTextRgb)),
                "FFFFFF",
                x + w - pad - 72,
                cy - 2,
                72,
                focused == textId,
            )
            cy += 22
            HudOverlaySettings.TEXT_PRESETS.forEachIndexed { index, rgb ->
                draw.swatch(x + pad + index * 22, cy, 0xFF000000.toInt() or rgb, style.timerTextRgb == rgb) {
                    TickTimers.updateHud { it.copy(timerTextRgb = rgb) }
                    fields[textId] = "%06X".format(rgb)
                }
            }
            cy += 26
            draw.text("Shadow", x + pad, cy + 2, draw.palette.text, draw.smallFont)
            draw.toggle(x + w - pad - 38, cy, style.timerShadow) {
                TickTimers.updateHud { it.copy(timerShadow = !it.timerShadow) }
            }
        }
        cy += 32
        draw.button("Reset style", x + pad, cy, ButtonStyle.DANGER) {
            TickTimers.updateHud { current ->
                if (notify) {
                    current.copy(
                        notifyScale = 150,
                        notifyWidth = 200,
                        notifyFont = HudFontId.MINECRAFT,
                        notifyFontSize = HudOverlaySettings.DEFAULT_FONT_SIZE,
                        notifyTextRgb = HudOverlaySettings.DEFAULT_NOTIFY_TEXT,
                        notifyBoxRgb = 0x000000,
                        notifyBoxAlpha = HudOverlaySettings.DEFAULT_BOX_ALPHA,
                        notifyShadow = true,
                        notifyBold = true,
                        lockNotifyColor = true,
                    )
                } else {
                    current.copy(
                        timerScale = 100,
                        timerFont = HudFontId.MINECRAFT,
                        timerFontSize = HudOverlaySettings.DEFAULT_FONT_SIZE,
                        timerTextRgb = 0xFFFFFF,
                        timerShadow = true,
                    )
                }
            }
            fields.clear()
        }
    }

    private fun applyField(id: String, value: String) {
        when (id) {
            "hud:size" -> value.toIntOrNull()?.let { size ->
                TickTimers.updateHud { it.withFontSize(selected, size) }
            }
            "hud:width" -> value.toIntOrNull()?.let { width ->
                TickTimers.updateHud { it.withNotifyWidth(width) }
            }
            "hud:text" -> HexColor.parse(value).onSuccess { rgb ->
                TickTimers.updateHud { it.copy(notifyTextRgb = rgb) }
            }
            "hud:box" -> HexColor.parse(value).onSuccess { rgb ->
                TickTimers.updateHud { it.copy(notifyBoxRgb = rgb) }
            }
            "hud:timerText" -> HexColor.parse(value).onSuccess { rgb ->
                TickTimers.updateHud { it.copy(timerTextRgb = rgb) }
            }
        }
    }

    private fun live(id: String, fallback: String = fields[id].orEmpty()): String {
        if (focused == id) return fields.getOrPut(id) { fallback }
        fields[id] = fallback
        return fallback
    }

    private fun placedPieces(): List<HudPiece> = IcantpyHud.editorPieces(width, height).map { piece ->
        val box = IcantpyHud.metrics(piece)
        val (x, y) = TimerSettings.clamp(piece.x, piece.y, box.screenW, box.screenH, width, height)
        piece.copy(x = x, y = y)
    }

    private fun hitPiece(mx: Int, my: Int): HudPiece? {
        val pieces = placedPieces()
        pieces.firstOrNull { piece ->
            if (piece.id != selected) return@firstOrNull false
            val box = IcantpyHud.metrics(piece)
            HudResize.hit(mx, my, HudBox(piece.x, piece.y, box.screenW, box.screenH), body = false) != null
        }?.let { return it }
        return pieces.lastOrNull { piece ->
            val box = IcantpyHud.metrics(piece)
            HudResize.hit(mx, my, HudBox(piece.x, piece.y, box.screenW, box.screenH), body = true) != null
        }
    }

    private fun alphaPercent(alpha: Int): Int = (alpha * 100 / 255f).toInt()
}
