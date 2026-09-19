package net.icantpy.qol.shards

data class ShardMenuParse(val observation: ShardObservation?, val rawName: String = "")

object ShardMenuParser {
    private val roman = mapOf("I" to 1,"II" to 2,"III" to 3,"IV" to 4,"V" to 5,"VI" to 6,"VII" to 7,"VIII" to 8,"IX" to 9,"X" to 10)
    private val suffix = Regex(" (VIII|VII|VI|III|II|IX|IV|V|X|I)$")
    private fun clean(value: String) = value.replace(Regex("§."), "").trim()

    fun parse(title: String, lore: List<String>, hypixelId: String? = null, catalog: ShardCatalog = ShardCatalog): ShardMenuParse {
        val cleanTitle = clean(title).replace(Regex("^\\(\\d+\\s*/\\s*\\d+\\)\\s*"), "")
        val baseName = cleanTitle.replace(suffix, "")
        val definition = catalog.resolve(hypixelId?.takeIf { it.isNotBlank() } ?: "") ?: catalog.resolve(cleanTitle) ?: catalog.resolve(baseName)
            ?: return ShardMenuParse(null, baseName)
        val lines = lore.map(::clean)
        val tier = if (cleanTitle != definition.displayName) suffix.find(cleanTitle)?.groupValues?.get(1)?.let { roman[it] } else null
        val abilityRegex = Regex("^${Regex.escape(definition.abilityName)}(?: (VIII|VII|VI|III|II|IX|IV|V|X|I))? \\(\\w+\\)$")
        val ability = lines.firstNotNullOfOrNull { abilityRegex.matchEntire(it)?.groupValues?.get(1)?.let(roman::get) }
        val level = tier ?: ability
        val owned = Regex("^Owned: (\\d[\\d,]*) Shards?$").find(lines.firstOrNull { it.startsWith("Owned:") } ?: "")?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val syphonMatch = Regex("^Syphon (\\d[\\d,]*)(?: more)?(?: shards?)? to (level up|unlock)!$").find(lines.firstOrNull { it.startsWith("Syphon ") } ?: "")
        val remaining = syphonMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val finalLevel = level ?: if (syphonMatch?.groupValues?.get(2) == "unlock" && remaining == 1) 0 else null
        val total = when {
            finalLevel == 10 -> ShardMath.consumed(definition, 10)
            finalLevel != null && remaining != null -> ShardMath.consumed(definition, finalLevel, remaining)
            else -> null
        }
        return ShardMenuParse(ShardObservation(definition.displayName, finalLevel, owned, total), baseName)
    }
    fun isMenu(title: String) = Regex("^(?:\\(\\d+\\s*/\\s*\\d+\\)\\s*)?(Attribute Menu|Hunting Box)$", RegexOption.IGNORE_CASE).matches(clean(title))
}
