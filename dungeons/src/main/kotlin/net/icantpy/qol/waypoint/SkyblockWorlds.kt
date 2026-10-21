package net.icantpy.qol.waypoint

import net.icantpy.dungeon.ChatClocks
import net.icantpy.dungeon.DungeonListener
import net.icantpy.location.LocationSnapshot
import net.icantpy.location.SkyblockIsland
import net.icantpy.location.SkyblockLocation
import net.icantpy.util.Text

data class WaypointWorld(val key: String) {
    fun label(): String = LABELS[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun matches(current: WaypointWorld): Boolean {
        if (key == ANY) return true
        return key == current.key
    }

    companion object {
        const val ANY = "any"
        const val UNKNOWN = "unknown"
        const val DUNGEON = "dungeon"
        const val HUB = "hub"
        const val DUNGEON_HUB = "dungeon_hub"
        const val ISLAND = "island"
        const val GARDEN = "garden"

        val PRESETS: List<String> = listOf(ANY, DUNGEON, HUB, DUNGEON_HUB, ISLAND, GARDEN)

        private val LABELS = mapOf(
            ANY to "Any",
            UNKNOWN to "Unknown",
            DUNGEON to "Dungeon",
            HUB to "Hub",
            DUNGEON_HUB to "Dungeon Hub",
            ISLAND to "Island",
            GARDEN to "Garden",
        )

        fun parse(raw: String?): WaypointWorld {
            val key = raw?.trim()?.lowercase()?.replace(' ', '_') ?: ANY
            if (key.isEmpty()) return WaypointWorld(ANY)
            return WaypointWorld(key)
        }

        fun cycle(current: String, extras: List<String> = emptyList(), delta: Int = 1): String {
            val options = LinkedHashSet<String>()
            options.addAll(PRESETS)
            extras.forEach { extra ->
                val key = parse(extra).key
                if (key != ANY && key != UNKNOWN) options.add(key)
            }
            val list = options.toList()
            val index = list.indexOf(parse(current).key).let { if (it < 0) 0 else it }
            val size = list.size
            val next = Math.floorMod(index + delta, size)
            return list[next]
        }
    }
}

object SkyblockWorlds {
    /** Tab-style "Area: X" text, still accepted by [fromSidebar] for lines that carry it. */
    private val AREA = Regex("""^Area:\s*(.+)$""", RegexOption.IGNORE_CASE)

    /** Waypoint world for the current location: the tab-list island first, then the sidebar lines. */
    fun current(): WaypointWorld = worldFor(SkyblockLocation.snapshot, DungeonListener.sidebarLines())

    fun worldFor(snapshot: LocationSnapshot, sidebar: Iterable<String>): WaypointWorld {
        if (snapshot.inDungeon) return WaypointWorld(WaypointWorld.DUNGEON)
        val key = when (snapshot.island) {
            SkyblockIsland.DUNGEON_HUB -> WaypointWorld.DUNGEON_HUB
            SkyblockIsland.HUB -> WaypointWorld.HUB
            SkyblockIsland.PRIVATE_ISLAND -> WaypointWorld.ISLAND
            SkyblockIsland.GARDEN -> WaypointWorld.GARDEN
            else -> return fromSidebar(sidebar)
        }
        return WaypointWorld(key)
    }

    /** Sidebar fallback for when the tab list has not named an island yet. */
    fun fromSidebar(lines: Iterable<String>): WaypointWorld {
        val stripped = lines.map { Text.strip(it) }
        if (ChatClocks.inDungeon(stripped)) {
            return WaypointWorld(WaypointWorld.DUNGEON)
        }
        for (line in stripped) {
            when {
                line.contains("Dungeon Hub", ignoreCase = true) ->
                    return WaypointWorld(WaypointWorld.DUNGEON_HUB)
                line.equals("Your Island", ignoreCase = true) ->
                    return WaypointWorld(WaypointWorld.ISLAND)
                line.contains("SkyBlock Hub", ignoreCase = true) ->
                    return WaypointWorld(WaypointWorld.HUB)
            }
            val area = AREA.find(line)?.groupValues?.get(1)?.trim().orEmpty()
            if (area.isNotEmpty()) return fromAreaName(area)
        }
        return WaypointWorld(WaypointWorld.UNKNOWN)
    }

    /** Maps a tab-list area name to a waypoint world key. Unknown names keep their normalized text. */
    fun fromAreaName(area: String): WaypointWorld {
        val island = SkyblockIsland.fromTabName(Text.strip(area))
        val key = when (island) {
            SkyblockIsland.HUB -> WaypointWorld.HUB
            SkyblockIsland.DUNGEON_HUB -> WaypointWorld.DUNGEON_HUB
            SkyblockIsland.PRIVATE_ISLAND -> WaypointWorld.ISLAND
            SkyblockIsland.GARDEN -> WaypointWorld.GARDEN
            SkyblockIsland.CATACOMBS -> WaypointWorld.DUNGEON
            else -> when (Text.strip(area).lowercase()) {
                "village" -> WaypointWorld.HUB
                "your island", "island" -> WaypointWorld.ISLAND
                else -> Text.strip(area).lowercase().replace(' ', '_')
            }
        }
        return WaypointWorld(key)
    }

    fun placeWorld(): String {
        val current = current()
        return if (current.key == WaypointWorld.UNKNOWN) WaypointWorld.ANY else current.key
    }
}
