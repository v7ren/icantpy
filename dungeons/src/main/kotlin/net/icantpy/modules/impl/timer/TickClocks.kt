package net.icantpy.modules.impl.timer

/**
 * Odin Tick Timers clocks (odtheking, BSD-3-Clause).
 * Chat must already be color-stripped.
 * Pad/Goldor wrap: set 0 back to max, then decrement on the same server tick.
 */
data class TickClocks(
    val necron: Int = INACTIVE,
    val goldorTick: Int = INACTIVE,
    val goldorStart: Int = INACTIVE,
    val goldorElapsed: Int = INACTIVE,
    val pad: Int = INACTIVE,
    val lightning: Int = INACTIVE,
    val py: Int = INACTIVE,
    val pyTriggered: Boolean = false,
    val stormTick: Int = INACTIVE,
    val secretsCounter: Int = 0,
) {
    fun onChat(line: String): TickClocks {
        return when {
            line.matches(MORT) -> copy(secretsCounter = 0)
            line.matches(NECRON) -> copy(necron = NECRON_TICKS)
            line.matches(GOLDOR) -> copy(goldorTick = GOLDOR_TICKS, goldorElapsed = 0)
            line.matches(CORE_OPENING) -> copy(
                goldorStart = INACTIVE,
                goldorTick = INACTIVE,
                goldorElapsed = INACTIVE,
            )
            line.matches(STORM_END) -> copy(
                goldorStart = GOLDOR_START_TICKS,
                pad = INACTIVE,
                stormTick = INACTIVE,
            )
            line.matches(STORM_START) -> copy(
                pad = PAD_TICKS,
                lightning = LIGHTNING_TICKS,
                stormTick = 0,
            )
            !pyTriggered && line.matches(STORM_PY) -> copy(pyTriggered = true, py = PY_TICKS)
            else -> this
        }
    }

    fun tick(
        inDungeons: Boolean = true,
        inBoss: Boolean = true,
        loopPad: Boolean = true,
        loopGoldor: Boolean = true,
    ): TickClocks {
        if (!inDungeons) return this
        var next = copy(secretsCounter = secretsCounter + 1)
        if (!inBoss) return next
        var nextGoldorTick = next.goldorTick
        if (nextGoldorTick == 0 && next.goldorStart <= 0 && loopGoldor) {
            nextGoldorTick = GOLDOR_TICKS
        }
        var nextPad = next.pad
        if (nextPad == 0 && loopPad) {
            nextPad = PAD_TICKS
        }
        return next.copy(
            goldorStart = dec(next.goldorStart),
            goldorTick = dec(nextGoldorTick),
            pad = dec(nextPad),
            lightning = dec(next.lightning),
            py = dec(next.py),
            necron = dec(next.necron),
            stormTick = if (next.stormTick >= 0) next.stormTick + 1 else INACTIVE,
            goldorElapsed = if (next.goldorElapsed >= 0) next.goldorElapsed + 1 else INACTIVE,
        )
    }

    fun secretRemaining(): Int = SECRET_TICKS - (secretsCounter % SECRET_TICKS)

    companion object {
        const val INACTIVE = -1
        const val NECRON_TICKS = 60
        const val GOLDOR_TICKS = 60
        const val GOLDOR_START_TICKS = 104
        const val GOLDOR_START_MAX = 100
        const val GOLDOR_ELAPSED_MAX = 400
        const val PAD_TICKS = 20
        const val LIGHTNING_TICKS = 560
        const val PY_TICKS = 95
        const val STORM_MAX = 620
        const val SECRET_TICKS = 20

        val MORT = Regex("""^\[NPC] Mort:.*""")
        val NECRON = Regex("""^\[BOSS] Necron: I'm afraid, your journey ends now\.$""")
        val GOLDOR = Regex("""^\[BOSS] Goldor: Who dares trespass into my domain\?$""")
        val CORE_OPENING = Regex("""^The Core entrance is opening!$""")
        val STORM_END = Regex("""^\[BOSS] Storm: I should have known that I stood no chance\.$""")
        val STORM_START = Regex("""^\[BOSS] Storm: Pathetic Maxor, just like expected\.$""")
        val STORM_PY = Regex("""^\[BOSS] Storm: (ENERGY HEED MY CALL|THUNDER LET ME BE YOUR CATALYST)!$""")

        fun format(
            prefix: String,
            time: Int,
            max: Int,
            showTicks: Boolean,
            settings: TimerSettings = TimerSettings(),
            overrideColor: String? = null,
        ): String {
            val color = overrideColor ?: when {
                time.toFloat() >= max * 0.66f -> "§a"
                time.toFloat() >= max * 0.33f -> "§6"
                else -> "§c"
            }
            val unit = if (showTicks) "t" else "s"
            val value = if (showTicks) time.toString() else String.format("%.2f", time / 20.0)
            val head = if (settings.showPrefix) "$prefix " else ""
            val tail = if (settings.showSuffix) unit else ""
            return "$head$color$value$tail"
        }

        fun secretColor(time: Int): String = when {
            time < 5 -> "§a"
            time < 10 -> "§6"
            else -> "§c"
        }

        private fun dec(value: Int): Int = when {
            value > 0 -> value - 1
            value == 0 -> INACTIVE
            else -> INACTIVE
        }
    }
}
