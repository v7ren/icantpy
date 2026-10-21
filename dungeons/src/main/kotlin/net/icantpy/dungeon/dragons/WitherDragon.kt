package net.icantpy.dungeon.dragons

/** Axis-aligned box with Minecraft AABB semantics: the minimum edge is inside, the maximum edge is outside. */
data class DragonBox(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double,
) {
    fun contains(x: Double, y: Double, z: Double): Boolean =
        x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ
}

/**
 * The five M7 phase 5 dragons. Spawn columns are the integer x/z ranges a spawn particle lands in,
 * and [box] is the area a dragon entity occupies while it is alive.
 */
enum class WitherDragon(
    val displayName: String,
    val colorCode: Char,
    val colorArgb: Int,
    val box: DragonBox,
    val spawnXRange: IntRange,
    val spawnZRange: IntRange,
) {
    Red("Red", 'c', 0xFFFF5555.toInt(), DragonBox(14.5, 13.0, 45.5, 39.5, 28.0, 70.5), 24..30, 56..62),
    Orange("Orange", '6', 0xFFFFAA00.toInt(), DragonBox(72.0, 8.0, 47.0, 102.0, 28.0, 77.0), 82..88, 53..59),
    Green("Green", 'a', 0xFF55FF55.toInt(), DragonBox(7.0, 8.0, 80.0, 37.0, 28.0, 110.0), 23..29, 91..97),
    Blue("Blue", 'b', 0xFF55FFFF.toInt(), DragonBox(71.5, 13.0, 82.5, 96.5, 26.0, 107.5), 82..88, 91..97),
    Purple("Purple", '5', 0xFFAA00AA.toInt(), DragonBox(45.5, 13.0, 113.5, 68.5, 23.0, 136.5), 53..59, 122..128);

    /** True when a spawn particle's x and z are whole numbers inside this dragon's spawn columns. */
    fun matchesSpawnColumn(x: Double, z: Double): Boolean {
        if (!isWhole(x) || !isWhole(z)) return false
        return x.toInt() in spawnXRange && z.toInt() in spawnZRange
    }

    private fun isWhole(value: Double): Boolean = value % 1.0 == 0.0
}
