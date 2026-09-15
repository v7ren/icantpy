package net.icantpy.modules.impl.timer

enum class TimerHud {
    NECRON,
    GOLDOR,
    PAD,
    LIGHTNING,
    PY,
    STORM_TICK,
    SECRETS,
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
        NOTIFICATION -> "notification"
    }
}

data class HudPiece(
    val id: TimerHud,
    val text: String,
    val x: Int,
    val y: Int,
)
