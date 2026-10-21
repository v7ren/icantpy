package net.icantpy.dungeon.dragons

enum class DragonPhase { DEAD, SPAWNING, ALIVE }

/**
 * Immutable snapshot of one dragon.
 * - [ticksToSpawn]: countdown while [DragonPhase.SPAWNING] (100 ticks after the spawn particle).
 * - [spawnedAtTick]: tracker tick when the dragon became alive; used for alive time.
 * - [lastDeadEntityId]: entity that died last, so a lingering corpse is not re-adopted as alive.
 */
data class DragonState(
    val dragon: WitherDragon,
    val phase: DragonPhase = DragonPhase.DEAD,
    val ticksToSpawn: Int = 0,
    val spawnedAtTick: Long = 0L,
    val health: Float? = null,
    val entityId: Int? = null,
    val lastDeadEntityId: Int? = null,
    val timesSpawned: Int = 0,
)

/** One dragon entity observed this tick. The integrator passes only EnderDragon entities. */
data class DragonEntitySample(
    val entityId: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val health: Float,
)

sealed interface DragonEvent {
    data class Spawning(val dragon: WitherDragon) : DragonEvent
    data class Spawned(val dragon: WitherDragon) : DragonEvent

    /** [aliveMs] is 0 for a dragon that was still spawning. [realHealthKill] is false for estimates from chat or disappearance. */
    data class Killed(val dragon: WitherDragon, val aliveMs: Long, val realHealthKill: Boolean) : DragonEvent

    /** [split] is true when two or more dragons were spawning together. */
    data class PriorityDecided(val dragon: WitherDragon, val split: Boolean) : DragonEvent

    /** Dragon vanished at high health with no death line within 2 s. Not counted as a kill. */
    data class Lost(val dragon: WitherDragon) : DragonEvent
}
