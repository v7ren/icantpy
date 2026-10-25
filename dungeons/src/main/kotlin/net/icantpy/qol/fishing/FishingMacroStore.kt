package net.icantpy.qol.fishing

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FishingMacroStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): FishingMacroConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return FishingMacroConfig()
        return try {
            FishingMacroConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read fishing macro config; using defaults", exception)
            FishingMacroConfig()
        }
    }

    fun save(config: FishingMacroConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(
                mapOf(
                    "version" to 1,
                    "key" to config.key,
                    "reelMinMs" to config.reelMinMs,
                    "reelMaxMs" to config.reelMaxMs,
                    "castMinMs" to config.castMinMs,
                    "castMaxMs" to config.castMaxMs,
                    "noBiteSeconds" to config.noBiteSeconds,
                ),
            ) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write fishing macro config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-fishing.json")
}
