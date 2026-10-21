package net.icantpy.slayer.aim

/**
 * Hellion Shield attunements of the Inferno Demonlord fight. Each demon shows its attunement on a name tag
 * (for example "ASHEN ♨8 ⓆⓊⒶⓏⒾⒾ 9.8M❤") and the player's dagger must match it. Pure string logic, no game types.
 */
enum class HellionAttunement {
    ASHEN, AURIC, SPIRIT, CRYSTAL;

    companion object {
        private val TAG = Regex("\\b(ASHEN|AURIC|SPIRIT|CRYSTAL)\\b")
        private val LORE = Regex("Attuned:\\s*(Ashen|Auric|Spirit|Crystal)\\b", RegexOption.IGNORE_CASE)

        /** The uppercase attunement word in a stripped name tag, matched as a whole word. */
        fun fromTag(strippedText: String): HellionAttunement? {
            val word = TAG.find(strippedText)?.groupValues?.get(1) ?: return null
            return valueOf(word)
        }

        /** The attunement from a stripped lore line reading "Attuned: <Name>", or null when no line has one. */
        fun fromLore(strippedLines: List<String>): HellionAttunement? {
            for (line in strippedLines) {
                val name = LORE.find(line)?.groupValues?.get(1) ?: continue
                return valueOf(name.uppercase())
            }
            return null
        }
    }
}

/** The Blaze slayer daggers and the attunements each one can carry. */
enum class Dagger(val attunements: Set<HellionAttunement>, private val names: List<String>) {
    TWILIGHT(
        setOf(HellionAttunement.SPIRIT, HellionAttunement.CRYSTAL),
        listOf("Twilight Dagger", "Mawdredge Dagger", "Deathripper Dagger"),
    ),
    FIREDUST(
        setOf(HellionAttunement.ASHEN, HellionAttunement.AURIC),
        listOf("Firedust Dagger", "Kindlebane Dagger", "Pyrochaos Dagger"),
    );

    companion object {
        /** The dagger named by a stripped item name, or null when it is not a Blaze dagger. */
        fun daggerOf(strippedItemName: String): Dagger? =
            entries.firstOrNull { dagger -> dagger.names.any { strippedItemName.contains(it, ignoreCase = true) } }
    }
}
