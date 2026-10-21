package net.icantpy.qol.invsort

import net.icantpy.qol.auto.CursorGuard

/**
 * Tick-level gate for the inventory sort, free of Minecraft types. It holds the one pending click
 * confirmation and decides when the next planned step may act.
 *
 * Settle ticks count client ticks. A sync with settle ticks above zero is not observed on the tick it was
 * armed, so a checkpoint costs at least one tick. A sync with zero settle ticks is observed right after its
 * click, so intermediate clicks of a swap cost no tick. Divergence is still checked on every observation.
 */
class InvSortPacer {
    private var tick = 0L
    private var armedTick = -1L
    private var sync: CursorGuard.Sync? = null

    val pending: Boolean get() = sync != null

    /** Call once at the start of every client tick. */
    fun beginTick() {
        tick++
    }

    /** Waits for [next] to settle; replaces any previous pending confirmation. */
    fun arm(next: CursorGuard.Sync) {
        sync = next
        armedTick = tick
    }

    fun clear() {
        sync = null
    }

    /**
     * Returns the verdict of the pending confirmation, or null when nothing is pending. WAITING also covers
     * a settled sync that must wait for a later tick. [snapshotOf] is only called when the sync is observed.
     */
    fun observe(nowMs: Long, snapshotOf: (Set<Int>) -> CursorGuard.Snapshot): CursorGuard.Verdict? {
        val pending = sync ?: return null
        if (pending.settleTicks > 0 && armedTick == tick) return CursorGuard.Verdict.WAITING
        val verdict = pending.observe(nowMs, snapshotOf(pending.watchedSlots))
        if (verdict != CursorGuard.Verdict.WAITING) sync = null
        return verdict
    }
}
