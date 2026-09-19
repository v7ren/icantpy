package net.icantpy.dungeon.timer

enum class TimerHud {
    NECRON,
    GOLDOR,
    PAD,
    LIGHTNING,
    PY,
    STORM_TICK,
    SECRETS,
    DUNGEON,
    CARRY,
    NOTIFICATION,
    ;

    fun title(): String = when (this) {
        NECRON -> "Necron Hud"
        GOLDOR -> "Goldor Hud"
        PAD -> "Storm Pad Hud"
        LIGHTNING -> "Storm Lightning Hud"
        PY -> "Storm PY Hud"
        STORM_TICK -> "Storm Tick Hud"
        SECRETS -> "Secrets Hud"
        DUNGEON -> "F1-F7 Dungeon Timers Hud"
        CARRY -> "Slayer Carries Hud"
        NOTIFICATION -> "Notification"
    }

    fun subtitle(): String = when (this) {
        NECRON -> "Timer for Necron's drop."
        GOLDOR -> "Goldor tick and optional start timer."
        PAD -> "Storm pad crush loop."
        LIGHTNING -> "Storm giga lightning."
        PY -> "Purple-yellow crush window."
        STORM_TICK -> "Counts up through Storm P2."
        SECRETS -> "Secret spawn ticks while clearing."
        DUNGEON -> "Displays every active F1-F7 boss phase and split timer together."
        CARRY -> "Carry count and the players you are carrying."
        NOTIFICATION -> "On-screen alert when a clock hits a time."
    }

    fun toggleKey(): String = when (this) {
        NECRON -> "necronHud"
        GOLDOR -> "goldorHud"
        PAD -> "padHud"
        LIGHTNING -> "lightningHud"
        PY -> "pyHud"
        STORM_TICK -> "stormTickHud"
        SECRETS -> "secretsHud"
        DUNGEON -> "dungeonHud"
        CARRY -> "carryHud"
        NOTIFICATION -> "notification"
    }
}

data class HudPiece(
    val id: TimerHud,
    val text: String,
    val x: Int,
    val y: Int,
)
