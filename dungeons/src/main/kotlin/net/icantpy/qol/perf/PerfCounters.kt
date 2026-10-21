package net.icantpy.qol.perf

/**
 * Per-frame performance counters, written on the render thread by frame generation and culling and read by the
 * performance HUD. Plain fields, no allocation: writers increment, [PerfCounters.endFrame] snapshots and resets.
 */
object PerfCounters {
    @JvmField var realFrames = 0L
    @JvmField var generatedFrames = 0L

    @JvmField var culledEntities = 0
    @JvmField var culledBlockEntities = 0
    @JvmField var culledParticles = 0
    @JvmField var hiddenNameTags = 0

    /** Counts from the last finished frame, for display. */
    @JvmField var lastCulledEntities = 0
    @JvmField var lastCulledBlockEntities = 0
    @JvmField var lastCulledParticles = 0
    @JvmField var lastHiddenNameTags = 0

    /** Call once at the start of each frame: keeps the previous frame's cull counts and starts new ones. */
    fun endFrame() {
        lastCulledEntities = culledEntities
        lastCulledBlockEntities = culledBlockEntities
        lastCulledParticles = culledParticles
        lastHiddenNameTags = hiddenNameTags
        culledEntities = 0
        culledBlockEntities = 0
        culledParticles = 0
        hiddenNameTags = 0
    }

    fun reset() {
        realFrames = 0L
        generatedFrames = 0L
        endFrame()
        endFrame()
    }
}
