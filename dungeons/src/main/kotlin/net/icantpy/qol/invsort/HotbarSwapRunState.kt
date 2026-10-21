package net.icantpy.qol.invsort

/**
 * Hotbar swap plan for a preset, free of Minecraft types. Only the preset's hotbar slots (36..43) are targets.
 * With [oneShot] the plan is cut to its first swap, so one press performs one click.
 */
object HotbarSwapRun {
    /** Target keys for the hotbar slots of [slots]; other slots are ignored. */
    fun hotbarTargets(slots: Map<Int, InvSortSlot>): Map<Int, String> =
        slots.filterKeys { it in InvSortSlots.HOTBAR_FIRST..HotbarSwapPlanner.HOTBAR_LAST_SWAPPABLE }
            .mapValues { it.value.key }

    fun swapsFor(startKeys: Map<Int, String?>, slots: Map<Int, InvSortSlot>, oneShot: Boolean): List<HotbarSwap> {
        val all = HotbarSwapPlanner.plan(startKeys, hotbarTargets(slots))
        return if (oneShot) all.take(1) else all
    }
}

/**
 * Timed script over a hotbar swap plan. Mirrors [InvSortRunState] timing: the cursor glides to the source slot
 * before each swap, and each swap follows a human delay. Every swap is a checkpoint, so the cursor is always
 * empty when a swap is planned.
 */
class HotbarSwapRunState(
    startKeys: Map<Int, String?>,
    private val swaps: List<HotbarSwap>,
    private val timing: InvSortTiming,
    private val moveCursor: Boolean,
    startNanos: Long,
    openDelayMs: Int,
) : InvSortScript {
    private val slots = HashMap<Int, String?>(startKeys)
    private var nextIndex = 0
    private var nextDueNanos = startNanos + timing.openDelayNanos(openDelayMs)
    private var cursorSlot: Int? = null
    private var pollCount = 0L
    private var movedOnPoll = -1L

    override fun poll(nowNanos: Long): InvSortStep? {
        pollCount++
        if (moveCursor && nextIndex < swaps.size && cursorSlot != swaps[nextIndex].slot) {
            cursorSlot = swaps[nextIndex].slot
            movedOnPoll = pollCount
            return InvSortStep.MoveCursor(swaps[nextIndex].slot)
        }
        if (nowNanos < nextDueNanos) return null
        if (nextIndex >= swaps.size) return InvSortStep.Finished
        if (moveCursor && !timing.instant && movedOnPoll == pollCount) return null
        return emitSwap(swaps[nextIndex], nowNanos)
    }

    private fun emitSwap(swap: HotbarSwap, nowNanos: Long): InvSortStep.Swap {
        val hotbarSlot = InvSortSlots.HOTBAR_FIRST + swap.hotbarIndex
        val step = InvSortStep.Swap(
            slot = swap.slot,
            hotbarIndex = swap.hotbarIndex,
            expectedSlotKey = slots[swap.slot],
            expectedHotbarKey = slots[hotbarSlot],
        )
        val held = slots[swap.slot]
        slots[swap.slot] = slots[hotbarSlot]
        slots[hotbarSlot] = held
        nextIndex++
        cursorSlot = null
        nextDueNanos = nowNanos + InvSortTiming.millisToNanos(timing.sampleDelayMs())
        return step
    }
}
