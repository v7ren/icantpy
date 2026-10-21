package net.icantpy.qol.invsort

import kotlin.random.Random

/** Samples human-like click delays uniformly within an inclusive millisecond range. */
class InvSortTiming(
    minMs: Int,
    maxMs: Int,
    /** When true every delay is zero and the runner may perform several steps in one tick. */
    val instant: Boolean = false,
    private val random: Random = Random.Default,
) {
    val minDelayMs: Int
    val maxDelayMs: Int

    init {
        val range = InvSortSettings.normalizeDelayRange(minMs, maxMs)
        minDelayMs = range.first
        maxDelayMs = range.second
    }

    fun sampleDelayMs(): Int {
        if (instant) return 0
        return minDelayMs + random.nextInt(maxDelayMs - minDelayMs + 1)
    }

    /** Delay before the first click after the inventory opens, in nanoseconds. */
    fun openDelayNanos(requestedMs: Int): Long = if (instant) 0L else millisToNanos(requestedMs)

    companion object {
        /** Cap on clicks performed in one tick in instant mode; the rest resume next tick. */
        const val MAX_CLICKS_PER_TICK = 12

        fun millisToNanos(ms: Int): Long = ms.toLong() * 1_000_000L
    }
}
