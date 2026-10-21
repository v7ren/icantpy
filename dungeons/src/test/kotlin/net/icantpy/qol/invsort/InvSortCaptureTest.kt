package net.icantpy.qol.invsort

import kotlin.test.Test
import kotlin.test.assertEquals

class InvSortCaptureTest {
    private fun slot(key: String) = InvSortSlot(key = key, itemId = "minecraft:$key", label = key)

    @Test fun capturesArmorAndInventorySlotsAndSkipsEmpty() {
        val captured = InvSortSlots.capturedSlots(
            mapOf(
                5 to slot("helm"),
                6 to null,
                7 to slot("legs"),
                8 to slot("boots"),
                9 to slot("sword"),
                10 to null,
            ),
        )
        assertEquals(setOf(5, 7, 8, 9), captured.keys)
        assertEquals("legs", captured.getValue(7).key)
    }

    @Test fun ignoresCraftingOffhandStarAndOutOfRangeSlots() {
        val captured = InvSortSlots.capturedSlots(
            mapOf(0 to slot("craft"), 4 to slot("craft"), 44 to slot("star"), 45 to slot("off"), 1 to slot("x"), 9 to slot("ok")),
        )
        assertEquals(setOf(9), captured.keys)
    }

    @Test fun armorSlotsFollowHeadChestLegsBootsOrder() {
        assertEquals(listOf(5, 6, 7, 8), InvSortSlots.ARMOR)
        assertEquals(listOf("Helmet", "Chest", "Legs", "Boots"), InvSortSlots.ARMOR_NAMES)
        assertEquals(InvSortSlots.ARMOR + InvSortSlots.SORTABLE, InvSortSlots.PRESET_SLOTS)
    }
}
