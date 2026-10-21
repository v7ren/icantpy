package net.icantpy.location

/** Skyblock islands as named in the tab list ("Area: X" or "Dungeon: X"). */
enum class SkyblockIsland(val tabName: String) {
    CATACOMBS("Catacombs"),
    DUNGEON_HUB("Dungeon Hub"),
    HUB("Hub"),
    PRIVATE_ISLAND("Private Island"),
    GARDEN("Garden"),
    CRIMSON_ISLE("Crimson Isle"),
    KUUDRA("Kuudra"),
    THE_END("The End"),
    DWARVEN_MINES("Dwarven Mines"),
    CRYSTAL_HOLLOWS("Crystal Hollows"),
    MINESHAFT("Mineshaft"),
    SPIDERS_DEN("Spider's Den"),
    THE_PARK("The Park"),
    GOLD_MINE("Gold Mine"),
    DEEP_CAVERNS("Deep Caverns"),
    FARMING_ISLANDS("The Farming Islands"),
    JERRYS_WORKSHOP("Jerry's Workshop"),
    RIFT("The Rift"),
    BACKWATER_BAYOU("Backwater Bayou"),
    GALATEA("Galatea"),
    DARK_AUCTION("Dark Auction"),
    UNKNOWN(""),
    ;

    companion object {
        /** Case-insensitive match on [tabName]. Unknown or blank names return [UNKNOWN]; callers keep the raw text. */
        fun fromTabName(raw: String): SkyblockIsland {
            val name = raw.trim()
            if (name.isEmpty()) return UNKNOWN
            return entries.firstOrNull { it != UNKNOWN && it.tabName.equals(name, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
