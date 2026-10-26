package net.icantpy.qol.camera

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class FreecamConfig(
    val enabled: Boolean = false,
    /** GLFW key that toggles freecam, or [UNBOUND]. */
    val key: Int = UNBOUND,
    /** Fly speed in blocks per second; Sprint multiplies it by [FreecamMath.BOOST]. */
    val speed: Float = DEFAULT_SPEED,
    /** Swallow attack, use and pick-block presses while flying, so a click never lands on what your body faces. */
    val blockClicks: Boolean = true,
    /** Leave freecam the moment the player takes damage. */
    val stopOnHurt: Boolean = true,
) {
    companion object {
        const val UNBOUND: Int = -1
        const val DEFAULT_SPEED: Float = 15f
        const val MIN_SPEED: Float = 2f
        const val MAX_SPEED: Float = 120f

        fun parse(json: String): FreecamConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return FreecamConfig()
            }
            if (!root.isJsonObject) return FreecamConfig()
            val obj = root.asJsonObject
            val defaults = FreecamConfig()
            val key = number(obj, "key")?.toInt() ?: UNBOUND
            val speed = number(obj, "speed")?.toFloat()?.takeIf { it.isFinite() } ?: defaults.speed
            return FreecamConfig(
                enabled = bool(obj, "enabled", defaults.enabled),
                key = if (key <= 0) UNBOUND else key,
                speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
                blockClicks = bool(obj, "blockClicks", defaults.blockClicks),
                stopOnHurt = bool(obj, "stopOnHurt", defaults.stopOnHurt),
            )
        }

        private fun number(obj: JsonObject, name: String): Double? =
            obj.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

        private fun bool(obj: JsonObject, name: String, default: Boolean): Boolean =
            obj.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default
    }
}

object FreecamStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): FreecamConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return FreecamConfig()
        return try {
            FreecamConfig.parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read freecam config; using defaults", exception)
            FreecamConfig()
        }
    }

    fun save(config: FreecamConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            val body = gson.toJson(
                mapOf(
                    "version" to 1,
                    "enabled" to config.enabled,
                    "key" to config.key,
                    "speed" to config.speed,
                    "blockClicks" to config.blockClicks,
                    "stopOnHurt" to config.stopOnHurt,
                ),
            ) + "\n"
            Files.writeString(tmp, body)
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write freecam config", exception)
        }
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-freecam.json")
}
