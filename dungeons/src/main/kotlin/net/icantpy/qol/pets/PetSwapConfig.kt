package net.icantpy.qol.pets

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One pet and the key that summons it. [key] is a GLFW key code, or [PetSwapConfig.UNBOUND]. */
data class PetBind(val name: String, val key: Int = PetSwapConfig.UNBOUND)

data class PetSwapConfig(
    val enabled: Boolean = true,
    val pets: List<PetBind> = emptyList(),
    /** No delays at all: the swap runs as fast as the server answers. */
    val instant: Boolean = false,
    /** Random delay range, in ms, before /pets, before each click and before closing. Ignored when [instant]. */
    val delayMinMs: Int = DEFAULT_DELAY_MIN_MS,
    val delayMaxMs: Int = DEFAULT_DELAY_MAX_MS,
    /** Summon [castPet] each time the player casts a fishing rod. */
    val swapOnCast: Boolean = false,
    val castPet: String = "",
) {
    fun withPet(index: Int, change: (PetBind) -> PetBind): PetSwapConfig {
        if (index !in pets.indices) return this
        return copy(pets = pets.toMutableList().also { it[index] = change(it[index]) })
    }

    fun withAdded(name: String): PetSwapConfig {
        val clean = PetMenu.cleanTarget(name)
        if (clean.isEmpty() || pets.size >= MAX_PETS) return this
        return copy(pets = pets + PetBind(clean))
    }

    fun withRemoved(index: Int): PetSwapConfig =
        if (index in pets.indices) copy(pets = pets.filterIndexed { i, _ -> i != index }) else this

    companion object {
        const val UNBOUND: Int = -1
        const val MAX_PETS: Int = 16
        const val DEFAULT_DELAY_MIN_MS: Int = 80
        const val DEFAULT_DELAY_MAX_MS: Int = 200
        const val MAX_DELAY_MS: Int = 2_000

        fun parse(json: String): PetSwapConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return PetSwapConfig()
            }
            if (!root.isJsonObject) return PetSwapConfig()
            val obj = root.asJsonObject
            val enabled = obj.get("enabled")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean ?: true
            val pets = obj.getAsJsonArray("pets")?.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                readBind(element.asJsonObject)
            }.orEmpty().take(MAX_PETS)
            val instant = obj.get("instant")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean ?: false
            val low = readDelay(obj, "delayMinMs", DEFAULT_DELAY_MIN_MS)
            val high = readDelay(obj, "delayMaxMs", DEFAULT_DELAY_MAX_MS)
            val swapOnCast = obj.get("swapOnCast")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean ?: false
            val castPet = obj.get("castPet")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.let(PetMenu::cleanTarget).orEmpty()
            return PetSwapConfig(enabled, pets, instant, minOf(low, high), maxOf(low, high), swapOnCast, castPet)
        }

        private fun readDelay(obj: JsonObject, name: String, default: Int): Int =
            (obj.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: default)
                .coerceIn(0, MAX_DELAY_MS)

        private fun readBind(obj: JsonObject): PetBind? {
            val name = obj.get("name")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.let(PetMenu::cleanTarget)
                ?.takeIf { it.isNotEmpty() } ?: return null
            val key = obj.get("key")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asInt ?: UNBOUND
            return PetBind(name, if (key <= 0) UNBOUND else key)
        }
    }
}
