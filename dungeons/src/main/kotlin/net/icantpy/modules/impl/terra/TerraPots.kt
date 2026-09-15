package net.icantpy.modules.impl.terra

data class TerraPot(
    val x: Int,
    val y: Int,
    val z: Int,
    val ticksLeft: Int,
)

data class TerraPots(
    val entries: List<TerraPot> = emptyList(),
) {
    fun add(x: Int, y: Int, z: Int, duration: Int): TerraPots {
        if (entries.any { it.x == x && it.y == y && it.z == z }) return this
        return copy(entries = entries + TerraPot(x, y, z, duration))
    }

    fun tick(): TerraPots {
        val next = entries.map { it.copy(ticksLeft = it.ticksLeft - 1) }.filter { it.ticksLeft > 0 }
        return copy(entries = next)
    }

    fun clear(): TerraPots = TerraPots()

    fun soonest(): TerraPot? = entries.minByOrNull { it.ticksLeft }
}
