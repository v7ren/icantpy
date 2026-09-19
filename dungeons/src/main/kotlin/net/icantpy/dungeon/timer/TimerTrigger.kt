package net.icantpy.dungeon.timer

import java.util.UUID
import kotlin.math.roundToInt

data class TimerTrigger(
    val id: String,
    val clock: TimerClock,
    val ticks: Int,
    val message: String = "",
    val durationTicks: Int = DEFAULT_DURATION_TICKS,
    val enabled: Boolean = true,
    val sound: String = "",
) {
    fun overlayText(): String {
        val custom = message.trim()
        if (custom.isNotEmpty()) {
            return if (custom.startsWith("§")) custom else "§e§l$custom"
        }
        val whenText = when {
            ticks == 0 -> "DONE"
            ticks < 0 -> "${formatSeconds(-ticks)} AFTER"
            else -> formatSeconds(ticks)
        }
        return "§e§l${clock.label().uppercase()} $whenText"
    }

    fun secondsInput(): String = ticksToInput(ticks)

    fun durationInput(): String = ticksToInput(durationTicks)

    companion object {
        const val DEFAULT_DURATION_TICKS: Int = 40

        fun create(
            clock: TimerClock = TimerClock.PAD,
            seconds: Double = 0.0,
            message: String = "",
            durationSeconds: Double = 2.0,
        ): TimerTrigger = TimerTrigger(
            id = UUID.randomUUID().toString(),
            clock = clock,
            ticks = secondsToTicks(seconds),
            message = message,
            durationTicks = secondsToTicks(durationSeconds).coerceAtLeast(1),
        )

        fun secondsToTicks(seconds: Double): Int =
            (seconds * 20.0).roundToInt()

        fun formatSeconds(ticks: Int): String {
            val seconds = ticks / 20.0
            return if (ticks % 20 == 0) "${ticks / 20}s" else String.format("%.2fs", seconds)
        }

        fun ticksToInput(ticks: Int): String {
            if (ticks % 20 == 0) return (ticks / 20).toString()
            return String.format("%.2f", ticks / 20.0)
        }

        fun parseSeconds(raw: String): Double? {
            val trimmed = raw.trim().replace(',', '.')
            if (trimmed.isEmpty()) return 0.0
            if (trimmed == "-" || trimmed == "." || trimmed == "-.") return null
            return trimmed.toDoubleOrNull()?.takeIf { it >= -3600.0 && it <= 3600.0 }
        }
    }
}

data class ActiveNotification(
    val text: String,
    val ticksLeft: Int,
)
