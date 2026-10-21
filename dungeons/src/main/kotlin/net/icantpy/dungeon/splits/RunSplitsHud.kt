package net.icantpy.dungeon.splits

import net.icantpy.dungeon.DungeonFloor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max

/** Draws the splits table. Hidden unless a run is active or just finished, or when [render] is called with preview. */
object RunSplitsHud {
    private const val GAP = 6
    private const val LINE_H = 10
    private const val PAD = 2
    private const val COLUMNS = 4

    private val WHITE: Int = 0xFFFFFFFF.toInt()
    private val GRAY: Int = 0xFF888888.toInt()
    private val GREEN: Int = 0xFF55FF55.toInt()
    private val RED: Int = 0xFFFF5555.toInt()
    private val GOLD: Int = 0xFFFFAA00.toInt()
    private val YELLOW: Int = 0xFFFFFF55.toInt()
    private val BACKGROUND: Int = 0x66000000

    private val mc: Minecraft get() = Minecraft.getInstance()

    private class Cell(val text: String, val color: Int)

    /** [grid] lines align cells to shared columns; title-style lines are drawn at x=0 and do not affect column widths. */
    private class TextLine(val cells: List<Cell>, val grid: Boolean)

    private class Layout(val colX: IntArray, val colW: IntArray, val width: Int, val height: Int)

    /** Draws the table at ([x], [y]) in GUI pixels, scaled by [scale]. */
    fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, preview: Boolean = false) {
        val view = viewFor(preview) ?: return
        val lines = buildLines(view, RunSplitsController.settings)
        val layout = layoutOf(lines)

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        graphics.fill(-PAD, -PAD, layout.width + PAD, layout.height + PAD, BACKGROUND)
        lines.forEachIndexed { row, line ->
            val py = row * LINE_H
            line.cells.forEachIndexed { column, cell ->
                if (cell.text.isEmpty() || column >= COLUMNS) return@forEachIndexed
                val px = when {
                    !line.grid -> 0
                    column == 0 -> layout.colX[0]
                    else -> layout.colX[column] + layout.colW[column] - mc.font.width(cell.text)
                }
                graphics.text(mc.font, cell.text, px, py, cell.color, true)
            }
        }
        pose.popMatrix()
    }

    /** Unscaled pixel size of the table, for the HUD editor box. Uses sample data when no run is live. */
    fun size(): Pair<Int, Int> {
        val view = viewFor(preview = true) ?: sampleView(null)
        val layout = layoutOf(buildLines(view, RunSplitsController.settings))
        return layout.width to layout.height
    }

    private fun viewFor(preview: Boolean): SplitsView? {
        val live = RunSplitsController.snapshot()
        if (live.active || live.finished) {
            return if (!preview && !RunSplitsController.settings.enabled) null else live
        }
        return if (preview) sampleView(live.floor) else null
    }

    private fun buildLines(view: SplitsView, settings: RunSplitsSettings): List<TextLine> {
        val lines = ArrayList<TextLine>()
        val title = "Splits" + (view.floor?.let { " · ${it.name}" } ?: "")
        lines += TextLine(listOf(Cell(title, WHITE)), grid = false)

        for (row in view.rows) {
            val rowColor = stateColor(row.state)
            lines += TextLine(
                listOf(
                    Cell(row.name, rowColor),
                    Cell(
                        if (settings.compact) "" else RunSplitsFormat.time(row.segmentMs),
                        if (row.isNewBestSegment) GOLD else rowColor,
                    ),
                    Cell(RunSplitsFormat.time(row.cumulativeMs), rowColor),
                    Cell(
                        if (settings.showPbDelta) RunSplitsFormat.delta(row.deltaMs) else "",
                        deltaColor(row.deltaMs),
                    ),
                ),
                grid = true,
            )
        }

        lines += TextLine(
            listOf(
                Cell("Total", WHITE),
                Cell("", WHITE),
                Cell(RunSplitsFormat.time(view.totalMs), WHITE),
                Cell(
                    if (settings.showPbDelta) RunSplitsFormat.delta(view.totalDeltaMs) else "",
                    deltaColor(view.totalDeltaMs),
                ),
            ),
            grid = true,
        )

        val sumOfBest = view.sumOfBestMs
        if (settings.showSumOfBest && sumOfBest != null) {
            lines += TextLine(
                listOf(
                    Cell("Sum of best", GRAY),
                    Cell("", GRAY),
                    Cell(RunSplitsFormat.time(sumOfBest), GRAY),
                    Cell("", GRAY),
                ),
                grid = true,
            )
        }
        return lines
    }

    private fun layoutOf(lines: List<TextLine>): Layout {
        val colW = IntArray(COLUMNS)
        for (line in lines) {
            if (!line.grid) continue
            line.cells.forEachIndexed { column, cell ->
                if (column < COLUMNS) colW[column] = max(colW[column], mc.font.width(cell.text))
            }
        }
        val colX = IntArray(COLUMNS)
        var cursor = 0
        for (column in 0 until COLUMNS) {
            colX[column] = cursor
            if (colW[column] > 0) cursor += colW[column] + GAP
        }
        val gridWidth = (cursor - GAP).coerceAtLeast(0)
        val titleWidth = lines.filter { !it.grid }
            .maxOfOrNull { line -> line.cells.sumOf { mc.font.width(it.text) } } ?: 0
        return Layout(colX, colW, max(gridWidth, titleWidth), lines.size * LINE_H)
    }

    private fun stateColor(state: SplitState): Int = when (state) {
        SplitState.DONE -> WHITE
        SplitState.RUNNING -> YELLOW
        SplitState.PENDING -> GRAY
    }

    private fun deltaColor(deltaMs: Long?): Int = when {
        deltaMs == null || deltaMs == 0L -> WHITE
        deltaMs < 0L -> GREEN
        else -> RED
    }

    /** Placeholder rows for the editor when no run is live. */
    fun sampleView(floor: DungeonFloor?): SplitsView {
        val defs = SplitLayouts.forFloor(floor ?: DungeonFloor.F7)
        val step = 38_500L
        var sumOfBest = 0L
        val rows = defs.mapIndexed { index, def ->
            val segment = step + index * 1_000L
            sumOfBest += segment
            SplitRow(
                id = def.id,
                name = def.name,
                state = SplitState.DONE,
                segmentMs = segment,
                cumulativeMs = sumOfBest,
                pbSegmentMs = segment,
                bestSegmentMs = segment,
                deltaMs = if (index % 2 == 0) -1_200L else 800L,
                isNewBestSegment = index == 1,
            )
        }
        return SplitsView(
            floor = floor ?: DungeonFloor.F7,
            active = false,
            finished = false,
            rows = rows,
            totalMs = sumOfBest,
            pbTotalMs = sumOfBest,
            totalDeltaMs = -400L,
            sumOfBestMs = sumOfBest,
            isPbRun = false,
        )
    }
}
