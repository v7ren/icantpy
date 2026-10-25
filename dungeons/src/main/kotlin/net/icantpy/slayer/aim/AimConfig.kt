package net.icantpy.slayer.aim

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** What turns the camera toward a boss: a held left mouse button, a held key, or a toggled key. */
enum class AimActivation(val id: String, val label: String) {
    HOLD_LEFT_CLICK("hold_left_click", "Hold left click"),
    HOLD_KEY("hold_key", "Hold key"),
    TOGGLE_KEY("toggle_key", "Toggle key"),
    ;

    companion object {
        fun fromId(raw: String?): AimActivation? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/** Point on the target the camera aims at. */
enum class AimPoint(val id: String, val label: String) {
    CENTER("center", "Center"),
    CHEST("chest", "Chest"),
    HEAD("head", "Head"),
    ;

    companion object {
        fun fromId(raw: String?): AimPoint? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * Boss aim settings. Parsing is tolerant: wrong types fall back to defaults and numbers are clamped to their
 * ranges, so a hand-edited file never throws.
 */
data class AimConfig(
    val enabled: Boolean = false,
    val activation: AimActivation = AimActivation.HOLD_LEFT_CLICK,
    /** GLFW key code for the HOLD_KEY and TOGGLE_KEY activations. [UNBOUND] when unset. */
    val key: Int = UNBOUND,
    /**
     * Debug lock-on: pressing this GLFW key locks onto the mob under the crosshair and aims at it until it dies or
     * the key is pressed again, regardless of [activation]. [UNBOUND] when unset.
     */
    val lockKey: Int = UNBOUND,
    val speedDegPerSec: Float = DEFAULT_SPEED_DEG_PER_SEC,
    val minDurationMs: Int = DEFAULT_MIN_DURATION_MS,
    val maxDurationMs: Int = DEFAULT_MAX_DURATION_MS,
    val curve: Float = DEFAULT_CURVE,
    val easing: AimEasing = AimEasing.EASE_IN_OUT,
    val aimPoint: AimPoint = AimPoint.CHEST,
    /** Targets farther than this, in blocks, are ignored. */
    val maxRange: Float = DEFAULT_MAX_RANGE,
    /** Only targets within this full cone around the crosshair, in degrees, are considered. */
    val fovLimit: Float = DEFAULT_FOV_LIMIT,
    val requireLineOfSight: Boolean = true,
    val ownSlayerBoss: Boolean = true,
    /** Case-insensitive substrings matched against mob and hologram names, for other bosses. */
    val extraTargets: List<String> = emptyList(),
    val highlightTarget: Boolean = true,
    /** Aim at the Watcher's blood mobs while clearing the blood room. */
    val bloodAim: Boolean = false,
    /** Aim at the real Livid in the F5/M5 boss room. */
    val lividAim: Boolean = false,
    /** Aim at a slowly wandering point inside the hitbox instead of the exact aim point. */
    val lazyAim: Boolean = false,
    /** How far the lazy point may wander from the aim point: 0 = never, 1 = up to the hitbox edge. */
    val lazyStrength: Float = 0.6f,
    /** With lazy aim, stop correcting while the crosshair is already on the target's hitbox. */
    val lazyDeadzone: Boolean = true,
    /** While aiming at a target and attacking, tap S after each hit, never into a pit or during a beam sweep. */
    val strafe: Boolean = false,
    /** Length of one S tap, in ticks. */
    val strafeTapTicks: Int = DEFAULT_STRAFE_TAP_TICKS,
) {
    /** Motion parameters for [net.icantpy.slayer.aim.AimMotion]. */
    fun settings(): AimSettings = AimSettings(
        speedDegPerSec = speedDegPerSec,
        minDurationMs = minDurationMs,
        maxDurationMs = maxDurationMs,
        curve = curve,
        easing = easing,
    )

    /** True when any aim feature is on: slayer aim, blood aim or Livid aim. */
    val active: Boolean get() = enabled || bloodAim || lividAim

    fun toJsonObject(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("version", FORMAT_VERSION)
        obj.addProperty("enabled", enabled)
        obj.addProperty("activation", activation.id)
        obj.addProperty("key", if (key == UNBOUND) 0 else key)
        obj.addProperty("lockKey", if (lockKey == UNBOUND) 0 else lockKey)
        obj.addProperty("speedDegPerSec", speedDegPerSec)
        obj.addProperty("minDurationMs", minDurationMs)
        obj.addProperty("maxDurationMs", maxDurationMs)
        obj.addProperty("curve", curve)
        obj.addProperty("easing", easing.id)
        obj.addProperty("aimPoint", aimPoint.id)
        obj.addProperty("maxRange", maxRange)
        obj.addProperty("fovLimit", fovLimit)
        obj.addProperty("requireLineOfSight", requireLineOfSight)
        obj.addProperty("ownSlayerBoss", ownSlayerBoss)
        val extras = JsonArray()
        extraTargets.forEach { extras.add(it) }
        obj.add("extraTargets", extras)
        obj.addProperty("highlightTarget", highlightTarget)
        obj.addProperty("bloodAim", bloodAim)
        obj.addProperty("lividAim", lividAim)
        obj.addProperty("lazyAim", lazyAim)
        obj.addProperty("lazyStrength", lazyStrength)
        obj.addProperty("lazyDeadzone", lazyDeadzone)
        obj.addProperty("strafe", strafe)
        obj.addProperty("strafeTapTicks", strafeTapTicks)
        return obj
    }

    companion object {
        const val FORMAT_VERSION: Int = 1

        /** Same convention as the freelook key: no key bound. */
        const val UNBOUND: Int = -1

        const val MIN_SPEED_DEG_PER_SEC: Float = 30f
        const val MAX_SPEED_DEG_PER_SEC: Float = 720f
        const val DEFAULT_SPEED_DEG_PER_SEC: Float = 180f

        const val MIN_MIN_DURATION_MS: Int = 60
        const val MAX_MIN_DURATION_MS: Int = 1000
        const val DEFAULT_MIN_DURATION_MS: Int = 180

        const val MIN_MAX_DURATION_MS: Int = 200
        const val MAX_MAX_DURATION_MS: Int = 3000
        const val DEFAULT_MAX_DURATION_MS: Int = 900

        const val MIN_CURVE: Float = 0f
        const val MAX_CURVE: Float = 1f
        const val DEFAULT_CURVE: Float = 0.35f

        const val MIN_MAX_RANGE: Float = 3f
        const val MAX_MAX_RANGE: Float = 32f
        const val DEFAULT_MAX_RANGE: Float = 12f

        const val MIN_FOV_LIMIT: Float = 20f
        const val MAX_FOV_LIMIT: Float = 360f
        const val DEFAULT_FOV_LIMIT: Float = 120f

        const val MIN_STRAFE_TAP_TICKS: Int = 1
        const val MAX_STRAFE_TAP_TICKS: Int = 20
        const val DEFAULT_STRAFE_TAP_TICKS: Int = 3

        const val MAX_EXTRA_TARGETS: Int = 32
        const val MAX_EXTRA_TARGET_LENGTH: Int = 64

        fun parse(json: String): AimConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return AimConfig()
            }
            if (!root.isJsonObject) return AimConfig()
            return fromJsonObject(root.asJsonObject)
        }

        fun fromJsonObject(obj: JsonObject): AimConfig {
            val defaults = AimConfig()
            val minDuration = int(obj, "minDurationMs", defaults.minDurationMs)
                .coerceIn(MIN_MIN_DURATION_MS, MAX_MIN_DURATION_MS)
            val maxDuration = int(obj, "maxDurationMs", defaults.maxDurationMs)
                .coerceIn(MIN_MAX_DURATION_MS, MAX_MAX_DURATION_MS)
                .coerceAtLeast(minDuration)
            val key = int(obj, "key", defaults.key)
            val lockKey = int(obj, "lockKey", defaults.lockKey)
            return AimConfig(
                enabled = bool(obj, "enabled", defaults.enabled),
                activation = AimActivation.fromId(string(obj, "activation")) ?: defaults.activation,
                key = if (key == 0) UNBOUND else key,
                lockKey = if (lockKey == 0) UNBOUND else lockKey,
                speedDegPerSec = float(obj, "speedDegPerSec", defaults.speedDegPerSec)
                    .coerceIn(MIN_SPEED_DEG_PER_SEC, MAX_SPEED_DEG_PER_SEC),
                minDurationMs = minDuration,
                maxDurationMs = maxDuration,
                curve = float(obj, "curve", defaults.curve).coerceIn(MIN_CURVE, MAX_CURVE),
                easing = AimEasing.fromId(string(obj, "easing")) ?: defaults.easing,
                aimPoint = AimPoint.fromId(string(obj, "aimPoint")) ?: defaults.aimPoint,
                maxRange = float(obj, "maxRange", defaults.maxRange).coerceIn(MIN_MAX_RANGE, MAX_MAX_RANGE),
                fovLimit = float(obj, "fovLimit", defaults.fovLimit).coerceIn(MIN_FOV_LIMIT, MAX_FOV_LIMIT),
                requireLineOfSight = bool(obj, "requireLineOfSight", defaults.requireLineOfSight),
                ownSlayerBoss = bool(obj, "ownSlayerBoss", defaults.ownSlayerBoss),
                extraTargets = filterList(obj.get("extraTargets")) ?: defaults.extraTargets,
                highlightTarget = bool(obj, "highlightTarget", defaults.highlightTarget),
                bloodAim = bool(obj, "bloodAim", defaults.bloodAim),
                lividAim = bool(obj, "lividAim", defaults.lividAim),
                lazyAim = bool(obj, "lazyAim", defaults.lazyAim),
                lazyStrength = float(obj, "lazyStrength", defaults.lazyStrength).coerceIn(0f, 1f),
                lazyDeadzone = bool(obj, "lazyDeadzone", defaults.lazyDeadzone),
                strafe = bool(obj, "strafe", defaults.strafe),
                strafeTapTicks = int(obj, "strafeTapTicks", defaults.strafeTapTicks)
                    .coerceIn(MIN_STRAFE_TAP_TICKS, MAX_STRAFE_TAP_TICKS),
            )
        }

        /** Null when the element is missing or not an array, so the default list is used. */
        private fun filterList(element: JsonElement?): List<String>? {
            if (element == null || !element.isJsonArray) return null
            return element.asJsonArray
                .mapNotNull { item ->
                    item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString?.trim()?.take(MAX_EXTRA_TARGET_LENGTH)
                }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .take(MAX_EXTRA_TARGETS)
        }

        private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

        private fun string(obj: JsonObject, key: String): String? =
            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun int(obj: JsonObject, key: String, default: Int): Int {
            val number = number(obj, key) ?: return default
            return number.toInt()
        }

        private fun float(obj: JsonObject, key: String, default: Float): Float {
            val number = number(obj, key) ?: return default
            return number.toFloat().takeIf { it.isFinite() } ?: default
        }

        private fun number(obj: JsonObject, key: String): Double? {
            val value = obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: return null
            return value.asDouble.takeIf { it.isFinite() }
        }
    }
}
