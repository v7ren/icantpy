package net.icantpy.slayer.aim

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes `icantpy-aim.json` in the game config directory. Loading never writes, so a malformed file
 * survives until the next deliberate save.
 */
object AimStore {
    const val FILE_NAME: String = "icantpy-aim.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): AimConfig = loadFrom(path())

    fun save(config: AimConfig) {
        try {
            saveTo(path(), config)
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write boss aim config", exception)
        }
    }

    internal fun loadFrom(path: Path): AimConfig {
        if (!Files.isRegularFile(path)) return AimConfig()
        return try {
            AimConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read boss aim config; using defaults", exception)
            AimConfig()
        }
    }

    /** Writes to a temp file, then moves it over the target. Falls back to a plain replace when atomic move is refused. */
    internal fun saveTo(path: Path, config: AimConfig) {
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(tmp, gson.toJson(config.toJsonObject()) + "\n")
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun path(): Path = FabricLoader.getInstance().configDir.resolve(FILE_NAME)
}
