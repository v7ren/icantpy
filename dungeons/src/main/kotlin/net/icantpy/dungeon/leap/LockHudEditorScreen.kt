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
import net.icantpy.hud.HudText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Drag the lock HUD (who / zone / reason / door opener) where you want it. The position is stored as an offset from its
 * default spot, so it keeps working at any window size. Right-click resets it; Esc saves and goes back.
 */
class LockHudEditorScreen : Screen(Component.literal("Lock HUD position")) {
    private val surface = SkiaSurface()
    private var hits: List<GuiHit> = emptyList()
    private var dragging = false
    private var grabX = 0
    private var grabY = 0
    private var startOffsetX = 0
    private var startOffsetY = 0

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        McUi.releaseMouse(Minecraft.getInstance())
        SkiaContext.initialize()
    }

    /** What the HUD shows now, or a sample when nothing is armed, so there is always something to grab. */
    private fun lines(): List<String> =
        LeapOrient.hudText().ifEmpty { listOf("§dPlayerName", "§7Door opener", "§8state m3p1") }

    private fun box(lines: List<String>): HudBox {
        val cfg = LeapMenu.settings()
        val font = Minecraft.getInstance().font
        val widest = lines.maxOf { font.width(HudText.plain(it)) }
        val x = (width - widest) / 2 + cfg.lockHudX
        val y = height - 48 - (lines.size - 1) * 10 + cfg.lockHudY
        return HudBox(x - 2, y - 2, widest + 4, lines.size * 10 + 2)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (TickTimers.settings.look.backgroundBlur) extractBlurredBackground(graphics)
        extractTransparentBackground(graphics)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val chrome = GuiChrome.of(TickTimers.settings.look)
        val shown = lines()
        val hud = box(shown)
        surface.paint(graphics) { canvas ->
            val draw = MenuDraw(canvas, 0, height, 0, width, mouseX, mouseY, chrome)
            draw.round(0, 0, width, height, 0f, draw.palette.scrim)
            draw.grid(20, 0xFF20252E.toInt())
            draw.round(0, 0, width, 44, 0f, draw.palette.window)
            draw.line(0, 44, width, draw.palette.line)
            draw.text("Lock HUD position", 16, 14, draw.palette.text, draw.buttonFont)
            draw.text("Drag the box. Right-click resets.", 190, 16, draw.palette.secondary, draw.descFont)
            draw.button("Done (Esc)", width - 96, 11, ButtonStyle.PRIMARY) { onClose() }
            draw.button("Reset", width - 160, 11, ButtonStyle.GHOST) { reset() }
            hits = draw.hits
        }
        val font = Minecraft.getInstance().font
        var y = hud.y + 2
        shown.forEach { text ->
            val w = font.width(HudText.plain(text))
            graphics.text(font, text, (width - w) / 2 + LeapMenu.settings().lockHudX, y, 0xFFFFFFFF.toInt(), true)
            y += 10
        }
        surface.paint(graphics) { canvas ->
            val draw = MenuDraw(canvas, 0, height, 0, width, mouseX, mouseY, chrome)
            draw.outline(hud.x, hud.y, hud.w, hud.h, 0f, if (dragging) draw.palette.accent else draw.palette.secondary)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            reset()
            return true
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        hits.lastOrNull { it.rect.contains(mx, my) }?.let {
            it.action?.invoke()
            return true
        }
        if (box(lines()).contains(mx, my)) {
            val cfg = LeapMenu.settings()
            dragging = true
            grabX = mx
            grabY = my
            startOffsetX = cfg.lockHudX
            startOffsetY = cfg.lockHudY
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (!dragging) return false
        val dx = event.x().toInt() - grabX
        val dy = event.y().toInt() - grabY
        TickTimers.updateLeap(persist = false) {
            it.copy(
                lockHudX = (startOffsetX + dx).coerceIn(-2000, 2000),
                lockHudY = (startOffsetY + dy).coerceIn(-2000, 2000),
            )
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging) {
            dragging = false
            TickTimers.persist()
        }
        return true
    }

    private fun reset() {
        TickTimers.updateLeap { it.copy(lockHudX = 0, lockHudY = 0) }
    }

    override fun removed() {
        surface.close()
    }

    override fun onClose() {
        TickTimers.persist()
        IcantpyGui.showConfig()
    }
}
