package net.icantpy.qol.invsort

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvSortArmorPlannerTest {
    private fun inventory(vararg entries: Pair<Int, String?>): Map<Int, String?> = entries.toMap()

    private fun target(vararg entries: Pair<Int, String>): Map<Int, String> = entries.toMap()

    @Test fun swapsHelmetInFromInventoryWithThreeClicks() {
        val current = inventory(9 to "helm", 5 to "oldHelm")
        val target = target(5 to "helm")
        val clicks = InvSortPlanner.plan(current, target)
        assertEquals(listOf(9, 5, 9), clicks)
        val result = InvSortPlanner.simulate(current, clicks)
        assertEquals("helm", result[5])
        assertEquals("oldHelm", result[9])
    }

    @Test fun fillsEmptyArmorSlotWithTwoClicks() {
        val current = inventory(12 to "chest", 6 to null)
        val clicks = InvSortPlanner.plan(current, target(6 to "chest"))
        assertEquals(listOf(12, 6), clicks)
        val result = InvSortPlanner.simulate(current, clicks)
        assertEquals("chest", result[6])
        assertEquals(null, result[12])
    }

    @Test fun pieceAlreadyWornProducesNoClicks() {
        val current = inventory(5 to "helm", 9 to "helm")
        assertTrue(InvSortPlanner.plan(current, target(5 to "helm")).isEmpty())
    }

    @Test fun recordedHelmetIsNeverPlacedIntoChestSlot() {
        // Chest is recorded as "chest"; a helmet lying in the inventory must go to the head slot only.
        val current = inventory(9 to "helm", 10 to "chest", 6 to null)
        val target = target(5 to "helm", 6 to "chest")
        val clicks = InvSortPlanner.plan(current, target)
        val result = InvSortPlanner.simulate(current, clicks)
        assertEquals("helm", result[5])
        assertEquals("chest", result[6])
    }

    @Test fun wornArmorIsNeverUsedAsSourceForAnotherArmorSlot() {
        // The helmet is worn in the chest slot; the head target must not pull it across.
        val current = inventory(6 to "helm", 5 to null)
        assertTrue(InvSortPlanner.plan(current, target(5 to "helm")).isEmpty())
    }

    @Test fun wornArmorWithoutTargetIsLeftAlone() {
        val current = inventory(5 to "helm", 9 to null)
        val clicks = InvSortPlanner.plan(current, target(9 to "chest"))
        assertTrue(clicks.none { it in InvSortSlots.ARMOR }, "clicks=$clicks")
        assertTrue(clicks.isEmpty())
    }

    @Test fun inventoryTargetCannotTakeWornArmor() {
        val current = inventory(7 to "legs", 9 to null)
        assertTrue(InvSortPlanner.plan(current, target(9 to "legs")).isEmpty())
    }

    @Test fun armorTargetsAreSatisfiedBeforeInventoryTargets() {
        val current = inventory(9 to "helm", 10 to "sword", 5 to "oldHelm")
        val target = target(5 to "helm", 9 to "oldHelm", 10 to "sword")
        val clicks = InvSortPlanner.plan(current, target)
        val result = InvSortPlanner.simulate(current, clicks)
        target.forEach { (slot, key) -> assertEquals(key, result[slot], "slot $slot") }
    }

    @Test fun simulateMatchesEveryReachableArmorAndInventoryTarget() {
        val current = inventory(
            5 to "oldHelm", 6 to null, 7 to "legs", 8 to "boots",
            9 to "helm", 10 to "chest", 11 to "sword", 20 to "boots",
        )
        // Only one helmet exists in the inventory and it goes to the head, so slot 10 takes the spare boots.
        val target = target(5 to "helm", 6 to "chest", 7 to "legs", 8 to "boots", 9 to "sword", 10 to "boots")
        val clicks = InvSortPlanner.plan(current, target)
        val result = InvSortPlanner.simulate(current, clicks)
        assertEquals("helm", result[5])
        assertEquals("chest", result[6])
        assertEquals("legs", result[7])
        assertEquals("boots", result[8])
        assertEquals("sword", result[9])
        assertEquals("boots", result[10])
        assertTrue(clicks.all { it in InvSortSlots.PRESET_SLOTS })
        assertTrue(clicks.none { it == 44 || it == 45 || it in 0..4 })
    }
}
