package net.icantpy.qol.perf

import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Persists the player's pre-preset video values so they can be restored. */
object PerformancePresetStore {
    /** Returns null when no backup exists or the file cannot be read. */
    fun load(): VideoSnapshot? {
        val path = path()
        if (!Files.isRegularFile(path)) return null
        return try {
            val text = Files.readString(path)
            VideoSnapshotJson.parseOrNull(text) ?: run {
                Icantpy.LOGGER.warn("Performance backup is malformed; preserving it as .malformed")
                preserveMalformed(path)
                null
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read performance backup", exception)
            null
        }
    }

    fun save(snapshot: VideoSnapshot) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            Files.writeString(tmp, VideoSnapshotJson.toJson(snapshot) + "\n")
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write performance backup", exception)
        }
    }

    fun delete() {
        try {
            Files.deleteIfExists(path())
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to delete performance backup", exception)
        }
    }

    private fun preserveMalformed(path: java.nio.file.Path) {
        try {
            Files.move(
                path,
                path.resolveSibling("${path.fileName}.malformed"),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to preserve malformed performance backup", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-performance.json")
}
