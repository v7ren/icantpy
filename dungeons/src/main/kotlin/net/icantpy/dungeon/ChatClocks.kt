package net.icantpy.dungeon

import net.icantpy.util.Text

object ChatClocks {
    private val FLOOR = Regex("""(?:The Catacombs|Master Mode Catacombs)\s*\(([MF]\d)\)""", RegexOption.IGNORE_CASE)
    private val CLEARED = Regex("""Cleared:\s*(\d+)%""")

    fun floorFromLines(lines: Iterable<String>): DungeonFloor {
        for (line in lines) {
            val match = FLOOR.find(Text.strip(line)) ?: continue
            val parsed = DungeonFloor.parse(match.groupValues[1])
            if (parsed != DungeonFloor.NONE) return parsed
        }
        return DungeonFloor.NONE
    }

    fun inDungeon(lines: Iterable<String>): Boolean {
        return lines.any { raw ->
            val line = Text.strip(raw)
            line.contains("Catacombs", ignoreCase = true) ||
                line.startsWith("Cleared:") ||
                line.startsWith("Time Elapsed:") ||
                line.contains("Crypts:") ||
                line.contains("Secrets Found:") ||
                line.startsWith("Team Deaths:")
        }
    }

    fun percentCleared(lines: Iterable<String>): Int? {
        for (raw in lines) {
            val match = CLEARED.find(Text.strip(raw)) ?: continue
            return match.groupValues[1].toIntOrNull()
        }
        return null
    }

    fun elsewhere(lines: Iterable<String>): Boolean {
        if (inDungeon(lines)) return false
        if (floorFromLines(lines) != DungeonFloor.NONE) return false
        return lines.any { raw ->
            val line = Text.strip(raw)
            line.contains("SkyBlock Hub", ignoreCase = true) ||
                line.contains("Dungeon Hub", ignoreCase = true) ||
                line.equals("Your Island", ignoreCase = true) ||
                line.contains("Area: Hub", ignoreCase = true) ||
                line.contains("Area: Private Island", ignoreCase = true) ||
                line.contains("Area: Garden", ignoreCase = true)
        }
    }
}
