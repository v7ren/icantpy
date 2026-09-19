package net.icantpy.cosmetics.items

import com.google.gson.JsonParser
import net.icantpy.Icantpy
import java.io.InputStream

/**
 * Hypixel dye presets shipped from the NEU repository (`constants/dyes.json`): named static colours
 * and animated colour cycles. Used by the customizer's dye picker.
 */
object RepoDyes {
    /** Approximate per-frame duration used to play animated dye cycles. */
    private const val FRAME_MILLIS = 100L
    private const val RESOURCE_PATH = "assets/icantpy/custom/dyes.json"

    private val fallbackStaticDyes = linkedMapOf(
        "DYE_CARMINE" to 0x960018,
        "DYE_NECRON" to 0xE7413C,
        "DYE_EMERALD" to 0x50C878,
        "DYE_AQUAMARINE" to 0x7FFFD4,
        "DYE_PURE_WHITE" to 0xFFFFFF,
        "DYE_PURE_BLACK" to 0x000000,
    )
    private val fallbackAnimatedDyes = mapOf(
        "DYE_ROSE" to listOf(0xFF5A8A, 0xFFB347, 0xFF5A8A),
    )

    @Volatile
    private var loaded = false

    @Volatile
    private var staticDyes: Map<String, Int> = emptyMap()

    @Volatile
    private var animatedDyes: Map<String, List<Int>> = emptyMap()

    fun names(): List<String> {
        ensureLoaded()
        return (staticDyes.keys + animatedDyes.keys).sorted()
    }

    fun isAnimated(name: String): Boolean {
        ensureLoaded()
        return animatedDyes.containsKey(name)
    }

    fun staticColor(name: String): Int? {
        ensureLoaded()
        return staticDyes[name]
    }

    /** Full frame list of an animated dye, or null when the dye is unknown. */
    fun animatedColors(name: String): List<Int>? {
        ensureLoaded()
        return animatedDyes[name]
    }

    /** Current colour of an animated dye at [elapsedMillis], or null when the dye is unknown. */
    fun animatedColor(name: String, elapsedMillis: Long): Int? {
        ensureLoaded()
        val frames = animatedDyes[name] ?: return null
        if (frames.isEmpty()) return null
        val index = ((elapsedMillis / FRAME_MILLIS) % frames.size).toInt()
        return frames[index]
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            staticDyes = fallbackStaticDyes
            animatedDyes = fallbackAnimatedDyes
            try {
                val stream = openCatalog()
                    ?: throw IllegalStateException("$RESOURCE_PATH was not found in the payload classloaders")
                val root = stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseString(it.readText()).asJsonObject }
                val statics = linkedMapOf<String, Int>()
                root.getAsJsonObject("static")?.entrySet()?.forEach { (key, value) ->
                    if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        parseHex(value.asString)?.let { statics[key] = it }
                    }
                }
                val animated = linkedMapOf<String, List<Int>>()
                root.getAsJsonObject("animated")?.entrySet()?.forEach { (key, value) ->
                    if (!value.isJsonArray) return@forEach
                    val frames = value.asJsonArray.mapNotNull { element ->
                        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) parseHex(element.asString) else null
                    }
                    if (frames.isNotEmpty()) animated[key] = frames
                }
                staticDyes = fallbackStaticDyes + statics
                animatedDyes = fallbackAnimatedDyes + animated
                loaded = true
            } catch (exception: Exception) {
                Icantpy.LOGGER.warn("Failed to load dye presets", exception)
                loaded = true
            }
        }
    }

    private fun openCatalog(): InputStream? {
        RepoDyes::class.java.getResourceAsStream("/$RESOURCE_PATH")?.let { return it }
        Thread.currentThread().contextClassLoader?.getResourceAsStream(RESOURCE_PATH)?.let { return it }
        RepoDyes::class.java.classLoader?.getResourceAsStream(RESOURCE_PATH)?.let { return it }
        return ClassLoader.getSystemResourceAsStream(RESOURCE_PATH)
    }

    private fun parseHex(raw: String): Int? {
        val hex = raw.trim().removePrefix("#")
        if (hex.length != 6) return null
        return hex.toIntOrNull(16)
    }
}
