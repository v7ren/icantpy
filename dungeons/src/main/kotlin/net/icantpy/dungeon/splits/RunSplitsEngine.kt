package net.icantpy.dungeon.splits

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.leap.LeapTriggerEvent

enum class SplitState { DONE, RUNNING, PENDING }

/**
 * One row of the splits table.
 * [segmentMs] is time since the previous recorded split; [cumulativeMs] is run time at this split.
 * [deltaMs] is cumulative minus the PB cumulative for the same split (negative = ahead).
 */
data class SplitRow(
    val id: String,
    val name: String,
    val state: SplitState,
    val segmentMs: Long?,
    val cumulativeMs: Long?,
    val pbSegmentMs: Long?,
    val bestSegmentMs: Long?,
    val deltaMs: Long?,
    val isNewBestSegment: Boolean,
)

data class SplitsView(
    val floor: DungeonFloor?,
    val active: Boolean,
    val finished: Boolean,
    val rows: List<SplitRow>,
    val totalMs: Long?,
    val pbTotalMs: Long?,
    val totalDeltaMs: Long?,
    val sumOfBestMs: Long?,
    val isPbRun: Boolean,
)

/**
 * Pure run state machine. Feed it trigger events with a monotonic clock in ms.
 *
 * Events are kept in arrival order and accepted only when their split comes after the last accepted one,
 * so out-of-order and repeated events are ignored. Rebuilding from the raw list means a floor learned
 * after the run started re-maps the already-seen events.
 */
class RunSplitsEngine {
    enum class Phase { IDLE, RUNNING, FINISHED }

    private class RawEvent(val event: LeapTriggerEvent, val atMs: Long)

    var phase: Phase = Phase.IDLE
        private set

    var floor: DungeonFloor? = null
        private set

    val isRunning: Boolean get() = phase == Phase.RUNNING

    private var startMs: Long = 0L
    private val raw = ArrayList<RawEvent>()
    private val recorded = LinkedHashMap<String, Long>()
    private var finishedTotalMs: Long? = null
    private var lastRunPb: Boolean = false
    private var lastNewBest: Set<String> = emptySet()

    fun start(floor: DungeonFloor?, nowMs: Long) {
        this.floor = floor?.takeUnless { it == DungeonFloor.NONE }
        startMs = nowMs
        raw.clear()
        recorded.clear()
        finishedTotalMs = null
        lastRunPb = false
        lastNewBest = emptySet()
        phase = Phase.RUNNING
    }

    /** Floor may become known after the run started. Null or NONE means unknown. */
    fun setFloor(newFloor: DungeonFloor?) {
        val resolved = newFloor?.takeUnless { it == DungeonFloor.NONE }
        if (resolved == floor) return
        floor = resolved
        rebuild()
    }

    /**
     * Feeds one trigger event. Returns the finished run when this event ends it, otherwise null.
     * RUN_START begins a run (repeats are ignored). BLOOD_OPEN starts a run as a fallback if RUN_START never arrived.
     * DUNGEON_LEFT abandons an unfinished run and clears a finished one from view.
     */
    fun onEvent(event: LeapTriggerEvent, nowMs: Long, wallMs: Long = System.currentTimeMillis()): CompletedRun? {
        when (event) {
            LeapTriggerEvent.RUN_START -> {
                if (phase != Phase.RUNNING) start(floor, nowMs)
                return null
            }
            LeapTriggerEvent.DUNGEON_LEFT -> {
                clear()
                return null
            }
            LeapTriggerEvent.RUN_END -> {
                if (phase != Phase.RUNNING) return null
                raw += RawEvent(event, nowMs)
                rebuild()
                return completeRun(nowMs, wallMs)
            }
            else -> Unit
        }
        if (phase == Phase.IDLE) {
            if (event != LeapTriggerEvent.BLOOD_OPEN) return null
            start(floor, nowMs)
        }
        if (phase != Phase.RUNNING || event !in SplitLayouts.TRACKED_EVENTS) return null
        raw += RawEvent(event, nowMs)
        rebuild()
        return null
    }

    /** Ends the run at [nowMs] as if RUN_END arrived. Returns null when no run is active. */
    fun finish(nowMs: Long, wallMs: Long = System.currentTimeMillis()): CompletedRun? {
        if (phase != Phase.RUNNING) return null
        return completeRun(nowMs, wallMs)
    }

    /** Drops the active run without saving it. */
    fun abandon() = clear()

    /** Called by the controller after PB evaluation so the finished view can show PB status. */
    fun markFinishedRun(isPb: Boolean, newBestSegmentIds: Set<String>) {
        lastRunPb = isPb
        lastNewBest = newBestSegmentIds
    }

