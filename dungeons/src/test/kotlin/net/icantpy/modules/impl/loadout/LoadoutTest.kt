package net.icantpy.modules.impl.loadout

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

    @Test
    fun usesRightSideLoadoutSlotsInsteadOfLeftEquipment() {
        assertTrue(Loadout.isLoadoutSlot(14))
        assertTrue(Loadout.isLoadoutSlot(25))
        assertTrue(Loadout.isLoadoutSlot(43))
        assertFalse(Loadout.isLoadoutSlot(10))
        assertFalse(Loadout.isLoadoutSlot(11))
        assertFalse(Loadout.isLoadoutSlot(19))
        assertTrue(Loadout.isLoadoutIconName("Loadout 6"))
        assertTrue(Loadout.isLoadoutIconName("§aLoadout 1"))
        assertFalse(Loadout.isLoadoutIconName("Ancient Primordial Helmet"))
        assertFalse(Loadout.isLoadoutIconName(""))
    }
}
