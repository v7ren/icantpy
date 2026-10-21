package net.icantpy.qol.loadout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reproduces the real Loadouts page 1 of 3 (26.1.2 latest.log, "[icantpy loadoutdump]" lines). Lore is trimmed to the
 * lines the detection reads; the names and slot indexes are exact.
 */
class LoadoutMenuDumpTest {
    private data class Dumped(val slot: Int, val name: String, val lore: List<String>)

    private val configured = listOf("§7Helmet: Ancient Storm's Helmet", "§7Cloak: Blended Shadow Assassin Cloak", "§7Pet: [Lvl 200] Golden Dragon", "", "§eLeft-click to equip!", "§eRight-click to edit")
    private val equipped = listOf("§7Helmet: Necrotic Wither Goggles", "§7Cloak: Glistening Shadow Assassin Cloak", "§7Pet: [Lvl 200] Golden Dragon", "", "§7Power Stone: Bizarre", "", "§eRight-click to edit")
    private val empty = listOf("§7Helmet: None", "§7Cloak: None", "§7Pet: None", "", "§eRight-click to edit", "", "§cYou must customize this loadout", "before you can equip it!")
    private val decorativeNecklace = listOf("Health: +61 (+4) (+384.3)", "Defense: +49.5 (+283.5)", "", "Right-click to equip!", "", "a LEGENDARY DUNGEON NECKLACE")
    private val tree = listOf("Quickly swap between saved trees.", "", "Current: Heart of the Forest 1", "", "Swapping trees has a 10m cooldown!", "", "Click to view!")
    private val nextPage = listOf("Page 2")

    private val page1 = listOf(
        Dumped(9, "Heart of the Forest Slot", tree),
        Dumped(10, " Brilliant Bone Necklace ✪✪✪✪✪", decorativeNecklace),
        Dumped(11, "Necrotic Wither Goggles ✪✪✪✪✪", listOf("Gear Score: 652 (3399)", "Right-click to equip!")),
        Dumped(14, "rcm slot", equipped),
        Dumped(15, "lcm", configured),
        Dumped(16, "terms", configured),
        Dumped(18, "Heart of the Mountain Slot", tree),
        Dumped(19, "Glistening Shadow Assassin Cloak ✪✪✪✪✪", listOf("True Defense: +3.5", "Right-click to equip!", "", "EPIC DUNGEON CLOAK")),
        Dumped(20, "Loving Storm's Chestplate ✪✪✪✪✪", listOf("Gear Score: 1115 (5000)", "Right-click to equip!")),
        Dumped(23, "Loadout 4", configured),
        Dumped(24, "Loadout 5", configured),
        Dumped(25, "Loadout 6", configured),
        Dumped(27, "Power Stone", listOf("Choose your selected Power Stone.", "", "Current: Bizarre", "", "Click to view!")),
        Dumped(28, "Brilliant Implosion Belt", listOf("Ability: Consolidated", "", "Right-click to equip!", "", "EPIC BELT")),
        Dumped(32, "blaze", configured),
        Dumped(33, "Loadout 8", configured),
        Dumped(34, "Loadout 9", empty),
        Dumped(36, "Stats Tuning", listOf("Your tuning:", "", "Click to view!")),
        Dumped(37, "Brilliant Soulweaver Gloves ✪✪✪✪✪", listOf("Haunted Skull", "Right-click to equip!", "", "EPIC DUNGEON GLOVES")),
        Dumped(41, "Loadout 10", empty),
        Dumped(42, "Loadout 11", empty),
        Dumped(43, "Loadout 12", empty),
        Dumped(44, "Next Page", nextPage),
        Dumped(49, "Close", emptyList()),
    )

    private fun loadouts(page: Int, slots: List<Dumped>): List<Pair<Int, Int>> {
        val candidates = slots
            .filter { !LoadoutIcons.isNavigationName(it.name) }
            .filter { LoadoutIcons.isLoadoutCandidate(it.name, it.lore) }
            .map { LoadoutIcons.Candidate(it.slot, it.name, it.lore) }
        return LoadoutIcons.number(candidates, page = page, iconsPerPage = LoadoutIcons.GRID_PER_PAGE)
    }

    @Test
    fun findsLoadoutsOneToTwelveOnPageOne() {
        val entries = loadouts(1, page1)
        assertEquals(
            listOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43),
            entries.map { it.first },
        )
        assertEquals((1..12).toList(), entries.map { it.second })
    }

    @Test
    fun decorativeEquipmentNavAndFillerAreNotLoadouts() {
        val found = loadouts(1, page1).map { it.first }.toSet()
        listOf(9, 10, 11, 18, 19, 20, 27, 28, 36, 37, 44, 49).forEach { slot ->
            assertFalse(slot in found, "slot $slot should not be a loadout")
        }
    }

    @Test
    fun equippedLoadoutIsSlotFourteenOnly() {
        val equippedSlots = page1
            .filter { LoadoutIcons.isLoadoutCandidate(it.name, it.lore) }
            .filter { LoadoutEquipped.isEquippedLore(it.lore) }
            .map { it.slot }
        assertEquals(listOf(14), equippedSlots)
        val number = loadouts(1, page1).first { it.first == 14 }.second
        assertEquals(1, number)
    }

    @Test
    fun emptyLoadoutsAreListedButNeverEquipped() {
        assertTrue(LoadoutIcons.isLoadoutCandidate("Loadout 9", empty))
        assertFalse(LoadoutEquipped.isEquippedLore(empty))
    }

    @Test
    fun secondPageContinuesTheGridNumbering() {
        val page2 = listOf(
            Dumped(14, "Loadout 13", configured),
            Dumped(15, "Loadout 14", configured),
            Dumped(44, "Previous Page", nextPage),
        )
        assertEquals(listOf(14 to 13, 15 to 14), loadouts(2, page2))
    }
}
