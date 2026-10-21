package net.icantpy.qol.invsort

import kotlin.math.max
import kotlin.math.min

/**
 * Pure geometry for [InvSortEditorScreen]. All rectangles are absolute screen pixels, so the screen
 * can draw and hit-test with the same values. No Minecraft types are used here, which keeps it testable.
 */
object InvSortEditorLayout {
    /** Axis-aligned rectangle covering x until x+w and y until y+h. */
    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        val right: Int get() = x + w
        val bottom: Int get() = y + h

        fun contains(px: Int, py: Int): Boolean = px in x until right && py in y until bottom

        fun inside(outer: Rect): Boolean =
            x >= outer.x && y >= outer.y && right <= outer.right && bottom <= outer.bottom

        fun overlaps(other: Rect): Boolean =
            w > 0 && h > 0 && other.w > 0 && other.h > 0 &&
                x < other.right && other.x < right && y < other.bottom && other.y < bottom
    }

    /** Everything the editor draws or hit-tests in one frame. [listScroll] is already clamped. */
    data class EditorLayout(
        val panel: Rect,
        val list: Rect,
        val visibleRows: Int,
        val listScroll: Int,
        /** Scrollbar track inside the list, or null when every preset fits. */
        val scrollbar: Rect?,
        val grid: Rect,
        val cell: Int,
        val armor: Rect,
        val renameBox: Rect,
        val hotkey: Rect,
        val moveUp: Rect,
        val moveDown: Rect,
        /** One rect per entry of the button widths passed to [compute], in the same order. */
        val buttons: List<Rect>,
        val status: Rect,
        val stacked: Boolean,
    )

    const val DESIRED_W = 500
    const val DESIRED_H = 250
    const val SCREEN_MARGIN = 8
    const val PAD = 14
    const val TOP_OFFSET = 34
    const val LIST_ROW = 20
    const val LIST_MAX_W = 150
    const val LIST_MIN_W = 90
    const val LIST_BOTTOM_GAP = 8
    const val STACK_LIST_ROWS = 3
    const val STACK_GAP = 10
    const val SCROLLBAR_W = 4
    const val CELL_MAX = 28
    const val CELL_MIN = 14
    const val GRID_COLS = 9
    const val GRID_ROWS = 4
    const val GRID_GAP_X = 12
    const val ARMOR_GAP = 10
    const val RENAME_W = 200
    const val RENAME_H = 20
    const val RENAME_GAP = 6
    const val HOTKEY_ROW_OFFSET = 26
    const val HOTKEY_H = 18
    const val HOTKEY_MAX_W = 200
    const val MOVE_W = 22
    const val MOVE_GAP = 6
    const val MOVE_SPACING = 2
    const val BUTTON_H = 18
    const val BUTTON_GAP = 4
    const val STATUS_H = 10
    const val STATUS_BOTTOM_OFFSET = 22
    const val BUTTONS_STATUS_GAP = 6
    private const val MIN_THUMB_H = 8

    /**
     * Computes the editor layout for a screen of [screenW] x [screenH] pixels.
     *
     * [buttonWidths] are the natural widths of the bottom buttons (label width plus padding). Buttons
     * wrap onto extra rows when they do not fit, and stretch to fill their row.
     *
     * Strategy: prefer list and grid side by side, shrinking the grid cell (28 down to 14) and the list
     * column (down to [LIST_MIN_W]). If nothing fits side by side, stack the list above the grid.
     */
    fun compute(
        screenW: Int,
        screenH: Int,
        presetCount: Int,
        scroll: Int,
        buttonWidths: List<Int>,
    ): EditorLayout {
        val panelW = min(DESIRED_W, screenW - 2 * SCREEN_MARGIN).coerceAtLeast(1)
        val panelH = min(DESIRED_H, screenH - 2 * SCREEN_MARGIN).coerceAtLeast(1)
        val panel = Rect((screenW - panelW) / 2, (screenH - panelH) / 2, panelW, panelH)
        val avail = panelW - 2 * PAD

        for (cell in CELL_MAX downTo CELL_MIN) {
            val listW = min(LIST_MAX_W, avail - GRID_GAP_X - ARMOR_GAP - (GRID_COLS + 1) * cell)
            if (listW < LIST_MIN_W) continue
            val layout = build(panel, cell, listW, false, presetCount, scroll, buttonWidths)
            if (fits(layout)) return layout
        }
        for (cell in CELL_MAX downTo CELL_MIN) {
            if ((GRID_COLS + 1) * cell + ARMOR_GAP > avail) continue
            val layout = build(panel, cell, avail, true, presetCount, scroll, buttonWidths)
            if (fits(layout)) return layout
        }
        // Nothing fits cleanly (very small screens): smallest cells, side by side.
        val fallbackW = max(0, avail - GRID_GAP_X - ARMOR_GAP - (GRID_COLS + 1) * CELL_MIN)
        return build(panel, CELL_MIN, fallbackW, false, presetCount, scroll, buttonWidths)
    }

    private fun build(
        panel: Rect,
        cell: Int,
        listW: Int,
        stacked: Boolean,
        presetCount: Int,
        scroll: Int,
        buttonWidths: List<Int>,
    ): EditorLayout {
        val avail = panel.w - 2 * PAD
        val listTop = panel.y + TOP_OFFSET
        val statusY = panel.bottom - STATUS_BOTTOM_OFFSET
        val status = Rect(panel.x + PAD, statusY, avail, STATUS_H)

        // Bottom button block sits above the status line, growing upward when it wraps.
        val flow = flowButtons(buttonWidths, avail)
        val buttonsTop = statusY - BUTTONS_STATUS_GAP - flow.blockH
        val buttons = flow.rects.map { Rect(panel.x + PAD + it.x, buttonsTop + it.y, it.w, it.h) }

        val list: Rect
        val gridX: Int
        val gridY: Int
        if (stacked) {
            list = Rect(panel.x + PAD, listTop, avail, STACK_LIST_ROWS * LIST_ROW)
            gridX = panel.x + PAD
            gridY = list.bottom + STACK_GAP
        } else {
            list = Rect(panel.x + PAD, listTop, listW, max(0, buttonsTop - LIST_BOTTOM_GAP - listTop))
            gridX = list.right + GRID_GAP_X
            gridY = listTop
        }

        val grid = Rect(gridX, gridY, GRID_COLS * cell, GRID_ROWS * cell)
        val armor = Rect(grid.right + ARMOR_GAP, gridY, cell, GRID_ROWS * cell)
        val renameBox = Rect(gridX, grid.bottom + RENAME_GAP, min(RENAME_W, armor.right - gridX), RENAME_H)

        val hotkeyY = grid.bottom + HOTKEY_ROW_OFFSET
        val hotkeyW = (GRID_COLS * cell - MOVE_GAP - 2 * MOVE_W - MOVE_SPACING).coerceAtMost(HOTKEY_MAX_W)
        val hotkey = Rect(gridX, hotkeyY, hotkeyW, HOTKEY_H)
        val moveUp = Rect(hotkey.right + MOVE_GAP, hotkeyY, MOVE_W, HOTKEY_H)
        val moveDown = Rect(moveUp.right + MOVE_SPACING, hotkeyY, MOVE_W, HOTKEY_H)

        val visibleRows = list.h / LIST_ROW
        val clamped = clampScroll(scroll, presetCount, visibleRows)
        val scrollbar = if (visibleRows > 0 && presetCount > visibleRows) {
            Rect(list.right - SCROLLBAR_W, list.y, SCROLLBAR_W, visibleRows * LIST_ROW)
        } else {
            null
        }

        return EditorLayout(
            panel = panel,
            list = list,
            visibleRows = visibleRows,
            listScroll = clamped,
            scrollbar = scrollbar,
            grid = grid,
            cell = cell,
            armor = armor,
            renameBox = renameBox,
            hotkey = hotkey,
            moveUp = moveUp,
            moveDown = moveDown,
            buttons = buttons,
            status = status,
            stacked = stacked,
        )
    }

    /** True when every element is inside the panel and no two interactive elements overlap. */
    private fun fits(layout: EditorLayout): Boolean {
        if (layout.visibleRows < 1) return false
        val all = listOf(layout.list, layout.grid, layout.armor, layout.renameBox, layout.hotkey, layout.moveUp, layout.moveDown, layout.status) +
            layout.buttons
        if (!all.all { it.inside(layout.panel) }) return false
        for (i in all.indices) for (j in i + 1 until all.size) if (all[i].overlaps(all[j])) return false
        return true
    }

    private data class Flow(val rects: List<Rect>, val blockH: Int)

    /**
     * Packs buttons left to right into rows of at most [avail] pixels. Rects are relative to the top-left
     * of the block. Each row stretches its buttons evenly to fill the width.
     */
    private fun flowButtons(widths: List<Int>, avail: Int): Flow {
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0
        widths.forEachIndexed { index, w ->
            val current = rows.lastOrNull()
            if (current == null || used + BUTTON_GAP + w > avail) {
                rows += mutableListOf(index)
                used = w
            } else {
                current += index
                used += BUTTON_GAP + w
            }
        }
        val rects = arrayOfNulls<Rect>(widths.size)
        rows.forEachIndexed { row, indices ->
            val y = row * (BUTTON_H + BUTTON_GAP)
            val natural = indices.sumOf { widths[it] } + BUTTON_GAP * (indices.size - 1)
            val extra = if (avail > natural) (avail - natural) / indices.size else 0
            var bx = 0
            indices.forEach { index ->
                val w = widths[index] + extra
                rects[index] = Rect(bx, y, w, BUTTON_H)
                bx += w + BUTTON_GAP
            }
        }
        val blockH = if (rows.isEmpty()) 0 else rows.size * BUTTON_H + (rows.size - 1) * BUTTON_GAP
        return Flow(rects.map { it ?: Rect(0, 0, 0, 0) }, blockH)
    }

    /** Largest valid scroll offset: zero when everything fits. */
    fun maxScroll(count: Int, visibleRows: Int): Int = (count - visibleRows).coerceAtLeast(0)

    fun clampScroll(scroll: Int, count: Int, visibleRows: Int): Int =
        scroll.coerceIn(0, maxScroll(count, visibleRows))

    /** Offset that brings [index] into the visible window, moving as little as possible. */
    fun scrollIntoView(scroll: Int, index: Int, count: Int, visibleRows: Int): Int {
        if (count <= 0 || visibleRows <= 0) return 0
        val target = when {
            index < scroll -> index
            index >= scroll + visibleRows -> index - visibleRows + 1
            else -> scroll
        }
        return clampScroll(target, count, visibleRows)
    }

    /** One mouse-wheel notch: positive [verticalAmount] (wheel up) scrolls toward the top. */
    fun wheelScroll(scroll: Int, verticalAmount: Double, count: Int, visibleRows: Int): Int {
        if (verticalAmount == 0.0) return clampScroll(scroll, count, visibleRows)
        val step = if (verticalAmount > 0) -1 else 1
        return clampScroll(scroll + step, count, visibleRows)
    }

    /** -1 when the mouse is near the top edge of the list, +1 near the bottom edge, otherwise 0. */
    fun autoScrollStep(mouseY: Int, listTop: Int, listBottom: Int): Int = when {
        mouseY < listTop + LIST_ROW -> -1
        mouseY >= listBottom - LIST_ROW -> 1
        else -> 0
    }

    /** Preset index under [mouseY], or null for gaps, the bottom padding of a row, or empty space. */
    fun rowAt(mouseY: Int, listTop: Int, count: Int, visibleRows: Int, scroll: Int): Int? {
        val offset = mouseY - listTop
        if (offset < 0 || offset % LIST_ROW >= LIST_ROW - 2) return null
        val visibleIndex = offset / LIST_ROW
        if (visibleIndex >= visibleRows) return null
        val index = scroll + visibleIndex
        return index.takeIf { it < count }
    }

    /**
     * Drop index for a drag released at [mouseY], with the list scrolled by [scroll] rows. Shifting the
     * top edge by the scroll keeps the result in absolute preset indices.
     */
    fun dropIndex(mouseY: Int, listTop: Int, count: Int, scroll: Int, from: Int): Int =
        InvSortPresetDrag.dropIndex(mouseY, listTop - scroll * LIST_ROW, LIST_ROW, count, from)

    /** Thumb rect for a scrollbar [track]; null when nothing overflows. */
    fun thumb(track: Rect, count: Int, visibleRows: Int, scroll: Int): Rect? {
        if (visibleRows <= 0 || count <= visibleRows) return null
        val h = max(MIN_THUMB_H, track.h * visibleRows / count).coerceAtMost(track.h)
        val travel = track.h - h
        val maxS = maxScroll(count, visibleRows)
        val y = track.y + travel * clampScroll(scroll, count, visibleRows) / maxS
        return Rect(track.x, y, track.w, h)
    }

    /** [text] shortened with "..." so that [measure] reports at most [maxW] pixels. */
    fun fitText(text: String, maxW: Int, measure: (String) -> Int): String {
        if (measure(text) <= maxW) return text
        val ellipsis = "..."
        var end = text.length
        while (end > 0 && measure(text.substring(0, end) + ellipsis) > maxW) end--
        return if (end == 0) "" else text.substring(0, end) + ellipsis
    }
}
