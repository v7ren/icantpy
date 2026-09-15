package net.icantpy.modules.impl.dungeon

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DungeonDebugDetectionTest {
    @Test
    fun debugModeMustNotFreezeLiveM7OrF7Detection() {
        assertFalse(DungeonListener.shouldKeepDebugState(true, listOf("The Catacombs (M7)", "Cleared: 100%")))
        assertFalse(DungeonListener.shouldKeepDebugState(true, listOf("The Catacombs (F7)", "Cleared: 100%")))
    }

    @Test
    fun localDebugWithoutDungeonBoardRetainsSimulation() {
        assertTrue(DungeonListener.shouldKeepDebugState(true, emptyList()))
        assertTrue(DungeonListener.shouldKeepDebugState(true, listOf("SKYBLOCK", "Village")))
        assertFalse(DungeonListener.shouldKeepDebugState(false, emptyList()))
    }
}
