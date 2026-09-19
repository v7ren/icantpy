package net.icantpy.slayer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlayerCarryCounterTest {
    @Test
    fun defaultsAreDisabledWithNoPlayers() {
        val defaults = SlayerCarrySettings()
        assertFalse(defaults.enabled)
        assertTrue(defaults.autoCount)
        assertTrue(defaults.notifyOnTarget)
        assertEquals(SlayerCarrySettings.UNBOUND, defaults.plusKey)
        assertEquals(SlayerCarrySettings.UNBOUND, defaults.minusKey)
        assertTrue(defaults.players.isEmpty())
        assertNull(defaults.selectedPlayer())
    }

    @Test
    fun progressTextShowsTargetWhenSet() {
        assertEquals("3/20", CarriedPlayer("Alice", count = 3, target = 20).progress())
        assertEquals("3", CarriedPlayer("Alice", count = 3).progress())
        assertTrue(CarriedPlayer("Alice", count = 20, target = 20).reachedTarget())
        assertFalse(CarriedPlayer("Alice", count = 19, target = 20).reachedTarget())
        assertFalse(CarriedPlayer("Alice", count = 99).reachedTarget())
    }

    @Test
    fun addingPlayersTrimsDeduplicatesAndSelects() {
        val settings = SlayerCarrySettings()
            .addPlayer("  Alice ", 20)
            .addPlayer("bob", 5)
            .addPlayer("ALICE", 30)
        assertEquals(listOf("Alice", "bob"), settings.players.map { it.name })
        assertEquals(30, settings.player("alice")?.target)
        assertEquals("Alice", settings.selected)
        assertEquals(0, settings.player("alice")?.count)
    }

    @Test
    fun removingPlayerUpdatesSelection() {
        val settings = SlayerCarrySettings()
            .addPlayer("Alice", 20)
            .addPlayer("Bob", 20)
            .select("Alice")
            .removePlayer("alice")
        assertEquals(listOf("Bob"), settings.players.map { it.name })
        assertEquals("Bob", settings.selected)
    }

    @Test
    fun adjustAndUpdateSelectedPlayer() {
        val settings = SlayerCarrySettings()
            .addPlayer("Alice", 20)
            .adjustPlayer("alice", 3)
            .updatePlayer("alice") { it.withTarget(10) }
        val player = settings.player("Alice")!!
        assertEquals(3, player.count)
        assertEquals(10, player.target)
    }

    @Test
    fun countAndTargetAreClamped() {
        assertEquals(0, CarriedPlayer("A").adjusted(-5).count)
        assertEquals(CarriedPlayer.MAX_COUNT, CarriedPlayer("A").withCount(Int.MAX_VALUE).count)
        assertEquals(0, CarriedPlayer("A").withTarget(-5).target)
        assertEquals(CarriedPlayer.MAX_COUNT, CarriedPlayer("A").withTarget(Int.MAX_VALUE).target)
    }

    @Test
    fun unknownSelectKeepsCurrentSelection() {
        val settings = SlayerCarrySettings().addPlayer("Alice", 20).select("Nobody")
        assertEquals("Alice", settings.selected)
    }

    @Test
    fun cycleSelectionWrapsAroundTheList() {
        val settings = SlayerCarrySettings()
            .addPlayer("A", 0)
            .addPlayer("B", 0)
            .addPlayer("C", 0)
            .select("A")
        assertEquals("B", settings.cycleSelection(1).selected)
        assertEquals("C", settings.cycleSelection(-1).selected)
        assertEquals("A", settings.cycleSelection(3).selected)
        assertNull(SlayerCarrySettings().cycleSelection(1).selected)
    }

    @Test
    fun settingsRoundTripThroughJson() {
        val custom = SlayerCarrySettings(
            enabled = true,
            autoCount = false,
            notifyOnTarget = false,
            plusKey = 86,
            minusKey = 88,
            cycleNextKey = 264,
            cyclePrevKey = 265,
            selected = "Bob",
            players = listOf(CarriedPlayer("Alice", 3, 20), CarriedPlayer("Bob", 0, 10)),
        )
        assertEquals(custom, SlayerCarrySettings.fromJson(custom.toJson()))
    }

    @Test
    fun legacyJsonMigratesPlayersAndGlobalCount() {
        val obj = JsonObject().apply {
            addProperty("enabled", true)
            addProperty("count", 7)
            add("carriedPlayers", JsonArray().apply {
                add("Alice")
                add("alice")
                add("Bob")
            })
        }
        val settings = SlayerCarrySettings.fromJson(obj)
        assertEquals(listOf("Alice", "Bob"), settings.players.map { it.name })
        assertEquals(7, settings.player("Alice")?.count)
        assertEquals(0, settings.player("Bob")?.count)
        assertEquals("Alice", settings.selected)
    }

    @Test
    fun ownerTagParsesSlayerHolograms() {
        assertEquals("PlayerOne", SlayerCarryCounter.ownerFromTag("Spawned by: PlayerOne"))
        assertEquals("Player_2", SlayerCarryCounter.ownerFromTag("§7Spawned by: §bPlayer_2"))
        assertNull(SlayerCarryCounter.ownerFromTag("Revenant Horror IV"))
        assertNull(SlayerCarryCounter.ownerFromTag(""))
    }
}
