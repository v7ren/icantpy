package net.icantpy.qol.loadout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadoutEquippedTest {
    @Test
    fun parsesNameVariants() {
        assertEquals(4, LoadoutEquipped.numberFromName("Loadout 4"))
        assertEquals(4, LoadoutEquipped.numberFromName("Loadout #4"))
        assertEquals(4, LoadoutEquipped.numberFromName("§aLoadout 4 (Equipped)"))
        assertEquals(4, LoadoutEquipped.numberFromName("loadout 4 (selected)"))
        assertEquals(12, LoadoutEquipped.numberFromName("Loadout  12 "))
    }

    @Test
    fun rejectsNamesThatAreNotLoadouts() {
        assertNull(LoadoutEquipped.numberFromName("Loadout 2 extra"))
        assertNull(LoadoutEquipped.numberFromName("Loadouts 1"))
        assertNull(LoadoutEquipped.numberFromName("Loadout"))
        assertNull(LoadoutEquipped.numberFromName("Empty Loadout"))
    }

    @Test
    fun fallsBackToFirstLoreLineWhenNameIsCustom() {
        assertEquals(4, LoadoutEquipped.numberOf("My Favourite Set", listOf("Loadout 4", "Click to view")))
        assertEquals(2, LoadoutEquipped.numberOf("Loadout 2", listOf("Loadout 9")))
        assertNull(LoadoutEquipped.numberOf("My Set", listOf("Some lore", "Loadout 4")))
        assertNull(LoadoutEquipped.numberOf("My Set", emptyList()))
    }

    @Test
    fun detectsEquippedLoreMarkers() {
        assertTrue(LoadoutEquipped.isEquippedLore(listOf("§7Items", "§aCurrently equipped")))
        assertTrue(LoadoutEquipped.isEquippedLore(listOf("§eClick to unequip")))
        assertTrue(LoadoutEquipped.isEquippedLore(listOf("§7Status: §aSELECTED")))
        assertTrue(LoadoutEquipped.isEquippedLore(listOf("§7Loadout 4 (Equipped)")))
        assertTrue(LoadoutEquipped.isEquippedLore(listOf("Loadout 4 is already equipped!")))
    }

    @Test
    fun plainLoreIsNotEquipped() {
        assertFalse(LoadoutEquipped.isEquippedLore(emptyList()))
        assertFalse(LoadoutEquipped.isEquippedLore(listOf("§7Click to equip")))
        assertFalse(LoadoutEquipped.isEquippedLore(listOf("§7Armor and inventory")))
    }

    @Test
    fun parsesChatLines() {
        assertEquals(4, LoadoutEquipped.numberFromChat("You equipped Loadout 4!"))
        assertEquals(4, LoadoutEquipped.numberFromChat("§aYou equipped Loadout 4!"))
        assertEquals(4, LoadoutEquipped.numberFromChat("Loadout 4 is already equipped!"))
        assertNull(LoadoutEquipped.numberFromChat("Auto loadout: Loadout 4 not found"))
        assertNull(LoadoutEquipped.numberFromChat("You equipped Loadout 4! extra"))
        assertNull(LoadoutEquipped.numberFromChat("Party > You equipped Loadout 4!"))
    }

    @Test
    fun memoryFollowsChatAndSkipsOnlyTheEquippedNumber() {
        val memory = LoadoutMemory()
        assertFalse(memory.isKnownEquipped(4))
        assertTrue(memory.onChat("You equipped Loadout 4!"))
        assertTrue(memory.isKnownEquipped(4))
        assertFalse(memory.isKnownEquipped(3))
        assertFalse(memory.onChat("hello there"))
        assertTrue(memory.onChat("You equipped Loadout 2!"))
        assertEquals(2, memory.equipped)
        assertFalse(memory.isKnownEquipped(4))
    }

    @Test
    fun memoryLearnsFromLoreAndResetsOnDisconnect() {
        val memory = LoadoutMemory()
        memory.learn(4)
        assertTrue(memory.isKnownEquipped(4))
        memory.reset()
        assertNull(memory.equipped)
        assertFalse(memory.isKnownEquipped(4))
    }

    @Test
    fun emptyMenuIsWaitedOnBrieflyOnly() {
        assertTrue(LoadoutEquipped.shouldWaitForIcons(0L))
        assertTrue(LoadoutEquipped.shouldWaitForIcons(999L))
        assertFalse(LoadoutEquipped.shouldWaitForIcons(1_000L))
        assertFalse(LoadoutEquipped.shouldWaitForIcons(5_000L))
    }
}
