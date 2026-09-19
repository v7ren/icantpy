package net.icantpy.dungeon.leap

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.timer.ClockSnapshot
import net.icantpy.dungeon.timer.TickClocks
import net.icantpy.dungeon.timer.TimerClock
import net.icantpy.dungeon.timer.TimerTrigger

enum class LeapFloor {
    ANY,
    UNKNOWN,
    F7,
    M7,
    ;

    fun label(): String = when (this) {
        ANY -> "Any"
        UNKNOWN -> "Unknown"
        F7 -> "F7"
        M7 -> "M7"
    }

    companion object {
        fun fromDungeon(floor: DungeonFloor): LeapFloor = when (floor) {
            DungeonFloor.F7 -> F7
            DungeonFloor.M7 -> M7
            else -> UNKNOWN
        }

        fun fromToken(raw: String): LeapFloor = when (raw.lowercase().trim()) {
            "any", "" -> ANY
            "unknown" -> UNKNOWN
            "f7" -> F7
            "m7" -> M7
            else -> UNKNOWN
        }
    }
}

enum class LeapPhase {
    ANY,
    UNKNOWN,
    CLEAR,
    P1,
    P2,
    P3,
    P4,
    P5,
    ;

    fun label(): String = when (this) {
        ANY -> "Any"
        UNKNOWN -> "Unknown"
        CLEAR -> "Clear"
        P1 -> "P1"
        P2 -> "P2"
        P3 -> "P3"
        P4 -> "P4"
        P5 -> "P5"
    }

    fun group(): String = when (this) {
        P1 -> "P1"
        P2 -> "P2"
        P3 -> "P3"
        P4 -> "P4"
        P5 -> "P5"
        CLEAR -> "Clear"
        ANY, UNKNOWN -> "Other"
    }

    companion object {
        fun fromToken(raw: String): LeapPhase = when (raw.lowercase().trim()) {
            "any", "" -> ANY
            "unknown" -> UNKNOWN
            "clear" -> CLEAR
            "p1", "maxor" -> P1
            "p2", "storm" -> P2
            "p3", "goldor" -> P3
            "p4", "necron" -> P4
            "p5", "relic", "dragons" -> P5
            else -> UNKNOWN
        }
    }
}

enum class LeapStormRoute {
    ANY,
    GY,
    PY,
    ;

    fun label(): String = when (this) {
        ANY -> "Any"
        GY -> "GY"
        PY -> "PY"
    }

    fun next(): LeapStormRoute = when (this) {
        ANY -> GY
        GY -> PY
        PY -> ANY
    }

    companion object {
        fun fromToken(raw: String): LeapStormRoute = when (raw.lowercase().trim()) {
            "gy", "green" -> GY
            "py", "purple" -> PY
            else -> ANY
        }
    }
}

enum class LeapSourceKind {
    PARTY_PING,
    SELF_LEAP,
    CHAIN,
    BOSS,
    CLOCK,
    CUSTOM,
    ;

    fun rank(): Int = when (this) {
        PARTY_PING -> 100
        SELF_LEAP -> 80
        CHAIN -> 70
        BOSS -> 60
        CLOCK -> 40
        CUSTOM -> 20
    }

    fun label(): String = when (this) {
        PARTY_PING -> "Party ping"
        SELF_LEAP -> "Leap chat"
        CHAIN -> "Leap chain"
        BOSS -> "Boss"
        CLOCK -> "Clock"
        CUSTOM -> "Custom"
    }

    companion object {
        fun fromEvent(event: LeapTriggerEvent, preset: Boolean): LeapSourceKind {
            if (!preset) return CUSTOM
            return when (event) {
                LeapTriggerEvent.LEAPED_TO, LeapTriggerEvent.SELF_LEAP -> SELF_LEAP
                LeapTriggerEvent.LEAP_CHAIN -> CHAIN
                LeapTriggerEvent.CLOCK -> CLOCK
                else -> BOSS
            }
        }
    }
}

enum class ClockMetric {
    ELAPSED,
    REMAINING,
    ;

    fun label(): String = when (this) {
        ELAPSED -> "Elapsed"
        REMAINING -> "Left"
    }

    fun next(): ClockMetric = if (this == ELAPSED) REMAINING else ELAPSED
}

enum class ClockCrossing {
    RISING,
    FALLING,
    ;

    fun label(): String = when (this) {
        RISING -> "Hits"
        FALLING -> "Down to"
    }

    fun next(): ClockCrossing = if (this == RISING) FALLING else RISING
}

