package net.icantpy.dungeon.terminal

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import kotlin.math.PI
import kotlin.math.sin

/** Draws a solved terminal into a [TerminalLayout]. Shared by the live overlay and the preview editor. */
object TerminalRenderer {
    private const val ITEM_SIZE = 16f
    private const val BASE_CELL = 18f
    private const val OPAQUE = 0xFF000000.toInt()
    private const val PANEL_BORDER_WIDTH = 1
    private const val PULSE_PERIOD_MS = 900.0
    private const val PULSE_DEPTH = 0.45f

    /** The melody track under the target column is tinted with the column colour at this strength. */
    private const val MELODY_COLUMN_TINT = 0.45f
    private const val HEADER_SPACING = 8
    private const val HEADER_SEPARATOR = " · "
    private const val WIDEST_COUNT = 88

    fun render(
        graphics: GuiGraphicsExtractor,
        solution: TerminalSolution,
        layout: TerminalLayout,
        settings: TerminalSettings,
        title: String,
        screenW: Int,
        screenH: Int,
        mouseX: Int,
        mouseY: Int,
        animations: TerminalAnimations,
        stackAt: (Int) -> ItemStack,
        canClick: (TerminalCell) -> Boolean,
    ) {
        val font = panelFont(settings)
        val now = System.currentTimeMillis()
        animations.observe(solution.cells, now)
        if (settings.screenDim > 0f) graphics.fill(0, 0, screenW, screenH, argb(0, settings.screenDim))
        drawPanel(graphics, layout, settings)
        drawHeader(graphics, font, solution, layout, settings, title)
        val hovered = layout.slotAt(mouseX, mouseY, solution.cells.map { it.slot })
        val pulse = pulse(now)
        solution.cells.forEach { cell ->
            // Melody cells change state every time the pane moves; fading them would leave a trail behind it.
            val fades = settings.fadeDone && cell.state == TerminalCellState.DONE && solution.type != TerminalType.MELODY
            val fade = if (fades) animations.doneProgress(cell.slot, now) else 1f
            if (!visible(cell, settings) && fade >= 1f) return@forEach
            drawCell(graphics, font, cell, solution, layout, settings, stackAt, fade, pulse)
            if (settings.showHover && cell.slot == hovered && canClick(cell)) {
                TerminalShapes.ring(
                    graphics, layout.cellX(cell.slot), layout.cellY(cell.slot), layout.cell, layout.cell,
                    settings.cellRadius, 1, OPAQUE or settings.hoverRgb,
                )
            }
        }
    }

    /**
     * The layout for [solution], made wide enough for its header in the chosen font so the title never spills out
     * of the panel. The counter is measured at two digits so the panel does not change width as it counts down.
     */
    fun layout(screenW: Int, screenH: Int, solution: TerminalSolution, title: String, settings: TerminalSettings): TerminalLayout {
        val font = panelFont(settings)
        return TerminalLayout.compute(
            screenW, screenH, solution.cells.map { it.slot }, settings, headerWidth(font, solution, title, settings),
        )
    }

    /** Unscaled width of the header text, including the space between title and counter in the split layout. */
    private fun headerWidth(font: Font, solution: TerminalSolution, title: String, settings: TerminalSettings): Int {
        val label = if (settings.showTitle) width(font, label(solution, title), settings) else 0
        val counter = if (settings.showRemaining) width(font, remainingText(WIDEST_COUNT), settings) else 0
        if (label == 0 || counter == 0) return label + counter
        val between = if (settings.titleAlign == TerminalTitleAlign.CENTER) width(font, HEADER_SEPARATOR, settings) else HEADER_SPACING
        return label + between + counter
    }

    private fun remainingText(count: Int): String = "$count left"

    /** The player's own font file when one is chosen and readable, otherwise the game font (styled per [styled]). */
    private fun panelFont(settings: TerminalSettings): Font =
        TerminalUserFonts.fontFor(settings) ?: Minecraft.getInstance().font

    /** [text] in the panel's font; vanilla's own font and a player's font file need no style. */
    private fun styled(text: String, settings: TerminalSettings): Component {
        val component = Component.literal(text)
        val id = settings.font.fontId ?: return component
        return component.withStyle { it.withFont(FontDescription.Resource(Identifier.parse(id))) }
    }

    private fun width(font: Font, text: String, settings: TerminalSettings): Int = font.width(styled(text, settings))

    /** 0..1 breathing value for the pulse on the next cell. */
    private fun pulse(nowMs: Long): Float = (0.5 + 0.5 * sin(nowMs / PULSE_PERIOD_MS * 2 * PI)).toFloat()

