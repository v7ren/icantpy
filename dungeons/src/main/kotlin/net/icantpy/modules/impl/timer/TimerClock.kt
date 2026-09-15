package net.icantpy.modules.impl.timer

enum class TimerClock {
    PAD,
    GOLDOR_TICK,
    GOLDOR_START,
    GOLDOR_ELAPSED,
    LIGHTNING,
    PY,
    STORM_ELAPSED,
    NECRON,
    SECRETS,
    ;

    fun label(): String = when (this) {
        PAD -> "Pad"
        GOLDOR_TICK -> "Goldor tick"
        GOLDOR_START -> "Goldor start"
        GOLDOR_ELAPSED -> "Goldor S1"
        LIGHTNING -> "Lightning"
        PY -> "PY"
        STORM_ELAPSED -> "Storm"
        NECRON -> "Necron"
        SECRETS -> "Secrets"
    }

    fun countsUp(): Boolean = this == STORM_ELAPSED || this == GOLDOR_ELAPSED

    fun next(): TimerClock {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    fun previous(): TimerClock {
        val all = entries
        return all[(ordinal + all.size - 1) % all.size]
    }
}
