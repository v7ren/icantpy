package net.icantpy.qol.invsort

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.icantpy.qol.auto.CursorGuard

/** [settings] is always usable; on [error] it holds defaults and the caller should preserve the source. */
data class InvSortParseResult(val settings: InvSortSettings, val error: String? = null)

object InvSortCodec {
    const val VERSION = 2
    private const val DEFAULT_OFFSET_X = 4
    private const val DEFAULT_OFFSET_Y = 4

    /** Version 1 stored one tick-based delay; it is read once and converted to a millisecond range. */
    private const val LEGACY_MS_PER_TICK = 50
    private const val LEGACY_RANGE_SPREAD_MS = 80

    fun toJson(settings: InvSortSettings): JsonObject {
        val root = JsonObject()
        root.addProperty("version", VERSION)
        root.addProperty("enabled", settings.enabled)
        root.addProperty("clickDelayMinMs", settings.clickDelayMinMs)
        root.addProperty("clickDelayMaxMs", settings.clickDelayMaxMs)
        root.addProperty("openDelayMs", settings.openDelayMs)
        root.addProperty("closeWhenDone", settings.closeWhenDone)
        root.addProperty("moveCursor", settings.moveCursor)
        root.addProperty("instant", settings.instant)
        root.addProperty("safetyLevel", settings.safetyLevel.name)
        root.addProperty("lockInput", settings.lockInput)
        root.addProperty("buttonOffsetX", settings.buttonOffsetX)
        root.addProperty("buttonOffsetY", settings.buttonOffsetY)
        root.addProperty("swapOnePerPress", settings.swapOnePerPress)
        val presets = JsonArray()
        settings.presets.forEach { presets.add(presetToJson(it)) }
        root.add("presets", presets)
        return root
    }

