package net.icantpy.slayer.blaze

/**
 * Detection speed limit: a column only passes [filter] once it has been present for the configured number of ticks,
 * so the helper reacts no faster than that after something appears. Columns that vanish are forgotten.
 */
class DetectDelay {
    private val firstSeen = HashMap<Long, Long>()

    /** Adds to [out] the columns of [current] that have been present for at least [delayTicks] ticks at [tick]. */
    fun filter(current: Set<Long>, tick: Long, delayTicks: Int, out: MutableSet<Long>) {
        if (delayTicks <= 0) {
            firstSeen.clear()
            out.addAll(current)
            return
        }
        firstSeen.keys.retainAll(current)
        for (key in current) {
            val since = firstSeen.getOrPut(key) { tick }
            if (tick - since >= delayTicks) out.add(key)
        }
    }

    fun clear() = firstSeen.clear()

    companion object {
        const val TICK_MS = 50

        /** Whole ticks needed to cover [ms] milliseconds (20 ticks per second). */
        fun ticksOf(ms: Int): Int = if (ms <= 0) 0 else (ms + TICK_MS - 1) / TICK_MS
    }
}
