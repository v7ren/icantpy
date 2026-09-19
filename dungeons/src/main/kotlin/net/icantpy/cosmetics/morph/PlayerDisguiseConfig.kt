package net.icantpy.cosmetics.morph

import com.google.gson.JsonParser

enum class MorphCameraMode(val id: String, val label: String) {
    SHIFTED_CROSSHAIR("crosshair", "Shifted crosshair"),
    LOOK_AT("look", "Look-at camera");

    fun usesLookDirection(followEyeHeight: Boolean): Boolean = followEyeHeight && this == LOOK_AT

    fun movesCrosshair(followEyeHeight: Boolean): Boolean = followEyeHeight && this == SHIFTED_CROSSHAIR

    companion object {
        fun fromId(raw: String?): MorphCameraMode? = entries.firstOrNull {
            it.id == raw?.trim()?.lowercase()
        }
    }
}

data class PlayerDisguiseConfig(
    val entityId: String? = null,
    val followEyeHeight: Boolean = false,
    val cameraMode: MorphCameraMode = MorphCameraMode.LOOK_AT,
) {
    internal fun serialized(): Map<String, Any?> = mapOf(
        "version" to 1,
        "entity" to entityId,
        "followEyeHeight" to followEyeHeight,
        "cameraMode" to cameraMode.id,
    )

    companion object {
        private val ENTITY_ID = Regex("[a-z0-9_.-]+:[a-z0-9/_.-]+")

        fun normalizeEntityId(raw: String?): String? {
            val value = raw?.trim()?.lowercase().orEmpty()
            if (value.isBlank()) return null
            val namespaced = if (':' in value) value else "minecraft:$value"
            return namespaced.takeIf { ENTITY_ID.matches(it) }
        }

        fun parse(json: String): PlayerDisguiseConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return PlayerDisguiseConfig()
            }
            if (!root.isJsonObject) return PlayerDisguiseConfig()
            val obj = root.asJsonObject
            val raw = obj.get("entity")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
            val follow = obj.get("followEyeHeight")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean == true
            val mode = MorphCameraMode.fromId(
                obj.get("cameraMode")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString,
            ) ?: MorphCameraMode.LOOK_AT
            return PlayerDisguiseConfig(normalizeEntityId(raw), follow, mode)
        }
    }
}
