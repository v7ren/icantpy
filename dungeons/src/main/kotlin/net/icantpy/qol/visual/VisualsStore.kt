package net.icantpy.qol.visual

import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes `icantpy-visuals.json` in the game config directory. Loading never writes: a malformed file is
 * replaced with defaults in memory only, so the user's file survives until the next deliberate save.
 */
object VisualsStore {
    fun load(): VisualsConfig = loadFrom(path())

    fun save(config: VisualsConfig) {
        try {
            saveTo(path(), config)
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write visuals config", exception)
        }
    }

    internal fun loadFrom(path: Path): VisualsConfig {
        if (!Files.isRegularFile(path)) return VisualsConfig()
        return try {
            VisualsConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read visuals config; using defaults", exception)
            VisualsConfig()
        }
    }

    /** Writes to a temp file, then moves it over the target. Falls back to a plain replace when atomic move is refused. */
    internal fun saveTo(path: Path, config: VisualsConfig) {
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(tmp, config.toJson() + "\n")
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun path(): Path = FabricLoader.getInstance().configDir.resolve(FILE_NAME)

    const val FILE_NAME: String = "icantpy-visuals.json"
}
