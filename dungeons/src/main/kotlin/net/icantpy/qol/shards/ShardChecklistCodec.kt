package net.icantpy.qol.shards

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

data class ShardChecklist(val name: String = "My shards", val shards: List<ShardChecklistEntry>, val version: Int = 1)
data class ShardImportResult(val checklist: ShardChecklist? = null, val unknown: List<String> = emptyList(), val error: String? = null)

object ShardChecklistCodec {
    private const val MAX_RAW = 90_000
    private const val MAX_TEXT = 64_000
    private const val MAX_ENTRIES = 500
    private const val MAX_NAME = 200
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val nameKey = Regex("[^a-z0-9]")
    private val bullet = Regex("^\\s*-\\s*(.*?)\\s+--")

    fun normalizeName(value: String): String = value.lowercase().replace(nameKey, "")
    fun encode(checklist: ShardChecklist): String = Base64.getEncoder().encodeToString(gson.toJson(checklist).toByteArray(StandardCharsets.UTF_8))

    fun decode(input: String, catalog: Collection<ShardDefinition>): ShardImportResult {
        if (input.length > MAX_RAW) return ShardImportResult(error = "Input is too large")
        val raw = input.trim()
        if (raw.isEmpty()) return ShardImportResult(error = "Input is empty")
        if (raw.startsWith("{")) return parseJson(raw, catalog)
        if (raw.lineSequence().any { bullet.containsMatchIn(it) }) return parseMarkdown(raw, catalog)
        val decoded = decodeBase64(raw) ?: return ShardImportResult(error = "Invalid Base64 input")
        if (decoded.length > MAX_TEXT || decoded.isBlank()) return ShardImportResult(error = "Input is too large or empty")
        return if (decoded.trimStart().startsWith("{")) parseJson(decoded, catalog) else parseMarkdown(decoded, catalog)
    }

    private fun decodeBase64(raw: String): String? {
        val bytes = try { Base64.getDecoder().decode(raw) } catch (_: IllegalArgumentException) {
            try { Base64.getUrlDecoder().decode(raw) } catch (_: IllegalArgumentException) { return null }
        }
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { null }
    }

    fun parseJson(json: String, catalog: Collection<ShardDefinition>): ShardImportResult {
        if (json.length > MAX_TEXT) return ShardImportResult(error = "Input is too large")
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonObject) return ShardImportResult(error = "Expected a JSON object")
            val obj = root.asJsonObject
            val version = obj.get("version")?.let { integer(it, "version") } ?: 1
            if (version != 1) return ShardImportResult(error = "Unsupported checklist version $version")
            val title = obj.get("name")?.let { string(it, "name") } ?: "My shards"
            val raw = obj.get("shards") ?: return ShardImportResult(error = "Missing shards")
            if (!raw.isJsonArray || raw.asJsonArray.size() > MAX_ENTRIES) return ShardImportResult(error = "Invalid shards list")
            val entries = raw.asJsonArray.map { item ->
                if (item.isJsonPrimitive && item.asJsonPrimitive.isString) ShardChecklistEntry(string(item, "name"))
                else if (item.isJsonObject) {
                    val entry = item.asJsonObject
                    val name = string(entry.get("name") ?: throw IllegalArgumentException("name is required"), "name")
                    val level = entry.get("targetLevel")?.let { integer(it, "targetLevel") } ?: 10
                    ShardChecklistEntry(name, level)
                } else throw IllegalArgumentException("Invalid shard entry")
            }
            validate(entries, title, catalog)
        } catch (e: Exception) { ShardImportResult(error = "Invalid JSON: ${e.message ?: "parse error"}") }
    }

    private fun string(element: JsonElement, field: String): String {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) throw IllegalArgumentException("$field must be a string")
        val value = element.asString
        if (value.isBlank() || value.length > MAX_NAME) throw IllegalArgumentException("$field is blank or too long")
        return value
    }

    private fun integer(element: JsonElement, field: String): Int {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) throw IllegalArgumentException("$field must be an integer")
        return try { BigDecimal(element.asString).toBigIntegerExact().intValueExact() } catch (_: Exception) { throw IllegalArgumentException("$field must be an integer") }
    }

    fun parseMarkdown(markdown: String, catalog: Collection<ShardDefinition>): ShardImportResult {
        if (markdown.length > MAX_TEXT) return ShardImportResult(error = "Input is too large")
        val entries = mutableListOf<ShardChecklistEntry>()
        for (line in markdown.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("-") && !trimmed.startsWith("--")) {
                val match = bullet.find(line) ?: return ShardImportResult(error = "Invalid shard entry")
                val text = match.groupValues[1].replace("**", "").trim()
                if (text.isBlank()) return ShardImportResult(error = "Invalid shard entry")
                val mob = Regex("\\(([^()]+)\\)").find(text)?.groupValues?.get(1)?.trim()
                val name = mob ?: text.substringBefore(" --").trim()
                if (name.isBlank() || name.length > MAX_NAME) return ShardImportResult(error = "Invalid shard entry")
                entries += ShardChecklistEntry(name)
                if (entries.size > MAX_ENTRIES) return ShardImportResult(error = "Too many entries")
            }
        }
        if (entries.isEmpty()) return ShardImportResult(error = "No shard names found")
        return validate(entries, "Imported shards", catalog)
    }

    private fun validate(raw: List<ShardChecklistEntry>, title: String, catalog: Collection<ShardDefinition>): ShardImportResult {
        val primary = catalog.flatMap { definition ->
            (listOf(definition.displayName, definition.internalName, definition.bazaarName) + definition.aliases)
                .filter { it.isNotBlank() }.map { normalizeName(it) to definition }
        }.groupBy({ it.first }, { it.second })
        val abilities = catalog.filter { it.abilityName.isNotBlank() }
            .groupBy { normalizeName(it.abilityName) }
        val unknown = mutableListOf<String>()
        val result = linkedMapOf<String, ShardChecklistEntry>()
        raw.forEach { entry ->
            if (entry.name.isBlank() || entry.name.length > MAX_NAME || entry.targetLevel !in 1..10) { unknown += entry.name; return@forEach }
            val lookupKey = normalizeName(entry.name)
            val matches = (primary[lookupKey] ?: abilities[lookupKey].orEmpty()).distinctBy { it.displayName }
            if (matches.size != 1) { unknown += entry.name; return@forEach }
            val definition = matches.single()
            val canonicalKey = definition.displayName
            val existing = result[canonicalKey]
            if (existing == null || entry.targetLevel > existing.targetLevel) result[canonicalKey] = entry.copy(name = canonicalKey)
        }
        if (unknown.isNotEmpty()) return ShardImportResult(unknown = unknown, error = "Unknown or invalid shard names")
        if (result.isEmpty()) return ShardImportResult(error = "No recognized shard names")
        return ShardImportResult(ShardChecklist(title, result.values.toList()), emptyList())
    }
}
