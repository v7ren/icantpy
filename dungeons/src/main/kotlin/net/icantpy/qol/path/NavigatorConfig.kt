package net.icantpy.qol.path

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Ground navigator settings. Parsing is tolerant: wrong types fall back to defaults and numbers are clamped,
 * so a hand-edited file never throws.
 */
data class NavigatorConfig(
    /** Turn the camera toward the path while walking. */
    val rotateCamera: Boolean = true,
    /** Hold sprint while walking on flat ground. */
    val sprint: Boolean = true,
    /** Jump up ledges and over single-block steps. */
    val jump: Boolean = true,
    /** Draw the remaining path and goal marker in the world. */
    val renderPath: Boolean = true,
    /** Goals farther than this horizontal distance in blocks are rejected before searching. */
    val maxSearchBlocks: Int = DEFAULT_MAX_SEARCH_BLOCKS,
    /** Remember walkability of chunks seen while loaded, so paths can cross chunks that have unloaded. */
    val cacheWorld: Boolean = true,
    /** Chunk columns kept in the world snapshot before the least recently used one is dropped. */
    val cacheMaxChunks: Int = DEFAULT_CACHE_MAX_CHUNKS,
) {
    fun toJsonObject(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("version", FORMAT_VERSION)
        obj.addProperty("rotateCamera", rotateCamera)
        obj.addProperty("sprint", sprint)
        obj.addProperty("jump", jump)
        obj.addProperty("renderPath", renderPath)
        obj.addProperty("maxSearchBlocks", maxSearchBlocks)
        obj.addProperty("cacheWorld", cacheWorld)
        obj.addProperty("cacheMaxChunks", cacheMaxChunks)
        return obj
    }

    companion object {
        const val FORMAT_VERSION: Int = 1

        const val MIN_MAX_SEARCH_BLOCKS: Int = 16
        const val MAX_MAX_SEARCH_BLOCKS: Int = 512
        const val DEFAULT_MAX_SEARCH_BLOCKS: Int = 256

        const val MIN_CACHE_MAX_CHUNKS: Int = 64
        const val MAX_CACHE_MAX_CHUNKS: Int = 8192
        const val DEFAULT_CACHE_MAX_CHUNKS: Int = 1024

        fun parse(json: String): NavigatorConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return NavigatorConfig()
            }
            if (!root.isJsonObject) return NavigatorConfig()
            return fromJsonObject(root.asJsonObject)
        }

        fun fromJsonObject(obj: JsonObject): NavigatorConfig {
            val defaults = NavigatorConfig()
            return NavigatorConfig(
                rotateCamera = bool(obj, "rotateCamera", defaults.rotateCamera),
                sprint = bool(obj, "sprint", defaults.sprint),
                jump = bool(obj, "jump", defaults.jump),
                renderPath = bool(obj, "renderPath", defaults.renderPath),
                maxSearchBlocks = int(obj, "maxSearchBlocks", defaults.maxSearchBlocks)
                    .coerceIn(MIN_MAX_SEARCH_BLOCKS, MAX_MAX_SEARCH_BLOCKS),
                cacheWorld = bool(obj, "cacheWorld", defaults.cacheWorld),
                cacheMaxChunks = int(obj, "cacheMaxChunks", defaults.cacheMaxChunks)
                    .coerceIn(MIN_CACHE_MAX_CHUNKS, MAX_CACHE_MAX_CHUNKS),
            )
        }

        private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

        private fun int(obj: JsonObject, key: String, default: Int): Int {
            val value = obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: return default
            val number = value.asDouble
            if (!number.isFinite()) return default
            return number.toInt()
        }
    }
}
