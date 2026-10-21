package net.icantpy.slayer.blaze

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes `icantpy-firepit.json` in the game config directory. Loading never writes, so a malformed file
 * survives until the next deliberate save.
 */
object FirePitStore {
    const val FILE_NAME: String = "icantpy-firepit.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): FirePitConfig = loadFrom(path())

    fun save(config: FirePitConfig) {
        try {
            saveTo(path(), config)
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write fire pit config", exception)
        }
    }

    internal fun loadFrom(path: Path): FirePitConfig {
        if (!Files.isRegularFile(path)) return FirePitConfig()
        return try {
            FirePitConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read fire pit config; using defaults", exception)
            FirePitConfig()
        }
    }

    /** Writes to a temp file, then moves it over the target. Falls back to a plain replace when atomic move is refused. */
    internal fun saveTo(path: Path, config: FirePitConfig) {
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
