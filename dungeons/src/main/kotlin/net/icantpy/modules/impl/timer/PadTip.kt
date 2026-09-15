package net.icantpy.modules.impl.timer

enum class PadTip {
    NONE,
    STEP_ON,
    GYRO,
    GET_OFF,
    ;

    fun label(): String = when (this) {
        NONE -> ""
        STEP_ON -> "§a§lSTEP ON PAD NOW"
        GYRO -> "§e§lGYRO NOW"
        GET_OFF -> "§c§lGET OFF PAD NOW"
    }

    companion object {
        fun fromPadTicks(pad: Int): PadTip = when {
            pad < 0 -> NONE
            pad >= 15 -> STEP_ON
            pad >= 8 -> GYRO
            else -> GET_OFF
        }
    }
}