    /** Header label for a terminal, for example "Starts with 'A'". */
    fun label(solution: TerminalSolution, title: String): String = when (solution.type) {
        TerminalType.PANES -> "Correct all the panes"
        TerminalType.IN_ORDER -> "Click in order"
        TerminalType.STARTS_WITH -> "Starts with '${TerminalType.startsWithLetter(title) ?: "?"}'"
        TerminalType.SELECT_COLOR -> "Select all ${TerminalType.selectColorWords(title) ?: "?"}"
        TerminalType.RUBIX -> "Change all to ${solution.rubixTarget?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "?"}"
        TerminalType.MELODY -> "Click on time"
    }

    private fun drawPanel(graphics: GuiGraphicsExtractor, layout: TerminalLayout, settings: TerminalSettings) {
        TerminalShapes.fill(
            graphics, layout.panelX, layout.panelY, layout.panelW, layout.panelH,
            settings.panelRadius, argb(settings.panelRgb, settings.panelAlpha),
        )
        if (settings.panelBorder) {
            TerminalShapes.ring(
                graphics, layout.panelX, layout.panelY, layout.panelW, layout.panelH,
                settings.panelRadius, PANEL_BORDER_WIDTH, OPAQUE or settings.borderRgb,
            )
        }
    }

    /** Melody's track is the timing reference, so the hide options never remove it. */
    private fun visible(cell: TerminalCell, settings: TerminalSettings): Boolean = cell.melodyRole != null || when (cell.state) {
        TerminalCellState.TARGET -> true
        TerminalCellState.DONE -> !settings.hideDone
        TerminalCellState.WRONG -> !settings.hideWrong
    }

    private fun drawHeader(
        graphics: GuiGraphicsExtractor,
        font: Font,
        solution: TerminalSolution,
        layout: TerminalLayout,
        settings: TerminalSettings,
        title: String,
    ) {
        if (layout.headerH <= 0) return
        val color = OPAQUE or settings.textRgb
        val scale = textScale(layout, settings)
        val y = layout.headerY
        val left = layout.panelX + layout.padding
        val right = layout.panelX + layout.panelW - layout.padding
        if (settings.titleAlign == TerminalTitleAlign.CENTER) {
            val parts = listOfNotNull(
                label(solution, title).takeIf { settings.showTitle },
                remainingText(solution.remaining).takeIf { settings.showRemaining },
            )
            val text = styled(parts.joinToString(HEADER_SEPARATOR), settings)
            val x = layout.panelX + layout.panelW / 2f - font.width(text) * scale / 2f
            scaledText(graphics, font, text, x, y.toFloat(), scale, color, settings.textShadow)
            return
        }
        if (settings.showRemaining) {
            val remaining = styled(remainingText(solution.remaining), settings)
            scaledText(graphics, font, remaining, right - font.width(remaining) * scale, y.toFloat(), scale, color, settings.textShadow)
        }
        if (settings.showTitle) {
            scaledText(graphics, font, styled(label(solution, title), settings), left.toFloat(), y.toFloat(), scale, color, settings.textShadow)
        }
    }

    private fun drawCell(
        graphics: GuiGraphicsExtractor,
        font: Font,
        cell: TerminalCell,
        solution: TerminalSolution,
        layout: TerminalLayout,
        settings: TerminalSettings,
        stackAt: (Int) -> ItemStack,
        fade: Float,
        pulse: Float,
    ) {
        val type = solution.type
        val x = layout.cellX(cell.slot)
        val y = layout.cellY(cell.slot)
        var rgb = if (type == TerminalType.MELODY) melodyColor(cell, solution, settings) else colorFor(cell, type, settings)
        var alpha = settings.cellAlpha
        if (fade < 1f && type != TerminalType.RUBIX) {
            rgb = lerpRgb(settings.targetRgb, rgb, fade)
            // A finished cell that is hidden fades out instead of staying in the finished colour.
            if (settings.hideDone) alpha *= 1f - fade
        }
        if (settings.pulseNext && isNext(cell, type)) alpha *= 1f - PULSE_DEPTH * pulse
        drawCellShape(graphics, x, y, layout.cell, rgb, alpha, settings)
        when (type) {
            TerminalType.IN_ORDER -> if (cell.state == TerminalCellState.TARGET) {
                cell.number?.let { centeredText(graphics, font, it.toString(), x, y, layout, settings, settings.textRgb) }
            }
            TerminalType.RUBIX -> rubixLabel(cell, settings)?.let { (text, rgb) ->
                centeredText(graphics, font, text, x, y, layout, settings, rgb)
            }
            TerminalType.STARTS_WITH, TerminalType.SELECT_COLOR ->
                if (settings.showItems && cell.state != TerminalCellState.DONE) drawItem(graphics, stackAt(cell.slot), x, y, layout.cell)
            TerminalType.PANES, TerminalType.MELODY -> Unit
        }
    }

