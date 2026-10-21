package net.icantpy.dungeon.leap

import net.icantpy.dungeon.dragons.DragonEvent
import net.icantpy.dungeon.dragons.WitherDragon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeapDragonEventsTest {
    @Test
    fun killedDragonMapsToKilledTriggerWithColorAsCapturedName() {
        val mapped = LeapDragonEvents.map(DragonEvent.Killed(WitherDragon.Purple, 1_000L, realHealthKill = true))
        assertEquals(LeapTriggerEvent.DRAGON_KILLED, mapped?.event)
        assertEquals("Purple", mapped?.color)
    }

    @Test
    fun spawnPriorityAndSpawnedMapToTheirTriggers() {
        assertEquals(LeapTriggerEvent.DRAGON_SPAWNING, LeapDragonEvents.map(DragonEvent.Spawning(WitherDragon.Red))?.event)
        assertEquals(LeapTriggerEvent.DRAGON_SPAWNED, LeapDragonEvents.map(DragonEvent.Spawned(WitherDragon.Red))?.event)
        assertEquals(
            LeapTriggerEvent.DRAGON_PRIORITY,
            LeapDragonEvents.map(DragonEvent.PriorityDecided(WitherDragon.Green, split = true))?.event,
        )
    }

    @Test
    fun lostDragonHasNoTrigger() {
        assertNull(LeapDragonEvents.map(DragonEvent.Lost(WitherDragon.Blue)))
    }
}
