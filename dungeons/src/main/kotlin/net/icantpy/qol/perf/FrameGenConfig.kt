package net.icantpy.qol.perf

import com.google.gson.JsonParser

/**
 * Rotation-reprojection frame generation. [generatedPerReal] is the number of generated frames shown after
 * each real world frame: 0 is off, 1 gives 2x output and 2 gives 3x output.
 *
 * [adaptive] limits generation to frames where the game runs below the monitor refresh rate. Generated frames
 * cannot add smoothness past the display, so at or above refresh every frame is rendered for real.
 */
data class FrameGenConfig(val generatedPerReal: Int = 0, val adaptive: Boolean = true) {
    companion object {
        val MODES: List<Int> = listOf(0, 1, 2)

        /** Clamps an arbitrary value into the supported modes. Out-of-range values fall back to the nearest end. */
        fun normalize(value: Int): Int = value.coerceIn(MODES.first(), MODES.last())

        /**
         * Tolerant parse: malformed JSON or a wrong [generatedPerReal] type falls back to off. A missing or
         * non-boolean [adaptive] (for example in files written before the setting existed) is treated as true.
         */
        fun parse(json: String): FrameGenConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return FrameGenConfig()
            }
            if (!root.isJsonObject) return FrameGenConfig()
            val fields = root.asJsonObject
            val generated = fields.get("generatedPerReal")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asInt
                ?: return FrameGenConfig()
            val adaptive = fields.get("adaptive")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean
                ?: true
            return FrameGenConfig(normalize(generated), adaptive)
        }
    }
}
