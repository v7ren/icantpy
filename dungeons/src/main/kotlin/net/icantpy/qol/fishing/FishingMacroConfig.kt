package net.icantpy.qol.fishing

import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class FishingMacroConfig(
    /** GLFW key that starts and stops the macro, or [UNBOUND]. */
    val key: Int = UNBOUND,
    val reelMinMs: Int = 60,
    val reelMaxMs: Int = 180,
    val castMinMs: Int = 350,
    val castMaxMs: Int = 800,
    val noBiteSeconds: Int = 45,
) {
    fun timing(): FishingTiming =
        FishingTiming(reelMinMs, reelMaxMs, castMinMs, castMaxMs, noBiteSeconds * 1_000L)

    companion object {
        const val UNBOUND: Int = -1
        const val MAX_DELAY_MS: Int = 5_000
        const val MIN_NO_BITE_SECONDS: Int = 10
        const val MAX_NO_BITE_SECONDS: Int = 300

        fun parse(json: String): FishingMacroConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return FishingMacroConfig()
            }
            if (!root.isJsonObject) return FishingMacroConfig()
            val obj = root.asJsonObject
            val defaults = FishingMacroConfig()
            val key = int(obj, "key", UNBOUND)
            val reelA = delay(obj, "reelMinMs", defaults.reelMinMs)
            val reelB = delay(obj, "reelMaxMs", defaults.reelMaxMs)
            val castA = delay(obj, "castMinMs", defaults.castMinMs)
            val castB = delay(obj, "castMaxMs", defaults.castMaxMs)
            return FishingMacroConfig(
                key = if (key <= 0) UNBOUND else key,
                reelMinMs = minOf(reelA, reelB),
                reelMaxMs = maxOf(reelA, reelB),
                castMinMs = minOf(castA, castB),
                castMaxMs = maxOf(castA, castB),
                noBiteSeconds = int(obj, "noBiteSeconds", defaults.noBiteSeconds)
                    .coerceIn(MIN_NO_BITE_SECONDS, MAX_NO_BITE_SECONDS),
            )
        }

        private fun int(obj: JsonObject, name: String, default: Int): Int =
            obj.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: default

        private fun delay(obj: JsonObject, name: String, default: Int): Int =
            int(obj, name, default).coerceIn(0, MAX_DELAY_MS)
    }
}
