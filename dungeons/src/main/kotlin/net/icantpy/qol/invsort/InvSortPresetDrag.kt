package net.icantpy.qol.invsort

import kotlin.math.abs

/**
 * Pure geometry for dragging a preset row in the editor list. Units are GUI pixels; indices refer
 * to the preset list. A "gap" is a row boundary: gap k sits above row k, and gap count sits below
 * the last row.
 */
object InvSortPresetDrag {
    /** A press only becomes a drag once the mouse has moved more than this many pixels vertically. */
    const val DRAG_START_PX = 3

    fun exceedsThreshold(pressY: Int, mouseY: Int): Boolean = abs(mouseY - pressY) > DRAG_START_PX

    /**
     * Final index the dragged preset takes when released at [mouseY]. The drop goes to the nearest
     * row boundary; the dragged row itself is removed first, so boundaries below it shift up by one.
     */
    fun dropIndex(mouseY: Int, listTop: Int, rowHeight: Int, count: Int, from: Int): Int {
        if (count <= 0) return 0
        val nearestGap = Math.floorDiv(mouseY - listTop + rowHeight / 2, rowHeight).coerceIn(0, count)
        val target = if (nearestGap > from) nearestGap - 1 else nearestGap
        return target.coerceIn(0, count - 1)
    }

    /** Gap where the insertion line is drawn for a drop at final index [drop]. */
    fun insertionGap(from: Int, drop: Int): Int = if (drop > from) drop + 1 else drop
}
