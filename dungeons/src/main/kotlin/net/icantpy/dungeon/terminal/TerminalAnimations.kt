package net.icantpy.dungeon.terminal

/**
 * Remembers when each cell finished so it can fade from its click colour to the finished colour. One instance per
 * view (the live overlay, the preview); [begin] with a new key (window or sample) forgets everything, and cells that
 * are already finished the first time they are seen do not animate.
 */
class TerminalAnimations {
    private var key: Any? = null
    private var seeded = false
    private val previous = HashMap<Int, TerminalCellState>()
    private val doneAt = HashMap<Int, Long>()

    fun begin(newKey: Any) {
        if (newKey == key) return
        key = newKey
        seeded = false
        previous.clear()
        doneAt.clear()
    }

    /** Call once per frame with the cells about to be drawn. */
    fun observe(cells: List<TerminalCell>, nowMs: Long) {
        cells.forEach { cell ->
            val before = previous.put(cell.slot, cell.state)
            if (!seeded) return@forEach
            if (cell.state == TerminalCellState.DONE && before != null && before != TerminalCellState.DONE) {
                doneAt[cell.slot] = nowMs
            } else if (cell.state != TerminalCellState.DONE) {
                doneAt.remove(cell.slot)
            }
        }
        seeded = true
    }

    /** 0 just finished, 1 fully faded (or never animated). */
    fun doneProgress(slot: Int, nowMs: Long): Float {
        val at = doneAt[slot] ?: return 1f
        return ((nowMs - at).toFloat() / FADE_MS).coerceIn(0f, 1f)
    }

    companion object {
        const val FADE_MS = 180f
    }
}
