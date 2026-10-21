package net.icantpy.dungeon.splits

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** Pure JSON codec for [RunSplitsDocument]. Unknown or bad fields fall back to defaults; unparseable roots are malformed. */
object RunSplitsCodec {
    const val VERSION: Int = 1

    class Decoded(val document: RunSplitsDocument, val malformed: Boolean)

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(document: RunSplitsDocument): String {
        val root = JsonObject()
        root.addProperty("version", VERSION)
        root.add("settings", settingsJson(document.settings))
        val floors = JsonObject()
        document.floors.toSortedMap().forEach { (key, record) -> floors.add(key, floorJson(record)) }
        root.add("floors", floors)
        return gson.toJson(root)
    }

    fun decode(text: String): Decoded {
        val root = try {
            JsonParser.parseString(text)
        } catch (_: Exception) {
            return Decoded(RunSplitsDocument(), malformed = true)
        }
        if (!root.isJsonObject) return Decoded(RunSplitsDocument(), malformed = true)
        val obj = root.asJsonObject
        val settings = obj.objectOrNull("settings")?.let { parseSettings(it) } ?: RunSplitsSettings()
        val floors = obj.objectOrNull("floors")?.let { parseFloors(it) } ?: emptyMap()
        return Decoded(RunSplitsDocument(settings, floors), malformed = false)
    }

    private fun settingsJson(s: RunSplitsSettings): JsonObject = JsonObject().apply {
        addProperty("enabled", s.enabled)
        addProperty("showPbDelta", s.showPbDelta)
        addProperty("showSumOfBest", s.showSumOfBest)
        addProperty("compact", s.compact)
        addProperty("hudX", s.hudX)
        addProperty("hudY", s.hudY)
        addProperty("hudScale", s.hudScale)
    }

    private fun floorJson(record: FloorRecord): JsonObject = JsonObject().apply {
        record.bestTotalMs?.let { addProperty("bestTotalMs", it) }
        add("pbSplits", longMapJson(record.pbSplits))
        add("bestSegments", longMapJson(record.bestSegments))
        val history = JsonArray()
        record.history.take(PbLedger.HISTORY_LIMIT).forEach { run ->
            history.add(JsonObject().apply {
                addProperty("endedAtEpochMs", run.endedAtEpochMs)
                addProperty("totalMs", run.totalMs)
                addProperty("isPb", run.isPb)
            })
        }
        add("history", history)
    }

    private fun longMapJson(map: Map<String, Long>): JsonObject = JsonObject().apply {
        map.forEach { (key, value) -> addProperty(key, value) }
    }

    private fun parseSettings(obj: JsonObject): RunSplitsSettings {
        val defaults = RunSplitsSettings()
        return RunSplitsSettings(
            enabled = obj.bool("enabled", defaults.enabled),
            showPbDelta = obj.bool("showPbDelta", defaults.showPbDelta),
            showSumOfBest = obj.bool("showSumOfBest", defaults.showSumOfBest),
            compact = obj.bool("compact", defaults.compact),
            hudX = obj.int("hudX", defaults.hudX),
            hudY = obj.int("hudY", defaults.hudY),
            hudScale = obj.float("hudScale", defaults.hudScale).coerceIn(0.5f, 3.0f),
        )
    }

    private fun parseFloors(obj: JsonObject): Map<String, FloorRecord> {
        val out = LinkedHashMap<String, FloorRecord>()
        for ((key, value) in obj.entrySet()) {
            if (value.isJsonObject) out[key] = parseFloor(value.asJsonObject)
        }
        return out
    }

    private fun parseFloor(obj: JsonObject): FloorRecord = FloorRecord(
        bestTotalMs = obj.long("bestTotalMs"),
        pbSplits = obj.longMap("pbSplits"),
        bestSegments = obj.longMap("bestSegments"),
        history = obj.history(),
    )

    private fun JsonObject.history(): List<RunRecord> {
        val array = get("history")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        val out = ArrayList<RunRecord>()
        for (element in array) {
            if (out.size >= PbLedger.HISTORY_LIMIT) break
            if (!element.isJsonObject) continue
            val run = element.asJsonObject
            val endedAt = run.long("endedAtEpochMs") ?: continue
            val total = run.long("totalMs") ?: continue
            out += RunRecord(endedAt, total, run.bool("isPb", false))
        }
        return out
    }

    private fun JsonObject.longMap(key: String): Map<String, Long> {
        val obj = objectOrNull(key) ?: return emptyMap()
        val out = LinkedHashMap<String, Long>()
        for ((name, value) in obj.entrySet()) {
            val number = primitiveOrNull(value)?.takeIf { it.isNumber }?.asLong ?: continue
            if (number >= 0L) out[name] = number
        }
        return out
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.primitive(key: String): JsonPrimitive? = primitiveOrNull(get(key))

    private fun primitiveOrNull(element: com.google.gson.JsonElement?): JsonPrimitive? =
        element?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        primitive(key)?.takeIf { it.isBoolean }?.asBoolean ?: default

    private fun JsonObject.int(key: String, default: Int): Int =
        primitive(key)?.takeIf { it.isNumber }?.asInt ?: default

    private fun JsonObject.float(key: String, default: Float): Float =
        primitive(key)?.takeIf { it.isNumber }?.asFloat?.takeIf { it.isFinite() } ?: default

    private fun JsonObject.long(key: String): Long? =
        primitive(key)?.takeIf { it.isNumber }?.asLong?.takeIf { it >= 0L }
}
