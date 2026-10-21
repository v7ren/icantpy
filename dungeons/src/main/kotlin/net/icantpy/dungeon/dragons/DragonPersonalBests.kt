package net.icantpy.dungeon.dragons

/** Fastest alive time per dragon. Only real health kills count, since estimates can be late or early. */
object DragonPersonalBests {
    data class Update(val bests: Map<WitherDragon, Long>, val improved: Boolean)

    fun record(bests: Map<WitherDragon, Long>, dragon: WitherDragon, aliveMs: Long, realHealthKill: Boolean): Update {
        if (!realHealthKill || aliveMs <= 0L) return Update(bests, improved = false)
        val current = bests[dragon]
        if (current != null && aliveMs >= current) return Update(bests, improved = false)
        return Update(bests + (dragon to aliveMs), improved = true)
    }
}
