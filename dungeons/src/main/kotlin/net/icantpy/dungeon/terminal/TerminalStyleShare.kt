package net.icantpy.dungeon.terminal

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/**
 * The "look" half of [TerminalSettings]: colours, shapes, text and animation. Presets reset exactly these fields, and
 * a style string carries only these, so sharing a style never changes someone's position, scale or click rules.
 * Pure; uses the settings codec's JSON keys as the single list of what belongs to the look.
 */
object TerminalStyleShare {
    private const val PREFIX = "icantpy-term:"

    val LOOK_KEYS: Set<String> = setOf(
        "panelColor", "panelAlpha", "targetColor", "doneColor", "wrongColor", "orderSecondColor", "orderThirdColor",
        "rubixLeftColor", "rubixRightColor", "textColor", "cellStyle", "cellAlpha", "cellRadius", "outlineWidth",
        "panelRadius", "panelBorder", "borderColor", "textScale", "textShadow", "showHover", "hoverColor", "pulseNext",
        "fadeDone", "headerPosition", "titleAlign", "padding", "screenDim", "hideWrong", "hideDone", "showItems",
        "showTitle", "showRemaining", "gap", "font",
        "customFont", "customFontSize", "customFontShift",
    )

    /** [settings] with every look field back to its default. */
    fun resetLook(settings: TerminalSettings): TerminalSettings {
        val json = toJson(settings)
        LOOK_KEYS.forEach(json::remove)
        return TerminalSettingsCodec.decode(json.toString()) ?: settings
    }

    fun export(settings: TerminalSettings): String {
        val json = toJson(settings)
        val look = JsonObject()
        LOOK_KEYS.sorted().forEach { key -> json.get(key)?.let { look.add(key, it) } }
        return PREFIX + Base64.getEncoder().encodeToString(look.toString().toByteArray(Charsets.UTF_8))
    }

    /** [current] with the look fields of a style string; null when the text is not a valid style. */
    fun import(text: String, current: TerminalSettings): TerminalSettings? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        val decoded = try {
            String(Base64.getDecoder().decode(trimmed.removePrefix(PREFIX)), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val look = try {
            JsonParser.parseString(decoded).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val merged = toJson(current)
        look.entrySet().filter { it.key in LOOK_KEYS }.forEach { (key, value) -> merged.add(key, value) }
        return TerminalSettingsCodec.decode(merged.toString())
    }

    private fun toJson(settings: TerminalSettings): JsonObject =
        JsonParser.parseString(TerminalSettingsCodec.encode(settings)).asJsonObject
}