    fun snapshot(nowMs: Long, pb: FloorRecord? = null): SplitsView {
        val defs = layout()
        val liveMs = (nowMs - startMs).coerceAtLeast(0L)
        val runningIndex = if (phase == Phase.RUNNING) defs.indexOfFirst { !recorded.containsKey(it.id) } else -1

        val rows = ArrayList<SplitRow>(defs.size)
        var prevCumulative = 0L
        var pbPrevCumulative = 0L
        defs.forEachIndexed { index, def ->
            val done = recorded[def.id]
            val state: SplitState
            val cumulative: Long?
            when {
                done != null -> { state = SplitState.DONE; cumulative = done }
                index == runningIndex -> { state = SplitState.RUNNING; cumulative = liveMs }
                else -> { state = SplitState.PENDING; cumulative = null }
            }
            val segment = cumulative?.let { it - prevCumulative }
            val exact = done != null && (index == 0 || recorded.containsKey(defs[index - 1].id))
            if (state == SplitState.DONE) prevCumulative = done ?: prevCumulative

            val pbCumulative = pb?.pbSplits?.get(def.id)
            val pbSegment = pbCumulative?.let { it - pbPrevCumulative }
            if (pbCumulative != null) pbPrevCumulative = pbCumulative

            val bestSegment = pb?.bestSegments?.get(def.id)
            val newBest = state == SplitState.DONE && exact && segment != null && when {
                phase == Phase.FINISHED -> def.id in lastNewBest
                else -> bestSegment == null || segment < bestSegment
            }

            rows += SplitRow(
                id = def.id,
                name = def.name,
                state = state,
                segmentMs = segment,
                cumulativeMs = cumulative,
                pbSegmentMs = pbSegment,
                bestSegmentMs = bestSegment,
                deltaMs = if (cumulative != null && pbCumulative != null) cumulative - pbCumulative else null,
                isNewBestSegment = newBest,
            )
        }

        val total = when (phase) {
            Phase.IDLE -> null
            Phase.RUNNING -> liveMs
            Phase.FINISHED -> finishedTotalMs
        }
        val pbTotal = pb?.bestTotalMs
        val totalDelta = if (phase == Phase.FINISHED && total != null && pbTotal != null) {
            total - pbTotal
        } else {
            rows.lastOrNull { it.state != SplitState.PENDING && it.deltaMs != null }?.deltaMs
        }
        val sumOfBest = if (defs.isNotEmpty() && rows.all { it.bestSegmentMs != null }) {
            rows.sumOf { it.bestSegmentMs ?: 0L }
        } else {
            null
        }

        return SplitsView(
            floor = floor,
            active = phase == Phase.RUNNING,
            finished = phase == Phase.FINISHED,
            rows = rows,
            totalMs = total,
            pbTotalMs = pbTotal,
            totalDeltaMs = totalDelta,
            sumOfBestMs = sumOfBest,
            isPbRun = phase == Phase.FINISHED && lastRunPb,
        )
    }

    private fun layout(): List<SplitDef> = SplitLayouts.forFloor(floor)

    /** Re-derives [recorded] from the raw events in arrival order. */
    private fun rebuild() {
        recorded.clear()
        val defs = layout()
        var maxIndex = -1
        for (entry in raw) {
            val index = defs.indexOfFirst { it.event == entry.event }
            if (index < 0 || index <= maxIndex) continue
            maxIndex = index
            recorded[defs[index].id] = (entry.atMs - startMs).coerceAtLeast(0L)
        }
    }

    private fun completeRun(nowMs: Long, wallMs: Long): CompletedRun {
        val total = (nowMs - startMs).coerceAtLeast(0L)
        finishedTotalMs = total
        phase = Phase.FINISHED

        val defs = layout()
        val segments = LinkedHashMap<String, Long>()
        defs.forEachIndexed { index, def ->
            val cumulative = recorded[def.id] ?: return@forEachIndexed
            if (index == 0) {
                segments[def.id] = cumulative
            } else {
                val previous = recorded[defs[index - 1].id]
                if (previous != null) segments[def.id] = cumulative - previous
            }
        }
        return CompletedRun(
            floor = floor,
            totalMs = total,
            cumulativeMs = LinkedHashMap(recorded),
            segmentMs = segments,
            endedAtEpochMs = wallMs,
        )
    }

    private fun clear() {
        phase = Phase.IDLE
        raw.clear()
        recorded.clear()
        finishedTotalMs = null
        lastRunPb = false
        lastNewBest = emptySet()
    }
}
