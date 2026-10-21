package net.icantpy.qol.shards

enum class ShardBrowseMode { TRACKED, CATALOG, MISSING }

data class ShardBrowseResult(
    val listed: List<ShardDefinition>,
    val extra: List<ShardDefinition> = emptyList(),
)

object ShardQuery {
    private val shardWord = Regex("\\bshards?\\b")
    private val nonWord = Regex("[^a-z0-9]")

    fun normalize(value: String): String =
        value.lowercase().replace(shardWord, "").replace(nonWord, "").removeSuffix("shard").removeSuffix("shards")

    fun matches(definition: ShardDefinition, query: String): Boolean {
        if (query.trim().isEmpty()) return true
        val needle = normalize(query)
        // Query was only filler words such as "shard"; show everything rather than nothing.
        if (needle.isEmpty()) return true
        if (nameKeys(definition).any { it.contains(needle) }) return true
        return idKeys(definition).any { it == needle }
    }

    private fun nameKeys(definition: ShardDefinition): List<String> =
        keys(listOf(definition.displayName, definition.abilityName, definition.shardLabel, definition.attributeLabel) + definition.aliases)

    private fun idKeys(definition: ShardDefinition): List<String> =
        keys(listOf(definition.shardId, definition.internalName, definition.bazaarName))

    private fun keys(values: List<String>): List<String> =
        values.filter { it.isNotBlank() }.map(::normalize).filter { it.isNotEmpty() }.distinct()

    fun browse(
        query: String,
        mode: ShardBrowseMode,
        catalog: List<ShardDefinition>,
        tracked: List<ShardDefinition>,
        isMissing: (ShardDefinition) -> Boolean,
    ): ShardBrowseResult {
        val trackedMatches = tracked.filter { matches(it, query) }
        return when (mode) {
            ShardBrowseMode.TRACKED -> ShardBrowseResult(
                listed = trackedMatches,
                extra = extraCatalogMatches(query, catalog, tracked),
            )
            ShardBrowseMode.MISSING -> ShardBrowseResult(trackedMatches.filter(isMissing))
            ShardBrowseMode.CATALOG -> ShardBrowseResult(catalog.filter { matches(it, query) })
        }
    }

    fun withTracked(checklist: ShardChecklist, definition: ShardDefinition, tracked: Boolean): ShardChecklist {
        val key = ShardChecklistCodec.normalizeName(definition.displayName)
        val already = checklist.shards.any { ShardChecklistCodec.normalizeName(it.name) == key }
        if (tracked) {
            if (already) return checklist
            return checklist.copy(shards = checklist.shards + ShardChecklistEntry(definition.displayName, 10))
        }
        return checklist.copy(shards = checklist.shards.filter { ShardChecklistCodec.normalizeName(it.name) != key })
    }

    private fun extraCatalogMatches(
        query: String,
        catalog: List<ShardDefinition>,
        tracked: List<ShardDefinition>,
    ): List<ShardDefinition> {
        if (query.trim().isEmpty()) return emptyList()
        val trackedKeys = tracked.map { ShardChecklistCodec.normalizeName(it.displayName) }.toSet()
        return catalog.filter { definition ->
            matches(definition, query) && ShardChecklistCodec.normalizeName(definition.displayName) !in trackedKeys
        }
    }
}
