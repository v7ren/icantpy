package net.icantpy.modules.impl.render

import net.icantpy.gui.IcantpyGui
import net.icantpy.gui.McUi
import net.icantpy.gui.SkiaContext
import net.icantpy.gui.SkiaSurface
import net.icantpy.gui.configUI.GuiFonts
import net.icantpy.gui.configUI.HudFontId
import net.icantpy.gui.configUI.HudOverlaySettings
import net.icantpy.modules.impl.dungeon.leaporient.LeapMenu
import net.icantpy.modules.impl.dungeon.leaporient.LeapMenuScreen
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrient
import net.icantpy.modules.impl.timer.HudPiece
import net.icantpy.modules.impl.timer.TickTimers
import net.icantpy.modules.impl.timer.TimerHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Typeface
import kotlin.math.roundToInt

object IcantpyHud {
    private const val BASE_SIZE: Float = 12f
    private const val PAD: Int = 4

    private val mc: Minecraft get() = Minecraft.getInstance()
    private val skia = SkiaSurface()
    private val fill = Paint()

    fun render(graphics: GuiGraphicsExtractor) {
        val screen = McUi.currentScreen(mc)
        if (IcantpyGui.isOurScreen(screen) ||
            LeapMenuScreen.isOpen() ||
            (screen != null && LeapMenu.overlayReady(screen))
        ) {
            return
        }
        paint(graphics, preview = false)
    }

    fun paint(graphics: GuiGraphicsExtractor, preview: Boolean) {
        val pieces = visiblePieces(preview)
        renderLeapOrientHud(graphics, preview)
        val minecraftPieces = pieces.filter { fontId(it.id).guiFont() == null }
        val skiaPieces = pieces.filter { fontId(it.id).guiFont() != null }
        if (skiaPieces.isNotEmpty()) {
            SkiaContext.initialize()
            skia.paint(graphics) { canvas ->
                drawSkiaPieces(canvas, skiaPieces)
            }
        }
        minecraftPieces.forEach { piece ->
            drawMinecraft(graphics, mc.font, piece)
        }
    }

    fun drawSkiaPieces(canvas: org.jetbrains.skia.Canvas, pieces: List<HudPiece>) {
        pieces.filter { fontId(it.id).guiFont() != null }.forEach { piece ->
            drawSkia(canvas, piece)
        }
    }

    fun drawVanillaPieces(graphics: GuiGraphicsExtractor, pieces: List<HudPiece>) {
        pieces.filter { fontId(it.id).guiFont() == null }.forEach { piece ->
            drawMinecraft(graphics, mc.font, piece)
        }
    }

    fun editorPieces(screenW: Int, screenH: Int): List<HudPiece> {
        val live = TickTimers.hudPieces(preview = false)
        val timers = live.ifEmpty { TickTimers.hudPieces(preview = true) }
        return timers + notificationPiece(screenW, screenH)
    }

    fun metrics(piece: HudPiece): HudMetrics = layout(piece.text, piece.id)

    fun notificationPiece(screenW: Int, screenH: Int): HudPiece {
        val text = TickTimers.notifications.firstOrNull()?.text ?: "§e§lleave pad"
        val box = layout(text, TimerHud.NOTIFICATION)
        val (x, y) = TickTimers.settings.resolveNotifyPosition(box.screenW, box.screenH, screenW, screenH)
        return HudPiece(TimerHud.NOTIFICATION, text, x, y)
    }

    private fun renderLeapOrientHud(graphics: GuiGraphicsExtractor, preview: Boolean) {
        if (preview) return
        val lines = LeapOrient.hudText()
        if (lines.isEmpty()) return
        val window = mc.window
        var y = window.guiScaledHeight - 48 - (lines.size - 1) * 10
        lines.forEach { text ->
            val width = mc.font.width(HudText.plain(text))
            val x = (window.guiScaledWidth - width) / 2
            graphics.text(mc.font, text, x, y, 0xFFFFFFFF.toInt(), true)
            y += 10
        }
    }

    private fun visiblePieces(preview: Boolean): List<HudPiece> {
        val timers = TickTimers.hudPieces(preview)
        val window = mc.window
        val notify = if (TickTimers.notifications.isNotEmpty() || preview) {
            notificationPiece(window.guiScaledWidth, window.guiScaledHeight)
        } else {
            null
        }
        return if (notify == null) timers else timers + notify
    }

