package net.icantpy.dungeon.leap

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import kotlin.math.min

object LeapMenuRenderer {
    const val BOX_WIDTH = LeapMenuLayout.CORNER_WIDTH
    const val BOX_HEIGHT = LeapMenuLayout.CORNER_HEIGHT
    const val CENTER_WIDTH = LeapMenuLayout.CENTER_WIDTH
    const val CENTER_HEIGHT = LeapMenuLayout.CENTER_HEIGHT
    private const val HOVER_MS = 200f
    private const val BACKGROUND = 0xFF616161.toInt()
    const val DIM = 0x40000000.toInt()

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
        val selected = LeapMenuLayout.hit(screenW, screenH, mouseX, mouseY, LeapMenu.settings(), centerActive)
        repeat(4) { index ->
            approach(hover, index, selected == index, dt)
        }
        if (hover.size > 4) {
            approach(hover, 4, selected == 4, dt)
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
        settings: LeapOrientSettings = LeapMenu.settings(),
    ) {
        val font = Minecraft.getInstance().font
        val boxes = LeapMenuLayout.boxes(screenW, screenH, settings)
        val fillAlpha = settings.cardFillAlpha()
        if (fillAlpha <= 0f) return
        LeapMenuLayout.layers(settings).forEach { index ->
            val player = if (index == 4) center else teammates.getOrNull(index)
            if (player == null || player.clazz == LeapDungeonClass.EMPTY) return@forEach
            val grow = hover.getOrElse(index) { 0f } * min(5f, settings.menuGap / 3f)
            drawLabel(
                graphics,
                font,
                paintBox(
                    graphics, player, boxes[index], grow, colorStyle, onlyClass,
                    if (index == 4) centerSubtitle else null,
                    fillAlpha,
                ),
            )
        }
    }

    fun release() {
    }

    private fun paintBox(
        graphics: GuiGraphicsExtractor,
        player: LeapPlayer,
        box: LeapMenuBox,
        grow: Float,
        colorStyle: Boolean,
        onlyClass: Boolean,
        subtitleOverride: String?,
        fillAlpha: Float,
    ): BoxLabel {
        val w = (box.width + grow * 2f).coerceAtLeast(1f)
        val h = (box.height + grow * 2f).coerceAtLeast(1f)
        val left = box.centerX - w / 2f
        val top = box.centerY - h / 2f
        val alpha = fillAlpha.coerceIn(0f, 1f)
        val bg = if (colorStyle) {
            withAlpha(player.clazz.colorArgb, alpha)
        } else {
            withAlpha(BACKGROUND, alpha)
        }
        graphics.fill(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt(), bg)
        val pad = 9f * min(w / BOX_WIDTH, h / BOX_HEIGHT).coerceIn(0.35f, 1.5f)
        val face = min(h * 0.76f, (w - pad * 2f - 48f).coerceAtLeast(8f)).toInt().coerceAtLeast(8)
        val title = if (onlyClass) player.clazz.displayName else player.name
        val sub = subtitleOverride ?: when {
            player.isDead -> "DEAD"
            onlyClass -> null
            else -> player.clazz.displayName
        }
        return BoxLabel(
            player = player,
            faceX = (left + pad).toInt(),
            faceY = (top + pad).toInt(),
            faceSize = face,
            nameX = (left + pad + face + 6f).toInt(),
            nameY = (top + h / 2.5f).toInt(),
            nameColor = withAlpha(if (colorStyle) BACKGROUND else player.clazz.colorArgb, alpha),
            title = title,
            subtitle = sub,
            subtitleY = (top + h / 1.7f).toInt(),
            subtitleColor = withAlpha(if (player.isDead) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt(), alpha),
            clipLeft = left.toInt(),
            clipTop = top.toInt(),
            clipRight = (left + w).toInt(),
            clipBottom = (top + h).toInt(),
            faceAlpha = alpha,
        )
    }

    private fun drawLabel(graphics: GuiGraphicsExtractor, font: Font, label: BoxLabel) {
        graphics.enableScissor(label.clipLeft, label.clipTop, label.clipRight, label.clipBottom)
        try {
            drawFace(graphics, font, label.player, label.faceX, label.faceY, label.faceSize, label.faceAlpha)
            graphics.text(font, label.title, label.nameX, label.nameY, label.nameColor, false)
            val sub = label.subtitle ?: return
            graphics.text(font, sub, label.nameX, label.subtitleY, label.subtitleColor, false)
        } finally {
            graphics.disableScissor()
        }
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
        fillAlpha: Float,
    ) {
        val mc = Minecraft.getInstance()
        val entity = mc.level?.players()?.find { it.name.string.equals(player.name, ignoreCase = true) }
        if (entity != null) {
            PlayerFaceExtractor.extractRenderState(graphics, entity.skin, x, y, size)
            return
        }
        graphics.fill(x, y, x + size, y + size, withAlpha(player.clazz.colorArgb, fillAlpha.coerceIn(0f, 1f)))
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
        val clipLeft: Int,
        val clipTop: Int,
        val clipRight: Int,
        val clipBottom: Int,
        val faceAlpha: Float,
    )
}
