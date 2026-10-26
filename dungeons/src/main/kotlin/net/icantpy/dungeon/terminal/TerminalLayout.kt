package net.icantpy.dungeon.terminal

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Where the solver panel and its cells sit on screen. Only the bounding box of the puzzle's cells is drawn, so the
 * glass-pane border of the chest takes no space. Pure; used by both the live overlay and the preview editor.
 */
data class TerminalLayout(
    val panelX: Int,
    val panelY: Int,
    val panelW: Int,
    val panelH: Int,
    val gridX: Int,
    val gridY: Int,
    val cell: Int,
    val gap: Int,
    val minRow: Int,
    val minCol: Int,
    val headerH: Int,
    val headerY: Int,
    val padding: Int,
    val scale: Float,
) {
    fun cellX(slot: Int): Int = gridX + (slot % TerminalSnapshot.COLUMNS - minCol) * (cell + gap)

    fun cellY(slot: Int): Int = gridY + (slot / TerminalSnapshot.COLUMNS - minRow) * (cell + gap)

    /** The slot under the mouse among [slots], ignoring the gaps between cells. */
    fun slotAt(mouseX: Int, mouseY: Int, slots: Collection<Int>): Int? = slots.firstOrNull { slot ->
        val x = cellX(slot)
        val y = cellY(slot)
        mouseX in x until x + cell && mouseY in y until y + cell
    }

    fun contains(mouseX: Int, mouseY: Int): Boolean =
        mouseX in panelX until panelX + panelW && mouseY in panelY until panelY + panelH

    companion object {
        private const val BASE_CELL = 18
        private const val BASE_HEADER = 13
        private const val TEXT_HEIGHT = 9
        private const val EMPTY_COLUMNS = 7
        private const val EMPTY_ROWS = 3

        /**
         * Lays out [slots] centred on the screen, moved by the settings' offset and kept on screen. With no slots
         * yet (the server fills the chest a moment after it opens) it reserves an empty 7 by 3 grid. The panel is at
         * least as wide as [headerTextWidth] (unscaled text pixels) so the header stays inside it; a narrower grid is
         * centred.
         */
        fun compute(
            screenW: Int,
            screenH: Int,
            slots: Collection<Int>,
            settings: TerminalSettings,
            headerTextWidth: Int = 0,
        ): TerminalLayout {
            val scale = settings.scale
            val cell = (BASE_CELL * scale).roundToInt()
            val gap = settings.gap
            val padding = (settings.padding * scale).roundToInt()
            val textScale = scale * settings.textScale
            val headerH = if (settings.showTitle || settings.showRemaining) (BASE_HEADER * textScale).roundToInt() else 0
            val headerOnTop = settings.headerPosition == TerminalHeaderPosition.TOP
            val rows = slots.map { it / TerminalSnapshot.COLUMNS }
            val cols = slots.map { it % TerminalSnapshot.COLUMNS }
            val minRow = rows.minOrNull() ?: 0
            val minCol = cols.minOrNull() ?: 0
            val rowCount = if (rows.isEmpty()) EMPTY_ROWS else rows.max() - minRow + 1
            val colCount = if (cols.isEmpty()) EMPTY_COLUMNS else cols.max() - minCol + 1
            val gridW = colCount * cell + (colCount - 1) * gap
            val gridH = rowCount * cell + (rowCount - 1) * gap
            val headerW = if (headerH > 0) ceil(headerTextWidth * textScale).toInt() else 0
            val innerW = maxOf(gridW, headerW)
            val panelW = innerW + padding * 2
            val panelH = gridH + padding * 2 + headerH
            val panelX = clamp((screenW - panelW) / 2 + settings.offsetX, screenW - panelW)
            val panelY = clamp((screenH - panelH) / 2 + settings.offsetY, screenH - panelH)
            return TerminalLayout(
                panelX = panelX,
                panelY = panelY,
                panelW = panelW,
                panelH = panelH,
                gridX = panelX + padding + (innerW - gridW) / 2,
                gridY = panelY + padding + if (headerOnTop) headerH else 0,
                cell = cell,
                gap = gap,
                minRow = minRow,
                minCol = minCol,
                headerH = headerH,
                headerY = if (headerOnTop) {
                    panelY + padding
                } else {
                    panelY + padding + gridH + headerH - (TEXT_HEIGHT * textScale).roundToInt()
                },
                padding = padding,
                scale = scale,
            )
        }

        /** The offset that puts a panel of this size at [panelX], [panelY]; used when dragging in the editor. */
        fun offsetFor(screenW: Int, screenH: Int, layout: TerminalLayout, panelX: Int, panelY: Int): Pair<Int, Int> {
            val x = clamp(panelX, screenW - layout.panelW) - (screenW - layout.panelW) / 2
            val y = clamp(panelY, screenH - layout.panelH) - (screenH - layout.panelH) / 2
            return x to y
        }

        private fun clamp(value: Int, max: Int): Int = value.coerceIn(0, max.coerceAtLeast(0))
    }
}
