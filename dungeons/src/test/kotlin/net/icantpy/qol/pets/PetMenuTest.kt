package net.icantpy.qol.pets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetMenuTest {
    private val despawn = listOf("§7Held Item: none", "", "§cClick to despawn!")
    private val summon = listOf("§7Held Item: none", "", "§eClick to summon!")

    @Test
    fun recognizesPetsMenuTitlesWithAndWithoutPages() {
        assertTrue(PetMenu.isPetsTitle("Pets"))
        assertTrue(PetMenu.isPetsTitle("Pets (2/3)"))
        assertTrue(PetMenu.isPetsTitle("(1/3) Pets"))
        assertTrue(PetMenu.isPetsTitle("§aPets"))
        assertFalse(PetMenu.isPetsTitle("Pet Score"))
        assertFalse(PetMenu.isPetsTitle("Stats & Equipment"))
        assertFalse(PetMenu.isPetsTitle("Pets Upgrade"))
    }

    @Test
    fun readsThePageNumber() {
        assertEquals(2, PetMenu.pageOf("Pets (2/3)"))
        assertEquals(1, PetMenu.pageOf("(1/3) Pets"))
        assertNull(PetMenu.pageOf("Pets"))
    }

    @Test
    fun readsAnIconFromItsNameAndLore() {
        val icon = PetMenu.iconOf(11, "§7[Lvl 100] §6Golden Dragon", despawn)!!
        assertEquals(PetIcon(11, 100, "Golden Dragon", active = true), icon)
        val idle = PetMenu.iconOf(12, "[Lvl 7] Tiger", summon)!!
        assertEquals(PetIcon(12, 7, "Tiger", active = false), idle)
    }

    @Test
    fun ignoresItemsThatAreNotPets() {
        assertNull(PetMenu.iconOf(53, "Next Page", emptyList()))
        assertNull(PetMenu.iconOf(49, "Close", emptyList()))
        assertNull(PetMenu.iconOf(48, "Pet Score", emptyList()))
    }

    @Test
    fun findPrefersExactThenPrefixThenContains() {
        val icons = listOf(
            PetIcon(10, 100, "Golden Dragon", false),
            PetIcon(11, 80, "Dragon", false),
            PetIcon(12, 50, "Tiger", false),
        )
        assertEquals(11, PetMenu.find(icons, "dragon")?.slotIndex)
        assertEquals(12, PetMenu.find(icons, "TIG")?.slotIndex)
        assertEquals(10, PetMenu.find(icons, "golden")?.slotIndex)
        assertEquals(10, PetMenu.find(icons, "den dra")?.slotIndex)
        assertNull(PetMenu.find(icons, "Wolf"))
        assertNull(PetMenu.find(icons, "  "))
    }

    @Test
    fun cleanTargetDropsALeadingLevelTag() {
        assertEquals("Golden Dragon", PetMenu.cleanTarget("  [Lvl 100] Golden Dragon "))
        assertEquals("Tiger", PetMenu.cleanTarget("Tiger"))
        assertEquals("", PetMenu.cleanTarget("   "))
    }

    @Test
    fun recognizesTheNextPageArrow() {
        assertTrue(PetMenu.isNextPageName("§aNext Page"))
        assertFalse(PetMenu.isNextPageName("Previous Page"))
    }
}
