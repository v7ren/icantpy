package net.icantpy.qol.waypoint

import net.icantpy.dungeon.ChatClocks
import net.icantpy.dungeon.DungeonListener
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
    private val AREA = Regex("""^Area:\s*(.+)$""", RegexOption.IGNORE_CASE)

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

    fun fromAreaName(area: String): WaypointWorld {
        val normalized = Text.strip(area).lowercase()
        val key = when (normalized) {
            "hub", "village" -> WaypointWorld.HUB
            "dungeon hub" -> WaypointWorld.DUNGEON_HUB
            "private island", "your island", "island" -> WaypointWorld.ISLAND
            "garden" -> WaypointWorld.GARDEN
            "catacombs", "the catacombs", "dungeon" -> WaypointWorld.DUNGEON
            else -> normalized.replace(' ', '_')
        }
        return WaypointWorld(key)
    }

    fun current(): WaypointWorld = fromSidebar(DungeonListener.sidebarLines())

    fun placeWorld(): String {
        val current = current()
        return if (current.key == WaypointWorld.UNKNOWN) WaypointWorld.ANY else current.key
    }
}
