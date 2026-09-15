package net.icantpy.gui.configUI

import com.google.gson.JsonObject
import net.icantpy.modules.impl.timer.TimerHud

enum class HudFontId {
    MINECRAFT,
    SEGOE,
    TAHOMA,
    VERDANA,
    ARIAL,
    CALIBRI,
    CONSOLAS,
    CASCADIA,
    YAHEI,
    NUNITO,
    ;

    fun label(): String = when (this) {
        MINECRAFT -> "Minecraft"
        SEGOE -> GuiFontId.SEGOE.label()
        TAHOMA -> GuiFontId.TAHOMA.label()
        VERDANA -> GuiFontId.VERDANA.label()
        ARIAL -> GuiFontId.ARIAL.label()
        CALIBRI -> GuiFontId.CALIBRI.label()
        CONSOLAS -> GuiFontId.CONSOLAS.label()
        CASCADIA -> GuiFontId.CASCADIA.label()
        YAHEI -> GuiFontId.YAHEI.label()
        NUNITO -> GuiFontId.NUNITO.label()
    }

    fun next(): HudFontId = entries[(ordinal + 1) % entries.size]

    fun previous(): HudFontId {
        val last = entries.size - 1
        return entries[if (ordinal == 0) last else ordinal - 1]
    }

    fun guiFont(): GuiFontId? = when (this) {
        MINECRAFT -> null
        SEGOE -> GuiFontId.SEGOE
        TAHOMA -> GuiFontId.TAHOMA
        VERDANA -> GuiFontId.VERDANA
        ARIAL -> GuiFontId.ARIAL
        CALIBRI -> GuiFontId.CALIBRI
        CONSOLAS -> GuiFontId.CONSOLAS
        CASCADIA -> GuiFontId.CASCADIA
        YAHEI -> GuiFontId.YAHEI
        NUNITO -> GuiFontId.NUNITO
    }

    companion object {
        fun parse(raw: String?): HudFontId =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MINECRAFT
    }
}

