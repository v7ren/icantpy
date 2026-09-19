package net.icantpy.dungeon.leap

enum class LeapDungeonClass(
    val displayName: String,
    val shortName: String,
    val colorArgb: Int,
    val defaultQuadrant: Int,
    val priority: Int,
) {
    ARCHER("Archer", "Arch", 0xFFFFAA00.toInt(), 0, 2),
    BERSERK("Berserk", "Bers", 0xFFAA0000.toInt(), 1, 0),
    HEALER("Healer", "Heal", 0xFFFF55FF.toInt(), 2, 2),
    MAGE("Mage", "Mage", 0xFF55FFFF.toInt(), 3, 2),
    TANK("Tank", "Tank", 0xFF00AA00.toInt(), 3, 1),
    EMPTY("Empty", "Empty", 0xFFFFFFFF.toInt(), 0, 0),
    ;

    fun isReal(): Boolean = this != EMPTY

    companion object {
        val PLAYABLE: List<LeapDungeonClass> = listOf(ARCHER, BERSERK, HEALER, MAGE, TANK)

        fun fromToken(raw: String): LeapDungeonClass? = when (raw.lowercase().trim()) {
            "archer", "arch" -> ARCHER
            "berserk", "berserker", "bers" -> BERSERK
            "healer", "heal" -> HEALER
            "mage" -> MAGE
            "tank" -> TANK
            "empty", "auto", "live", "none" -> EMPTY
            else -> null
        }
    }
}

data class LeapPlayer(
    val name: String,
    val clazz: LeapDungeonClass,
    val isDead: Boolean = false,
)
