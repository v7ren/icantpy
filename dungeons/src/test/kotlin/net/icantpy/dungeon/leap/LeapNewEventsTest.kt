package net.icantpy.dungeon.leap

import com.google.gson.JsonObject
import net.icantpy.location.DungeonTabStats
import net.icantpy.location.LocationChange
import net.icantpy.dungeon.DungeonFloor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapNewEventsTest {
    private fun assertOne(line: String, event: LeapTriggerEvent, player: String? = null) {
        assertEquals(listOf(event to player), LeapChatEvents.detect(line), "line: $line")
    }

    private fun assertNone(line: String) {
        assertEquals(emptyList(), LeapChatEvents.detect(line), "line: $line")
    }

    @Test
    fun lobbyLines() {
        assertOne("Notch is now ready!", LeapTriggerEvent.PLAYER_READY, "Notch")
        assertNone("Notch is now ready")
        assertOne("Starting in 1 second.", LeapTriggerEvent.COUNTDOWN)
        assertNone("Starting in 2 seconds.")
        assertOne("You have selected the Healer Dungeon Class!", LeapTriggerEvent.CLASS_SELECTED, "Healer")
        assertNone("You have selected the Pirate Dungeon Class!")
        assertOne("Party Finder > Notch joined the dungeon group! (Mage Level 50)", LeapTriggerEvent.PF_JOINED, "Notch")
        assertNone("Party Finder > Notch joined the group!")
    }

    @Test
    fun puzzleLines() {
        assertOne("PUZZLE SOLVED! Creeper Beams solved!", LeapTriggerEvent.PUZZLE_SOLVED)
        assertNone("PUZZLE SOLVED!")
        assertOne("Party > [MVP+] Notch: Mimic dead!", LeapTriggerEvent.MIMIC_DEAD)
        assertOne("Party > Notch: mimic killed!", LeapTriggerEvent.MIMIC_DEAD)
        assertNone("Mimic dead!")
        assertNone("Party > Notch: the mimic is alive")
        assertOne("DUNGEON BUFF! Notch found a Blessing of Power II! Blessing ...", LeapTriggerEvent.BLESSING, "Power")
        assertOne("A Blessing of Life 2 was picked up!", LeapTriggerEvent.BLESSING, "Life")
        assertNone("A Blessing of Life was dropped!")
    }

    @Test
    fun kuudraLines() {
        assertOne(
            "[NPC] Elle: ARGH! All of the supplies fell into the lava! You need to retrieve them quickly!",
            LeapTriggerEvent.KUUDRA_SUPPLIES,
        )
        assertOne(
            "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!",
            LeapTriggerEvent.KUUDRA_BUILD_DONE,
        )
        assertOne(
            "[NPC] Elle: POW! SURELY THAT’S IT! I don't think he has any more in him!",
            LeapTriggerEvent.KUUDRA_STUN,
        )
        assertOne("[NPC] Elle: What just happened!? Is this Kuudra’s real lair?", LeapTriggerEvent.KUUDRA_LAIR)
        assertNone("[NPC] Elle: POW!")
    }

    @Test
    fun slayerLines() {
        assertOne("  SLAYER QUEST STARTED!", LeapTriggerEvent.SLAYER_STARTED)
        assertNone("SLAYER QUEST STARTED")
        assertOne("SLAYER QUEST COMPLETE!", LeapTriggerEvent.SLAYER_COMPLETE)
        assertOne("NICE! SLAYER BOSS SLAIN!", LeapTriggerEvent.SLAYER_COMPLETE)
        assertOne("SLAYER QUEST FAILED!", LeapTriggerEvent.SLAYER_FAILED)
        assertNone("SLAYER QUEST FAILED")
    }

    @Test
    fun valueFilterTreatsBlankAnyAndSelfAsAll() {
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.ISLAND_ENTERED)
        assertTrue(LeapOrientEngine.valueMatchHits(trigger.copy(match = "any"), "Crimson Isle"))
        assertTrue(LeapOrientEngine.valueMatchHits(trigger.copy(match = "self"), "Crimson Isle"))
        assertTrue(LeapOrientEngine.valueMatchHits(trigger.copy(match = ""), "Crimson Isle"))
        assertTrue(LeapOrientEngine.valueMatchHits(trigger.copy(match = "crimson isle"), "Crimson Isle"))
        assertFalse(LeapOrientEngine.valueMatchHits(trigger.copy(match = "Hub"), "Crimson Isle"))
    }

    @Test
    fun locationChangesMapToEvents() {
        assertEquals(
            listOf(LeapTriggerEvent.ISLAND_LEFT to "Hub", LeapTriggerEvent.ISLAND_ENTERED to "Crimson Isle"),
            LeapLocationSignals.eventsFor(LocationChange.IslandChanged("Hub", "Crimson Isle")),
        )
        assertEquals(
            listOf(LeapTriggerEvent.ISLAND_ENTERED to "Crimson Isle"),
            LeapLocationSignals.eventsFor(LocationChange.IslandChanged("", "Crimson Isle")),
        )
        assertEquals(
            listOf(LeapTriggerEvent.DUNGEON_LEFT to "Catacombs"),
            LeapLocationSignals.eventsFor(LocationChange.DungeonLeft),
        )
        assertEquals(
            listOf(LeapTriggerEvent.DUNGEON_ENTERED to "F7"),
            LeapLocationSignals.eventsFor(LocationChange.DungeonEntered(DungeonFloor.F7)),
        )
        assertEquals(
            listOf(LeapTriggerEvent.DUNGEON_ENTERED to "unknown floor"),
            LeapLocationSignals.eventsFor(LocationChange.DungeonEntered(DungeonFloor.NONE)),
        )
    }

    private fun threshold(event: LeapTriggerEvent, value: Int) = LeapOrientTrigger.create(event).copy(threshold = value)

    @Test
    fun thresholdFiresOnceWhenCountCrossesRunOnly() {
        val watcher = LeapProgressWatcher()
        val card = threshold(LeapTriggerEvent.SECRETS_REACHED, 10)
        assertEquals(emptyList(), watcher.update(DungeonTabStats(secrets = 5), listOf(card)))
        assertEquals(emptyList(), watcher.update(DungeonTabStats(secrets = 9), listOf(card)))
        val hits = watcher.update(DungeonTabStats(secrets = 10), listOf(card))
        assertEquals(listOf(LeapTriggerEvent.SECRETS_REACHED), hits.map { it.event })
        assertEquals("10", hits.single().value)
        assertEquals(emptyList(), watcher.update(DungeonTabStats(secrets = 12), listOf(card)))
        watcher.reset()
        assertEquals(emptyList(), watcher.update(DungeonTabStats(secrets = 3), listOf(card)))
        assertEquals(1, watcher.update(DungeonTabStats(secrets = 15), listOf(card)).size)
    }

    @Test
    fun thresholdZeroAndDisabledNeverFire() {
        val watcher = LeapProgressWatcher()
        val off = threshold(LeapTriggerEvent.CRYPTS_REACHED, 0)
        val disabled = threshold(LeapTriggerEvent.CRYPTS_REACHED, 2).copy(enabled = false)
        watcher.update(DungeonTabStats(crypts = 0), listOf(off, disabled))
        assertEquals(emptyList(), watcher.update(DungeonTabStats(crypts = 5), listOf(off, disabled)))
    }

    @Test
    fun clearedThresholdUsesSidebarPercent() {
        val watcher = LeapProgressWatcher()
        val card = threshold(LeapTriggerEvent.CLEARED_REACHED, 50)
        watcher.update(DungeonTabStats(cleared = 40), listOf(card))
        assertEquals(1, watcher.update(DungeonTabStats(cleared = 55), listOf(card)).size)
    }

    @Test
    fun deathsFireOnEachIncrease() {
        val watcher = LeapProgressWatcher()
        val card = LeapOrientTrigger.create(LeapTriggerEvent.DEATHS_CHANGED)
        watcher.update(DungeonTabStats(deaths = 0), listOf(card))
        assertEquals(1, watcher.update(DungeonTabStats(deaths = 1), listOf(card)).size)
        assertEquals(emptyList(), watcher.update(DungeonTabStats(deaths = 1), listOf(card)))
        assertEquals(1, watcher.update(DungeonTabStats(deaths = 3), listOf(card)).size)
        assertEquals(emptyList(), watcher.update(DungeonTabStats(deaths = null), listOf(card)))
    }

    @Test
    fun eventRingKeepsNewestThirty() {
        val ring = LeapEventRing()
        repeat(35) { index ->
            ring.record(LeapEventEntry(index.toLong(), LeapTriggerEvent.COUNTDOWN, "v$index", "chat", 0))
        }
        val entries = ring.newestFirst()
        assertEquals(LeapEventRing.CAPACITY, entries.size)
        assertEquals("v34", entries.first().value)
        assertEquals("v5", entries.last().value)
        ring.clear()
        assertEquals(emptyList(), ring.newestFirst())
    }

    @Test
    fun dropdownListsEveryEventOnce() {
        val groups = LeapTriggerEventMenu.groups()
        val flattened = groups.flatMap { it.second }
        assertEquals(LeapTriggerEvent.entries.toList(), flattened)
        assertEquals(groups.size, groups.map { it.first }.distinct().size, "group names must be contiguous")
        assertTrue(listOf("Location", "Lobby", "Progress", "Puzzles", "Kuudra", "Slayer").all { name ->
            groups.any { it.first == name }
        })
    }

    @Test
    fun thresholdDefaultsAreOfferedAsLevels() {
        for (event in listOf(LeapTriggerEvent.SECRETS_REACHED, LeapTriggerEvent.CRYPTS_REACHED, LeapTriggerEvent.CLEARED_REACHED)) {
            assertTrue(LeapTriggerEventMenu.defaultThreshold(event) in LeapTriggerEventMenu.thresholdLevels(event), event.name)
        }
    }

    @Test
    fun thresholdRoundTripsThroughJson() {
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.SECRETS_REACHED).copy(threshold = 7)
        val obj = JsonObject()
        obj.add("triggers", LeapTriggerJson.triggersToJson(listOf(trigger)))
        assertEquals(7, LeapTriggerJson.triggersFromJson(obj)!!.single().threshold)
    }
}