data class ClockSpec(
    val clock: TimerClock = TimerClock.STORM_ELAPSED,
    val metric: ClockMetric = ClockMetric.ELAPSED,
    val thresholdTicks: Int = 0,
    val crossing: ClockCrossing = ClockCrossing.RISING,
) {
    fun isAfterTrigger(): Boolean = thresholdTicks < 0

    fun secondsInput(): String = TimerTrigger.ticksToInput(thresholdTicks)

    fun summary(): String {
        if (thresholdTicks < 0) {
            val after = TimerTrigger.formatSeconds(-thresholdTicks)
            return "${clock.label()} $after after"
        }
        val amount = if (thresholdTicks % 20 == 0) "${thresholdTicks / 20}s" else String.format("%.2fs", thresholdTicks / 20.0)
        return "${clock.label()} ${metric.label().lowercase()} $amount"
    }

    companion object {
        fun stormElapsed(seconds: Double): ClockSpec = forSeconds(TimerClock.STORM_ELAPSED, seconds)

        fun remaining(clock: TimerClock, seconds: Double): ClockSpec = forSeconds(clock, seconds)

        fun goldorElapsed(seconds: Double): ClockSpec = forSeconds(TimerClock.GOLDOR_ELAPSED, seconds)

        fun forSeconds(clock: TimerClock, seconds: Double): ClockSpec {
            val ticks = secondsToTicks(seconds)
            if (ticks < 0) {
                return ClockSpec(
                    clock = clock,
                    metric = ClockMetric.ELAPSED,
                    thresholdTicks = ticks,
                    crossing = ClockCrossing.RISING,
                )
            }
            return if (clock.countsUp()) {
                ClockSpec(
                    clock = clock,
                    metric = ClockMetric.ELAPSED,
                    thresholdTicks = ticks,
                    crossing = ClockCrossing.RISING,
                )
            } else {
                ClockSpec(
                    clock = clock,
                    metric = ClockMetric.REMAINING,
                    thresholdTicks = ticks,
                    crossing = ClockCrossing.FALLING,
                )
            }
        }

        fun secondsToTicks(seconds: Double): Int = TimerTrigger.secondsToTicks(seconds)
    }
}

data class LeapRouteContext(
    val floor: LeapFloor = LeapFloor.UNKNOWN,
    val phase: LeapPhase = LeapPhase.UNKNOWN,
    val section: Int = 0,
    val route: LeapStormRoute = LeapStormRoute.PY,
    val selfClass: LeapDungeonClass = LeapDungeonClass.EMPTY,
    val ee2Owner: LeapDungeonClass = LeapDungeonClass.MAGE,
    val knownPlayers: List<LeapPlayer> = emptyList(),
    val classPlayers: Map<LeapDungeonClass, String> = emptyMap(),
) {
    fun gameStateId(): String {
        val floorKey = when (floor) {
            LeapFloor.F7 -> "f7"
            LeapFloor.M7 -> "m7"
            else -> "m7"
        }
        return when (phase) {
            LeapPhase.P1 -> "${floorKey}p1"
            LeapPhase.P2 -> "${floorKey}p2"
            LeapPhase.P3 -> if (section in 1..4) "${floorKey}p3s$section" else "${floorKey}p3"
            LeapPhase.P4 -> "${floorKey}p4"
            LeapPhase.P5 -> "${floorKey}p5"
            else -> "unknown"
        }
    }
}

object LeapClocks {
    fun nativeValue(snapshot: ClockSnapshot, spec: ClockSpec): Int {
        val raw = snapshot.value(spec.clock)
        if (raw < 0) return TickClocks.INACTIVE
        return when (spec.metric) {
            ClockMetric.ELAPSED -> if (spec.clock.countsUp()) raw else elapsedFromRemaining(spec.clock, raw)
            ClockMetric.REMAINING -> if (spec.clock.countsUp()) remainingFromElapsed(spec.clock, raw) else raw
        }
    }

    fun crossed(lastNative: Int, currentNative: Int, spec: ClockSpec): Boolean {
        if (lastNative < 0) return false
        val current = if (currentNative < 0) {
            if (spec.clock.countsUp() || spec.metric != ClockMetric.REMAINING || spec.crossing != ClockCrossing.FALLING) {
                return false
            }
            // A polled countdown can finish without exposing its zero snapshot.
            0
        } else currentNative
        val threshold = spec.thresholdTicks
        return when (spec.crossing) {
            ClockCrossing.RISING -> lastNative < threshold && current >= threshold
            ClockCrossing.FALLING -> lastNative > threshold && current <= threshold
        }
    }

    fun reachedZero(lastRaw: Int, currentRaw: Int): Boolean = lastRaw > 0 && currentRaw <= 0

    // Completion starts after timing; only a restart clears it.
    fun resetGeneration(lastRaw: Int, currentRaw: Int): Boolean = lastRaw < 0 && currentRaw >= 0

    private fun elapsedFromRemaining(clock: TimerClock, remaining: Int): Int {
        val max = maxOf(clock)
        return if (max <= 0) remaining else (max - remaining).coerceAtLeast(0)
    }

    private fun remainingFromElapsed(clock: TimerClock, elapsed: Int): Int {
        val max = maxOf(clock)
        return if (max <= 0) elapsed else (max - elapsed).coerceAtLeast(0)
    }

    private fun maxOf(clock: TimerClock): Int = when (clock) {
        TimerClock.PAD -> TickClocks.PAD_TICKS
        TimerClock.GOLDOR_TICK -> TickClocks.GOLDOR_TICKS
        TimerClock.GOLDOR_START -> TickClocks.GOLDOR_START_TICKS
        TimerClock.GOLDOR_ELAPSED -> TickClocks.GOLDOR_ELAPSED_MAX
        TimerClock.MAXOR_ELAPSED -> TickClocks.MAXOR_ELAPSED_MAX
        TimerClock.LIGHTNING -> TickClocks.LIGHTNING_TICKS
        TimerClock.PY -> TickClocks.PY_TICKS
        TimerClock.STORM_ELAPSED -> TickClocks.STORM_MAX
        TimerClock.NECRON -> TickClocks.NECRON_TICKS
        TimerClock.SECRETS -> TickClocks.SECRET_TICKS
        else -> 0
    }
}
