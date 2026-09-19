package net.icantpy.dungeon.timer

data class ClockSnapshot(
    val values: Map<TimerClock, Int>,
) {
    fun value(clock: TimerClock): Int = values[clock] ?: TickClocks.INACTIVE

    companion object {
        fun from(clocks: TickClocks): ClockSnapshot {
            val values = TimerClock.entries.associateWith { clock ->
                if (clock in TimerClock.elapsedClocks && clock !in setOf(
                        TimerClock.GOLDOR_ELAPSED,
                        TimerClock.MAXOR_ELAPSED,
                        TimerClock.STORM_ELAPSED,
                    )
                ) {
                    clocks.phaseElapsed[clock] ?: TickClocks.INACTIVE
                } else when (clock) {
                    TimerClock.PAD -> clocks.pad
                    TimerClock.GOLDOR_TICK -> clocks.goldorTick
                    TimerClock.GOLDOR_START -> clocks.goldorStart
                    TimerClock.GOLDOR_ELAPSED -> clocks.goldorElapsed
                    TimerClock.MAXOR_ELAPSED -> clocks.maxorElapsed
                    TimerClock.LIGHTNING -> clocks.lightning
                    TimerClock.PY -> clocks.py
                    TimerClock.STORM_ELAPSED -> clocks.stormTick
                    TimerClock.NECRON -> clocks.necron
                    TimerClock.SECRETS -> clocks.secretRemaining()
                    else -> TickClocks.INACTIVE
                }
            }
            return ClockSnapshot(values)
        }
    }
}

class TriggerArm {
    private var previous: Map<TimerClock, Int> = emptyMap()
    private val sinceZero = HashMap<TimerClock, Int>()
    private val sinceInactive = HashMap<TimerClock, Int>()

    fun reset() {
        previous = emptyMap()
        sinceZero.clear()
        sinceInactive.clear()
    }

    fun evaluate(snapshot: ClockSnapshot, triggers: List<TimerTrigger>): List<TimerTrigger> {
        val previousZero = HashMap(sinceZero)
        val previousSince = HashMap(sinceInactive)
        for (clock in TimerClock.entries) {
            val current = snapshot.value(clock)
            val last = previous[clock] ?: TickClocks.INACTIVE
            when {
                clock in sinceZero -> sinceZero[clock] = sinceZero.getValue(clock) + 1
                current == 0 && last != 0 -> sinceZero[clock] = 0
            }
            when {
                current >= 0 -> sinceInactive.remove(clock)
                last >= 0 -> sinceInactive[clock] = 0
                clock in sinceInactive -> sinceInactive[clock] = sinceInactive.getValue(clock) + 1
            }
        }
        val fired = ArrayList<TimerTrigger>()
        for (trigger in triggers) {
            if (!trigger.enabled) continue
            val current = snapshot.value(trigger.clock)
            val last = previous[trigger.clock] ?: TickClocks.INACTIVE
            val crossed = when {
                trigger.ticks < 0 && trigger.clock.countsUp() ->
                    delayed(sinceInactive[trigger.clock], previousSince[trigger.clock], -trigger.ticks)
                trigger.ticks < 0 ->
                    delayed(sinceZero[trigger.clock], previousZero[trigger.clock], -trigger.ticks)
                trigger.clock.countsUp() -> last < trigger.ticks && current >= trigger.ticks && current >= 0
                else -> current >= 0 && last > trigger.ticks && current <= trigger.ticks
            }
            if (crossed) fired += trigger
        }
        for (clock in TimerClock.entries) {
            val current = snapshot.value(clock)
            val last = previous[clock] ?: TickClocks.INACTIVE
            if (current == 0 && last != 0 && previousZero.containsKey(clock)) {
                sinceZero[clock] = 0
            }
        }
        previous = snapshot.values
        return fired
    }

    private fun delayed(elapsed: Int?, was: Int?, target: Int): Boolean {
        if (elapsed == null) return false
        val previous = was ?: -1
        return previous < target && elapsed >= target
    }
}
