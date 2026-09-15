package net.icantpy.modules.impl.dungeon.leaporient

import net.icantpy.gui.SkiaContext
import net.icantpy.gui.SkiaSurface
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import kotlin.math.min

object LeapMenuRenderer {
    const val BOX_WIDTH = 200
    const val BOX_HEIGHT = 75
    const val CENTER_WIDTH = 180
    const val CENTER_HEIGHT = 64
    private const val HOVER_MS = 200f
    private const val BACKGROUND = 0xFF616161.toInt()
    const val DIM = 0x40000000.toInt()
    private val skia = SkiaSurface()
    private val fill = Paint()

    fun tickHover(
        hover: FloatArray,
        screenW: Int,
        screenH: Int,
        mouseX: Int,
        mouseY: Int,
        lastTickMs: Long,
        centerActive: Boolean,
        scale: Float,
    ): Long {
        val now = System.currentTimeMillis()
        val dt = (now - lastTickMs).coerceAtMost(50)
        val halfW = screenW / 2
        val halfH = screenH / 2
        val centerHovered = centerActive &&
            LeapOrientSpots.centerHit(screenW, screenH, mouseX, mouseY, scale, CENTER_WIDTH, CENTER_HEIGHT)
        repeat(4) { index ->
            val col = index % 2
            val row = index / 2
            val hovered = !centerHovered &&
                (if (col == 0) mouseX < halfW else mouseX >= halfW) &&
                (if (row == 0) mouseY < halfH else mouseY >= halfH)
            approach(hover, index, hovered, dt)
        }
        if (hover.size > 4) {
            approach(hover, 4, centerHovered, dt)
        }
        return now
    }

    fun render(
        graphics: GuiGraphicsExtractor,
        teammates: List<LeapPlayer>,
        screenW: Int,
        screenH: Int,
        mouseX: Int,
        mouseY: Int,
        hover: FloatArray,
        scale: Float,
        colorStyle: Boolean,
        onlyClass: Boolean,
        center: LeapPlayer?,
        centerSubtitle: String?,
    ) {
        val mc = Minecraft.getInstance()
        val font = mc.font
        val halfW = screenW / 2
        val halfH = screenH / 2
        val labels = ArrayList<BoxLabel>(5)
        SkiaContext.initialize()
        skia.paint(graphics) { canvas ->
            repeat(4) { index ->
                val player = teammates.getOrNull(index) ?: return@repeat
                if (player.clazz == LeapDungeonClass.EMPTY) return@repeat
                val col = index % 2
                val row = index / 2
                val nearX = if (col == 0) halfW - 24 else halfW + 24
                val nearY = if (row == 0) halfH - 24 else halfH + 24
                val localX = if (col == 0) -BOX_WIDTH else 0
                val localY = if (row == 0) -BOX_HEIGHT else 0
                val grow = hover.getOrElse(index) { 0f } * 5f
                labels += paintBox(
                    canvas,
                    player,
                    nearX,
                    nearY,
                    localX,
                    localY,
                    BOX_WIDTH,
                    BOX_HEIGHT,
                    grow,
                    scale,
                    colorStyle,
                    onlyClass,
                    null,
                )
            }
            if (center != null) {
                val grow = hover.getOrElse(4) { 0f } * 5f
                labels += paintBox(
                    canvas,
                    center,
                    halfW,
                    halfH,
                    -CENTER_WIDTH / 2,
                    -CENTER_HEIGHT / 2,
                    CENTER_WIDTH,
                    CENTER_HEIGHT,
                    grow,
                    scale,
                    colorStyle,
                    onlyClass,
                    centerSubtitle,
                )
            }
        }
        labels.forEach { label -> drawLabel(graphics, font, label) }
    }

    fun release() {
        skia.close()
    }

