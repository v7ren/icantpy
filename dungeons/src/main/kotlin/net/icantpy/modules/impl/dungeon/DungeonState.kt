package net.icantpy.modules.impl.dungeon

enum class DungeonFloor {
    NONE,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    M1,
    M2,
    M3,
    M4,
    M5,
    M6,
    M7,
    ;

    companion object {
        fun parse(raw: String): DungeonFloor {
            val text = raw.uppercase()
            return entries.firstOrNull { it != NONE && text.contains(it.name) } ?: NONE
        }
    }
}

data class DungeonState(
    val inDungeon: Boolean = false,
    val inBoss: Boolean = false,
    val floor: DungeonFloor = DungeonFloor.NONE,
    val percentCleared: Int = -1,
)
