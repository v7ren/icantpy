package net.icantpy.api

import java.util.UUID

data class IcantpyBossBar(val name: String, val progress: Float)

/** Raw server values, not the HUD's interpolated health. Owned by the client thread. */
class IcantpyBossBarState {
    private var bars: Map<UUID, IcantpyBossBar> = emptyMap()

    fun add(id: UUID, name: String, progress: Float): IcantpyBossBar? {
        if (!progress.isFinite()) return null
        return IcantpyBossBar(name, progress).also { bars = bars + (id to it) }
    }

    fun progress(id: UUID, progress: Float): IcantpyBossBar? {
        val bar = bars[id] ?: return null
        return add(id, bar.name, progress)
    }

    fun name(id: UUID, name: String): IcantpyBossBar? {
        val bar = bars[id] ?: return null
        return add(id, name, bar.progress)
    }

    fun remove(id: UUID) { bars = bars - id }
    fun clear() { bars = emptyMap() }
}
