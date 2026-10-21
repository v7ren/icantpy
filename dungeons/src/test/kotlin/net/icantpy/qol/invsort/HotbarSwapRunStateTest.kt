package net.icantpy.qol.invsort

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotbarSwapRunStateTest {
    private val msNanos = 1_000_000L

    // Hotbar 36..38 hold a, b, c; main slots 9..11 hold x, y, z. Target: x, y, z in the hotbar, three swaps.
    private val start: Map<Int, String?> = buildMap {
        for (slot in 9..43) put(slot, null)
        put(9, "x")
        put(10, "y")
        put(11, "z")
        put(36, "a")
        put(37, "b")
        put(38, "c")
    }
    private val targets: Map<Int, InvSortSlot> = mapOf(
        36 to slot("x"),
        37 to slot("y"),
        38 to slot("z"),
    )

    private fun slot(key: String) = InvSortSlot(key = key, itemId = "minecraft:stone", label = key)

    private fun newState(
        swaps: List<HotbarSwap>,
        moveCursor: Boolean = false,
        instant: Boolean = false,
    ) = HotbarSwapRunState(
        startKeys = start,
        swaps = swaps,
        timing = InvSortTiming(100, 100, instant = instant, random = Random(5)),
        moveCursor = moveCursor,
        startNanos = 0L,
        openDelayMs = 0,
    )

    private fun drain(state: InvSortScript): List<InvSortStep> {
        val steps = mutableListOf<InvSortStep>()
        var now = 0L
        while (now < 60_000 * msNanos) {
            val step = state.poll(now)
            if (step is InvSortStep.Finished) {
                steps += step
                break
            }
            if (step != null) steps += step
            now += msNanos
        }
        return steps
    }

    @Test
    fun executesExactlyPlanSizeSwapSteps() {
        val swaps = HotbarSwapRun.swapsFor(start, targets, oneShot = false)
        val steps = drain(newState(swaps))
        assertEquals(swaps.size, steps.count { it is InvSortStep.Swap })
        assertEquals(InvSortStep.Finished, steps.last())
    }

    @Test
    fun swapStepsCarryTheKeysThePlanExpects() {
        val swaps = HotbarSwapRun.swapsFor(start, targets, oneShot = false)
        val swapSteps = drain(newState(swaps)).filterIsInstance<InvSortStep.Swap>()
        val first = swapSteps.first()
        assertEquals(swaps.first().slot, first.slot)
        assertEquals(swaps.first().hotbarIndex, first.hotbarIndex)
        assertEquals(start[swaps.first().slot], first.expectedSlotKey)
        assertEquals(start[36 + swaps.first().hotbarIndex], first.expectedHotbarKey)
    }

    @Test
    fun oneShotPlansOnlyTheFirstSwap() {
        val full = HotbarSwapRun.swapsFor(start, targets, oneShot = false)
        val once = HotbarSwapRun.swapsFor(start, targets, oneShot = true)
        assertTrue(full.size > 1)
        assertEquals(full.take(1), once)
        assertEquals(1, drain(newState(once)).count { it is InvSortStep.Swap })
    }

    @Test
    fun oneShotRemainingCountMatchesPlanAfterFirstSwap() {
        val full = HotbarSwapRun.swapsFor(start, targets, oneShot = false)
        val afterOne = HotbarSwapPlanner.simulate(start, full.take(1))
        assertEquals(full.size - 1, HotbarSwapPlanner.stepsRemaining(afterOne, HotbarSwapRun.hotbarTargets(targets)))
    }

    @Test
    fun cursorIsNeverExpectedToHoldAnItem() {
        // A swap step has no carried field: the runner requires an empty cursor before every swap.
        val swaps = HotbarSwapRun.swapsFor(start, targets, oneShot = false)
        val steps = drain(newState(swaps, moveCursor = true))
        assertTrue(steps.all { it is InvSortStep.Swap || it is InvSortStep.MoveCursor || it is InvSortStep.Finished })
    }

    @Test
    fun moveCursorTargetsSourceSlotBeforeEachSwap() {
        val swaps = HotbarSwapRun.swapsFor(start, targets, oneShot = false)
        val steps = drain(newState(swaps, moveCursor = true))
        val moves = steps.filterIsInstance<InvSortStep.MoveCursor>().map { it.slot }
        assertEquals(swaps.map { it.slot }, moves)
    }

    @Test
    fun instantStillEmitsEachSwapAsCheckpointStep() {
        val swaps = HotbarSwapRun.swapsFor(start, targets, oneShot = false)
        val state = HotbarSwapRunState(
            startKeys = start,
            swaps = swaps,
            timing = InvSortTiming(0, 0, instant = true, random = Random(1)),
            moveCursor = false,
            startNanos = 0L,
            openDelayMs = 0,
        )
        val emitted = mutableListOf<InvSortStep>()
        while (true) {
            val step = state.poll(0L) ?: break
            emitted += step
            if (step is InvSortStep.Finished) break
        }
        assertEquals(swaps.size, emitted.count { it is InvSortStep.Swap })
    }

    @Test
    fun hotbarTargetsIgnoreMainInventorySlots() {
        val mixed = targets + (9 to slot("main"))
        assertEquals(setOf(36, 37, 38), HotbarSwapRun.hotbarTargets(mixed).keys)
    }
}
