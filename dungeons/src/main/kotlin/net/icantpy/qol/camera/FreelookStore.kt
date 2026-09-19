package net.icantpy.qol.camera

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FreelookStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): FreelookConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return FreelookConfig()
        return try {
            FreelookConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read freelook config; using defaults", exception)
            FreelookConfig()
        }
    }

    fun save(config: FreelookConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(
                mapOf(
                    "version" to 1,
                    "enabled" to config.enabled,
                    "key" to config.key,
                    "mode" to config.mode.id,
                ),
            ) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write freelook config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-freelook.json")
}
