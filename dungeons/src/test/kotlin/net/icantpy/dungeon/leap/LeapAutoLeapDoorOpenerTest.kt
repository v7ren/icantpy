package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeapAutoLeapDoorOpenerTest {
    @Test
    fun capturesNamedWitherDoorOpenerWithOptionalRankAndFormatting() {
        assertEquals("Alice", LeapAutoLeapDoorOpener.parse("Alice opened a WITHER door!"))
        assertEquals("Bob_123", LeapAutoLeapDoorOpener.parse("§a[MVP§c+§a] Bob_123 opened a WITHER door!"))
        assertEquals("Alice", LeapAutoLeapDoorOpener.parse("[VIP] Alice has opened the WITHER door!"))
        assertEquals("Ren", LeapAutoLeapDoorOpener.parse("[Mage] [MVP+] Ren opened a WITHER door!"))
        assertEquals("Ren", LeapAutoLeapDoorOpener.parse("§b[Mage] §a[MVP§c+§a] Ren opened a WITHER door!"))
    }

    @Test
    fun latestNamedDoorOpenerWinsButAnonymousBloodDoesNotEraseIt() {
        val wither = LeapAutoLeapDoorOpener.update(null, "Alice opened a WITHER door!")
        assertEquals("Alice", wither)
        assertEquals("Alice", LeapAutoLeapDoorOpener.update(wither, "The BLOOD DOOR has been opened!"))
        val blood = LeapAutoLeapDoorOpener.update(wither, "[MVP+] Bob opened the BLOOD door!")
        assertEquals("Bob", blood)
        assertEquals("Carol", LeapAutoLeapDoorOpener.update(blood, "Carol opened a WITHER door!"))
    }

    @Test
    fun ignoresPartyChatAndUnrelatedOrMalformedDoorMessages() {
        listOf(
            "Party > [MVP+] Bob: Alice opened a WITHER door!",
            "Alice opened a door!",
            "Alice opened a FAIRY door!",
            "Alice opened a WITHER door! hello",
            "a_name_more_than16_chars opened a WITHER door!",
            "The BLOOD DOOR has been opened!",
        ).forEach { assertNull(LeapAutoLeapDoorOpener.parse(it), it) }
    }

    @Test
    fun clearingRemovesOldRunOpener() {
        LeapAutoLeapDoorOpener.clear()
        LeapAutoLeapDoorOpener.onChat("Alice opened a WITHER door!")
        assertEquals("Alice", LeapAutoLeapDoorOpener.latestName())
        LeapAutoLeapDoorOpener.clear()
        assertNull(LeapAutoLeapDoorOpener.latestName())
    }

    @Test
    fun lockHudShowsLatestDoorOpenerWhenEnabled() {
        val cfg = LeapOrientSettings(doorOpenerLeapEnabled = true)
        assertEquals(listOf("§dAlice", "§7Door opener"), LeapAutoLeapDoorOpener.hudLines(cfg, "Alice"))
        assertEquals(emptyList(), LeapAutoLeapDoorOpener.hudLines(cfg.copy(showLockHud = false), "Alice"))
        assertEquals(emptyList(), LeapAutoLeapDoorOpener.hudLines(cfg.copy(doorOpenerLeapEnabled = false), "Alice"))
        assertEquals(emptyList(), LeapAutoLeapDoorOpener.hudLines(cfg, null))
        assertEquals(emptyList(), LeapAutoLeapDoorOpener.hudLines(cfg, "  "))
    }

    @Test
    fun leftClickDoorOpenerIsDungeonOnlyAndSkippedInBossRooms() {
        val cfg = LeapOrientSettings(doorOpenerLeapEnabled = true)
        assertEquals(true, LeapAutoLeapPolicy.doorOpenerClick(cfg, inDungeon = true, inBossRoom = false))
        assertEquals(false, LeapAutoLeapPolicy.doorOpenerClick(cfg, inDungeon = true, inBossRoom = true))
        assertEquals(false, LeapAutoLeapPolicy.doorOpenerClick(cfg, inDungeon = false, inBossRoom = false))
        assertEquals(false, LeapAutoLeapPolicy.doorOpenerClick(cfg.copy(enabled = false), true, false))
        assertEquals(false, LeapAutoLeapPolicy.doorOpenerClick(cfg.copy(doorOpenerLeapEnabled = false), true, false))
    }
}
