package net.icantpy.qol.shards

import com.google.gson.JsonParser

object ShardCatalog {
    private val nonWord = Regex("[^a-z0-9]")
    private val shardWord = Regex("\\bshard\\b")
    private val defaultNames = listOf(
        "Flash", "Bezal", "Quake", "Cuboa", "Golden Ghoul", "Sycophant", "Bolt", "Kada Knight", "Matcho",
        "Falcon", "Aero", "Alligator", "Thorn", "Flare", "Shellwise", "Barbarian Duke", "Toucan",
        "Obsidian Defender", "Caiman", "XYZ", "Ghost", "Storm", "Tiamat", "Wyvern", "Spike", "Kraken",
        "Daemon", "Shinyfish", "Hideonbox", "Burningsoul", "Cinderbat", "Power Dragon", "Condor",
        "Endstone Protector", "Apex Dragon", "Jormung", "Etherdrake", "Molthorn", "Starborn", "Driftling",
        "Haggard", "Chuckwalla", "Wolverine", "Primordial", "Paragon"
    )
    val definitions: List<ShardDefinition> by lazy { load() }
    private val nameIndex by lazy { definitions.flatMap { d -> (listOf(d.displayName) + d.aliases).map { normalize(it) to d } }.groupBy({ it.first }, { it.second }) }
    private val idIndex by lazy { definitions.flatMap { d -> listOf(d.internalName, d.bazaarName).map { normalizeId(it) to d } }.groupBy({ it.first }, { it.second }) }
    private val abilityIndex by lazy { definitions.groupBy { normalize(it.abilityName) } }
    private fun load(): List<ShardDefinition> {
        val stream = ShardCatalog::class.java.getResourceAsStream("/assets/icantpy/shards/attribute_shards.json") ?: error("Bundled catalog missing")
        stream.use { input ->
            val root = JsonParser.parseString(input.bufferedReader().readText()).asJsonObject
            val curves = root["attribute_levelling"].asJsonObject
            val result = root["attributes"].asJsonArray.map { e ->
                val o = e.asJsonObject
                val rarity = o["rarity"].asString
                val costs = curves[rarity].asJsonArray.map { it.asInt }
                require(costs.size==10 && costs.all{it>0}) { "Invalid curve for $rarity" }
                ShardDefinition(o["displayName"].asString, o["abilityName"].asString, rarity,
                    o["alignment"]?.asString ?: "", 10, costs,
                    o["internalName"]?.asString ?: "", o["bazaarName"]?.asString ?: "")
            }
            require(result.map{it.displayName.lowercase()}.distinct().size==result.size) { "Duplicate display name" }
            return result.map { definition ->
                when (definition.displayName) {
                    "Tempest" -> definition.copy(aliases = listOf("Storm"))
                    "Inferno Demonlord" -> definition.copy(aliases = listOf("Burningsoul", "Burning Soul"))
                    "Barbarian Duke X" -> definition.copy(aliases = listOf("Barbarian Duke"))
                    "End Stone Protector" -> definition.copy(aliases = listOf("Endstone Protector"))
                    else -> definition
                }
            }
        }
    }
    fun resolve(name:String): ShardDefinition? {
        val key = normalize(name)
        if (key.isEmpty()) return null
        nameIndex[key]?.distinct()?.singleOrNull()?.let { return it }
        idIndex[normalizeId(name)]?.distinct()?.singleOrNull()?.let { return it }
        return abilityIndex[key]?.distinct()?.singleOrNull()
    }
    fun defaultChecklist() = ShardChecklist("Blaze Slayer shards", defaultNames.map { name ->
        ShardChecklistEntry(resolve(name)?.displayName ?: error("Missing default $name"), 10)
    })
    private fun normalize(value: String) = value.lowercase().replace(shardWord, "").replace(nonWord, "")
    private fun normalizeId(value: String) = normalize(value).removeSuffix("1")
}
