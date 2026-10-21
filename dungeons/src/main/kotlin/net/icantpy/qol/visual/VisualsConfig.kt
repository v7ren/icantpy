package net.icantpy.qol.visual

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** First-person swing replacement. [VANILLA] leaves vanilla's swing untouched. */
enum class SwingMode(val id: String) {
    VANILLA("vanilla"),
    PUSH("push"),
    STAB("stab"),
    SPIN("spin"),
    PUNCH("punch"),
    SWING_DOWN("swing_down"),
    ;

    companion object {
        fun fromId(raw: String?): SwingMode? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/** Full-screen recolor applied only to first-person hand and held-item pixels. */
enum class HandShaderMode(val id: String, val label: String) {
    SOLID("solid", "Solid"),
    CHROMA("chroma", "Chroma"),
    STRIPES("stripes", "Stripes"),
    GLOW("glow", "Glow"),
    SNOW("snow", "Snow"),
    SMOKE("smoke", "Smoke"),
    BALATRO("balatro", "Balatro"),
    GLASS("glass", "Glass"),
    ;

    companion object {
        fun fromId(raw: String?): HandShaderMode? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/** How the movement trail is drawn: a line ribbon or a stream of client-only particles. */
enum class TrailStyle(val id: String, val label: String) {
    LINE("line", "Line"),
    DUST("dust", "Dust"),
    HEART("heart", "Hearts"),
    NOTE("note", "Notes"),
    CRIT("crit", "Crit"),
    END_ROD("end_rod", "Sparkle"),
    ;

    companion object {
        fun fromId(raw: String?): TrailStyle? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/** Client-only particles floating around the player. */
enum class AmbientStyle(val id: String, val label: String) {
    STARS("stars", "Stars"),
    HEARTS("hearts", "Hearts"),
    SNOW("snow", "Snow"),
    FLAME("flame", "Flame"),
    GLOW("glow", "Glow"),
    CHERRY("cherry", "Cherry"),
    FIREFLY("firefly", "Firefly"),
    NOTES("notes", "Notes"),
    DUST("dust", "Dust"),
    ;

    companion object {
        fun fromId(raw: String?): AmbientStyle? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * Client-only visual settings. Every field defaults to vanilla, so a fresh install draws exactly what the game does.
 * Ranges are enforced on parse: out-of-range numbers are clamped and non-finite numbers fall back to the default.
 */
data class VisualsConfig(
    // Render cleanup
    val noFireOverlay: Boolean = false,
    val noWaterOverlay: Boolean = false,
    val noInWallOverlay: Boolean = false,
    val noHurtCamera: Boolean = false,
    val noVignette: Boolean = false,
    val noBlockBreakParticles: Boolean = false,
    val noExplosionParticles: Boolean = false,
    val noLightning: Boolean = false,
    // World look
    val fullbright: Boolean = false,
    val timeChanger: Boolean = false,
    /** Fixed client time of day in ticks, used only when [timeChanger] is on. */
    val dayTime: Long = DEFAULT_DAY_TIME,
    val noFog: Boolean = false,
    /** Fog distance multiplier. 1 is vanilla. */
    val fogDistance: Float = 1f,
    // Perspective
    val customCameraDistance: Boolean = false,
    val cameraDistance: Float = DEFAULT_CAMERA_DISTANCE,
    val cameraThroughWalls: Boolean = false,
    // Zoom
    /** GLFW key code, or [UNBOUND]. Zoom is inactive while unbound. */
    val zoomKey: Int = UNBOUND,
    /** Zoom divides the FOV by this value. 4 means a quarter of the normal FOV. */
    val zoomFactor: Float = DEFAULT_ZOOM_FACTOR,
    val zoomSmooth: Boolean = true,
    // Aspect ratio
    val customAspect: Boolean = false,
    /** Projection width / height. */
    val aspectRatio: Float = DEFAULT_ASPECT_RATIO,
    // Viewmodel
    val viewmodelEnabled: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val rotYaw: Float = 0f,
    val rotPitch: Float = 0f,
    val rotRoll: Float = 0f,
    val scale: Float = 1f,
    val swingMode: SwingMode = SwingMode.VANILLA,
    /** Swing speed multiplier. Higher is faster: the vanilla swing length is divided by this value. */
    val swingSpeed: Float = 1f,
    val noEquipReset: Boolean = false,
    // Hand shader
    val handShader: Boolean = false,
    val handShaderMode: HandShaderMode = HandShaderMode.CHROMA,
    /** RGB 0xRRGGBB. Primary color; two-tone modes also use [handColor2]. */
    val handColor1: Int = 0xFF7AF0,
    val handColor2: Int = 0x6AD5FF,
    /** Animation speed multiplier. */
    val handShaderSpeed: Float = 1f,
    /** How strongly the effect replaces the hand's own colors, 0..1. */
    val handShaderOpacity: Float = 0.85f,
    // Trail
    val trail: Boolean = false,
    val trailStyle: TrailStyle = TrailStyle.LINE,
    val trailRainbow: Boolean = true,
    val trailColor: Int = 0xFF7AF0,
    /** Number of recorded positions (one per tick) the trail keeps. */
    val trailLength: Int = 30,
    val trailWidth: Float = 2f,
    // China hat
    val chinaHat: Boolean = false,
    val chinaHatRainbow: Boolean = true,
    val chinaHatColor: Int = 0x6AD5FF,
    /** Cone radius in blocks. */
    val chinaHatSize: Float = 0.7f,
    /** Cone height in blocks. */
    val chinaHatHeight: Float = 0.3f,
    // Player model (local player only, seen in third person)
    val dinnerbone: Boolean = false,
    val spinbot: Boolean = false,
    /** Degrees per tick. */
    val spinSpeed: Float = 20f,
    val modelScaleX: Float = 1f,
    val modelScaleY: Float = 1f,
    val modelScaleZ: Float = 1f,
    // Ambient particles
    val ambientParticles: Boolean = false,
    val ambientStyle: AmbientStyle = AmbientStyle.STARS,
    /** Particles spawned per second. */
    val ambientDensity: Int = 6,
    /** Spawn radius around the player in blocks. */
    val ambientRadius: Float = 3f,
) {
    /** True when the local player's model is changed in any way. */
    fun playerModelActive(): Boolean =
        dinnerbone || spinbot || modelScaleX != 1f || modelScaleY != 1f || modelScaleZ != 1f

    /** True when any setting differs from vanilla. The mixins' master switch follows this. */
    fun anyActive(): Boolean =
        noFireOverlay || noWaterOverlay || noInWallOverlay || noHurtCamera || noVignette ||
            noBlockBreakParticles || noExplosionParticles || noLightning ||
            fullbright || timeChanger || noFog || fogDistance != 1f ||
            customCameraDistance || cameraThroughWalls || zoomKey != UNBOUND ||
            customAspect || viewmodelEnabled || handShader || playerModelActive()

    fun toJson(): String {
        val obj = JsonObject()
        obj.addProperty("version", VERSION)
        obj.addProperty("noFireOverlay", noFireOverlay)
        obj.addProperty("noWaterOverlay", noWaterOverlay)
        obj.addProperty("noInWallOverlay", noInWallOverlay)
        obj.addProperty("noHurtCamera", noHurtCamera)
        obj.addProperty("noVignette", noVignette)
        obj.addProperty("noBlockBreakParticles", noBlockBreakParticles)
        obj.addProperty("noExplosionParticles", noExplosionParticles)
        obj.addProperty("noLightning", noLightning)
        obj.addProperty("fullbright", fullbright)
        obj.addProperty("timeChanger", timeChanger)
        obj.addProperty("dayTime", dayTime)
        obj.addProperty("noFog", noFog)
        obj.addProperty("fogDistance", fogDistance)
        obj.addProperty("customCameraDistance", customCameraDistance)
        obj.addProperty("cameraDistance", cameraDistance)
        obj.addProperty("cameraThroughWalls", cameraThroughWalls)
        obj.addProperty("zoomKey", zoomKey)
        obj.addProperty("zoomFactor", zoomFactor)
        obj.addProperty("zoomSmooth", zoomSmooth)
        obj.addProperty("customAspect", customAspect)
        obj.addProperty("aspectRatio", aspectRatio)
        obj.addProperty("viewmodelEnabled", viewmodelEnabled)
        obj.addProperty("offsetX", offsetX)
        obj.addProperty("offsetY", offsetY)
        obj.addProperty("offsetZ", offsetZ)
        obj.addProperty("rotYaw", rotYaw)
        obj.addProperty("rotPitch", rotPitch)
        obj.addProperty("rotRoll", rotRoll)
        obj.addProperty("scale", scale)
        obj.addProperty("swingMode", swingMode.id)
        obj.addProperty("swingSpeed", swingSpeed)
        obj.addProperty("noEquipReset", noEquipReset)
        obj.addProperty("handShader", handShader)
        obj.addProperty("handShaderMode", handShaderMode.id)
        obj.addProperty("handColor1", handColor1)
        obj.addProperty("handColor2", handColor2)
        obj.addProperty("handShaderSpeed", handShaderSpeed)
        obj.addProperty("handShaderOpacity", handShaderOpacity)
        obj.addProperty("trail", trail)
        obj.addProperty("trailStyle", trailStyle.id)
        obj.addProperty("trailRainbow", trailRainbow)
        obj.addProperty("trailColor", trailColor)
        obj.addProperty("trailLength", trailLength)
        obj.addProperty("trailWidth", trailWidth)
        obj.addProperty("chinaHat", chinaHat)
        obj.addProperty("chinaHatRainbow", chinaHatRainbow)
        obj.addProperty("chinaHatColor", chinaHatColor)
        obj.addProperty("chinaHatSize", chinaHatSize)
        obj.addProperty("chinaHatHeight", chinaHatHeight)
        obj.addProperty("dinnerbone", dinnerbone)
        obj.addProperty("spinbot", spinbot)
        obj.addProperty("spinSpeed", spinSpeed)
        obj.addProperty("modelScaleX", modelScaleX)
        obj.addProperty("modelScaleY", modelScaleY)
        obj.addProperty("modelScaleZ", modelScaleZ)
        obj.addProperty("ambientParticles", ambientParticles)
        obj.addProperty("ambientStyle", ambientStyle.id)
        obj.addProperty("ambientDensity", ambientDensity)
        obj.addProperty("ambientRadius", ambientRadius)
        return obj.toString()
    }

    companion object {
        const val VERSION: Int = 1
        const val UNBOUND: Int = -1
        const val DEFAULT_DAY_TIME: Long = 6000L
        const val DEFAULT_CAMERA_DISTANCE: Float = 4f
        const val DEFAULT_ZOOM_FACTOR: Float = 4f
        const val DEFAULT_ASPECT_RATIO: Float = 16f / 9f

        /** Parses a settings file. Never throws: malformed JSON or a non-object root yields defaults. */
        fun parse(json: String): VisualsConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return VisualsConfig()
            }
            if (!root.isJsonObject) return VisualsConfig()
            return read(Fields(root.asJsonObject))
        }

        private fun read(f: Fields): VisualsConfig {
            val d = VisualsConfig()
            return VisualsConfig(
                noFireOverlay = f.bool("noFireOverlay", d.noFireOverlay),
                noWaterOverlay = f.bool("noWaterOverlay", d.noWaterOverlay),
                noInWallOverlay = f.bool("noInWallOverlay", d.noInWallOverlay),
                noHurtCamera = f.bool("noHurtCamera", d.noHurtCamera),
                noVignette = f.bool("noVignette", d.noVignette),
                noBlockBreakParticles = f.bool("noBlockBreakParticles", d.noBlockBreakParticles),
                noExplosionParticles = f.bool("noExplosionParticles", d.noExplosionParticles),
                noLightning = f.bool("noLightning", d.noLightning),
                fullbright = f.bool("fullbright", d.fullbright),
                timeChanger = f.bool("timeChanger", d.timeChanger),
                dayTime = f.long("dayTime", d.dayTime, 0L, 23999L),
                noFog = f.bool("noFog", d.noFog),
                fogDistance = f.float("fogDistance", d.fogDistance, 0.25f, 4f),
                customCameraDistance = f.bool("customCameraDistance", d.customCameraDistance),
                cameraDistance = f.float("cameraDistance", d.cameraDistance, 1f, 20f),
                cameraThroughWalls = f.bool("cameraThroughWalls", d.cameraThroughWalls),
                zoomKey = f.zoomKey("zoomKey"),
                zoomFactor = f.float("zoomFactor", d.zoomFactor, 1.5f, 10f),
                zoomSmooth = f.bool("zoomSmooth", d.zoomSmooth),
                customAspect = f.bool("customAspect", d.customAspect),
                aspectRatio = f.float("aspectRatio", d.aspectRatio, 0.5f, 3f),
                viewmodelEnabled = f.bool("viewmodelEnabled", d.viewmodelEnabled),
                offsetX = f.float("offsetX", d.offsetX, -2f, 2f),
                offsetY = f.float("offsetY", d.offsetY, -2f, 2f),
                offsetZ = f.float("offsetZ", d.offsetZ, -2f, 2f),
                rotYaw = f.float("rotYaw", d.rotYaw, -180f, 180f),
                rotPitch = f.float("rotPitch", d.rotPitch, -180f, 180f),
                rotRoll = f.float("rotRoll", d.rotRoll, -180f, 180f),
                scale = f.float("scale", d.scale, 0.1f, 2f),
                swingMode = f.swingMode("swingMode", d.swingMode),
                swingSpeed = f.float("swingSpeed", d.swingSpeed, 0.25f, 4f),
                noEquipReset = f.bool("noEquipReset", d.noEquipReset),
                handShader = f.bool("handShader", d.handShader),
                handShaderMode = f.choice("handShaderMode", d.handShaderMode, HandShaderMode::fromId),
                handColor1 = f.rgb("handColor1", d.handColor1),
                handColor2 = f.rgb("handColor2", d.handColor2),
                handShaderSpeed = f.float("handShaderSpeed", d.handShaderSpeed, 0.1f, 4f),
                handShaderOpacity = f.float("handShaderOpacity", d.handShaderOpacity, 0f, 1f),
                trail = f.bool("trail", d.trail),
                trailStyle = f.choice("trailStyle", d.trailStyle, TrailStyle::fromId),
                trailRainbow = f.bool("trailRainbow", d.trailRainbow),
                trailColor = f.rgb("trailColor", d.trailColor),
                trailLength = f.long("trailLength", d.trailLength.toLong(), 5L, 100L).toInt(),
                trailWidth = f.float("trailWidth", d.trailWidth, 1f, 6f),
                chinaHat = f.bool("chinaHat", d.chinaHat),
                chinaHatRainbow = f.bool("chinaHatRainbow", d.chinaHatRainbow),
                chinaHatColor = f.rgb("chinaHatColor", d.chinaHatColor),
                chinaHatSize = f.float("chinaHatSize", d.chinaHatSize, 0.3f, 1.5f),
                chinaHatHeight = f.float("chinaHatHeight", d.chinaHatHeight, 0.1f, 0.8f),
                dinnerbone = f.bool("dinnerbone", d.dinnerbone),
                spinbot = f.bool("spinbot", d.spinbot),
                spinSpeed = f.float("spinSpeed", d.spinSpeed, 1f, 60f),
                modelScaleX = f.float("modelScaleX", d.modelScaleX, 0.25f, 2f),
                modelScaleY = f.float("modelScaleY", d.modelScaleY, 0.25f, 2f),
                modelScaleZ = f.float("modelScaleZ", d.modelScaleZ, 0.25f, 2f),
                ambientParticles = f.bool("ambientParticles", d.ambientParticles),
                ambientStyle = f.choice("ambientStyle", d.ambientStyle, AmbientStyle::fromId),
                ambientDensity = f.long("ambientDensity", d.ambientDensity.toLong(), 1L, 20L).toInt(),
                ambientRadius = f.float("ambientRadius", d.ambientRadius, 1f, 8f),
            )
        }
    }
}

/** Tolerant typed readers: a field with the wrong JSON type is treated as absent. */
private class Fields(private val obj: JsonObject) {
    private fun primitive(key: String) = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    fun bool(key: String, default: Boolean): Boolean {
        val p = primitive(key) ?: return default
        return if (p.isBoolean) p.asBoolean else default
    }

    fun float(key: String, default: Float, min: Float, max: Float): Float {
        val p = primitive(key)?.takeIf { it.isNumber } ?: return default
        val value = p.asDouble
        if (!value.isFinite()) return default
        return value.toFloat().coerceIn(min, max)
    }

    fun long(key: String, default: Long, min: Long, max: Long): Long {
        val p = primitive(key)?.takeIf { it.isNumber } ?: return default
        val value = p.asDouble
        if (!value.isFinite()) return default
        return value.toLong().coerceIn(min, max)
    }

    /** Zero is accepted as "unbound", matching the freelook key convention. */
    fun zoomKey(key: String): Int {
        val p = primitive(key)?.takeIf { it.isNumber } ?: return VisualsConfig.UNBOUND
        val code = p.asInt
        return if (code == 0) VisualsConfig.UNBOUND else code
    }

    fun swingMode(key: String, default: SwingMode): SwingMode {
        val p = primitive(key)?.takeIf { it.isString } ?: return default
        return SwingMode.fromId(p.asString) ?: default
    }

    fun <T> choice(key: String, default: T, fromId: (String?) -> T?): T {
        val p = primitive(key)?.takeIf { it.isString } ?: return default
        return fromId(p.asString) ?: default
    }

    /** An RGB color 0xRRGGBB. Alpha bits are dropped; a non-number falls back to [default]. */
    fun rgb(key: String, default: Int): Int {
        val p = primitive(key)?.takeIf { it.isNumber } ?: return default
        val value = p.asDouble
        if (!value.isFinite()) return default
        return value.toLong().toInt() and 0xFFFFFF
    }
}
