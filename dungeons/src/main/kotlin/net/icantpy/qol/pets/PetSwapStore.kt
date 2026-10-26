package net.icantpy.qol.pets

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object PetSwapStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): PetSwapConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return PetSwapConfig()
        return try {
            PetSwapConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read pet swap config; using defaults", exception)
            PetSwapConfig()
        }
    }

    fun save(config: PetSwapConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(
                mapOf(
                    "version" to 1,
                    "enabled" to config.enabled,
                    "instant" to config.instant,
                    "delayMinMs" to config.delayMinMs,
                    "delayMaxMs" to config.delayMaxMs,
                    "swapOnCast" to config.swapOnCast,
                    "castPet" to config.castPet,
                    "pets" to config.pets.map { mapOf("name" to it.name, "key" to it.key) },
                ),
            ) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write pet swap config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-petswap.json")
}
