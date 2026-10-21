package net.icantpy.location

import net.icantpy.dungeon.ChatClocks
import net.icantpy.dungeon.DungeonFloor
import net.icantpy.qol.waypoint.SkyblockWorlds
import net.icantpy.qol.waypoint.WaypointWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkyblockLocationTest {
    private val sbTitle = "SKYBLOCK"

    private fun areaSnapshot(area: String, extra: List<String> = emptyList()): LocationSnapshot =
        SkyblockLocationParser.parse(listOf("Area: $area"), listOf(sbTitle) + extra)

    private fun dungeonSnapshot(floor: String?, extra: List<String> = emptyList()): LocationSnapshot {
        val side = listOf(sbTitle) + listOfNotNull(floor?.let { "The Catacombs ($it)" }) + extra
        return SkyblockLocationParser.parse(listOf("Dungeon: Catacombs"), side)
    }

    @Test
    fun tabNamesMatchIslandsCaseInsensitively() {
        assertEquals(SkyblockIsland.CRIMSON_ISLE, SkyblockIsland.fromTabName("crimson isle"))
        assertEquals(SkyblockIsland.SPIDERS_DEN, SkyblockIsland.fromTabName("Spider's Den"))
        assertEquals(SkyblockIsland.CATACOMBS, SkyblockIsland.fromTabName("Catacombs"))
        assertEquals(SkyblockIsland.UNKNOWN, SkyblockIsland.fromTabName("Mystery Land"))
        assertEquals(SkyblockIsland.UNKNOWN, SkyblockIsland.fromTabName("  "))
    }

    @Test
    fun unknownAreaKeepsRawNameAsLabel() {
        val snapshot = areaSnapshot("Mystery Land")
        assertEquals(SkyblockIsland.UNKNOWN, snapshot.island)
        assertEquals("Mystery Land", snapshot.islandLabel)
    }

    @Test
    fun tabAreaAndSidebarSubareaAreParsed() {
        val snapshot = SkyblockLocationParser.parse(
            listOf("Area: Crimson Isle"),
            listOf(sbTitle, " ⏣ Village", "Secrets Found: 5"),
        )
        assertEquals(SkyblockIsland.CRIMSON_ISLE, snapshot.island)
        assertEquals("Crimson Isle", snapshot.islandLabel)
        assertEquals("Village", snapshot.subarea)
        assertTrue(snapshot.onSkyblock)
        assertFalse(snapshot.inDungeon)
        assertEquals(5, snapshot.stats.secrets)
    }

    @Test
    fun phoneticSubareaMarkerIsAccepted() {
        val snapshot = SkyblockLocationParser.parse(emptyList(), listOf(sbTitle, "ф Hub"))
        assertEquals("Hub", snapshot.subarea)
    }

    @Test
    fun dungeonStatsAndFloorComeFromTabAndSidebar() {
        val snapshot = dungeonSnapshot(
            "F7",
            listOf("Cleared: 45% (12)", "Crypts: 2", "Team Deaths: 1", "Puzzles: (3)"),
        )
        assertEquals(SkyblockIsland.CATACOMBS, snapshot.island)
        assertEquals(DungeonFloor.F7, snapshot.floor)
        assertTrue(snapshot.inDungeon)
        assertEquals(45, snapshot.stats.cleared)
        assertEquals(2, snapshot.stats.crypts)
        assertEquals(1, snapshot.stats.deaths)
        assertEquals(3, snapshot.stats.puzzles)
    }

    @Test
    fun floorLineAloneCountsAsDungeon() {
        val snapshot = SkyblockLocationParser.parse(emptyList(), listOf(sbTitle, "The Catacombs (M4)"))
        assertEquals(DungeonFloor.M4, snapshot.floor)
        assertTrue(snapshot.inDungeon)
    }

    @Test
    fun colorCodesAreIgnoredInStats() {
        val snapshot = SkyblockLocationParser.parse(listOf("§7Secrets Found: §b12"), emptyList())
        assertEquals(12, snapshot.stats.secrets)
    }

    @Test
    fun slayerBossLineIsDetectedOnlyWhenExact() {
        assertTrue(SkyblockLocationParser.parse(emptyList(), listOf(sbTitle, "Slay the boss!")).slayerBossVisible)
        assertFalse(SkyblockLocationParser.parse(emptyList(), listOf(sbTitle, "Slay the boss! 0%")).slayerBossVisible)
        assertTrue(
            SkyblockLocationParser.parse(emptyList(), listOf(sbTitle, "Inferno Demonlord IV", "Slay the boss!"))
                .blazeSlayerQuest,
        )
        assertFalse(SkyblockLocationParser.parse(emptyList(), listOf(sbTitle, "Revenant Horror V")).blazeSlayerQuest)
    }

    @Test
    fun trackerReportsIslandChangeOnceAndBaselineIsSilent() {
        val tracker = SkyblockLocationTracker()
        assertEquals(emptyList(), tracker.update(areaSnapshot("Hub"), 0L))
        assertEquals(
            listOf(LocationChange.IslandChanged("Hub", "Crimson Isle")),
            tracker.update(areaSnapshot("Crimson Isle"), 100L),
        )
        assertEquals(emptyList(), tracker.update(areaSnapshot("Crimson Isle"), 200L))
    }

    @Test
    fun trackerWaitsForFloorBeforeDungeonEntered() {
        val tracker = SkyblockLocationTracker(floorWaitMs = 10_000L)
        tracker.update(areaSnapshot("Hub"), 0L)
        assertEquals(
            listOf(LocationChange.IslandChanged("Hub", "Catacombs")),
            tracker.update(dungeonSnapshot(null), 1_000L),
        )
        assertEquals(
            listOf(LocationChange.DungeonEntered(DungeonFloor.F7)),
            tracker.update(dungeonSnapshot("F7"), 2_000L),
        )
    }

    @Test
    fun trackerFallsBackToNoFloorAfterWaitAndReportsLeave() {
        val tracker = SkyblockLocationTracker(floorWaitMs = 10_000L)
        tracker.update(areaSnapshot("Hub"), 0L)
        tracker.update(dungeonSnapshot(null), 1_000L)
        assertEquals(
            listOf(LocationChange.DungeonEntered(DungeonFloor.NONE)),
            tracker.update(dungeonSnapshot(null), 11_000L),
        )
        assertEquals(
            listOf(LocationChange.IslandChanged("Catacombs", "Hub"), LocationChange.DungeonLeft),
            tracker.update(areaSnapshot("Hub"), 12_000L),
        )
    }

    @Test
    fun trackerEmitsSlayerBossOnlyOnEdge() {
        val tracker = SkyblockLocationTracker()
        val quiet = SkyblockLocationParser.parse(emptyList(), listOf(sbTitle))
        val boss = SkyblockLocationParser.parse(emptyList(), listOf(sbTitle, "Slay the boss!"))
        tracker.update(quiet, 0L)
        assertEquals(listOf(LocationChange.SlayerBossSpawned), tracker.update(boss, 1L))
        assertEquals(emptyList(), tracker.update(boss, 2L))
    }

    @Test
    fun waypointWorldFollowsTabIsland() {
        assertEquals("dungeon_hub", SkyblockWorlds.worldFor(areaSnapshot("Dungeon Hub"), emptyList()).key)
        assertEquals("island", SkyblockWorlds.worldFor(areaSnapshot("Private Island"), emptyList()).key)
        assertEquals("hub", SkyblockWorlds.worldFor(areaSnapshot("Hub"), emptyList()).key)
        assertEquals("garden", SkyblockWorlds.worldFor(areaSnapshot("Garden"), emptyList()).key)
        assertEquals("dungeon", SkyblockWorlds.worldFor(dungeonSnapshot("F6"), emptyList()).key)
    }

    @Test
    fun waypointWorldFallsBackToSidebarThenUnknown() {
        assertEquals(
            WaypointWorld.DUNGEON_HUB,
            SkyblockWorlds.worldFor(LocationSnapshot(), listOf("Dungeon Hub")).key,
        )
        assertEquals(WaypointWorld.UNKNOWN, SkyblockWorlds.worldFor(LocationSnapshot(), emptyList()).key)
    }

    @Test
    fun areaNamesMapToWaypointKeys() {
        assertEquals("hub", SkyblockWorlds.fromAreaName("Village").key)
        assertEquals("island", SkyblockWorlds.fromAreaName("Your Island").key)
        assertEquals("crimson_isle", SkyblockWorlds.fromAreaName("Crimson Isle").key)
    }

    @Test
    fun chatClocksUseIslandWhenLinesAreSilent() {
        assertTrue(ChatClocks.inDungeon(emptyList(), net.icantpy.location.SkyblockIsland.CATACOMBS))
        assertTrue(ChatClocks.elsewhere(emptyList(), net.icantpy.location.SkyblockIsland.GARDEN))
        assertFalse(ChatClocks.elsewhere(emptyList(), net.icantpy.location.SkyblockIsland.CATACOMBS))
    }

    @Test
    fun whereCommandSummarizesLocation() {
        val text = areaSnapshot("Crimson Isle", listOf("⏣ Village")).summary()
        assertTrue(text.contains("island: Crimson Isle"), text)
        assertTrue(text.contains("subarea: Village"), text)
        assertTrue(text.contains("floor: none"), text)
    }
}
