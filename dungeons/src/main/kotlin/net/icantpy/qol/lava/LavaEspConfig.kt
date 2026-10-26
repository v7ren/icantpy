package net.icantpy.qol.lava

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class LavaEspConfig(
    val enabled: Boolean = false,
    /** Only lava strictly above this block height is highlighted. */
    val minY: Int = DEFAULT_MIN_Y,
    /** How far around you, in chunks, loaded chunks are scanned. */
    val radiusChunks: Int = DEFAULT_RADIUS,
    /** Highlight only lava with no lava above it, so a deep pool shows its surface instead of every block. */
    val surfaceOnly: Boolean = true,
    /** Draw on top of walls and terrain instead of being hidden behind them. */
    val throughWalls: Boolean = true,
    /** Outline colour, 0xRRGGBB. */
    val rgb: Int = DEFAULT_RGB,
) {
    companion object {
        const val DEFAULT_MIN_Y: Int = 64
        const val MIN_Y_LIMIT: Int = -64
        const val MAX_Y_LIMIT: Int = 320
        const val DEFAULT_RADIUS: Int = 8
        const val MIN_RADIUS: Int = 1
        const val MAX_RADIUS: Int = 32
        const val DEFAULT_RGB: Int = 0xFF5A00

        fun parse(json: String): LavaEspConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return LavaEspConfig()
            }
            if (!root.isJsonObject) return LavaEspConfig()
            val obj = root.asJsonObject
            val d = LavaEspConfig()
            return LavaEspConfig(
                enabled = bool(obj, "enabled", d.enabled),
                minY = int(obj, "minY", d.minY).coerceIn(MIN_Y_LIMIT, MAX_Y_LIMIT),
                radiusChunks = int(obj, "radiusChunks", d.radiusChunks).coerceIn(MIN_RADIUS, MAX_RADIUS),
                surfaceOnly = bool(obj, "surfaceOnly", d.surfaceOnly),
                throughWalls = bool(obj, "throughWalls", d.throughWalls),
                rgb = int(obj, "rgb", d.rgb) and 0xFFFFFF,
            )
        }

        private fun int(obj: JsonObject, name: String, default: Int): Int =
            obj.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: default

        private fun bool(obj: JsonObject, name: String, default: Boolean): Boolean =
            obj.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default
    }
}

object LavaEspStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): LavaEspConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return LavaEspConfig()
        return try {
            LavaEspConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read lava esp config; using defaults", exception)
            LavaEspConfig()
        }
    }

    fun save(config: LavaEspConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(
                mapOf(
                    "version" to 1,
                    "enabled" to config.enabled,
                    "minY" to config.minY,
                    "radiusChunks" to config.radiusChunks,
                    "surfaceOnly" to config.surfaceOnly,
                    "throughWalls" to config.throughWalls,
                    "rgb" to config.rgb,
                ),
            ) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write lava esp config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-lava.json")
}
