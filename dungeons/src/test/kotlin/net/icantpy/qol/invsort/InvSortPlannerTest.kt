package net.icantpy.qol.invsort

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvSortPlannerTest {
    private fun inventory(vararg entries: Pair<Int, String?>): Map<Int, String?> = entries.toMap()

    private fun target(vararg entries: Pair<Int, String>): Map<Int, String> = entries.toMap()

    @Test fun alreadySortedProducesNoClicks() {
        val current = inventory(9 to "a", 10 to "b")
        assertTrue(InvSortPlanner.plan(current, target(9 to "a", 10 to "b")).isEmpty())
    }

    @Test fun movesIntoEmptySlotWithTwoClicks() {
        val current = inventory(9 to "a", 10 to null)
        assertEquals(listOf(9, 10), InvSortPlanner.plan(current, target(10 to "a")))
    }

    @Test fun swapsTwoItemsWithThreeClicks() {
        val current = inventory(9 to "a", 10 to "b")
        val target = target(9 to "b", 10 to "a")
        val clicks = InvSortPlanner.plan(current, target)
        assertEquals(listOf(10, 9, 10), clicks)
        assertEquals(mapOf(9 to "b", 10 to "a"), InvSortPlanner.simulate(current, clicks).filterKeys { it in 9..10 })
    }

    @Test fun resolvesThreeCycle() {
        val current = inventory(9 to "a", 10 to "b", 11 to "c")
        val target = target(9 to "b", 10 to "c", 11 to "a")
        val clicks = InvSortPlanner.plan(current, target)
        val result = InvSortPlanner.simulate(current, clicks)
        assertEquals("b", result[9])
        assertEquals("c", result[10])
        assertEquals("a", result[11])
    }

    @Test fun missingTargetKeyIsSkipped() {
        val current = inventory(9 to "a", 10 to null)
        assertTrue(InvSortPlanner.plan(current, target(9 to "z")).isEmpty())
    }

    @Test fun neverTouchesStarSlotOrOtherNonSortableSlots() {
        val current = inventory(9 to null, 44 to "a", 45 to "b", 5 to "c")
        val clicks = InvSortPlanner.plan(current, target(9 to "a", 44 to "b", 5 to "c", 45 to "a"))
        assertTrue(clicks.none { it == 44 || it == 45 || it == 5 }, "clicks=$clicks")
        assertTrue(clicks.all { InvSortSlots.isSortable(it) })
    }

    @Test fun satisfiableTargetsMatchAfterSimulation() {
        val current = inventory(9 to "a", 10 to "b", 20 to "c", 36 to "d", 43 to null, 40 to "a")
        val target = target(9 to "d", 10 to "a", 11 to "c", 36 to "b", 43 to "a")
        val clicks = InvSortPlanner.plan(current, target)
        val result = InvSortPlanner.simulate(current, clicks)
        target.forEach { (slot, key) -> assertEquals(key, result[slot], "slot $slot") }
        assertTrue(clicks.all { InvSortSlots.isSortable(it) })
    }

    @Test fun simulateMovesStackIntoEmptySlot() {
        val result = InvSortPlanner.simulate(inventory(9 to "a", 10 to null), listOf(9, 10))
        assertEquals(null, result[9])
        assertEquals("a", result[10])
    }
}
