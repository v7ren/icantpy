package net.icantpy.qol.perf

import com.google.gson.JsonParser

/**
 * Accumulation motion blur settings. [strength] is the weight of the previous frame blended into the
 * current one: 0.1 is a faint trail, 0.9 is a long smear.
 */
data class MotionBlurConfig(
    val enabled: Boolean = false,
    val strength: Float = DEFAULT_STRENGTH,
) {
    companion object {
        const val MIN_STRENGTH: Float = 0.1f
        const val MAX_STRENGTH: Float = 0.9f
        const val DEFAULT_STRENGTH: Float = 0.5f
        val STRENGTH_PRESETS: List<Float> = listOf(0.25f, 0.5f, 0.7f, 0.85f)

        fun clampStrength(value: Float): Float = value.coerceIn(MIN_STRENGTH, MAX_STRENGTH)

        /** Tolerant parse: malformed JSON or wrong field types fall back to the default for that field. */
        fun parse(json: String): MotionBlurConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return MotionBlurConfig()
            }
            if (!root.isJsonObject) return MotionBlurConfig()
            val obj = root.asJsonObject
            val enabled = obj.get("enabled")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean ?: false
            val strength = obj.get("strength")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asFloat
                ?.takeIf { it.isFinite() }
                ?: DEFAULT_STRENGTH
            return MotionBlurConfig(enabled, clampStrength(strength))
        }
    }
}
