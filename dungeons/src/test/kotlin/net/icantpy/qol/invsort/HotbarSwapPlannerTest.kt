package net.icantpy.qol.invsort

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotbarSwapPlannerTest {
    private fun inventory(vararg entries: Pair<Int, String>): Map<Int, String?> = entries.toMap()

    @Test
    fun alreadySortedNeedsNoSwaps() {
        val current = inventory(36 to "A", 37 to "B")
        val target = mapOf(36 to "A", 37 to "B")
        assertEquals(emptyList(), HotbarSwapPlanner.plan(current, target))
        assertEquals(0, HotbarSwapPlanner.stepsRemaining(current, target))
    }

    @Test
    fun mainInventoryItemIsSwappedInWithOneClick() {
        val current = inventory(20 to "A", 36 to "X")
        val target = mapOf(36 to "A")
        assertEquals(listOf(HotbarSwap(20, 0)), HotbarSwapPlanner.plan(current, target))
    }

    @Test
    fun twoExchangedHotbarSlotsTakeOneClick() {
        val current = inventory(36 to "A", 37 to "B")
        val target = mapOf(36 to "B", 37 to "A")
        assertEquals(listOf(HotbarSwap(37, 0)), HotbarSwapPlanner.plan(current, target))
    }

    @Test
    fun chainOfThreeUsesTwoClicks() {
        val current = inventory(36 to "A", 37 to "B", 38 to "C")
        val target = mapOf(36 to "B", 37 to "C", 38 to "A")
        val swaps = HotbarSwapPlanner.plan(current, target)
        assertEquals(listOf(HotbarSwap(37, 0), HotbarSwap(38, 1)), swaps)
        val result = HotbarSwapPlanner.simulate(current, swaps)
        assertEquals("B", result[36])
        assertEquals("C", result[37])
        assertEquals("A", result[38])
    }

    @Test
    fun missingKeyIsSkipped() {
        val current = inventory(36 to "A", 20 to "C")
        val target = mapOf(36 to "A", 37 to "MISSING")
        assertEquals(emptyList(), HotbarSwapPlanner.plan(current, target))
    }

    @Test
    fun missingKeyDoesNotBlockOtherSlots() {
        val current = inventory(20 to "A", 36 to "X")
        val target = mapOf(36 to "MISSING", 37 to "A")
        assertEquals(listOf(HotbarSwap(20, 1)), HotbarSwapPlanner.plan(current, target))
    }

    @Test
    fun slot44IsNeverSourceOrTarget() {
        val current = inventory(44 to "A", 36 to "X")
        val target = mapOf(36 to "A", 44 to "A")
        assertEquals(emptyList(), HotbarSwapPlanner.plan(current, target))
    }

    @Test
    fun slot44ItemIsNotUsedAsSource() {
        val current = inventory(44 to "A", 36 to "X", 37 to "Y")
        val target = mapOf(36 to "A")
        assertEquals(emptyList(), HotbarSwapPlanner.plan(current, target))
    }

    @Test
    fun neverTargetsMainInventory() {
        val current = inventory(9 to "A", 36 to "X")
        val target = mapOf(9 to "A", 20 to "A")
        assertEquals(emptyList(), HotbarSwapPlanner.plan(current, target))
    }

    @Test
    fun everySwapTargetsHotbarIndexZeroToSeven() {
        val current = inventory(9 to "A", 10 to "B", 36 to "X", 37 to "Y", 43 to "Z")
        val target = mapOf(36 to "A", 37 to "B", 43 to "Z")
        val swaps = HotbarSwapPlanner.plan(current, target)
        assertTrue(swaps.all { it.hotbarIndex in 0..7 })
    }

    @Test
    fun simulatedPlanSatisfiesReachableTargets() {
        val current = inventory(
            9 to "A", 12 to "B", 20 to "C", 36 to "D", 37 to "E", 38 to "F", 40 to "G", 44 to "H",
        )
        val target = mapOf(36 to "G", 37 to "A", 38 to "E", 39 to "B", 40 to "D", 41 to "H")
        val swaps = HotbarSwapPlanner.plan(current, target)
        val result = HotbarSwapPlanner.simulate(current, swaps)
        assertEquals("G", result[36])
        assertEquals("A", result[37])
        assertEquals("E", result[38])
        assertEquals("B", result[39])
        assertEquals("D", result[40])
        assertEquals("H", result[44])
    }

    @Test
    fun oneSwapPerMismatchedHotbarSlotAtMost() {
        val current = inventory(9 to "A", 10 to "B", 11 to "C", 36 to "X", 37 to "Y", 38 to "Z")
        val target = mapOf(36 to "A", 37 to "B", 38 to "C")
        val mismatched = target.count { (slot, key) -> current[slot] != key }
        val swaps = HotbarSwapPlanner.plan(current, target)
        assertTrue(swaps.size <= mismatched)
        assertEquals(mismatched, swaps.size)
    }
}
