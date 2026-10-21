package net.icantpy.qol.perf

import com.google.gson.JsonParser
import com.google.gson.JsonObject

/**
 * Plain-Kotlin snapshot of the vanilla video options touched by the Max FPS preset.
 * Enum-valued options are stored by enum constant name so this file has no Minecraft types.
 */
data class VideoSnapshot(
    val renderDistance: Int = 12,
    val simulationDistance: Int = 12,
    val graphicsPreset: String = "FANCY",
    val ambientOcclusion: Boolean = true,
    val particles: String = "ALL",
    val cloudStatus: String = "FANCY",
    val biomeBlendRadius: Int = 2,
    val entityShadows: Boolean = true,
    val entityDistanceScaling: Double = 1.0,
    val mipmapLevels: Int = 4,
    val enableVsync: Boolean = true,
    val framerateLimit: Int = PerformancePlan.UNLIMITED_FRAMERATE,
)

/** Target values and rules for the preset. Tweak the constants here. */
object PerformancePlan {
    /** Options.UNLIMITED_FRAMERATE_CUTOFF in both supported versions. */
    const val UNLIMITED_FRAMERATE: Int = 260

    const val RENDER_DISTANCE_MAX: Int = 8
    const val SIMULATION_DISTANCE_MAX: Int = 6
    const val MIPMAP_LEVELS_MAX: Int = 2

    const val GRAPHICS_PRESET: String = "FAST"
    const val AMBIENT_OCCLUSION: Boolean = false
    const val PARTICLES: String = "DECREASED"
    const val CLOUD_STATUS: String = "OFF"
    const val BIOME_BLEND_RADIUS: Int = 0
    const val ENTITY_SHADOWS: Boolean = false
    const val ENTITY_DISTANCE_SCALING: Double = 0.75
    const val VSYNC: Boolean = false
    const val FRAMERATE_LIMIT: Int = UNLIMITED_FRAMERATE

    val GRAPHICS_PRESET_NAMES: Set<String> = setOf("FAST", "FANCY", "FABULOUS", "CUSTOM")
    val PARTICLE_NAMES: Set<String> = setOf("ALL", "DECREASED", "MINIMAL")
    val CLOUD_NAMES: Set<String> = setOf("OFF", "FAST", "FANCY")

    /**
     * Returns the values the preset wants given the player's current values.
     * Render distance, simulation distance and mipmap levels are only ever lowered.
     */
    fun target(current: VideoSnapshot): VideoSnapshot = VideoSnapshot(
        renderDistance = minOf(current.renderDistance, RENDER_DISTANCE_MAX),
        simulationDistance = minOf(current.simulationDistance, SIMULATION_DISTANCE_MAX),
        graphicsPreset = GRAPHICS_PRESET,
        ambientOcclusion = AMBIENT_OCCLUSION,
        particles = PARTICLES,
        cloudStatus = CLOUD_STATUS,
        biomeBlendRadius = BIOME_BLEND_RADIUS,
        entityShadows = ENTITY_SHADOWS,
        entityDistanceScaling = ENTITY_DISTANCE_SCALING,
        mipmapLevels = minOf(current.mipmapLevels, MIPMAP_LEVELS_MAX),
        enableVsync = VSYNC,
        framerateLimit = FRAMERATE_LIMIT,
    )
}

/**
 * JSON for the backup snapshot. Parsing is tolerant: wrong-typed or unknown
 * fields fall back to the VideoSnapshot defaults.
 */
object VideoSnapshotJson {
    const val VERSION: Int = 1

    fun toJson(snapshot: VideoSnapshot): String {
        val obj = JsonObject()
        obj.addProperty("version", VERSION)
        obj.addProperty("renderDistance", snapshot.renderDistance)
        obj.addProperty("simulationDistance", snapshot.simulationDistance)
        obj.addProperty("graphicsPreset", snapshot.graphicsPreset)
        obj.addProperty("ambientOcclusion", snapshot.ambientOcclusion)
        obj.addProperty("particles", snapshot.particles)
        obj.addProperty("cloudStatus", snapshot.cloudStatus)
        obj.addProperty("biomeBlendRadius", snapshot.biomeBlendRadius)
        obj.addProperty("entityShadows", snapshot.entityShadows)
        obj.addProperty("entityDistanceScaling", snapshot.entityDistanceScaling)
        obj.addProperty("mipmapLevels", snapshot.mipmapLevels)
        obj.addProperty("enableVsync", snapshot.enableVsync)
        obj.addProperty("framerateLimit", snapshot.framerateLimit)
        return obj.toString()
    }

    /** Returns null when the text is not a JSON object at all. */
    fun parseOrNull(json: String): VideoSnapshot? {
        val root = try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            return null
        }
        if (!root.isJsonObject) return null
        return fromObject(root.asJsonObject)
    }

    /** Tolerant parse: malformed input yields a default snapshot. */
    fun parse(json: String): VideoSnapshot = parseOrNull(json) ?: VideoSnapshot()

    private fun fromObject(obj: JsonObject): VideoSnapshot {
        val d = VideoSnapshot()
        return VideoSnapshot(
            renderDistance = int(obj, "renderDistance") ?: d.renderDistance,
            simulationDistance = int(obj, "simulationDistance") ?: d.simulationDistance,
            graphicsPreset = name(obj, "graphicsPreset", PerformancePlan.GRAPHICS_PRESET_NAMES)
                ?: d.graphicsPreset,
            ambientOcclusion = bool(obj, "ambientOcclusion") ?: d.ambientOcclusion,
            particles = name(obj, "particles", PerformancePlan.PARTICLE_NAMES) ?: d.particles,
            cloudStatus = name(obj, "cloudStatus", PerformancePlan.CLOUD_NAMES) ?: d.cloudStatus,
            biomeBlendRadius = int(obj, "biomeBlendRadius") ?: d.biomeBlendRadius,
            entityShadows = bool(obj, "entityShadows") ?: d.entityShadows,
            entityDistanceScaling = double(obj, "entityDistanceScaling") ?: d.entityDistanceScaling,
            mipmapLevels = int(obj, "mipmapLevels") ?: d.mipmapLevels,
            enableVsync = bool(obj, "enableVsync") ?: d.enableVsync,
            framerateLimit = int(obj, "framerateLimit") ?: d.framerateLimit,
        )
    }

    private fun primitive(obj: JsonObject, key: String) =
        obj.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    private fun int(obj: JsonObject, key: String): Int? =
        primitive(obj, key)?.takeIf { it.isNumber }?.let {
            runCatching { it.asDouble }.getOrNull()
        }?.takeIf { it.isFinite() && it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }
            ?.toInt()

    private fun double(obj: JsonObject, key: String): Double? =
        primitive(obj, key)?.takeIf { it.isNumber }?.asDouble?.takeIf { it.isFinite() }

    private fun bool(obj: JsonObject, key: String): Boolean? =
        primitive(obj, key)?.takeIf { it.isBoolean }?.asBoolean

    private fun name(obj: JsonObject, key: String, allowed: Set<String>): String? =
        primitive(obj, key)?.takeIf { it.isString }?.asString?.trim()?.takeIf { it in allowed }
}
