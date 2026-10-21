package net.icantpy.qol.invsort

/** One action the runner should perform on this tick. Click carries the state the plan expects. */
sealed interface InvSortStep {
    data class MoveCursor(val slot: Int) : InvSortStep

    data class Click(
        val slot: Int,
        val expectedSlotKey: String?,
        val expectedCarriedKey: String?,
    ) : InvSortStep

    /** One hotbar number-key swap; the runner checks [expectedSlotKey] and [expectedHotbarKey] before it. */
    data class Swap(
        val slot: Int,
        val hotbarIndex: Int,
        val expectedSlotKey: String?,
        val expectedHotbarKey: String?,
    ) : InvSortStep

    data object Finished : InvSortStep
}

/** A timed sequence of [InvSortStep]s that the runner drives. Implemented by click and hotbar swap scripts. */
interface InvSortScript {
    /** Returns the next step once its delay has elapsed, or null while still waiting. */
    fun poll(nowNanos: Long): InvSortStep?
}

/**
 * Time-based script over a planned click list, free of Minecraft types.
 * The first click waits [openDelayMs]; each later click waits a random delay sampled by [timing], counted from
 * the previous click. Server confirmation is not part of this script; the runner gates each step on it.
 *
 * With [moveCursor], the cursor moves toward the next target as soon as the previous click is done, so the
 * hover overlaps the human delay. The click follows on a later poll; with a non-instant [timing] that poll is
 * never the same one as the move, so the hover always gets a tick. Instant timing moves and clicks in one poll.
 */
class InvSortRunState(
    startKeys: Map<Int, String?>,
    private val clicks: List<Int>,
    private val timing: InvSortTiming,
    private val moveCursor: Boolean,
    startNanos: Long,
    openDelayMs: Int,
) : InvSortScript {
    private var slots: Map<Int, String?> = startKeys
    private var carried: String? = null
    private var nextIndex = 0
    private var nextDueNanos = startNanos + timing.openDelayNanos(openDelayMs)
    private var cursorSlot: Int? = null
    private var pollCount = 0L
    private var movedOnPoll = -1L

    override fun poll(nowNanos: Long): InvSortStep? {
        pollCount++
        if (moveCursor && nextIndex < clicks.size && cursorSlot != clicks[nextIndex]) {
            cursorSlot = clicks[nextIndex]
            movedOnPoll = pollCount
            return InvSortStep.MoveCursor(clicks[nextIndex])
        }
        if (nowNanos < nextDueNanos) return null
        if (nextIndex >= clicks.size) return InvSortStep.Finished
        // A hover needs a frame before the click, unless every delay is zero.
        if (moveCursor && !timing.instant && movedOnPoll == pollCount) return null
        val slot = clicks[nextIndex]
        val step = InvSortStep.Click(slot, slots[slot], carried)
        val result = InvSortPlanner.applyPickup(slots, carried, slot)
        slots = result.slots
        carried = result.carried
        nextIndex++
        cursorSlot = null
        nextDueNanos = nowNanos + InvSortTiming.millisToNanos(timing.sampleDelayMs())
        return step
    }
}