    private fun layout(text: String, hud: TimerHud): HudMetrics {
        val style = TickTimers.settings.hud
        val fontId = style.font(hud)
        val fontPx = style.fontSize(hud).toFloat()
        val minecraft = fontId.guiFont() == null
        val scale = if (minecraft) {
            (fontPx / BASE_SIZE) * style.scaleValue(hud)
        } else {
            style.scaleValue(hud)
        }
        val lineH = if (minecraft) 12 else (fontPx * 1.2f).roundToInt().coerceAtLeast(8)
        val wrap = if (hud == TimerHud.NOTIFICATION) (style.wrapWidth() / scale).toInt().coerceAtLeast(8) else 0
        val display = displayText(text, hud, style)
        val widthOf: (String) -> Int = if (minecraft) {
            { value -> mc.font.width(value) }
        } else {
            { value -> skiaWidth(fontId, value, fontPx, style.notifyBold && hud == TimerHud.NOTIFICATION) }
        }
        val lines = HudText.wrap(display, wrap, widthOf)
        val textW = lines.maxOfOrNull { widthOf(it) } ?: 0
        val innerW = if (hud == TimerHud.NOTIFICATION) wrap.coerceAtLeast(textW) else textW
        val innerH = (lines.size * lineH).coerceAtLeast(lineH)
        return HudMetrics(lines, innerW, innerH, scale, fontId, fontPx, lineH, hud)
    }

    private fun fontId(hud: TimerHud): HudFontId = TickTimers.settings.hud.font(hud)

    private fun displayText(text: String, hud: TimerHud, style: HudOverlaySettings): String {
        if (hud != TimerHud.NOTIFICATION) return HudText.plain(text)
        if (style.lockNotifyColor) {
            val plain = HudText.plain(text)
            return if (style.notifyBold) "§l$plain" else plain
        }
        return if (style.notifyBold && !text.contains("§l")) "§l$text" else text
    }

    private fun drawMinecraft(graphics: GuiGraphicsExtractor, font: Font, piece: HudPiece) {
        val box = layout(piece.text, piece.id)
        val style = TickTimers.settings.hud
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(piece.x.toFloat(), piece.y.toFloat())
        pose.scale(box.scale, box.scale)
        if (piece.id == TimerHud.NOTIFICATION) {
            val argb = style.notifyBoxArgb()
            if ((argb ushr 24) != 0) {
                graphics.fill(-PAD, -PAD, box.innerW + PAD, box.innerH + PAD, argb)
            }
        }
        val lock = piece.id == TimerHud.NOTIFICATION && style.lockNotifyColor
        val color = if (lock || piece.id != TimerHud.NOTIFICATION) {
            0xFF000000.toInt() or style.textRgb(piece.id)
        } else {
            0xFFFFFFFF.toInt()
        }
        box.lines.forEachIndexed { index, line ->
            graphics.text(font, line, 0, index * box.lineH, color, style.shadow(piece.id))
        }
        pose.popMatrix()
    }

