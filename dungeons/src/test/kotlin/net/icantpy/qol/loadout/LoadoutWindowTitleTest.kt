package net.icantpy.qol.loadout

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadoutWindowTitleTest {
    @Test
    fun windowTitleMatchesRefreshedAndPlainTitles() {
        assertTrue(Loadout.isLoadoutWindowTitle("(1/3) Loadouts"))
        assertTrue(Loadout.isLoadoutWindowTitle("Loadouts"))
        assertTrue(Loadout.isLoadoutWindowTitle("§7(2 / 3) loadouts"))
        assertTrue(Loadout.isLoadoutWindowTitle("Loadout"))
        assertFalse(Loadout.isLoadoutWindowTitle("Stats & Equipment"))
        assertFalse(Loadout.isLoadoutWindowTitle("Chest"))
    }
}
