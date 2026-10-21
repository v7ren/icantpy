package net.icantpy.dungeon.score

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.roundToInt

/** User settings for the score HUD and alerts. HUD fields are read by the HUD editor. */
data class ScoreSettings(
    val enabled: Boolean = true,
    val showBreakdown: Boolean = true,
    val alert270: Boolean = true,
    val alert300: Boolean = true,
    val threshold1: Int = DEFAULT_THRESHOLD_1,
    val threshold2: Int = DEFAULT_THRESHOLD_2,
    val assumeSpiritPet: Boolean = false,
    val paulBonus: Boolean = false,
    val hudX: Int = DEFAULT_HUD_X,
    val hudY: Int = DEFAULT_HUD_Y,
    val scale: Float = DEFAULT_SCALE,
) {
    companion object {
        const val DEFAULT_THRESHOLD_1 = 270
        const val DEFAULT_THRESHOLD_2 = 300
        const val DEFAULT_HUD_X = 10
        const val DEFAULT_HUD_Y = 60
        const val DEFAULT_SCALE = 1f
        const val MIN_THRESHOLD = 0
        const val MAX_THRESHOLD = 1000
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 3f
        const val MAX_HUD_COORDINATE = 10_000
    }
}

/**
 * JSON codec for [ScoreSettings]. [decode] returns null for malformed input (not an object, or a field with the
 * wrong type); missing fields take their defaults and numbers are clamped to their valid range.
 */
object ScoreSettingsCodec {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(settings: ScoreSettings): String {
        val json = JsonObject()
        json.addProperty("enabled", settings.enabled)
        json.addProperty("showBreakdown", settings.showBreakdown)
        json.addProperty("alert270", settings.alert270)
        json.addProperty("alert300", settings.alert300)
        json.addProperty("threshold1", settings.threshold1)
        json.addProperty("threshold2", settings.threshold2)
        json.addProperty("assumeSpiritPet", settings.assumeSpiritPet)
        json.addProperty("paulBonus", settings.paulBonus)
        json.addProperty("hudX", settings.hudX)
        json.addProperty("hudY", settings.hudY)
        json.addProperty("scale", settings.scale)
        return gson.toJson(json)
    }

    fun decode(text: String): ScoreSettings? {
        val root = try {
            JsonParser.parseString(text)
        } catch (_: Exception) {
            return null
        }
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        val defaults = ScoreSettings()
        return ScoreSettings(
            enabled = bool(obj, "enabled", defaults.enabled) ?: return null,
            showBreakdown = bool(obj, "showBreakdown", defaults.showBreakdown) ?: return null,
            alert270 = bool(obj, "alert270", defaults.alert270) ?: return null,
            alert300 = bool(obj, "alert300", defaults.alert300) ?: return null,
            threshold1 = int(obj, "threshold1", defaults.threshold1, ScoreSettings.MIN_THRESHOLD, ScoreSettings.MAX_THRESHOLD) ?: return null,
            threshold2 = int(obj, "threshold2", defaults.threshold2, ScoreSettings.MIN_THRESHOLD, ScoreSettings.MAX_THRESHOLD) ?: return null,
            assumeSpiritPet = bool(obj, "assumeSpiritPet", defaults.assumeSpiritPet) ?: return null,
            paulBonus = bool(obj, "paulBonus", defaults.paulBonus) ?: return null,
            hudX = int(obj, "hudX", defaults.hudX, -ScoreSettings.MAX_HUD_COORDINATE, ScoreSettings.MAX_HUD_COORDINATE) ?: return null,
            hudY = int(obj, "hudY", defaults.hudY, -ScoreSettings.MAX_HUD_COORDINATE, ScoreSettings.MAX_HUD_COORDINATE) ?: return null,
            scale = scale(obj, defaults.scale) ?: return null,
        )
    }

    /** Missing key gives [default]; a present value of the wrong type gives null (malformed). */
    private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean? {
        val value = obj.get(key) ?: return default
        if (!isPrimitive(value) || !value.asJsonPrimitive.isBoolean) return null
        return value.asBoolean
    }

    private fun int(obj: JsonObject, key: String, default: Int, min: Int, max: Int): Int? {
        val value = obj.get(key) ?: return default
        if (!isNumber(value)) return null
        return value.asDouble.roundToInt().coerceIn(min, max)
    }

    private fun scale(obj: JsonObject, default: Float): Float? {
        val value = obj.get("scale") ?: return default
        if (!isNumber(value)) return null
        return value.asFloat.coerceIn(ScoreSettings.MIN_SCALE, ScoreSettings.MAX_SCALE)
    }

    private fun isPrimitive(value: JsonElement): Boolean = value.isJsonPrimitive

    private fun isNumber(value: JsonElement): Boolean = value.isJsonPrimitive && value.asJsonPrimitive.isNumber
}
