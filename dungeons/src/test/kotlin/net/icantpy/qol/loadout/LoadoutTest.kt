package net.icantpy.qol.loadout

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadoutTest {
    @Test
    fun recognizesHypixelLoadoutPageTitles() {
        assertTrue(Loadout.isLoadoutTitle("(1/3) Loadouts"))
        assertTrue(Loadout.isLoadoutTitle("§7(2 / 3) Loadouts"))
        assertFalse(Loadout.isLoadoutTitle("Loadouts"))
        assertFalse(Loadout.isLoadoutTitle("Stats & Equipment"))
    }
}
