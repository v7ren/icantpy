package net.icantpy.dungeon.score

import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Loads and saves config/icantpy-score.json. A malformed file is moved aside to `.invalid.<time>` and defaults
 * are returned. Saves go through a temp file so a crash never leaves a half-written config.
 */
object ScoreSettingsStore {
    private const val FILE_NAME = "icantpy-score.json"

    fun defaultPath(): Path = FabricLoader.getInstance().configDir.resolve(FILE_NAME)

    fun load(path: Path = defaultPath()): ScoreSettings {
        if (!Files.isRegularFile(path)) return ScoreSettings()
        val text = Files.readString(path)
        val decoded = ScoreSettingsCodec.decode(text)
        if (decoded != null) return decoded
        val backup = path.resolveSibling(path.fileName.toString() + ".invalid." + System.currentTimeMillis())
        Icantpy.LOGGER.warn("Malformed score settings moved to {}", backup.fileName)
        Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING)
        return ScoreSettings()
    }

    fun save(path: Path, settings: ScoreSettings) {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, ScoreSettingsCodec.encode(settings))
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
