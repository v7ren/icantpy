package net.icantpy.dungeon.splits

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.leap.LeapTriggerEvent
import java.nio.file.Path

/**
 * Owns the run engine, the persisted document and a cached view for the HUD.
 * No Fabric event registration: the integration code forwards events, ticks and lifecycle calls.
 * All calls are expected on the client thread. [snapshot] is safe to read from render.
 */
object RunSplitsController {
    private val engine = RunSplitsEngine()
    private val path: Path by lazy { RunSplitsStore.defaultPath() }
    private var document = RunSplitsDocument()
    private var loaded = false

    /** Last IO failure on save. Saves are retried on the next change or on unload. */
    var lastSaveError: Exception? = null
        private set

    @Volatile
    private var cached: SplitsView = engine.snapshot(0L, null)

    val settings: RunSplitsSettings
        get() {
            ensureLoaded()
            return document.settings
        }

    /** Latest view, refreshed by [tick] and by every mutating call. */
    fun snapshot(): SplitsView = cached

    fun floorRecord(floor: DungeonFloor?): FloorRecord? {
        ensureLoaded()
        return document.floors[PbLedger.floorKey(floor)]
    }

    /**
     * Forwards a trigger event. Pass the currently known floor (null when unknown).
     * RUN_START resets the floor to the passed value; other events only set a known floor.
     */
    fun onEvent(
        event: LeapTriggerEvent,
        floor: DungeonFloor?,
        nowMs: Long = monotonicMs(),
        wallMs: Long = System.currentTimeMillis(),
    ) {
        ensureLoaded()
        if (!document.settings.enabled) return
        if (event == LeapTriggerEvent.RUN_START && !engine.isRunning) {
            engine.setFloor(floor)
        } else if (floor != null && floor != DungeonFloor.NONE) {
            engine.setFloor(floor)
        }
        val completed = engine.onEvent(event, nowMs, wallMs)
        if (completed != null) recordCompleted(completed)
        refresh(nowMs)
    }

    /** Refreshes the cached view. Call once per client tick. */
    fun tick(nowMs: Long = monotonicMs()) {
        ensureLoaded()
        refresh(nowMs)
    }

    fun onDisconnect() {
        ensureLoaded()
        engine.abandon()
        refresh(monotonicMs())
    }

    /** Abandons any active run and flushes settings and PB data. */
    fun onUnload() {
        ensureLoaded()
        engine.abandon()
        persist()
    }

    fun resetPb(floor: DungeonFloor) {
        ensureLoaded()
        document = document.copy(floors = document.floors - PbLedger.floorKey(floor))
        persist()
        refresh(monotonicMs())
    }

    fun updateSettings(transform: (RunSplitsSettings) -> RunSplitsSettings) {
        ensureLoaded()
        document = document.copy(settings = transform(document.settings))
        if (!document.settings.enabled) engine.abandon()
        persist()
        refresh(monotonicMs())
    }

    private fun recordCompleted(run: CompletedRun) {
        val result = PbLedger.record(document.floors, run)
        document = document.copy(floors = result.floors)
        engine.markFinishedRun(result.isPb, result.newBestSegmentIds)
        persist()
    }

    private fun refresh(nowMs: Long) {
        val pb = document.floors[PbLedger.floorKey(engine.floor)]
        cached = engine.snapshot(nowMs, pb)
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        document = RunSplitsStore.load(path)
    }

    private fun persist() {
        try {
            RunSplitsStore.save(path, document)
            lastSaveError = null
        } catch (error: Exception) {
            lastSaveError = error
        }
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000L
}
