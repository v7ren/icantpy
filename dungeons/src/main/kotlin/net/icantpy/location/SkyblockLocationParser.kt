package net.icantpy.location

import net.icantpy.dungeon.ChatClocks
import net.icantpy.dungeon.DungeonFloor
import net.icantpy.util.Text

/** Dungeon numbers read from the tab list and sidebar. Null means the line is not shown. */
data class DungeonTabStats(
    val secrets: Int? = null,
    val crypts: Int? = null,
    val deaths: Int? = null,
    val puzzles: Int? = null,
    val cleared: Int? = null,
)

/** One reading of where the player is. Built from plain text lines, so it is testable without a client. */
data class LocationSnapshot(
    val onSkyblock: Boolean = false,
    val rawArea: String = "",
    val island: SkyblockIsland = SkyblockIsland.UNKNOWN,
    val subarea: String = "",
    val floor: DungeonFloor = DungeonFloor.NONE,
    val stats: DungeonTabStats = DungeonTabStats(),
    val slayerBossVisible: Boolean = false,
    /** The sidebar's slayer quest line names the Inferno Demonlord (Blaze slayer). */
    val blazeSlayerQuest: Boolean = false,
) {
    /** Island name for triggers and logs: the known tab name, otherwise the raw text from the tab. */
    val islandLabel: String get() = if (island != SkyblockIsland.UNKNOWN) island.tabName else rawArea

    /** A dungeon is either the Catacombs island or a sidebar floor line such as "The Catacombs (F7)". */
    val inDungeon: Boolean get() = island == SkyblockIsland.CATACOMBS || floor != DungeonFloor.NONE

    /** One-line chat reply for "/icantpy where". */
    fun summary(): String {
        val island = islandLabel.ifBlank { "unknown" }
        val sub = subarea.ifBlank { "none" }
        val floorText = floor.name.takeIf { it != "NONE" } ?: "none"
        return "[icantpy] island: $island · subarea: $sub · floor: $floorText · " +
            "onSkyblock: $onSkyblock · inDungeon: $inDungeon"
    }

    companion object {
        val EMPTY = LocationSnapshot()
    }
}

object SkyblockLocationParser {
    private val AREA = Regex("""^(?:Area|Dungeon): (.+)$""")
    private val SUBAREA = Regex("""^[⏣ф]\s*(.+)$""")
    private val SECRETS = Regex("""^Secrets Found: (\d+)$""")
    private val CRYPTS = Regex("""^Crypts: (\d+)$""")
    private val DEATHS = Regex("""^Team Deaths: (\d+)$""")
    private val PUZZLES = Regex("""^Puzzles: \((\d+)\)$""")
    private val CLEARED = Regex("""^Cleared: (\d+)%""")

    /** [tabLines] are the tab list display names; [sidebarLines] start with the sidebar title. */
    fun parse(tabLines: List<String>, sidebarLines: List<String>): LocationSnapshot {
        val tab = tabLines.map { Text.strip(it) }
        val side = sidebarLines.map { Text.strip(it) }
        val rawArea = tab.firstNotNullOfOrNull { AREA.find(it)?.groupValues?.get(1)?.trim() }.orEmpty()
        return LocationSnapshot(
            onSkyblock = side.firstOrNull()?.contains("SKYBLOCK", ignoreCase = true) == true,
            rawArea = rawArea,
            island = SkyblockIsland.fromTabName(rawArea),
            subarea = side.firstNotNullOfOrNull { SUBAREA.find(it)?.groupValues?.get(1)?.trim() }.orEmpty(),
            floor = ChatClocks.floorFromLines(side),
            stats = parseStats(tab + side),
            slayerBossVisible = side.any { it == "Slay the boss!" },
            blazeSlayerQuest = side.any { it.startsWith("Inferno Demonlord") },
        )
    }

    private fun parseStats(lines: List<String>): DungeonTabStats = DungeonTabStats(
        secrets = firstInt(lines, SECRETS),
        crypts = firstInt(lines, CRYPTS),
        deaths = firstInt(lines, DEATHS),
        puzzles = firstInt(lines, PUZZLES),
        cleared = firstInt(lines, CLEARED),
    )

    private fun firstInt(lines: List<String>, pattern: Regex): Int? =
        lines.firstNotNullOfOrNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
}
