package net.icantpy.qol.perf

import com.google.gson.JsonParser

/** Persisted culling switches. Disabled by default; each category can be turned off separately. */
data class CullingConfig(
    val enabled: Boolean = false,
    val entities: Boolean = true,
    val blockEntities: Boolean = true,
    val particles: Boolean = true,
    val nameTags: Boolean = true,
) {
    companion object {
        /** Tolerant parse: wrong types or malformed JSON fall back to the default for that field or the whole file. */
        fun parse(json: String): CullingConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return CullingConfig()
            }
            if (!root.isJsonObject) return CullingConfig()
            val obj = root.asJsonObject
            val defaults = CullingConfig()
            return CullingConfig(
                enabled = bool(obj, "enabled", defaults.enabled),
                entities = bool(obj, "entities", defaults.entities),
                blockEntities = bool(obj, "blockEntities", defaults.blockEntities),
                particles = bool(obj, "particles", defaults.particles),
                nameTags = bool(obj, "nameTags", defaults.nameTags),
            )
        }

        private fun bool(obj: com.google.gson.JsonObject, key: String, fallback: Boolean): Boolean {
            val value = obj.get(key) ?: return fallback
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return fallback
            return value.asBoolean
        }
    }
}