    private fun drawSkia(canvas: org.jetbrains.skia.Canvas, piece: HudPiece) {
        val box = layout(piece.text, piece.id)
        val style = TickTimers.settings.hud
        val x = piece.x.toFloat()
        val y = piece.y.toFloat()
        val guiFont = box.font.guiFont() ?: return
        val size = box.fontPx * box.scale
        if (piece.id == TimerHud.NOTIFICATION) {
            val argb = style.notifyBoxArgb()
            if ((argb ushr 24) != 0) {
                fill.color = argb
                fill.isAntiAlias = false
                canvas.drawRect(
                    Rect.makeXYWH(
                        x - PAD * box.scale,
                        y - PAD * box.scale,
                        box.screenW + PAD * 2 * box.scale,
                        box.screenH + PAD * 2 * box.scale,
                    ),
                    fill,
                )
            }
        }
        val forceBold = piece.id == TimerHud.NOTIFICATION && style.notifyBold
        val regular = sized(GuiFonts.of(guiFont).regular, size)
        val bold = sized(GuiFonts.of(guiFont).semibold, size)
        val lineH = box.lineH * box.scale
        val lock = piece.id == TimerHud.NOTIFICATION && style.lockNotifyColor
        val base = if (lock || piece.id != TimerHud.NOTIFICATION) {
            0xFF000000.toInt() or style.textRgb(piece.id)
        } else {
            0xFFFFFFFF.toInt()
        }
        box.lines.forEachIndexed { index, line ->
            var cursor = x
            val baseline = y + index * lineH - regular.metrics.ascent
            for (run in colorRuns(line, base, lock, forceBold)) {
                val font = if (run.bold) bold else regular
                if (style.shadow(piece.id)) {
                    fill.color = 0xAA000000.toInt()
                    fill.isAntiAlias = true
                    canvas.drawString(run.text, cursor + 1f, baseline + 1f, font, fill)
                }
                fill.color = run.color
                fill.isAntiAlias = true
                canvas.drawString(run.text, cursor, baseline, font, fill)
                cursor += font.measureText(run.text).width
            }
        }
    }

    private fun skiaWidth(fontId: HudFontId, text: String, size: Float, bold: Boolean): Int {
        val guiFont = fontId.guiFont() ?: return text.length * 6
        val typeface = if (bold) GuiFonts.of(guiFont).semibold else GuiFonts.of(guiFont).regular
        val font = sized(typeface, size)
        var width = 0f
        for (run in colorRuns(text, 0xFFFFFFFF.toInt(), lock = false, forceBold = bold)) {
            width += font.measureText(run.text).width
        }
        return width.toInt()
    }

    private fun sized(typeface: Typeface, size: Float) = org.jetbrains.skia.Font(typeface, size).apply {
        edging = FontEdging.SUBPIXEL_ANTI_ALIAS
        hinting = FontHinting.SLIGHT
        isSubpixel = true
    }

    private fun colorRuns(text: String, base: Int, lock: Boolean, forceBold: Boolean): List<ColorRun> {
        val runs = ArrayList<ColorRun>()
        val buffer = StringBuilder()
        var color = base
        var bold = forceBold
        fun flush() {
            if (buffer.isNotEmpty()) {
                runs += ColorRun(buffer.toString(), color, bold)
                buffer.clear()
            }
        }
        var index = 0
        while (index < text.length) {
            if (text[index] == '§' && index + 1 < text.length) {
                flush()
                when (val code = text[index + 1].lowercaseChar()) {
                    'l' -> bold = true
                    'r' -> {
                        bold = forceBold
                        color = base
                    }
                    else -> if (!lock) COLORS[code]?.let { color = it }
                }
                index += 2
            } else {
                buffer.append(text[index])
                index += 1
            }
        }
        flush()
        return runs
    }

    private data class ColorRun(val text: String, val color: Int, val bold: Boolean)
}

data class HudMetrics(
    val lines: List<String>,
    val innerW: Int,
    val innerH: Int,
    val scale: Float,
    val font: HudFontId,
    val fontPx: Float,
    val lineH: Int,
    val hud: TimerHud,
) {
    val screenW: Int get() = (innerW * scale).toInt().coerceAtLeast(8)
    val screenH: Int get() = (innerH * scale).toInt().coerceAtLeast(8)
}

private val COLORS: Map<Char, Int> = mapOf(
    '0' to 0xFF000000.toInt(),
    '1' to 0xFF0000AA.toInt(),
    '2' to 0xFF00AA00.toInt(),
    '3' to 0xFF00AAAA.toInt(),
    '4' to 0xFFAA0000.toInt(),
    '5' to 0xFFAA00AA.toInt(),
    '6' to 0xFFFFAA00.toInt(),
    '7' to 0xFFAAAAAA.toInt(),
    '8' to 0xFF555555.toInt(),
    '9' to 0xFF5555FF.toInt(),
    'a' to 0xFF55FF55.toInt(),
    'b' to 0xFF55FFFF.toInt(),
    'c' to 0xFFFF5555.toInt(),
    'd' to 0xFFFF55FF.toInt(),
    'e' to 0xFFFFFF55.toInt(),
    'f' to 0xFFFFFFFF.toInt(),
)