data class HudOverlaySettings(
    val timerScale: Int = 100,
    val notifyScale: Int = 150,
    val notifyWidth: Int = 200,
    val timerFont: HudFontId = HudFontId.MINECRAFT,
    val notifyFont: HudFontId = HudFontId.MINECRAFT,
    val timerFontSize: Int = DEFAULT_FONT_SIZE,
    val notifyFontSize: Int = DEFAULT_FONT_SIZE,
    val timerTextRgb: Int = 0xFFFFFF,
    val notifyTextRgb: Int = DEFAULT_NOTIFY_TEXT,
    val notifyBoxRgb: Int = 0x000000,
    val notifyBoxAlpha: Int = DEFAULT_BOX_ALPHA,
    val timerShadow: Boolean = true,
    val notifyShadow: Boolean = true,
    val notifyBold: Boolean = true,
    val lockNotifyColor: Boolean = true,
    val alertSound: String = "",
) {
    fun timerScaleValue(): Float = timerScale.coerceIn(MIN_SCALE, MAX_SCALE) / 100f

    fun notifyScaleValue(): Float = notifyScale.coerceIn(MIN_SCALE, MAX_SCALE) / 100f

    fun wrapWidth(): Int = notifyWidth.coerceIn(MIN_WIDTH, MAX_WIDTH)

    fun scalePercent(hud: TimerHud): Int =
        if (hud == TimerHud.NOTIFICATION) notifyScale else timerScale

    fun scaleValue(hud: TimerHud): Float = scalePercent(hud).coerceIn(MIN_SCALE, MAX_SCALE) / 100f

    fun fontSize(hud: TimerHud): Int =
        (if (hud == TimerHud.NOTIFICATION) notifyFontSize else timerFontSize).coerceIn(MIN_FONT, MAX_FONT)

    fun font(hud: TimerHud): HudFontId =
        if (hud == TimerHud.NOTIFICATION) notifyFont else timerFont

    fun textRgb(hud: TimerHud): Int =
        if (hud == TimerHud.NOTIFICATION) notifyTextRgb else timerTextRgb

    fun shadow(hud: TimerHud): Boolean =
        if (hud == TimerHud.NOTIFICATION) notifyShadow else timerShadow

    fun notifyBoxArgb(): Int = argb(notifyBoxAlpha.coerceIn(0, 255), notifyBoxRgb)

    fun withTimerScale(value: Int): HudOverlaySettings = copy(timerScale = value.coerceIn(MIN_SCALE, MAX_SCALE))

    fun withNotifyScale(value: Int): HudOverlaySettings = copy(notifyScale = value.coerceIn(MIN_SCALE, MAX_SCALE))

    fun withNotifyWidth(value: Int): HudOverlaySettings = copy(notifyWidth = value.coerceIn(MIN_WIDTH, MAX_WIDTH))

    fun withFontSize(hud: TimerHud, value: Int): HudOverlaySettings {
        val size = value.coerceIn(MIN_FONT, MAX_FONT)
        return if (hud == TimerHud.NOTIFICATION) copy(notifyFontSize = size) else copy(timerFontSize = size)
    }

    fun withScale(hud: TimerHud, value: Int): HudOverlaySettings =
        if (hud == TimerHud.NOTIFICATION) withNotifyScale(value) else withTimerScale(value)

    fun withFont(hud: TimerHud, font: HudFontId): HudOverlaySettings =
        if (hud == TimerHud.NOTIFICATION) copy(notifyFont = font) else copy(timerFont = font)

    companion object {
        const val MIN_SCALE: Int = 25
        const val MAX_SCALE: Int = 800
        const val MIN_WIDTH: Int = 40
        const val MAX_WIDTH: Int = 900
        const val MIN_FONT: Int = 8
        const val MAX_FONT: Int = 72
        const val DEFAULT_FONT_SIZE: Int = 12
        const val DEFAULT_NOTIFY_TEXT: Int = 0xFFFF55
        const val DEFAULT_BOX_ALPHA: Int = 0x66
        val SCALE_PRESETS: List<Int> = listOf(100, 150, 200, 250)
        val TEXT_PRESETS: List<Int> = listOf(0xFFFF55, 0xFFFFFF, 0xFFAA00, 0xFF5555, 0x55FFFF, 0x55FF55, 0xFF55FF)
        val BOX_PRESETS: List<Int> = listOf(0x000000, 0x1A1A1A, 0x0B1A2A, 0x2A0B0B, 0xFFFFFF)

        fun argb(alpha: Int, rgb: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

        fun fromJson(obj: JsonObject?): HudOverlaySettings {
            if (obj == null) return HudOverlaySettings()
            return HudOverlaySettings(
                timerScale = int(obj, "timerScale", 100).coerceIn(MIN_SCALE, MAX_SCALE),
                notifyScale = int(obj, "notifyScale", 150).coerceIn(MIN_SCALE, MAX_SCALE),
                notifyWidth = int(obj, "notifyWidth", 200).coerceIn(MIN_WIDTH, MAX_WIDTH),
                timerFont = HudFontId.parse(string(obj, "timerFont")),
                notifyFont = HudFontId.parse(string(obj, "notifyFont")),
                timerFontSize = int(obj, "timerFontSize", DEFAULT_FONT_SIZE).coerceIn(MIN_FONT, MAX_FONT),
                notifyFontSize = int(obj, "notifyFontSize", DEFAULT_FONT_SIZE).coerceIn(MIN_FONT, MAX_FONT),
                timerTextRgb = rgb(obj, "timerText", 0xFFFFFF),
                notifyTextRgb = rgb(obj, "notifyText", DEFAULT_NOTIFY_TEXT),
                notifyBoxRgb = rgb(obj, "notifyBox", 0x000000),
                notifyBoxAlpha = int(obj, "notifyBoxAlpha", DEFAULT_BOX_ALPHA).coerceIn(0, 255),
                timerShadow = bool(obj, "timerShadow", true),
                notifyShadow = bool(obj, "notifyShadow", true),
                notifyBold = bool(obj, "notifyBold", true),
                lockNotifyColor = bool(obj, "lockNotifyColor", true),
                alertSound = string(obj, "alertSound").orEmpty(),
            )
        }

        fun toJson(hud: HudOverlaySettings): JsonObject {
            val obj = JsonObject()
            obj.addProperty("timerScale", hud.timerScale)
            obj.addProperty("notifyScale", hud.notifyScale)
            obj.addProperty("notifyWidth", hud.notifyWidth)
            obj.addProperty("timerFont", hud.timerFont.name.lowercase())
            obj.addProperty("notifyFont", hud.notifyFont.name.lowercase())
            obj.addProperty("timerFontSize", hud.timerFontSize)
            obj.addProperty("notifyFontSize", hud.notifyFontSize)
            obj.addProperty("timerText", hex(hud.timerTextRgb))
            obj.addProperty("notifyText", hex(hud.notifyTextRgb))
            obj.addProperty("notifyBox", hex(hud.notifyBoxRgb))
            obj.addProperty("notifyBoxAlpha", hud.notifyBoxAlpha)
            obj.addProperty("timerShadow", hud.timerShadow)
            obj.addProperty("notifyShadow", hud.notifyShadow)
            obj.addProperty("notifyBold", hud.notifyBold)
            obj.addProperty("lockNotifyColor", hud.lockNotifyColor)
            obj.addProperty("alertSound", hud.alertSound)
            return obj
        }

        private fun hex(rgb: Int): String = "%06X".format(rgb and 0xFFFFFF)

        private fun rgb(obj: JsonObject, key: String, default: Int): Int {
            val raw = string(obj, key) ?: return default
            val hex = raw.trim().removePrefix("#")
            return hex.toIntOrNull(16)?.and(0xFFFFFF) ?: default
        }

        private fun string(obj: JsonObject, key: String): String? =
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asString else null

        private fun int(obj: JsonObject, key: String, default: Int): Int =
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asInt else default

        private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asBoolean else default
    }
}