    private fun paintBox(
        canvas: org.jetbrains.skia.Canvas,
        player: LeapPlayer,
        nearX: Int,
        nearY: Int,
        localX: Int,
        localY: Int,
        boxW: Int,
        boxH: Int,
        grow: Float,
        scale: Float,
        colorStyle: Boolean,
        onlyClass: Boolean,
        subtitleOverride: String?,
    ): BoxLabel {
        val sx = scale * (boxW + grow * 2f) / boxW
        val sy = scale * (boxH + grow * 2f) / boxH
        canvas.save()
        canvas.translate(nearX.toFloat(), nearY.toFloat())
        canvas.scale(sx, sy)
        val bg = if (colorStyle) {
            withAlpha(player.clazz.colorArgb, 0.85f)
        } else {
            withAlpha(BACKGROUND, 0.75f)
        }
        fill.color = bg
        fill.isAntiAlias = true
        canvas.drawRRect(
            RRect.makeXYWH(localX.toFloat(), localY.toFloat(), boxW.toFloat(), boxH.toFloat(), 9f),
            fill,
        )
        canvas.restore()
        val face = (boxH * 0.76f).toInt()
        val title = if (onlyClass) player.clazz.displayName else player.name
        val sub = subtitleOverride ?: when {
            player.isDead -> "DEAD"
            onlyClass -> null
            else -> player.clazz.displayName
        }
        return BoxLabel(
            player = player,
            faceX = (nearX + (localX + 9) * sx).toInt(),
            faceY = (nearY + (localY + 9) * sy).toInt(),
            faceSize = (face * sx).toInt().coerceAtLeast(8),
            nameX = (nearX + (localX + 15 + face) * sx).toInt(),
            nameY = (nearY + (localY + boxH / 2.5f) * sy).toInt(),
            nameColor = if (colorStyle) BACKGROUND else player.clazz.colorArgb,
            title = title,
            subtitle = sub,
            subtitleY = (nearY + (localY + boxH / 1.7f) * sy).toInt(),
            subtitleColor = if (player.isDead) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt(),
        )
    }

    private fun drawLabel(graphics: GuiGraphicsExtractor, font: Font, label: BoxLabel) {
        drawFace(graphics, font, label.player, label.faceX, label.faceY, label.faceSize)
        graphics.text(font, label.title, label.nameX, label.nameY, label.nameColor, false)
        val sub = label.subtitle ?: return
        graphics.text(font, sub, label.nameX, label.subtitleY, label.subtitleColor, false)
    }

    private fun approach(hover: FloatArray, index: Int, hovered: Boolean, dt: Long) {
        val target = if (hovered) 1f else 0f
        val speed = dt / HOVER_MS
        hover[index] += (target - hover[index]) * min(1f, speed * 5f)
    }

    private fun drawFace(
        graphics: GuiGraphicsExtractor,
        font: Font,
        player: LeapPlayer,
        x: Int,
        y: Int,
        size: Int,
    ) {
        val mc = Minecraft.getInstance()
        val entity = mc.level?.players()?.find { it.name.string.equals(player.name, ignoreCase = true) }
        if (entity != null) {
            PlayerFaceExtractor.extractRenderState(graphics, entity.skin, x, y, size)
            return
        }
        graphics.fill(x, y, x + size, y + size, withAlpha(player.clazz.colorArgb, 0.9f))
        val initial = player.clazz.displayName.first().toString()
        val textW = font.width(initial)
        graphics.text(font, initial, x + (size - textW) / 2, y + size / 2 - 4, 0xFFFFFFFF.toInt(), false)
    }

    private fun withAlpha(rgb: Int, alpha: Float): Int {
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        return (a shl 24) or (rgb and 0x00FFFFFF)
    }

    private data class BoxLabel(
        val player: LeapPlayer,
        val faceX: Int,
        val faceY: Int,
        val faceSize: Int,
        val nameX: Int,
        val nameY: Int,
        val nameColor: Int,
        val title: String,
        val subtitle: String?,
        val subtitleY: Int,
        val subtitleColor: Int,
    )
}
