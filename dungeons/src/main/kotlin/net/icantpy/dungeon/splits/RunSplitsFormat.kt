package net.icantpy.dungeon.splits

import kotlin.math.abs

/** Pure text formatting for split times and deltas. */
object RunSplitsFormat {
    private const val MISSING = "--:--.-"

    /** `m:ss.t` (or `h:mm:ss.t` past an hour). Null renders as a placeholder. */
    fun time(ms: Long?): String {
        if (ms == null) return MISSING
        val value = ms.coerceAtLeast(0L)
        val totalSeconds = value / 1000L
        val tenth = (value % 1000L) / 100L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes >= 60L) {
            "${minutes / 60L}:${pad2(minutes % 60L)}:${pad2(seconds)}.$tenth"
        } else {
            "$minutes:${pad2(seconds)}.$tenth"
        }
    }

    /** `+1.2` / `-0.4` under a minute, `+1:02.3` beyond. Null renders as empty. */
    fun delta(ms: Long?): String {
        if (ms == null) return ""
        val magnitude = abs(ms)
        if (magnitude >= 60_000L) {
            return (if (ms < 0) "-" else "+") + time(magnitude)
        }
        val tenths = (magnitude + 50L) / 100L
        val sign = if (ms < 0 && tenths > 0L) "-" else "+"
        return "$sign${tenths / 10L}.${tenths % 10L}"
    }

    private fun pad2(value: Long): String = if (value < 10L) "0$value" else "$value"
}
