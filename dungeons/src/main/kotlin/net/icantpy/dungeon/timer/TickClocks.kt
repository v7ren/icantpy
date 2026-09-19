package net.icantpy.dungeon.timer

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
    val maxorElapsed: Int = INACTIVE,
    val pad: Int = INACTIVE,
    val lightning: Int = INACTIVE,
    val py: Int = INACTIVE,
    val pyTriggered: Boolean = false,
    val stormTick: Int = INACTIVE,
    val secretsCounter: Int = 0,
    val phaseElapsed: Map<TimerClock, Int> = emptyMap(),
) {
    fun onChat(line: String): TickClocks {
        val next = when {
            line.matches(MORT) -> copy(secretsCounter = 0)
            line.startsWith("[BOSS] Maxor:") && maxorElapsed < 0 -> copy(maxorElapsed = 0)
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
                maxorElapsed = INACTIVE,
            )
            !pyTriggered && line.matches(STORM_PY) -> copy(pyTriggered = true, py = PY_TICKS)
            else -> this
        }
        return next.withDungeonSplit(line)
    }

    fun onBossBar(name: String, progress: Float): TickClocks {
        if (!progress.isFinite() || progress <= 0f) return this
        val lower = name.lowercase()
        val knownBoss = lower.contains("undead") || lower.contains("bonzo") || lower.contains("scarf") ||
            lower.contains("professor") || lower.contains("guardian") || lower.contains("thorn") ||
            lower.contains("livid") || lower.contains("giant") || lower.contains("sadan")
        var next = if (knownBoss) startPhase(TimerClock.BOSS_ELAPSED) else this
        next = when {
            lower.contains("undead") -> next.startPhase(TimerClock.F1_UNDEAD)
            lower.contains("bonzo") -> next.startPhase(TimerClock.F1_BONZO)
            lower.contains("scarf") -> next.startPhase(TimerClock.F2_FIRST_PHASE)
            lower.contains("professor") && lower.contains("guardian") -> next.startPhase(TimerClock.F3_GUARDIAN)
            lower.contains("professor") -> next.startPhase(TimerClock.F3_GUARDIANS)
            lower.contains("guardian") -> next.startPhase(TimerClock.F3_GUARDIAN)
            lower.contains("thorn") -> next.startPhase(TimerClock.F4_THORN)
            lower.contains("livid") -> next.startPhase(TimerClock.F5_LIVID)
            lower.contains("giant") -> next.startPhase(TimerClock.F6_GIANTS)
            lower.contains("sadan") && next.phaseElapsed.containsKey(TimerClock.F6_GIANTS) ->
                next.startPhase(TimerClock.F6_SADAN)
            lower.contains("sadan") -> next.startPhase(TimerClock.F6_TERRACOTTAS)
            else -> next
        }
        return next
    }

    fun onGoldorSection(section: Int): TickClocks {
        val clock = when (section) {
            1 -> TimerClock.F7_S1
            2 -> TimerClock.F7_S2
            3 -> TimerClock.F7_S3
            4 -> TimerClock.F7_S4
            else -> return this
        }
        return startPhase(clock)
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
            maxorElapsed = if (next.maxorElapsed >= 0) next.maxorElapsed + 1 else INACTIVE,
            phaseElapsed = next.phaseElapsed.mapValues { (_, value) -> value + 1 },
        )
    }

    private fun withDungeonSplit(line: String): TickClocks {
        val lower = line.lowercase()
        var next = this
        if (lower.startsWith("[boss]") && !lower.startsWith("[boss] the watcher:")) {
            next = next.startPhase(TimerClock.BOSS_ELAPSED)
        }
        when {
            lower.startsWith("[boss] bonzo:") -> next = next
                .startPhase(TimerClock.F1_UNDEAD)
                .startPhase(TimerClock.F1_BONZO)
            lower.startsWith("[boss] scarf:") -> next = next.startPhase(TimerClock.F2_FIRST_PHASE)
            lower.contains("second phase") || lower.contains("phase 2") -> next = next.startPhase(TimerClock.F2_SECOND_PHASE)
            lower.contains("guardians") -> next = next.startPhase(TimerClock.F3_GUARDIANS)
            lower.contains("human") -> next = next.startPhase(TimerClock.F3_HUMAN)
            lower.contains("guardian") -> next = next.startPhase(TimerClock.F3_GUARDIAN)
            lower.startsWith("[boss] thorn:") -> next = next.startPhase(TimerClock.F4_THORN)
            lower.startsWith("[boss] livid:") -> next = next.startPhase(TimerClock.F5_LIVID)
            lower.startsWith("[boss] sadan:") -> next = next.startPhase(TimerClock.F6_TERRACOTTAS)
            lower.contains("giants") || lower.contains("giant phase") -> next = next.startPhase(TimerClock.F6_GIANTS)
            lower.contains("sadan") && next.phaseElapsed.containsKey(TimerClock.F6_GIANTS) ->
                next = next.startPhase(TimerClock.F6_SADAN)
        }
        if (line.matches(GOLDOR)) {
            next = next.startPhase(TimerClock.F7_TERMINALS)
        }
        return next
    }

    private fun startPhase(clock: TimerClock): TickClocks {
        if (phaseElapsed.containsKey(clock)) return this
        return copy(phaseElapsed = phaseElapsed + (clock to 0))
    }

    fun secretRemaining(): Int = SECRET_TICKS - (secretsCounter % SECRET_TICKS)

    companion object {
        const val INACTIVE = -1
        const val NECRON_TICKS = 60
        const val GOLDOR_TICKS = 60
        const val GOLDOR_START_TICKS = 104
        const val GOLDOR_START_MAX = 100
        const val GOLDOR_ELAPSED_MAX = 400
        const val MAXOR_ELAPSED_MAX = 400
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
