package net.icantpy.gui.config

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.splits.FloorRecord
import net.icantpy.dungeon.splits.RunSplitsController
import net.icantpy.dungeon.splits.RunSplitsFormat

/** Config body for "Splits & PBs": display toggles, per-floor PB table with two-click reset, and the HUD editor entry. */
internal class SplitsPage {
    /** Floor whose reset button was clicked once. A second click on the same floor performs the reset. */
    private var pendingResetFloor: DungeonFloor? = null

    fun draw(draw: MenuDraw, m: ConfigMetrics, startY: Int, toast: (String) -> Unit, openHudEditor: () -> Unit): Int {
        val settings = RunSplitsController.settings
        var y = RunPageRows.help(
            draw, m, startY,
            "Tracks each dungeon run against your personal best. Runs start on the Spirit Leap run-start message and end on the scoreboard summary.",
        )
        y = RunPageRows.heading(draw, m, y, "Display")
        y = RunPageRows.toggle(draw, m, y, "Show PB delta", "Show how far ahead or behind your personal best each split is.", settings.showPbDelta) {
            RunSplitsController.updateSettings { it.copy(showPbDelta = !it.showPbDelta) }
        }
        y = RunPageRows.toggle(draw, m, y, "Show sum of best", "Show the sum of your best segment times as the target total.", settings.showSumOfBest) {
            RunSplitsController.updateSettings { it.copy(showSumOfBest = !it.showSumOfBest) }
        }
        y = RunPageRows.toggle(draw, m, y, "Compact", "Show fewer rows on the HUD.", settings.compact) {
            RunSplitsController.updateSettings { it.copy(compact = !it.compact) }
        }
        y = RunPageRows.button(
            draw, m, y + RunPageRows.GROUP_GAP, "HUD position", "Drag the splits box to move it and use + or - to scale it.",
            "Edit HUD position", ButtonStyle.PRIMARY, 150, openHudEditor,
        )
        y += RunPageRows.GROUP_GAP
        y = RunPageRows.heading(draw, m, y, "Personal bests")
        y = tableHeader(draw, m, y)
        DungeonFloor.entries.filter { it != DungeonFloor.NONE }.forEach { floor ->
            y = floorRow(draw, m, y, floor, RunSplitsController.floorRecord(floor), toast)
        }
        return y + RunPageRows.GROUP_GAP
    }

    private fun tableHeader(draw: MenuDraw, m: ConfigMetrics, y: Int): Int {
        val columns = columnsOf(m)
        draw.text("Floor", columns.floor, y, draw.palette.secondary, draw.microFont)
        draw.text("PB total", columns.pb, y, draw.palette.secondary, draw.microFont)
        draw.text("Sum of best", columns.sum, y, draw.palette.secondary, draw.microFont)
        draw.text("Runs", columns.runs, y, draw.palette.secondary, draw.microFont)
        return y + draw.lineHeight(draw.microFont) + 6
    }

    private fun floorRow(
        draw: MenuDraw,
        m: ConfigMetrics,
        y: Int,
        floor: DungeonFloor,
        record: FloorRecord?,
        toast: (String) -> Unit,
    ): Int {
        val columns = columnsOf(m)
        val cardHeight = m.row - 4
        draw.round(m.innerLeft, y, m.innerWidth, cardHeight, draw.chrome.cardRadius, draw.palette.group)
        val textY = y + (cardHeight - draw.lineHeight(draw.smallFont)) / 2
        val sumOfBest = record?.bestSegments?.values?.takeIf { it.isNotEmpty() }?.sum()
        draw.text(floor.name, columns.floor, textY, draw.palette.text, draw.smallFont)
        draw.text(RunSplitsFormat.time(record?.bestTotalMs), columns.pb, textY, draw.palette.text, draw.smallFont)
        draw.text(RunSplitsFormat.time(sumOfBest), columns.sum, textY, draw.palette.text, draw.smallFont)
        draw.text((record?.history?.size ?: 0).toString(), columns.runs, textY, draw.palette.text, draw.smallFont)
        if (record != null) {
            val armed = pendingResetFloor == floor
            val label = if (armed) "Confirm" else "Reset PB"
            draw.button(label, m.rightX(RESET_BUTTON_WIDTH), y + (cardHeight - 22) / 2, if (armed) ButtonStyle.PRIMARY else ButtonStyle.GHOST) {
                onResetClicked(floor, toast)
            }
        }
        return y + m.row
    }

    private fun onResetClicked(floor: DungeonFloor, toast: (String) -> Unit) {
        if (pendingResetFloor != floor) {
            pendingResetFloor = floor
            toast("Click Confirm to reset the ${floor.name} PB")
            return
        }
        pendingResetFloor = null
        RunSplitsController.resetPb(floor)
        toast("Reset the ${floor.name} PB")
    }

    private data class Columns(val floor: Int, val pb: Int, val sum: Int, val runs: Int)

    /**
     * Column starts. The Reset PB button owns the right edge, so the four columns must end before it: wide windows keep
     * the roomy spacing, narrower ones use a tighter set, and the last column never starts past the button.
     */
    private fun columnsOf(m: ConfigMetrics): Columns {
        val left = m.innerLeft + 18
        val usable = m.rightX(RESET_BUTTON_WIDTH) - 12 - left
        val offsets = if (usable >= 330) listOf(0, 70, 180, 300) else listOf(0, 48, 128, 208)
        val runs = offsets[3].coerceAtMost((usable - 24).coerceAtLeast(offsets[2] + 40))
        return Columns(floor = left, pb = left + offsets[1], sum = left + offsets[2], runs = left + runs)
    }

    private companion object {
        const val RESET_BUTTON_WIDTH = 88
    }
}
