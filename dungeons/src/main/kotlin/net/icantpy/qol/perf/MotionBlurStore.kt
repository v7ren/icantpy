package net.icantpy.qol.perf

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object MotionBlurStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): MotionBlurConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return MotionBlurConfig()
        return try {
            MotionBlurConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read motion blur config; using defaults", exception)
            MotionBlurConfig()
        }
    }

    fun save(config: MotionBlurConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(
                mapOf(
                    "version" to 1,
                    "enabled" to config.enabled,
                    "strength" to config.strength,
                ),
            ) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write motion blur config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-motionblur.json")
}
