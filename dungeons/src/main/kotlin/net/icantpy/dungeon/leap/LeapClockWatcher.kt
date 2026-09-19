package net.icantpy.dungeon.leap

import net.icantpy.dungeon.timer.ClockSnapshot
import net.icantpy.dungeon.timer.TimerClock

class LeapClockWatcher {
    private var previous = ClockSnapshot(emptyMap())
    private val generations = HashMap<TimerClock, Int>()
    private val sinceZero = HashMap<TimerClock, Int>()
    private val sinceInactive = HashMap<TimerClock, Int>()

    fun reset() {
        previous = ClockSnapshot(emptyMap())
        generations.clear()
        sinceZero.clear()
        sinceInactive.clear()
    }

    fun evaluate(snapshot: ClockSnapshot, triggers: List<LeapOrientTrigger>): List<LeapOrientTrigger> {
        val fired = ArrayList<LeapOrientTrigger>()
        val previousZero = HashMap(sinceZero)
        val previousSince = HashMap(sinceInactive)

        for (clock in TimerClock.entries) {
            val lastRaw = previous.value(clock)
            val currentRaw = snapshot.value(clock)
            if (LeapClocks.resetGeneration(lastRaw, currentRaw)) {
                generations[clock] = (generations[clock] ?: 0) + 1
                sinceZero.remove(clock)
                sinceInactive.remove(clock)
            }
            when {
                clock in sinceZero -> sinceZero[clock] = sinceZero.getValue(clock) + 1
                LeapClocks.reachedZero(lastRaw, currentRaw) -> sinceZero[clock] = 0
            }
            when {
                currentRaw >= 0 -> sinceInactive.remove(clock)
                lastRaw >= 0 -> sinceInactive[clock] = 0
                clock in sinceInactive -> sinceInactive[clock] = sinceInactive.getValue(clock) + 1
            }
        }

        for (trigger in triggers) {
            if (trigger.event != LeapTriggerEvent.CLOCK) continue
            val spec = trigger.clock ?: continue
            val crossed = if (spec.isAfterTrigger()) {
                crossedAfter(spec, previousZero, previousSince)
            } else {
                val last = LeapClocks.nativeValue(previous, spec)
                val current = LeapClocks.nativeValue(snapshot, spec)
                LeapClocks.crossed(last, current, spec)
            }
            if (crossed) fired += trigger
        }

        for (clock in TimerClock.entries) {
            val currentRaw = snapshot.value(clock)
            val lastRaw = previous.value(clock)
            if (LeapClocks.reachedZero(lastRaw, currentRaw) && previousZero.containsKey(clock)) {
                sinceZero[clock] = 0
            }
        }

        previous = snapshot
        return fired
    }

    private fun crossedAfter(
        spec: ClockSpec,
        previousZero: Map<TimerClock, Int>,
        previousSince: Map<TimerClock, Int>,
    ): Boolean {
        val target = -spec.thresholdTicks
        val elapsed = if (spec.clock.countsUp()) {
            sinceInactive[spec.clock]
        } else {
            sinceZero[spec.clock]
        } ?: return false
        val was = if (spec.clock.countsUp()) {
            previousSince[spec.clock]
        } else {
            previousZero[spec.clock]
        } ?: -1
        return was < target && elapsed >= target
    }
}
