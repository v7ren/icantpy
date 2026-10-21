package net.icantpy.dungeon.dragons

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Settings plus personal bests, stored together in one file. */
data class DragonFile(
    val settings: DragonSettings,
    val personalBestsMs: Map<WitherDragon, Long>,
)

/** [file] is null exactly when [error] is set. Missing or mistyped fields fall back to defaults. */
data class DragonDecodeResult(val file: DragonFile?, val error: String?)

object DragonSettingsCodec {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(file: DragonFile): String {
        val s = file.settings
        val root = JsonObject()
        root.addProperty("enabled", s.enabled)
        root.addProperty("priorityEnabled", s.priorityEnabled)
        root.addProperty("normalPower", s.normalPower)
        root.addProperty("easyPower", s.easyPower)
        root.addProperty("soloDebuff", s.soloDebuff.name)
        root.addProperty("soloDebuffOnAll", s.soloDebuffOnAll)
        root.addProperty("paulBuff", s.paulBuff)
        root.addProperty("showSpawnTitle", s.showSpawnTitle)
        root.addProperty("showTimerHud", s.showTimerHud)
        root.addProperty("timerStyle", s.timerStyle.name)
        root.addProperty("showKillMessages", s.showKillMessages)
        root.addProperty("showBoxes", s.showBoxes)
        root.addProperty("hudX", s.hudX)
        root.addProperty("hudY", s.hudY)
        root.addProperty("scale", s.scale)
        val bests = JsonObject()
        WitherDragon.entries.forEach { dragon -> file.personalBestsMs[dragon]?.let { bests.addProperty(dragon.name, it) } }
        root.add("personalBestsMs", bests)
        return gson.toJson(root)
    }

    fun decode(json: String): DragonDecodeResult {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            return DragonDecodeResult(null, "not valid JSON: ${e.message}")
        }
        if (!root.isJsonObject) return DragonDecodeResult(null, "root is not a JSON object")
        val obj = root.asJsonObject
        val defaults = DragonSettings()
        val settings = DragonSettings(
            enabled = bool(obj, "enabled", defaults.enabled),
            priorityEnabled = bool(obj, "priorityEnabled", defaults.priorityEnabled),
            normalPower = int(obj, "normalPower", defaults.normalPower),
            easyPower = int(obj, "easyPower", defaults.easyPower),
            soloDebuff = enumValue(obj, "soloDebuff", defaults.soloDebuff),
            soloDebuffOnAll = bool(obj, "soloDebuffOnAll", defaults.soloDebuffOnAll),
            paulBuff = bool(obj, "paulBuff", defaults.paulBuff),
            showSpawnTitle = bool(obj, "showSpawnTitle", defaults.showSpawnTitle),
            showTimerHud = bool(obj, "showTimerHud", defaults.showTimerHud),
            timerStyle = enumValue(obj, "timerStyle", defaults.timerStyle),
            showKillMessages = bool(obj, "showKillMessages", defaults.showKillMessages),
            showBoxes = bool(obj, "showBoxes", defaults.showBoxes),
            hudX = int(obj, "hudX", defaults.hudX),
            hudY = int(obj, "hudY", defaults.hudY),
            scale = float(obj, "scale", defaults.scale),
        ).clamped()
        return DragonDecodeResult(DragonFile(settings, decodeBests(obj)), null)
    }

    private fun decodeBests(obj: JsonObject): Map<WitherDragon, Long> {
        val bests = obj.get("personalBestsMs")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
        return WitherDragon.entries.mapNotNull { dragon ->
            val value = bests.get(dragon.name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
            if (value != null && value > 0L) dragon to value else null
        }.toMap()
    }

    private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
        obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

    private fun int(obj: JsonObject, key: String, default: Int): Int =
        obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: default

    private fun float(obj: JsonObject, key: String, default: Float): Float =
        obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: default

    private inline fun <reified E : Enum<E>> enumValue(obj: JsonObject, key: String, default: E): E {
        val name = obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
