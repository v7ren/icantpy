package net.icantpy.dungeon.dragons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DragonTrackerTest {
    private val redCenter = Triple(27.0, 20.0, 60.0)
    private val blueCenter = Triple(84.0, 20.0, 95.0)

    private fun spawnParticle(tracker: DragonTracker, x: Double, z: Double) =
        tracker.onParticle("flame", x, 19.0, z, 20, 2f, 3f, 2f, 0f)

    private fun sample(id: Int, at: Triple<Double, Double, Double>, health: Float = 1_000_000_000f) =
        DragonEntitySample(id, at.first, at.second, at.third, health)

    @Test
    fun flameParticleStartsSpawningTheMatchingDragon() {
        val tracker = DragonTracker()
        spawnParticle(tracker, 27.0, 59.0)

        val red = tracker.state(WitherDragon.Red)
        assertEquals(DragonPhase.SPAWNING, red.phase)
        assertEquals(100, red.ticksToSpawn)
        assertEquals(DragonPhase.DEAD, tracker.state(WitherDragon.Orange).phase)
        assertEquals(listOf<DragonEvent>(DragonEvent.Spawning(WitherDragon.Red)), tracker.drainEvents())
    }

    @Test
    fun nonSpawnParticlesAreIgnored() {
        val tracker = DragonTracker()
        tracker.onParticle("smoke", 27.0, 19.0, 59.0, 20, 2f, 3f, 2f, 0f)
        tracker.onParticle("flame", 27.0, 20.0, 59.0, 20, 2f, 3f, 2f, 0f)
        tracker.onParticle("flame", 27.0, 19.0, 59.0, 19, 2f, 3f, 2f, 0f)
        tracker.onParticle("flame", 27.5, 19.0, 59.0, 20, 2f, 3f, 2f, 0f)
        tracker.onParticle("flame", 5.0, 19.0, 59.0, 20, 2f, 3f, 2f, 0f)
        tracker.onParticle("flame", 27.0, 19.0, 59.0, 20, 1f, 3f, 2f, 0f)
        tracker.onParticle("flame", 27.0, 19.0, 59.0, 20, 2f, 3f, 2f, 0.5f)

        assertTrue(tracker.states().all { it.phase == DragonPhase.DEAD })
        assertTrue(tracker.drainEvents().isEmpty())
    }

    @Test
    fun countdownFallsBackToAliveAfterOneHundredTicks() {
        val tracker = DragonTracker()
        spawnParticle(tracker, 27.0, 59.0)
        repeat(100) { tracker.onServerTick() }
        assertEquals(DragonPhase.SPAWNING, tracker.state(WitherDragon.Red).phase)

        tracker.onServerTick()
        assertEquals(DragonPhase.ALIVE, tracker.state(WitherDragon.Red).phase)
        assertTrue(tracker.drainEvents().contains(DragonEvent.Spawned(WitherDragon.Red)))
    }

    @Test
    fun dragonEntityInsideBoxActivatesWithoutParticle() {
        val tracker = DragonTracker()
        tracker.onEntities(listOf(sample(7, redCenter)))

        val red = tracker.state(WitherDragon.Red)
        assertEquals(DragonPhase.ALIVE, red.phase)
        assertEquals(7, red.entityId)
    }

    @Test
    fun zeroHealthKillsWithRealAliveTime() {
        val tracker = DragonTracker()
        tracker.onEntities(listOf(sample(7, redCenter)))
        tracker.drainEvents()
        repeat(30) { tracker.onServerTick() }

        tracker.onEntities(listOf(sample(7, redCenter, health = 0f)))

        assertEquals(DragonPhase.DEAD, tracker.state(WitherDragon.Red).phase)
        assertEquals(listOf<DragonEvent>(DragonEvent.Killed(WitherDragon.Red, 1_500L, realHealthKill = true)), tracker.drainEvents())
    }

    @Test
    fun vanishedNearlyDeadEntityEstimatesKill() {
        val tracker = DragonTracker()
        tracker.onEntities(listOf(sample(7, redCenter, health = 50_000_000f)))
        tracker.drainEvents()

        tracker.onEntities(emptyList())

        assertEquals(DragonPhase.DEAD, tracker.state(WitherDragon.Red).phase)
        assertEquals(DragonEvent.Killed(WitherDragon.Red, 0L, realHealthKill = false), tracker.drainEvents().single())
    }

    @Test
    fun vanishedHealthyEntityIsLostWithoutKillWhenNoDeathLineFollows() {
        val tracker = DragonTracker()
        tracker.onEntities(listOf(sample(7, redCenter)))
        tracker.drainEvents()

        tracker.onEntities(emptyList())
        repeat(DragonTracker.DEATH_CHAT_WINDOW_TICKS.toInt() + 1) { tracker.onServerTick() }

        assertEquals(DragonPhase.DEAD, tracker.state(WitherDragon.Red).phase)
        assertEquals(DragonEvent.Lost(WitherDragon.Red), tracker.drainEvents().single())
    }

    @Test
    fun vanishedHealthyEntityIsKilledWhenDeathLineArrivesWithinTwoSeconds() {
        val tracker = DragonTracker()
        tracker.onEntities(listOf(sample(7, redCenter)))
        tracker.drainEvents()

        tracker.onEntities(emptyList())
        repeat(20) { tracker.onServerTick() }
        tracker.onChat("[BOSS] Wither King: Oh, this one hurts!")

        assertEquals(DragonPhase.DEAD, tracker.state(WitherDragon.Red).phase)
        assertEquals(DragonEvent.Killed(WitherDragon.Red, 1_000L, realHealthKill = false), tracker.drainEvents().single())
    }

    @Test
    fun removedCorpseIsNotAdoptedAgain() {
        val tracker = DragonTracker()
        tracker.onEntities(listOf(sample(7, redCenter)))
        tracker.onEntities(emptyList())
        repeat(DragonTracker.DEATH_CHAT_WINDOW_TICKS.toInt() + 1) { tracker.onServerTick() }
        tracker.onEntities(listOf(sample(7, redCenter)))

        assertEquals(DragonPhase.DEAD, tracker.state(WitherDragon.Red).phase)
    }

    @Test
    fun witherKingLineKillsMostRecentlyAliveDragon() {
        val tracker = DragonTracker()
        tracker.onEntities(listOf(sample(1, redCenter)))
        repeat(10) { tracker.onServerTick() }
        tracker.onEntities(listOf(sample(1, redCenter), sample(2, blueCenter)))
        tracker.drainEvents()

        tracker.onChat("[BOSS] Wither King: Oh, this one hurts!")

        assertEquals(DragonPhase.ALIVE, tracker.state(WitherDragon.Red).phase)
        assertEquals(DragonPhase.DEAD, tracker.state(WitherDragon.Blue).phase)
        assertEquals(DragonEvent.Killed(WitherDragon.Blue, 0L, realHealthKill = false), tracker.drainEvents().single())
    }

    @Test
    fun unrelatedChatIsIgnored() {
        val tracker = DragonTracker()
        tracker.onEntities(listOf(sample(1, redCenter)))
        tracker.drainEvents()

        tracker.onChat("[BOSS] Wither King: Hello there")

        assertEquals(DragonPhase.ALIVE, tracker.state(WitherDragon.Red).phase)
        assertTrue(tracker.drainEvents().isEmpty())
    }

    @Test
    fun priorityDecidedOnlyWhenTwoDragonsSpawnTogether() {
        val tracker = DragonTracker()
        spawnParticle(tracker, 27.0, 59.0)
        assertNull(tracker.priority)

        spawnParticle(tracker, 85.0, 56.0)

        assertEquals(WitherDragon.Red, tracker.priority)
        assertTrue(tracker.drainEvents().contains(DragonEvent.PriorityDecided(WitherDragon.Red, split = true)))
    }

    @Test
    fun priorityUsesInjectedResolver() {
        val tracker = DragonTracker(priorityResolver = { WitherDragon.Blue })
        spawnParticle(tracker, 27.0, 59.0)
        spawnParticle(tracker, 85.0, 56.0)

        assertEquals(WitherDragon.Blue, tracker.priority)
    }

    @Test
    fun resetClearsStateAndPriority() {
        val tracker = DragonTracker()
        spawnParticle(tracker, 27.0, 59.0)
        spawnParticle(tracker, 85.0, 56.0)

        tracker.reset()

        assertTrue(tracker.states().all { it.phase == DragonPhase.DEAD })
        assertNull(tracker.priority)
        assertTrue(tracker.drainEvents().isEmpty())
    }
}