    private fun drawCellShape(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, rgb: Int, alpha: Float, settings: TerminalSettings) {
        val style = settings.cellStyle
        if (style != TerminalCellStyle.OUTLINE) {
            TerminalShapes.fill(graphics, x, y, size, size, settings.cellRadius, argb(rgb, alpha))
        }
        if (style != TerminalCellStyle.FILLED) {
            val outlineAlpha = if (style == TerminalCellStyle.OUTLINE) alpha else 1f
            TerminalShapes.ring(graphics, x, y, size, size, settings.cellRadius, settings.outlineWidth, argb(rgb, outlineAlpha))
        }
    }

    /** The cell the player should click now: the next pane in order, or any open cell elsewhere. */
    private fun isNext(cell: TerminalCell, type: TerminalType): Boolean =
        cell.state == TerminalCellState.TARGET && (type != TerminalType.IN_ORDER || cell.order == 0)

    private fun lerpRgb(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255) shl shift
        }
        return channel(16) or channel(8) or channel(0)
    }

    private fun colorFor(cell: TerminalCell, type: TerminalType, settings: TerminalSettings): Int {
        if (type == TerminalType.RUBIX) return cell.rubixColor?.rgb ?: settings.doneRgb
        return when (cell.state) {
            TerminalCellState.DONE -> settings.doneRgb
            TerminalCellState.WRONG -> settings.wrongRgb
            TerminalCellState.TARGET -> when (cell.order) {
                null, 0 -> settings.targetRgb
                1 -> settings.orderSecondRgb
                2 -> settings.orderThirdRgb
                else -> settings.wrongRgb
            }
        }
    }

    /**
     * Melody reuses the theme's colours so every preset styles it: the column marker uses the second-click colour,
     * the moving pane and the active button turn the target colour when they line up and the third-click colour
     * otherwise, and the rest of the track and the other buttons use the finished colour.
     */
    private fun melodyColor(cell: TerminalCell, solution: TerminalSolution, settings: TerminalSettings): Int {
        val lined = cell.state == TerminalCellState.TARGET
        return when (cell.melodyRole) {
            MelodyRole.COLUMN -> settings.orderSecondRgb
            MelodyRole.POINTER -> if (lined) settings.targetRgb else settings.orderThirdRgb
            MelodyRole.BUTTON -> when (cell.state) {
                TerminalCellState.TARGET -> settings.targetRgb
                TerminalCellState.WRONG -> settings.orderThirdRgb
                TerminalCellState.DONE -> settings.doneRgb
            }
            MelodyRole.TRACK, null -> if (cell.slot % TerminalSnapshot.COLUMNS == solution.melodyColumn) {
                lerpRgb(settings.doneRgb, settings.orderSecondRgb, MELODY_COLUMN_TINT)
            } else {
                settings.doneRgb
            }
        }
    }

    /** A plain number means left clicks, a negative one right clicks. */
    private fun rubixLabel(cell: TerminalCell, settings: TerminalSettings): Pair<String, Int>? {
        if (cell.state != TerminalCellState.TARGET) return null
        if (settings.rubixDisplay == RubixDisplay.LEFT_ONLY || cell.leftClicks <= cell.rightClicks) {
            return cell.leftClicks.toString() to settings.rubixLeftRgb
        }
        return "-${cell.rightClicks}" to settings.rubixRightRgb
    }

    private fun drawItem(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int, cell: Int) {
        if (stack.isEmpty) return
        val itemScale = cell / BASE_CELL
        val size = ITEM_SIZE * itemScale
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x + (cell - size) / 2f, y + (cell - size) / 2f)
        pose.scale(itemScale, itemScale)
        graphics.item(stack, 0, 0)
        pose.popMatrix()
    }

    private fun centeredText(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        x: Int,
        y: Int,
        layout: TerminalLayout,
        settings: TerminalSettings,
        rgb: Int,
    ) {
        val scale = textScale(layout, settings)
        val component = styled(text, settings)
        val textX = x + layout.cell / 2f - font.width(component) * scale / 2f
        val textY = y + layout.cell / 2f - font.lineHeight * scale / 2f + scale / 2f
        scaledText(graphics, font, component, textX, textY, scale, OPAQUE or rgb, settings.textShadow)
    }

    private fun textScale(layout: TerminalLayout, settings: TerminalSettings): Float = layout.scale * settings.textScale

    private fun scaledText(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: Component,
        x: Float,
        y: Float,
        scale: Float,
        color: Int,
        shadow: Boolean,
    ) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        graphics.text(font, text, 0, 0, color, shadow)
        pose.popMatrix()
    }

    private fun argb(rgb: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return (a shl 24) or (rgb and 0xFFFFFF)
    }
}