    fun parseJson(json: String): InvSortParseResult {
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonObject) return InvSortParseResult(InvSortSettings(), "Expected a JSON object")
            InvSortParseResult(settingsFrom(root.asJsonObject))
        } catch (e: Exception) {
            InvSortParseResult(InvSortSettings(), "Invalid JSON: ${e.message ?: "parse error"}")
        }
    }

    private fun settingsFrom(obj: JsonObject): InvSortSettings {
        val (minMs, maxMs) = delayRangeFrom(obj)
        return InvSortSettings(
            enabled = bool(obj, "enabled", true),
            presets = InvSortSettings.withUniqueHotkeys(presetsFrom(obj.get("presets"))),
            clickDelayMinMs = minMs,
            clickDelayMaxMs = maxMs,
            openDelayMs = int(obj, "openDelayMs", InvSortSettings.DEFAULT_OPEN_DELAY_MS)
                .coerceIn(0, InvSortLimits.MAX_OPEN_DELAY_MS),
            closeWhenDone = bool(obj, "closeWhenDone", false),
            moveCursor = bool(obj, "moveCursor", true),
            instant = bool(obj, "instant", false),
            safetyLevel = CursorGuard.SafetyLevel.parse(text(obj, "safetyLevel")),
            lockInput = bool(obj, "lockInput", true),
            buttonOffsetX = int(obj, "buttonOffsetX", DEFAULT_OFFSET_X)
                .coerceIn(-InvSortLimits.MAX_BUTTON_OFFSET, InvSortLimits.MAX_BUTTON_OFFSET),
            buttonOffsetY = int(obj, "buttonOffsetY", DEFAULT_OFFSET_Y)
                .coerceIn(-InvSortLimits.MAX_BUTTON_OFFSET, InvSortLimits.MAX_BUTTON_OFFSET),
            swapOnePerPress = bool(obj, "swapOnePerPress", false),
        )
    }

    /** Reads the millisecond range, or converts a legacy "clickDelayTicks" value when no range is stored. */
    private fun delayRangeFrom(obj: JsonObject): Pair<Int, Int> {
        val hasRange = obj.has("clickDelayMinMs") || obj.has("clickDelayMaxMs")
        if (!hasRange && obj.has("clickDelayTicks")) {
            val ticks = int(obj, "clickDelayTicks", 2).coerceIn(1, 20)
            val minMs = ticks * LEGACY_MS_PER_TICK
            return InvSortSettings.normalizeDelayRange(minMs, minMs + LEGACY_RANGE_SPREAD_MS)
        }
        return InvSortSettings.normalizeDelayRange(
            int(obj, "clickDelayMinMs", InvSortSettings.DEFAULT_CLICK_MIN_MS),
            int(obj, "clickDelayMaxMs", InvSortSettings.DEFAULT_CLICK_MAX_MS),
        )
    }

    private fun presetToJson(preset: InvSortPreset): JsonObject {
        val item = JsonObject()
        item.addProperty("name", preset.name)
        item.addProperty("hotkey", preset.hotkey)
        item.addProperty("showButton", preset.showButton)
        item.addProperty("hotbarSwap", preset.hotbarSwap)
        val slots = JsonObject()
        preset.slots.toSortedMap().forEach { (index, slot) ->
            val entry = JsonObject()
            entry.addProperty("key", slot.key)
            entry.addProperty("itemId", slot.itemId)
            entry.addProperty("label", slot.label)
            slots.add(index.toString(), entry)
        }
        item.add("slots", slots)
        return item
    }

    private fun presetsFrom(element: JsonElement?): List<InvSortPreset> {
        if (element == null || !element.isJsonArray) return emptyList()
        return element.asJsonArray
            .mapNotNull { item -> presetFrom(item) }
            .take(InvSortLimits.MAX_PRESETS)
    }

    private fun presetFrom(element: JsonElement): InvSortPreset? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        val name = text(obj, "name")?.let { InvSortPreset.cleanName(it) } ?: return null
        return InvSortPreset(
            name = name,
            slots = slotsFrom(obj.get("slots")),
            hotkey = hotkeyFrom(obj),
            showButton = bool(obj, "showButton", true),
            hotbarSwap = bool(obj, "hotbarSwap", false),
        )
    }

    /** Missing or negative values mean "no hotkey". */
    private fun hotkeyFrom(obj: JsonObject): Int {
        val key = int(obj, "hotkey", InvSortLimits.NO_HOTKEY)
        return if (key < 0) InvSortLimits.NO_HOTKEY else key
    }

    private fun slotsFrom(element: JsonElement?): Map<Int, InvSortSlot> {
        if (element == null || !element.isJsonObject) return emptyMap()
        return element.asJsonObject.entrySet().mapNotNull { (rawIndex, value) ->
            val index = rawIndex.toIntOrNull() ?: return@mapNotNull null
            if (!InvSortSlots.isPresetSlot(index) || !value.isJsonObject) return@mapNotNull null
            slotFrom(value.asJsonObject)?.let { index to it }
        }.toMap()
    }

    private fun slotFrom(obj: JsonObject): InvSortSlot? {
        val key = text(obj, "key")?.takeIf { it.length <= InvSortLimits.MAX_KEY_LENGTH } ?: return null
        val itemId = text(obj, "itemId")?.takeIf { it.length <= InvSortLimits.MAX_KEY_LENGTH } ?: return null
        val label = text(obj, "label")?.take(InvSortLimits.MAX_LABEL_LENGTH) ?: key
        return InvSortSlot(key, itemId, label)
    }

    private fun text(obj: JsonObject, field: String): String? {
        val element = obj.get(field) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
        return element.asString.trim().takeIf { it.isNotEmpty() }
    }

    private fun int(obj: JsonObject, field: String, default: Int): Int {
        val element = obj.get(field) ?: return default
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return default
        return runCatching { element.asInt }.getOrDefault(default)
    }

    private fun bool(obj: JsonObject, field: String, default: Boolean): Boolean {
        val element = obj.get(field) ?: return default
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) return default
        return element.asBoolean
    }
}
