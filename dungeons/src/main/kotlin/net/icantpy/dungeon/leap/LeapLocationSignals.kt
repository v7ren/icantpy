package net.icantpy.dungeon.leap

import net.icantpy.location.LocationChange

/** Maps location changes to the trigger events they represent. The captured value is the island or floor name. */
object LeapLocationSignals {
    fun eventsFor(change: LocationChange): List<Pair<LeapTriggerEvent, String>> = when (change) {
        is LocationChange.IslandChanged -> buildList {
            if (change.from.isNotBlank()) add(LeapTriggerEvent.ISLAND_LEFT to change.from)
            add(LeapTriggerEvent.ISLAND_ENTERED to change.to)
        }
        is LocationChange.DungeonEntered -> listOf(LeapTriggerEvent.DUNGEON_ENTERED to floorName(change))
        LocationChange.DungeonLeft -> listOf(LeapTriggerEvent.DUNGEON_LEFT to "Catacombs")
        LocationChange.SlayerBossSpawned -> listOf(LeapTriggerEvent.SLAYER_BOSS_SPAWNED to "Slay the boss!")
        is LocationChange.SubareaChanged -> emptyList()
    }

    private fun floorName(change: LocationChange.DungeonEntered): String =
        change.floor.name.takeIf { it != "NONE" } ?: "unknown floor"
}
