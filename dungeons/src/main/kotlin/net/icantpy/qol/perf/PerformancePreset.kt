package net.icantpy.qol.perf

import net.minecraft.client.CloudStatus
import net.minecraft.client.GraphicsPreset
import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import net.minecraft.server.level.ParticleStatus
import net.icantpy.compat.refreshLevelAfterVideoChange

/**
 * One-click FPS preset over vanilla video options. Must run on the client thread.
 * Option names and enum constants are identical in 26.2 and 26.1.2; only the
 * level refresh call differs and lives in the per-version compat adapter.
 */
object PerformancePreset {
    private var backup: VideoSnapshot? = null
    private var loaded = false

    /** Reads the backup from disk. Safe to call more than once. */
    fun load() {
        backup = PerformancePresetStore.load()
        loaded = true
    }

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    val hasBackup: Boolean
        get() {
            ensureLoaded()
            return backup != null
        }

    /**
     * Writes the preset values. The current values are kept as the backup only when
     * no backup exists yet, so repeated applies never overwrite the original settings.
     */
    fun apply(): String {
        ensureLoaded()
        val options = Minecraft.getInstance().options
        val current = readOptions(options)
        if (backup == null) {
            PerformancePresetStore.save(current)
            backup = current
        }
        writeOptions(options, PerformancePlan.target(current))
        options.save()
        refreshLevelAfterVideoChange()
        return "Max FPS preset applied. Use the restore command to get your previous settings back."
    }

    /** Writes the backup values back, saves options and deletes the backup. */
    fun restore(): String {
        ensureLoaded()
        val saved = backup ?: return "No saved video settings to restore."
        val options = Minecraft.getInstance().options
        writeOptions(options, saved)
        options.save()
        refreshLevelAfterVideoChange()
        PerformancePresetStore.delete()
        backup = null
        return "Restored your previous video settings."
    }

    private fun readOptions(options: Options): VideoSnapshot = VideoSnapshot(
        renderDistance = options.renderDistance().get(),
        simulationDistance = options.simulationDistance().get(),
        graphicsPreset = options.graphicsPreset().get().name,
        ambientOcclusion = options.ambientOcclusion().get(),
        particles = options.particles().get().name,
        cloudStatus = options.cloudStatus().get().name,
        biomeBlendRadius = options.biomeBlendRadius().get(),
        entityShadows = options.entityShadows().get(),
        entityDistanceScaling = options.entityDistanceScaling().get(),
        mipmapLevels = options.mipmapLevels().get(),
        enableVsync = options.enableVsync().get(),
        framerateLimit = options.framerateLimit().get(),
    )

    private fun writeOptions(options: Options, values: VideoSnapshot) {
        // The graphics preset setter applies its own values to the other options, so it goes first.
        // Setting individual options afterwards flips the preset label to CUSTOM; the values still match.
        if (values.graphicsPreset != "CUSTOM") {
            enumNamed(GraphicsPreset.values(), values.graphicsPreset)?.let { options.graphicsPreset().set(it) }
        }
        options.renderDistance().set(values.renderDistance)
        options.simulationDistance().set(values.simulationDistance)
        options.ambientOcclusion().set(values.ambientOcclusion)
        enumNamed(ParticleStatus.values(), values.particles)?.let { options.particles().set(it) }
        enumNamed(CloudStatus.values(), values.cloudStatus)?.let { options.cloudStatus().set(it) }
        options.biomeBlendRadius().set(values.biomeBlendRadius)
        options.entityShadows().set(values.entityShadows)
        options.entityDistanceScaling().set(values.entityDistanceScaling)
        options.mipmapLevels().set(values.mipmapLevels)
        options.enableVsync().set(values.enableVsync)
        options.framerateLimit().set(values.framerateLimit)
    }

    private fun <T : Enum<T>> enumNamed(values: Array<T>, name: String): T? =
        values.firstOrNull { it.name == name }
}
