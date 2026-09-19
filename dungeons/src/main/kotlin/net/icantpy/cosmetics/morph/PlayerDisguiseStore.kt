package net.icantpy.cosmetics.morph

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object PlayerDisguiseStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): PlayerDisguiseConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return PlayerDisguiseConfig()
        return try {
            PlayerDisguiseConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read player disguise config; using vanilla appearance", exception)
            PlayerDisguiseConfig()
        }
    }

    fun save(config: PlayerDisguiseConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(config.serialized()) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write player disguise config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-player-disguise.json")
}
