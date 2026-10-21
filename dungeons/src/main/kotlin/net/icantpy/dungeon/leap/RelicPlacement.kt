package net.icantpy.dungeon.leap

import kotlin.math.hypot

/** One M7 P5 relic cauldron. [x] and [z] are block coordinates; the relic sits on the block center. */
data class RelicCauldron(val color: String, val blockX: Int, val blockZ: Int) {
    val centerX: Double get() = blockX + 0.5
    val centerZ: Double get() = blockZ + 0.5
}

/** A relic-bearing armor stand reduced to what placement detection needs. */
data class RelicStandSample(val x: Double, val z: Double, val color: String?)

/**
 * Pure relic placement rules: which color a relic item or head names, where each cauldron is, and
 * when a relic counts as placed. Minecraft reads happen in the callers.
 */
object RelicPlacement {
    val CAULDRONS: List<RelicCauldron> = listOf(
        RelicCauldron("Red", 51, 42),
        RelicCauldron("Orange", 57, 42),
        RelicCauldron("Green", 49, 44),
        RelicCauldron("Blue", 59, 44),
        RelicCauldron("Purple", 54, 41),
    )

    /** Player must be this close (XZ, blocks) to the cauldron when their relic leaves the inventory. */
    const val SELF_PLACE_RADIUS: Double = 4.5

    /** An armor stand must be this close (XZ, blocks) to its cauldron to count as a placed relic. */
    const val STAND_PLACE_RADIUS: Double = 1.5

    private val colorIdPattern = Regex("^(RED|ORANGE|GREEN|BLUE|PURPLE)_KING_RELIC$")
    private val colorNamePattern = Regex("Corrupted (Red|Orange|Green|Blue|Purple) Relic", RegexOption.IGNORE_CASE)

    /** "RED_KING_RELIC" to "Red"; null for any other SkyBlock id. */
    fun colorOfRelicId(id: String?): String? {
        val match = colorIdPattern.matchEntire(id?.uppercase().orEmpty()) ?: return null
        return titleCase(match.groupValues[1])
    }

    /** "Corrupted Blue Relic" to "Blue"; null when the name names no relic color. */
    fun colorOfRelicName(name: String?): String? {
        val match = colorNamePattern.find(name.orEmpty()) ?: return null
        return titleCase(match.groupValues[1])
    }

    fun cauldronFor(color: String): RelicCauldron? = CAULDRONS.firstOrNull { it.color.equals(color, ignoreCase = true) }

    fun distanceXz(x: Double, z: Double, cauldron: RelicCauldron): Double =
        hypot(x - cauldron.centerX, z - cauldron.centerZ)

    fun isNearCauldron(x: Double, z: Double, color: String, radius: Double): Boolean {
        val cauldron = cauldronFor(color) ?: return false
        return distanceXz(x, z, cauldron) <= radius
    }

    private fun titleCase(word: String): String = word.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Tracks the local player's relic. Each tick the caller passes the color of the relic found in the
 * inventory (null when none) and the player's XZ position. A relic that leaves the inventory while
 * the player stands near its cauldron counts as placed, once per pickup.
 */
class SelfRelicPlacementTracker {
    private var heldColor: String? = null
    private var placedThisPickup = false

    /** Returns the color that was just placed, or null. */
    fun update(inventoryColor: String?, x: Double, z: Double): String? {
        if (inventoryColor != null) {
            if (heldColor == null || inventoryColor != heldColor) placedThisPickup = false
            heldColor = inventoryColor
            return null
        }
        val previous = heldColor
        heldColor = null
        if (previous == null || placedThisPickup) return null
        if (!RelicPlacement.isNearCauldron(x, z, previous, RelicPlacement.SELF_PLACE_RADIUS)) return null
        placedThisPickup = true
        return previous
    }

    fun reset() {
        heldColor = null
        placedThisPickup = false
    }
}

/**
 * Detects any relic placed in a cauldron by an armor stand holding a relic head. Each color fires at
 * most once until [reset] (run end or disconnect).
 */
class AnyRelicPlacementTracker {
    private val firedColors = mutableSetOf<String>()

    /** Returns colors newly placed in this scan, in input order, without duplicates. */
    fun scan(samples: List<RelicStandSample>): List<String> {
        val placed = mutableListOf<String>()
        for (sample in samples) {
            val color = sample.color ?: continue
            if (color in firedColors) continue
            if (!RelicPlacement.isNearCauldron(sample.x, sample.z, color, RelicPlacement.STAND_PLACE_RADIUS)) continue
            firedColors += color
            placed += color
        }
        return placed
    }

    fun reset() {
        firedColors.clear()
    }
}

/** Minimum-interval gate for events that would otherwise repeat on every chat line. */
class LeapEventThrottle(private val intervalMs: Long) {
    private var lastMs: Long? = null

    /** True when the event may fire at [nowMs]; records the fire time when true. */
    fun allow(nowMs: Long): Boolean {
        val last = lastMs
        if (last != null && nowMs - last < intervalMs) return false
        lastMs = nowMs
        return true
    }

    fun reset() {
        lastMs = null
    }
}
