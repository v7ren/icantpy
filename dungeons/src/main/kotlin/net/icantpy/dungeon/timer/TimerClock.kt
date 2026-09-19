package net.icantpy.dungeon.timer

enum class TimerClock {
    BOSS_ELAPSED,
    F1_UNDEAD,
    F1_BONZO,
    F2_FIRST_PHASE,
    F2_SECOND_PHASE,
    F3_GUARDIANS,
    F3_HUMAN,
    F3_GUARDIAN,
    F4_THORN,
    F5_LIVID,
    F6_TERRACOTTAS,
    F6_GIANTS,
    F6_SADAN,
    F7_TERMINALS,
    F7_S1,
    F7_S2,
    F7_S3,
    F7_S4,
    PAD,
    GOLDOR_TICK,
    GOLDOR_START,
    GOLDOR_ELAPSED,
    MAXOR_ELAPSED,
    LIGHTNING,
    PY,
    STORM_ELAPSED,
    NECRON,
    SECRETS,
    ;

    fun label(): String = when (this) {
        BOSS_ELAPSED -> "Boss"
        F1_UNDEAD -> "Undead"
        F1_BONZO -> "Bonzo"
        F2_FIRST_PHASE -> "First Phase"
        F2_SECOND_PHASE -> "Second Phase"
        F3_GUARDIANS -> "Guardians"
        F3_HUMAN -> "Human"
        F3_GUARDIAN -> "Guardian"
        F4_THORN -> "Thorn"
        F5_LIVID -> "Livid"
        F6_TERRACOTTAS -> "Terracottas"
        F6_GIANTS -> "Giants"
        F6_SADAN -> "Sadan"
        F7_TERMINALS -> "Terminals"
        F7_S1 -> "S1"
        F7_S2 -> "S2"
        F7_S3 -> "S3"
        F7_S4 -> "S4"
        PAD -> "Pad"
        GOLDOR_TICK -> "Goldor tick"
        GOLDOR_START -> "Goldor start"
        GOLDOR_ELAPSED -> "Goldor S1"
        MAXOR_ELAPSED -> "Maxor spawn"
        LIGHTNING -> "Lightning"
        PY -> "PY"
        STORM_ELAPSED -> "Storm"
        NECRON -> "Necron"
        SECRETS -> "Secrets"
    }

    fun countsUp(): Boolean = this in elapsedClocks

    companion object {
        val elapsedClocks: Set<TimerClock> = setOf(
            BOSS_ELAPSED,
            F1_UNDEAD,
            F1_BONZO,
            F2_FIRST_PHASE,
            F2_SECOND_PHASE,
            F3_GUARDIANS,
            F3_HUMAN,
            F3_GUARDIAN,
            F4_THORN,
            F5_LIVID,
            F6_TERRACOTTAS,
            F6_GIANTS,
            F6_SADAN,
            F7_TERMINALS,
            F7_S1,
            F7_S2,
            F7_S3,
            F7_S4,
            GOLDOR_ELAPSED,
            MAXOR_ELAPSED,
            STORM_ELAPSED,
        )
    }

    fun next(): TimerClock {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    fun previous(): TimerClock {
        val all = entries
        return all[(ordinal + all.size - 1) % all.size]
    }
}
