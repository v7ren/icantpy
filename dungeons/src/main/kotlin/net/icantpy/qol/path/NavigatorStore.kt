package net.icantpy.qol.path

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes `icantpy-navigator.json` in the game config directory. Loading never writes, so a malformed
 * file survives until the next deliberate save.
 */
object NavigatorStore {
    const val FILE_NAME: String = "icantpy-navigator.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): NavigatorConfig = loadFrom(path())

    fun save(config: NavigatorConfig) {
        try {
            saveTo(path(), config)
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write navigator config", exception)
        }
    }

    internal fun loadFrom(path: Path): NavigatorConfig {
        if (!Files.isRegularFile(path)) return NavigatorConfig()
        return try {
            NavigatorConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read navigator config; using defaults", exception)
            NavigatorConfig()
        }
    }

    /** Writes to a temp file, then moves it over the target. Falls back to a plain replace when atomic move is refused. */
    internal fun saveTo(path: Path, config: NavigatorConfig) {
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
