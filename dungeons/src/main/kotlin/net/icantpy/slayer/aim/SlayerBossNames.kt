package net.icantpy.slayer.aim

/**
 * Slayer boss name tags as Hypixel shows them, already stripped of formatting. Hypixel does not show the owner a
 * "Spawned by" line on their own boss, so while the sidebar says "Slay the boss!" the nearest boss tag that no
 * other player's "Spawned by" claims is treated as the player's own.
 */
object SlayerBossNames {
    private val BOSS_NAMES = listOf(
        "Inferno Demonlord",
        // The Demonlord's demons during its split phases. Hypixel spells them in circled letters
        // (the wither skeleton and the zombified piglin); plain spellings are kept as a fallback.
        "ⓆⓊⒶⓏⒾⒾ",
        "ⓉⓎⓅⒽⓄⒺⓊⓈ",
        "Quazii",
        "Typhoeus",
        "Revenant Horror",
        "Atoned Horror",
        "Tarantula Broodfather",
        "Conjoined Brood",
        "Sven Packmaster",
        "Voidgloom Seraph",
        "Riftstalker Bloodfiend",
    )

    /** True when [strippedName] is a slayer boss name tag. */
    fun isBossTag(strippedName: String): Boolean = BOSS_NAMES.any { strippedName.contains(it, ignoreCase = true) }
}
