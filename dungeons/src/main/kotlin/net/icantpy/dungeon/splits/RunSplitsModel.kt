package net.icantpy.dungeon.splits

import net.icantpy.dungeon.DungeonFloor

/** Persisted HUD and behaviour settings. */
data class RunSplitsSettings(
    val enabled: Boolean = true,
    val showPbDelta: Boolean = true,
    val showSumOfBest: Boolean = true,
    val compact: Boolean = false,
    val hudX: Int = 4,
    val hudY: Int = 60,
    val hudScale: Float = 1.0f,
)

/** One completed run in a floor's history. */
data class RunRecord(val endedAtEpochMs: Long, val totalMs: Long, val isPb: Boolean)

/** Persisted per-floor PB state. Segment maps are keyed by [SplitDef.id] and hold cumulative ms (pbSplits) or segment ms (bestSegments). */
data class FloorRecord(
    val bestTotalMs: Long? = null,
    val pbSplits: Map<String, Long> = emptyMap(),
    val bestSegments: Map<String, Long> = emptyMap(),
    val history: List<RunRecord> = emptyList(),
)

/** Whole JSON document: settings plus per-floor records keyed by [DungeonFloor] name. */
data class RunSplitsDocument(
    val settings: RunSplitsSettings = RunSplitsSettings(),
    val floors: Map<String, FloorRecord> = emptyMap(),
)

/** Output of a finished run, handed to [PbLedger.record]. [segmentMs] only contains exact segments. */
data class CompletedRun(
    val floor: DungeonFloor?,
    val totalMs: Long,
    val cumulativeMs: Map<String, Long>,
    val segmentMs: Map<String, Long>,
    val endedAtEpochMs: Long,
)

data class PbResult(
    val floors: Map<String, FloorRecord>,
    val isPb: Boolean,
    val newBestSegmentIds: Set<String>,
)

/** Pure PB bookkeeping. */
object PbLedger {
    const val HISTORY_LIMIT: Int = 20

    fun floorKey(floor: DungeonFloor?): String = (floor ?: DungeonFloor.NONE).name

    fun record(floors: Map<String, FloorRecord>, run: CompletedRun): PbResult {
        val key = floorKey(run.floor)
        val old = floors[key] ?: FloorRecord()
        val previousBest = old.bestTotalMs
        val isPb = previousBest == null || run.totalMs < previousBest

        val bestSegments = LinkedHashMap(old.bestSegments)
        val newBestIds = LinkedHashSet<String>()
        for ((id, segment) in run.segmentMs) {
            val previous = old.bestSegments[id]
            if (previous == null || segment < previous) {
                bestSegments[id] = segment
                newBestIds += id
            }
        }

        val history = (listOf(RunRecord(run.endedAtEpochMs, run.totalMs, isPb)) + old.history).take(HISTORY_LIMIT)
        val updated = FloorRecord(
            bestTotalMs = if (isPb) run.totalMs else previousBest,
            pbSplits = if (isPb) run.cumulativeMs else old.pbSplits,
            bestSegments = bestSegments,
            history = history,
        )
        return PbResult(floors + (key to updated), isPb, newBestIds)
    }
}
