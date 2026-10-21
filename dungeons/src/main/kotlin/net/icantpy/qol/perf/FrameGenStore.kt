package net.icantpy.qol.perf

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FrameGenStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): FrameGenConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return FrameGenConfig()
        return try {
            FrameGenConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read frame generation config; using defaults", exception)
            FrameGenConfig()
        }
    }

    fun save(config: FrameGenConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(
                mapOf(
                    "version" to 1,
                    "generatedPerReal" to config.generatedPerReal,
                    "adaptive" to config.adaptive,
                ),
            ) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write frame generation config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-framegen.json")
}
