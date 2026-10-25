package net.icantpy.slayer.blaze

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** What the fire pit helper does once the player stands in a hazard column. */
enum class EscapeMode(val id: String, val label: String) {
    NOTIFY("notify", "Notify"),
    WALK("walk", "Walk out"),
    ;

    companion object {
        fun fromId(raw: String?): EscapeMode? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * The earliest pit colour that counts as a pit. Pits turn red, then yellow, then black before fire appears, so a
 * later phase means a later (and shorter) warning. Earlier-phase blocks are listed in [earlier].
 */
enum class PitPhase(val id: String, val label: String, val earlier: List<String>) {
    RED("red", "Red", emptyList()),
    YELLOW("yellow", "Yellow", listOf("minecraft:red_terracotta")),
    BLACK("black", "Black", listOf("minecraft:red_terracotta", "minecraft:yellow_terracotta")),
    ;

    companion object {
        fun fromId(raw: String?): PitPhase? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/** What the Quazii beam helper does: nothing, a warning only, or a warning plus a jump press. */
enum class BeamDodge(val id: String, val label: String) {
    OFF("off", "Off"),
    WARN("warn", "Warn"),
    JUMP("jump", "Jump"),
    ;

    companion object {
        fun fromId(raw: String?): BeamDodge? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * Fire pit helper settings. Parsing is tolerant: wrong types fall back to defaults and numbers are clamped to
 * their ranges, so a hand-edited file never throws.
 */
data class FirePitConfig(
    val enabled: Boolean = false,
    val mode: EscapeMode = EscapeMode.NOTIFY,
    /** Block registry ids (for example "minecraft:fire") treated as pit blocks at the feet or floor. */
    val pitBlocks: List<String> = DEFAULT_PIT_BLOCKS,
    /** Columns scanned in each direction around the player, in blocks. */
    val scanRadius: Int = DEFAULT_SCAN_RADIUS,
    /** Distance in blocks from a hazard column edge that still counts as inside the pit. */
    val margin: Float = DEFAULT_MARGIN,
    /** Extra blocks kept clear around every fire block: the area within this radius counts as fire too. */
    val fireRadius: Float = DEFAULT_FIRE_RADIUS,
    /**
     * Stop the player from stepping into fire (and the fire avoid radius), like an invisible wall. Movement is only
     * shortened on the client; no packets are sent or changed.
     */
    val hardAvoid: Boolean = true,
    /** Longest escape path considered, in blocks. */
    val maxEscapeDistance: Float = DEFAULT_MAX_ESCAPE_DISTANCE,
    val walkTimeoutMs: Int = DEFAULT_WALK_TIMEOUT_MS,
    val sound: Boolean = true,
    val debugLog: Boolean = false,
    /** When true the helper only runs during a Blaze slayer boss fight. */
    val onlyInBossFight: Boolean = true,
    /** Quazii beam helper: warn before the beam sweeps the player, or also jump over it in place. */
    val beamDodge: BeamDodge = BeamDodge.WARN,
    /** Jump only while health is below this percent; 100 jumps every time. A beam is 1,500 true damage. */
    val beamDodgeBelowHp: Int = DEFAULT_BEAM_DODGE_BELOW_HP,
    /** Keep out of Quazii's hitbox, where the beam hits repeatedly. Needs the beam helper on (Warn or Jump). */
    val avoidQuaziiBody: Boolean = true,
    /** Moves the beam jump this many ticks earlier than the measured timing. */
    val beamJumpEarlyTicks: Int = 0,
    /** Treat the block Quazii stands on, and everything within [quaziiStandRadius], like a fire pit. Needs [enabled]. */
    val quaziiStand: Boolean = false,
    /** Blocks around Quazii's feet that count as a pit; 0 is just the block he stands on. */
    val quaziiStandRadius: Float = DEFAULT_QUAZII_STAND_RADIUS,
    /** Treat the zone around mobs named in [avoidMobNames] like a fire pit. Needs [enabled]. */
    val avoidMobs: Boolean = true,
    /** Comma separated name parts, case-insensitive, matched against name tags such as "Typhoeus". */
    val avoidMobNames: String = "Typhoeus",
    /** Blocks around each matching mob's feet that count as a pit. */
    val avoidMobRadius: Float = DEFAULT_QUAZII_STAND_RADIUS,
    /** Treat the ground around every fire pillar ("2s 8 hits" countdown stand) like a pit. Needs [enabled]. */
    val avoidPillars: Boolean = true,
    /** Blocks around a fire pillar that count as a pit. */
    val pillarRadius: Float = DEFAULT_PILLAR_RADIUS,
    /** Earliest pit colour that counts as a pit. */
    val pitPhase: PitPhase = PitPhase.RED,
    /** Detection speed limit for pits: a pit must be seen this long before it counts. 0 reacts at once. */
    val pitDelayMs: Int = 0,
    /** Detection speed limit for fire on the ground: fire must be seen this long before it counts. */
    val fireDelayMs: Int = 0,
    /**
     * While airborne, steer in the air (client movement only) when the predicted landing block is a pit, fire or
     * Quazii's zone.
     */
    val airAvoid: Boolean = false,
    /**
     * Priorities, 0-10, higher wins. When two hazards compete, the higher one gets the action bar line, and a hazard
     * you stand in that outranks the beam stops the beam jump. Ties go to the beam (it still jumps and warns).
     */
    val priorityBeam: Int = DEFAULT_PRIORITY_BEAM,
    val priorityFire: Int = DEFAULT_PRIORITY_FIRE,
    val priorityPitRed: Int = DEFAULT_PRIORITY_PIT_RED,
    val priorityPitYellow: Int = DEFAULT_PRIORITY_PIT_YELLOW,
    val priorityPitBlack: Int = DEFAULT_PRIORITY_PIT_BLACK,
    val priorityQuaziiZone: Int = DEFAULT_PRIORITY_QUAZII_ZONE,
) {
    /** Priority of a pit in [phase] 0 (red), 1 (yellow) or 2 (black). */
    fun pitPriority(phase: Int): Int = when (phase) {
        1 -> priorityPitYellow
        2 -> priorityPitBlack
        else -> priorityPitRed
    }

    fun toJsonObject(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("version", FORMAT_VERSION)
        obj.addProperty("enabled", enabled)
        obj.addProperty("mode", mode.id)
        val blocks = JsonArray()
        pitBlocks.forEach { blocks.add(it) }
        obj.add("pitBlocks", blocks)
        obj.addProperty("scanRadius", scanRadius)
        obj.addProperty("margin", margin)
        obj.addProperty("fireRadius", fireRadius)
        obj.addProperty("hardAvoid", hardAvoid)
        obj.addProperty("maxEscapeDistance", maxEscapeDistance)
        obj.addProperty("walkTimeoutMs", walkTimeoutMs)
        obj.addProperty("sound", sound)
        obj.addProperty("debugLog", debugLog)
        obj.addProperty("onlyInBossFight", onlyInBossFight)
        obj.addProperty("beamDodge", beamDodge.id)
        obj.addProperty("beamDodgeBelowHp", beamDodgeBelowHp)
        obj.addProperty("avoidQuaziiBody", avoidQuaziiBody)
        obj.addProperty("beamJumpEarlyTicks", beamJumpEarlyTicks)
        obj.addProperty("quaziiStand", quaziiStand)
        obj.addProperty("quaziiStandRadius", quaziiStandRadius)
        obj.addProperty("avoidMobs", avoidMobs)
        obj.addProperty("avoidMobNames", avoidMobNames)
        obj.addProperty("avoidMobRadius", avoidMobRadius)
        obj.addProperty("avoidPillars", avoidPillars)
        obj.addProperty("pillarRadius", pillarRadius)
        obj.addProperty("pitPhase", pitPhase.id)
        obj.addProperty("pitDelayMs", pitDelayMs)
        obj.addProperty("fireDelayMs", fireDelayMs)
        obj.addProperty("airAvoid", airAvoid)
        obj.addProperty("priorityBeam", priorityBeam)
        obj.addProperty("priorityFire", priorityFire)
        obj.addProperty("priorityPitRed", priorityPitRed)
        obj.addProperty("priorityPitYellow", priorityPitYellow)
        obj.addProperty("priorityPitBlack", priorityPitBlack)
        obj.addProperty("priorityQuaziiZone", priorityQuaziiZone)
        return obj
    }

    companion object {
        const val FORMAT_VERSION: Int = 1

        const val MIN_SCAN_RADIUS: Int = 3
        const val MAX_SCAN_RADIUS: Int = 12
        const val DEFAULT_SCAN_RADIUS: Int = 7

        const val MIN_MARGIN: Float = 0f
        const val MAX_MARGIN: Float = 1.5f
        const val DEFAULT_MARGIN: Float = 0.4f

        const val MIN_FIRE_RADIUS: Float = 0f
        const val MAX_FIRE_RADIUS: Float = 4f
        const val DEFAULT_FIRE_RADIUS: Float = 0f

        const val MIN_MAX_ESCAPE_DISTANCE: Float = 2f
        const val MAX_MAX_ESCAPE_DISTANCE: Float = 10f
        const val DEFAULT_MAX_ESCAPE_DISTANCE: Float = 6f

        const val MIN_WALK_TIMEOUT_MS: Int = 500
        const val MAX_WALK_TIMEOUT_MS: Int = 5000
        const val DEFAULT_WALK_TIMEOUT_MS: Int = 2500

        const val MIN_BEAM_DODGE_BELOW_HP: Int = 10
        const val MAX_BEAM_DODGE_BELOW_HP: Int = 100
        const val DEFAULT_BEAM_DODGE_BELOW_HP: Int = 100

        const val MAX_BEAM_JUMP_EARLY_TICKS: Int = 6

        const val DEFAULT_PILLAR_RADIUS: Float = 3f

        const val MIN_QUAZII_STAND_RADIUS: Float = 0f
        const val MAX_QUAZII_STAND_RADIUS: Float = 6f
        const val DEFAULT_QUAZII_STAND_RADIUS: Float = 3f

        const val MAX_DETECT_DELAY_MS: Int = 1000

        const val MAX_PRIORITY: Int = 10

        /** The beam hits harder than ground fire, but a pit in its yellow phase (fire next) outranks the beam. */
        const val DEFAULT_PRIORITY_BEAM: Int = 5
        const val DEFAULT_PRIORITY_FIRE: Int = 3
        const val DEFAULT_PRIORITY_PIT_RED: Int = 4
        const val DEFAULT_PRIORITY_PIT_YELLOW: Int = 7
        const val DEFAULT_PRIORITY_PIT_BLACK: Int = 8
        const val DEFAULT_PRIORITY_QUAZII_ZONE: Int = 6

        /**
         * Confirmed from a T4 fight's debug log: each pit is a plus of floor blocks that turns red, then yellow,
         * then black terracotta (about 1.5 s in total) before fire appears on top of it.
         */
        val DEFAULT_PIT_BLOCKS: List<String> = listOf(
            "minecraft:red_terracotta",
            "minecraft:yellow_terracotta",
            "minecraft:black_terracotta",
            "minecraft:fire",
        )

        /** The first release's guessed list. A saved file still holding exactly this gets the confirmed list. */
        private val GUESSED_PIT_BLOCKS: List<String> = listOf(
            "minecraft:red_terracotta",
            "minecraft:fire",
            "minecraft:magma_block",
        )

        private val BLOCK_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

        fun parse(json: String): FirePitConfig {
            val root = try {
                JsonParser.parseString(json)
            } catch (_: Exception) {
                return FirePitConfig()
            }
            if (!root.isJsonObject) return FirePitConfig()
            return fromJsonObject(root.asJsonObject)
        }

        fun fromJsonObject(obj: JsonObject): FirePitConfig {
            val defaults = FirePitConfig()
            return FirePitConfig(
                enabled = bool(obj, "enabled", defaults.enabled),
                mode = EscapeMode.fromId(string(obj, "mode")) ?: defaults.mode,
                pitBlocks = blockIds(obj.get("pitBlocks"))
                    ?.takeUnless { it == GUESSED_PIT_BLOCKS } ?: defaults.pitBlocks,
                scanRadius = int(obj, "scanRadius", defaults.scanRadius)
                    .coerceIn(MIN_SCAN_RADIUS, MAX_SCAN_RADIUS),
                margin = float(obj, "margin", defaults.margin).coerceIn(MIN_MARGIN, MAX_MARGIN),
                fireRadius = float(obj, "fireRadius", defaults.fireRadius).coerceIn(MIN_FIRE_RADIUS, MAX_FIRE_RADIUS),
                hardAvoid = bool(obj, "hardAvoid", defaults.hardAvoid),
                maxEscapeDistance = float(obj, "maxEscapeDistance", defaults.maxEscapeDistance)
                    .coerceIn(MIN_MAX_ESCAPE_DISTANCE, MAX_MAX_ESCAPE_DISTANCE),
                walkTimeoutMs = int(obj, "walkTimeoutMs", defaults.walkTimeoutMs)
                    .coerceIn(MIN_WALK_TIMEOUT_MS, MAX_WALK_TIMEOUT_MS),
                sound = bool(obj, "sound", defaults.sound),
                debugLog = bool(obj, "debugLog", defaults.debugLog),
                onlyInBossFight = bool(obj, "onlyInBossFight", defaults.onlyInBossFight),
                beamDodge = BeamDodge.fromId(string(obj, "beamDodge")) ?: defaults.beamDodge,
                beamDodgeBelowHp = int(obj, "beamDodgeBelowHp", defaults.beamDodgeBelowHp)
                    .coerceIn(MIN_BEAM_DODGE_BELOW_HP, MAX_BEAM_DODGE_BELOW_HP),
                avoidQuaziiBody = bool(obj, "avoidQuaziiBody", defaults.avoidQuaziiBody),
                beamJumpEarlyTicks = int(obj, "beamJumpEarlyTicks", defaults.beamJumpEarlyTicks)
                    .coerceIn(0, MAX_BEAM_JUMP_EARLY_TICKS),
                quaziiStand = bool(obj, "quaziiStand", defaults.quaziiStand),
                quaziiStandRadius = float(obj, "quaziiStandRadius", defaults.quaziiStandRadius)
                    .coerceIn(MIN_QUAZII_STAND_RADIUS, MAX_QUAZII_STAND_RADIUS),
                avoidMobs = bool(obj, "avoidMobs", defaults.avoidMobs),
                avoidMobNames = string(obj, "avoidMobNames") ?: defaults.avoidMobNames,
                avoidMobRadius = float(obj, "avoidMobRadius", defaults.avoidMobRadius)
                    .coerceIn(MIN_QUAZII_STAND_RADIUS, MAX_QUAZII_STAND_RADIUS),
                avoidPillars = bool(obj, "avoidPillars", defaults.avoidPillars),
                pillarRadius = float(obj, "pillarRadius", defaults.pillarRadius)
                    .coerceIn(MIN_QUAZII_STAND_RADIUS, MAX_QUAZII_STAND_RADIUS),
                pitPhase = PitPhase.fromId(string(obj, "pitPhase")) ?: defaults.pitPhase,
                pitDelayMs = int(obj, "pitDelayMs", defaults.pitDelayMs).coerceIn(0, MAX_DETECT_DELAY_MS),
                fireDelayMs = int(obj, "fireDelayMs", defaults.fireDelayMs).coerceIn(0, MAX_DETECT_DELAY_MS),
                airAvoid = bool(obj, "airAvoid", defaults.airAvoid),
                priorityBeam = priority(obj, "priorityBeam", defaults.priorityBeam),
                priorityFire = priority(obj, "priorityFire", defaults.priorityFire),
                priorityPitRed = priority(obj, "priorityPitRed", defaults.priorityPitRed),
                priorityPitYellow = priority(obj, "priorityPitYellow", defaults.priorityPitYellow),
                priorityPitBlack = priority(obj, "priorityPitBlack", defaults.priorityPitBlack),
                priorityQuaziiZone = priority(obj, "priorityQuaziiZone", defaults.priorityQuaziiZone),
            )
        }

        /** Null when the element is missing or not an array, so the default list is used. */
        private fun blockIds(element: JsonElement?): List<String>? {
            if (element == null || !element.isJsonArray) return null
            return element.asJsonArray
                .mapNotNull { item ->
                    item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString?.trim()?.lowercase()
                }
                .filter { BLOCK_ID.matches(it) }
                .distinct()
        }

        private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

        private fun string(obj: JsonObject, key: String): String? =
            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun int(obj: JsonObject, key: String, default: Int): Int {
            val number = number(obj, key) ?: return default
            return number.toInt()
        }

        private fun priority(obj: JsonObject, key: String, default: Int): Int =
            int(obj, key, default).coerceIn(0, MAX_PRIORITY)

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
