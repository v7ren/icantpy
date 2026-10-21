package net.icantpy.qol.loadout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadoutIconsTest {
    private val equipHint = listOf("§7Helmet: None", "§7Pet: None", "§eRight-click to edit")

    @Test
    fun renamedIconIsFoundByLore() {
        assertTrue(LoadoutIcons.isLoadoutCandidate("§alcm", equipHint))
        assertTrue(LoadoutIcons.isLoadoutCandidate("terms", listOf("§7Helmet: None", "§eRight-click to edit")))
        assertTrue(LoadoutIcons.isLoadoutCandidate("Loadout 4", emptyList()))
        assertFalse(LoadoutIcons.isLoadoutCandidate("Diamond Sword", listOf("§7Damage: 10")))
        assertFalse(LoadoutIcons.isLoadoutCandidate("Brilliant Bone Necklace", listOf("§eRight-click to equip!")))
    }

    @Test
    fun fillerAndNavigationAreExcluded() {
        assertFalse(LoadoutIcons.isLoadoutCandidate(" ", equipHint))
        assertFalse(LoadoutIcons.isLoadoutCandidate("Next Page", equipHint))
        assertFalse(LoadoutIcons.isLoadoutCandidate("§aPrevious Page", equipHint))
        assertFalse(LoadoutIcons.isLoadoutCandidate("Go Back", equipHint))
        assertFalse(LoadoutIcons.isLoadoutCandidate("Close", equipHint))
        assertTrue(LoadoutIcons.isLoadoutCandidate("Closet set", equipHint))
    }

    @Test
    fun numbersByOrderWithPageOffset() {
        val candidates = listOf(
            LoadoutIcons.Candidate(12, "lcm", equipHint),
            LoadoutIcons.Candidate(10, "rcm", equipHint),
            LoadoutIcons.Candidate(11, "terms", equipHint),
        )
        assertEquals(listOf(10 to 1, 11 to 2, 12 to 3), LoadoutIcons.number(candidates, page = 1, iconsPerPage = 6))
        assertEquals(listOf(10 to 7, 11 to 8, 12 to 9), LoadoutIcons.number(candidates, page = 2, iconsPerPage = 6))
    }

    @Test
    fun explicitLoreNumberWinsOverOrder() {
        val candidates = listOf(
            LoadoutIcons.Candidate(10, "lcm", listOf("§7Helmet: None", "§eRight-click to edit", "Loadout #5")),
            LoadoutIcons.Candidate(11, "terms", equipHint),
        )
        assertEquals(listOf(11 to 2, 10 to 5), LoadoutIcons.number(candidates, page = 1, iconsPerPage = 6))
    }

    @Test
    fun explicitNameNumberWinsOverLoreNumber() {
        assertEquals(2, LoadoutIcons.explicitNumber("Loadout 2", listOf("Loadout 9")))
        assertEquals(9, LoadoutIcons.explicitNumber("lcm", listOf("Loadout #9")))
        assertNull(LoadoutIcons.explicitNumber("lcm", equipHint))
    }

    @Test
    fun renamedNameMapsFromEquippedChatMessage() {
        val memory = LoadoutMemory()
        memory.rememberNames(listOf("lcm" to 3, "terms" to 5))
        assertTrue(memory.onChat("You equipped lcm!"))
        assertTrue(memory.isKnownEquipped(3))
        assertTrue(memory.onChat("§aYou equipped Terms!"))
        assertTrue(memory.isKnownEquipped(5))
        assertFalse(memory.onChat("You equipped rcm slot!"))
        assertNull(memory.equipped)
        memory.reset()
        assertFalse(memory.onChat("You equipped lcm!"))
    }

    @Test
    fun nameChatParsingSkipsLoadoutLines() {
        assertEquals("lcm", LoadoutEquipped.nameFromChat("You equipped lcm!"))
        assertNull(LoadoutEquipped.nameFromChat("You equipped Loadout 4!"))
        assertNull(LoadoutEquipped.nameFromChat("hello"))
    }
}
