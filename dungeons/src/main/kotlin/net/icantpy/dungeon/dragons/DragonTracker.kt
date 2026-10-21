package net.icantpy.dungeon.dragons

/**
 * Pure state machine for the M7 phase 5 dragons. Feed it observations (spawn particles, dragon entities,
 * server ticks, chat); it updates per-dragon state and queues [DragonEvent]s. Nothing here touches Minecraft.
 *
 * Only DEAD dragons can start spawning, so a living dragon is never re-classified by a stray particle.
 */
class DragonTracker(
    private val priorityResolver: (Set<WitherDragon>) -> WitherDragon = DragonPriority::fallbackOrder,
) {
    private var states: Map<WitherDragon, DragonState> = freshStates()
    private val pending = mutableListOf<DragonEvent>()
    private var lastDeath: WitherDragon? = null

    // A dragon that vanished at high health. It is only counted as killed if a Wither King death line follows.
    private var pendingVanish: WitherDragon? = null
    private var pendingVanishTick: Long = 0L

    /** Server ticks since the tracker was created. Never reset, so alive times stay comparable across runs. */
    var tick: Long = 0L
        private set

    var priority: WitherDragon? = null
        private set

    fun state(dragon: WitherDragon): DragonState = states.getValue(dragon)

    fun states(): List<DragonState> = WitherDragon.entries.map { states.getValue(it) }

    /** Returns and clears events queued since the last call. */
    fun drainEvents(): List<DragonEvent> = pending.toList().also { pending.clear() }

    /** Forward every FLAME particle packet here; the filter for the spawn signature happens inside. */
    fun onParticle(
        type: String,
        x: Double,
        y: Double,
        z: Double,
        count: Int,
        xDist: Float,
        yDist: Float,
        zDist: Float,
        maxSpeed: Float,
    ) {
        if (!isSpawnParticle(type, x, y, z, count, xDist, yDist, zDist, maxSpeed)) return
        WitherDragon.entries
            .filter { state(it).phase == DragonPhase.DEAD && it.matchesSpawnColumn(x, z) }
            .forEach { startSpawning(it) }
        decidePriorityIfNeeded()
    }

    /** Call once per server tick. Counts down spawning dragons and falls back to ALIVE when the timer ends. */
    fun onServerTick() {
        tick++
        for (dragon in WitherDragon.entries) {
            val current = state(dragon)
            if (current.phase != DragonPhase.SPAWNING) continue
            if (current.ticksToSpawn > 0) {
                update(dragon) { it.copy(ticksToSpawn = it.ticksToSpawn - 1) }
            } else {
                activate(dragon, sample = null)
            }
        }
        val vanished = pendingVanish
        if (vanished != null && tick - pendingVanishTick > DEATH_CHAT_WINDOW_TICKS) markLost(vanished)
    }

    /**
     * Call every client tick with all dragon entities currently loaded.
     * Health 0 kills with a real kill. An entity that vanishes after being alive is a kill estimate.
     * A dragon entity inside a dragon's box activates it when no spawn particle was seen.
     */
    fun onEntities(samples: List<DragonEntitySample>) {
        val byId = samples.associateBy { it.entityId }
        for (dragon in WitherDragon.entries) updateKnownEntity(dragon, byId)
        for (sample in samples) adoptEntity(sample)
    }

    /** Wither King lines mark a death. The most recent death is attributed, else the most recently alive dragon. */
    fun onChat(line: String) {
        if (!WITHER_KING_LINE.matches(line)) return
        val vanished = pendingVanish
        if (vanished != null && tick - pendingVanishTick <= DEATH_CHAT_WINDOW_TICKS) {
            pendingVanish = null
            kill(vanished, realHealthKill = false)
            lastDeath = null
            return
        }
        val target = lastDeath ?: mostRecentLiving()
        if (target != null && state(target).phase != DragonPhase.DEAD) kill(target, realHealthKill = false)
        lastDeath = null
    }

    /** Clears all dragon state, priority and queued events. The tick counter keeps running. */
    fun reset() {
        states = freshStates()
        pending.clear()
        lastDeath = null
        pendingVanish = null
        priority = null
    }

    private fun startSpawning(dragon: WitherDragon) {
        update(dragon) { it.copy(phase = DragonPhase.SPAWNING, ticksToSpawn = SPAWN_TICKS, health = null, entityId = null) }
        pending += DragonEvent.Spawning(dragon)
    }

    private fun decidePriorityIfNeeded() {
        if (priority != null) return
        val spawning = WitherDragon.entries.filter { state(it).phase == DragonPhase.SPAWNING }.toSet()
        if (spawning.isEmpty()) return
        val totalSpawned = states.values.sumOf { it.timesSpawned }
        if (spawning.size < 2 && totalSpawned < 2) return
        val chosen = priorityResolver(spawning)
        priority = chosen
        pending += DragonEvent.PriorityDecided(chosen, split = spawning.size > 1)
    }

    private fun activate(dragon: WitherDragon, sample: DragonEntitySample?) {
        update(dragon) {
            it.copy(
                phase = DragonPhase.ALIVE,
                ticksToSpawn = 0,
                spawnedAtTick = tick,
                entityId = sample?.entityId,
                health = sample?.health,
                timesSpawned = it.timesSpawned + 1,
            )
        }
        pending += DragonEvent.Spawned(dragon)
    }

    private fun kill(dragon: WitherDragon, realHealthKill: Boolean) {
        val current = state(dragon)
        if (current.phase == DragonPhase.DEAD) return
        val aliveTicks = if (current.phase == DragonPhase.ALIVE) tick - current.spawnedAtTick else 0L
        pending += DragonEvent.Killed(dragon, aliveTicks * MS_PER_TICK, realHealthKill)
        update(dragon) {
            it.copy(
                phase = DragonPhase.DEAD,
                ticksToSpawn = 0,
                health = null,
                entityId = null,
                lastDeadEntityId = it.entityId ?: it.lastDeadEntityId,
            )
        }
        lastDeath = dragon
        if (priority == dragon) priority = null
    }

    private fun updateKnownEntity(dragon: WitherDragon, byId: Map<Int, DragonEntitySample>) {
        val current = state(dragon)
        if (current.phase != DragonPhase.ALIVE) return
        val entityId = current.entityId ?: return
        val sample = byId[entityId]
        when {
            sample == null -> onVanished(dragon, current)
            sample.health <= 0f -> kill(dragon, realHealthKill = true)
            else -> update(dragon) { it.copy(health = sample.health) }
        }
    }

    private fun adoptEntity(sample: DragonEntitySample) {
        if (states.values.any { it.entityId == sample.entityId }) return
        val dragon = WitherDragon.entries.firstOrNull { it.box.contains(sample.x, sample.y, sample.z) } ?: return
        val current = state(dragon)
        when (current.phase) {
            DragonPhase.SPAWNING -> activate(dragon, sample)
            DragonPhase.DEAD -> if (sample.health > 0f && sample.entityId != current.lastDeadEntityId) activate(dragon, sample)
            DragonPhase.ALIVE -> if (current.entityId == null && sample.health > 0f) {
                if (pendingVanish == dragon) pendingVanish = null
                update(dragon) { it.copy(entityId = sample.entityId, health = sample.health) }
            }
        }
    }

    /**
     * The entity is gone. Low last health (or a dragon that already took near-lethal damage) is a kill estimate.
     * Otherwise the dragon is held pending: a Wither King death line within the window confirms the kill, and
     * silence marks it lost, with no kill message.
     */
    private fun onVanished(dragon: WitherDragon, current: DragonState) {
        val lastHealth = current.health
        if (lastHealth != null && lastHealth <= MAX_HEALTH * NEARLY_DEAD_FRACTION) {
            kill(dragon, realHealthKill = false)
            return
        }
        update(dragon) { it.copy(entityId = null, health = null, lastDeadEntityId = it.entityId) }
        pendingVanish = dragon
        pendingVanishTick = tick
    }

    private fun markLost(dragon: WitherDragon) {
        pendingVanish = null
        update(dragon) { it.copy(phase = DragonPhase.DEAD, ticksToSpawn = 0, health = null, entityId = null) }
        pending += DragonEvent.Lost(dragon)
        if (priority == dragon) priority = null
    }

    private fun mostRecentLiving(): WitherDragon? =
        states.values
            .filter { it.phase == DragonPhase.ALIVE }
            .maxByOrNull { it.spawnedAtTick }
            ?.dragon
            ?: WitherDragon.entries.firstOrNull { state(it).phase == DragonPhase.SPAWNING }

    private fun update(dragon: WitherDragon, transform: (DragonState) -> DragonState) {
        states = states + (dragon to transform(states.getValue(dragon)))
    }

    companion object {
        const val SPAWN_TICKS = 100
        const val MS_PER_TICK = 50L
        const val DEATH_CHAT_WINDOW_TICKS = 40L
        const val MAX_HEALTH = 1_000_000_000f
        const val NEARLY_DEAD_FRACTION = 0.1f

        val WITHER_KING_LINE = Regex("^\\[BOSS] Wither King: (Oh, this one hurts!|I have more of those\\.|My soul is disposable\\.)$")

        private fun freshStates(): Map<WitherDragon, DragonState> = WitherDragon.entries.associateWith { DragonState(it) }

        private fun isSpawnParticle(
            type: String,
            x: Double,
            y: Double,
            z: Double,
            count: Int,
            xDist: Float,
            yDist: Float,
            zDist: Float,
            maxSpeed: Float,
        ): Boolean =
            type == "flame" &&
                count == 20 &&
                y == 19.0 &&
                xDist == 2f && yDist == 3f && zDist == 2f &&
                maxSpeed == 0f &&
                x % 1.0 == 0.0 && z % 1.0 == 0.0
    }
}
