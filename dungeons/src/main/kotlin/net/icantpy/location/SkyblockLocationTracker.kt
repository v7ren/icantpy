package net.icantpy.location

import net.icantpy.dungeon.DungeonFloor

/** A change between two location readings. Island labels are the tab name, or the raw text when unknown. */
sealed interface LocationChange {
    data class IslandChanged(val from: String, val to: String) : LocationChange
    data class SubareaChanged(val from: String, val to: String) : LocationChange
    data class DungeonEntered(val floor: DungeonFloor) : LocationChange
    object DungeonLeft : LocationChange
    object SlayerBossSpawned : LocationChange
}

/**
 * Turns successive snapshots into changes. The first snapshot is a baseline and produces nothing.
 * A dungeon entry is reported once the floor is known, or after [floorWaitMs] without a floor.
 */
class SkyblockLocationTracker(private val floorWaitMs: Long = FLOOR_WAIT_MS) {
    private var previous: LocationSnapshot? = null
    private var pendingDungeonSince: Long? = null

    fun update(next: LocationSnapshot, nowMs: Long): List<LocationChange> {
        val before = previous
        previous = next
        val changes = mutableListOf<LocationChange>()
        if (before != null) changes += diff(before, next)
        changes += dungeonChanges(before, next, nowMs)
        return changes
    }

    fun reset() {
        previous = null
        pendingDungeonSince = null
    }

    private fun diff(before: LocationSnapshot, next: LocationSnapshot): List<LocationChange> = buildList {
        if (before.islandLabel != next.islandLabel && next.islandLabel.isNotEmpty()) {
            add(LocationChange.IslandChanged(before.islandLabel, next.islandLabel))
        }
        if (before.subarea != next.subarea) add(LocationChange.SubareaChanged(before.subarea, next.subarea))
        if (!before.slayerBossVisible && next.slayerBossVisible) add(LocationChange.SlayerBossSpawned)
    }

    private fun dungeonChanges(before: LocationSnapshot?, next: LocationSnapshot, nowMs: Long): List<LocationChange> {
        val wasInDungeon = before?.inDungeon == true
        if (!next.inDungeon) {
            pendingDungeonSince = null
            return if (wasInDungeon) listOf(LocationChange.DungeonLeft) else emptyList()
        }
        if (before != null && !wasInDungeon) pendingDungeonSince = nowMs
        val since = pendingDungeonSince ?: return emptyList()
        val floorKnown = next.floor != DungeonFloor.NONE
        if (!floorKnown && nowMs - since < floorWaitMs) return emptyList()
        pendingDungeonSince = null
        return listOf(LocationChange.DungeonEntered(next.floor))
    }

    companion object {
        const val FLOOR_WAIT_MS: Long = 10_000L
    }
}
