package net.icantpy.dungeon.dragons

enum class SoloDebuffClass(val className: String) {
    TANK("TANK"),
    HEALER("HEALER"),
}

enum class DragonTimerStyle { MILLISECONDS, SECONDS, TICKS }

/**
 * Wither dragon feature settings. Powers are 0 to 40, matching the config page range. Use [clamped] after editing.
 */
data class DragonSettings(
    val enabled: Boolean = true,
    val priorityEnabled: Boolean = true,
    val normalPower: Int = 22,
    val easyPower: Int = 19,
    val soloDebuff: SoloDebuffClass = SoloDebuffClass.TANK,
    val soloDebuffOnAll: Boolean = false,
    val paulBuff: Boolean = false,
    val showSpawnTitle: Boolean = true,
    val showTimerHud: Boolean = true,
    val timerStyle: DragonTimerStyle = DragonTimerStyle.MILLISECONDS,
    val showKillMessages: Boolean = true,
    val showBoxes: Boolean = true,
    val hudX: Int = 10,
    val hudY: Int = 60,
    val scale: Float = 1f,
) {
    fun clamped(): DragonSettings = copy(
        normalPower = normalPower.coerceIn(MIN_POWER, MAX_POWER),
        easyPower = easyPower.coerceIn(MIN_POWER, MAX_POWER),
        hudX = hudX.coerceAtLeast(0),
        hudY = hudY.coerceAtLeast(0),
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
    )

    companion object {
        const val MIN_POWER = 0
        const val MAX_POWER = 40
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 4f
    }
}
