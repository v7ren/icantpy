package net.icantpy.dungeon.terminal

/**
 * Clicks sent to the server whose result has not arrived yet, so the UI can show them as done at once instead of
 * waiting a round trip. An entry ends when its slot's item changes (the server answered) or after [timeoutMs]
 * (the server ignored the click, for example because it came too fast), so a lost click shows up again.
 *
 * Colour-cycle entries count signed steps. When the server applies only some of several queued clicks, the applied
 * steps are subtracted and the rest stay pending.
 */
class TerminalPendingClicks(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    private data class Pending(val signature: String, val color: RubixColor?, val steps: Int, val sentAtMs: Long)

    private val bySlot = HashMap<Int, Pending>()

    fun record(item: TerminalItem, steps: Int, nowMs: Long) {
        val previous = bySlot[item.slot]?.takeIf { it.signature == signature(item) }
        val total = (previous?.steps ?: 0) + steps
        bySlot[item.slot] = Pending(signature(item), RubixColor.fromItemId(item.itemId), total, nowMs)
    }

    /** Drops answered and expired entries against the latest [snapshot]. */
    fun prune(snapshot: TerminalSnapshot, nowMs: Long) {
        if (bySlot.isEmpty()) return
        val items = snapshot.items.associateBy { it.slot }
        val iterator = bySlot.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val pending = entry.value
            val item = items[entry.key]
            if (item == null || nowMs - pending.sentAtMs > timeoutMs) {
                iterator.remove()
                continue
            }
            if (signature(item) == pending.signature) continue
            val remaining = remainingRubixSteps(pending, item)
            if (remaining == null) {
                iterator.remove()
            } else {
                entry.setValue(pending.copy(signature = signature(item), color = RubixColor.fromItemId(item.itemId), steps = remaining))
            }
        }
    }

    fun asMap(): Map<Int, Int> = bySlot.mapValues { it.value.steps }

    fun clear() {
        bySlot.clear()
    }

    /** Steps still in flight after the server moved a colour-cycle pane, or null when nothing is left pending. */
    private fun remainingRubixSteps(pending: Pending, item: TerminalItem): Int? {
        val from = pending.color ?: return null
        val to = RubixColor.fromItemId(item.itemId) ?: return null
        val size = RubixColor.entries.size
        val applied = if (pending.steps > 0) TerminalSolver.leftClicks(from, to) else -TerminalSolver.leftClicks(to, from)
        val remaining = pending.steps - applied
        val sameDirection = remaining != 0 && (remaining > 0) == (pending.steps > 0)
        return remaining.takeIf { sameDirection && kotlin.math.abs(it) < size }
    }

    private fun signature(item: TerminalItem): String = "${item.itemId}|${item.count}|${item.glint}|${item.name}"

    companion object {
        const val DEFAULT_TIMEOUT_MS = 800L
    }
}
