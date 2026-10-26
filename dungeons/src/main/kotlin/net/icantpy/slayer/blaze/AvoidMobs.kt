package net.icantpy.slayer.blaze

import net.icantpy.util.Text
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import kotlin.math.floor

/**
 * Custom avoid zones: every mob whose name tag (or own name) contains one of the configured names gets a zone of
 * columns within the configured radius around its feet, which the fire pit helper treats like a pit.
 */
object AvoidMobs {
    private const val RANGE = 40.0
    private const val TAG_BELOW_FEET = 0.5
    private const val TAG_ABOVE_HEAD = 4.0
    private const val TAG_HORIZONTAL_RANGE = 2.5
    private val stands = ArrayList<ArmorStand>()
    private val bodies = ArrayList<LivingEntity>()
    private val feet = HashSet<Long>()
    private val zone = HashSet<Long>()

    /** Lower-cased, trimmed, non-empty names from a comma separated list. */
    fun parse(text: String): List<String> =
        text.split(',').map { fold(it.trim()) }.filter { it.isNotEmpty() }

    /** The fire pillar's countdown stand reads like "2s 8 hits" (SkyHanni's FirePillarDisplay). */
    private val PILLAR_NAME = Regex("^\\d+(\\.\\d+)?s \\d+ hits?$")

    internal fun isPillarName(rawName: String): Boolean = PILLAR_NAME.matches(Text.strip(rawName).trim())

    /** Adds the zone columns around every fire pillar stand within range of [player] to [out]. */
    fun addPillars(level: ClientLevel, player: LocalPlayer, radius: Float, out: MutableSet<Long>) {
        feet.clear()
        val rangeSq = RANGE * RANGE
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || entity.distanceToSqr(player) > rangeSq) continue
            if (isPillarName(entity.name.string)) feet.add(packXZ(floor(entity.x).toInt(), floor(entity.z).toInt()))
        }
        if (feet.isEmpty()) return
        zone.clear()
        PitShapes.expand(feet, radius, zone)
        out.addAll(zone)
    }

    /** Adds the zone columns of every matching mob near [player] to [out]. */
    fun addZone(level: ClientLevel, player: LocalPlayer, names: List<String>, radius: Float, out: MutableSet<Long>) {
        if (names.isEmpty()) return
        stands.clear()
        bodies.clear()
        feet.clear()
        val rangeSq = RANGE * RANGE
        for (entity in level.entitiesForRendering()) {
            if (entity is ArmorStand) {
                if (matches(entity.name.string, names)) stands.add(entity)
            } else if (entity is LivingEntity && entity !is Player && entity.isAlive && entity.distanceToSqr(player) <= rangeSq) {
                if (entity.hasCustomName() && matches(entity.name.string, names)) {
                    feet.add(packXZ(floor(entity.x).toInt(), floor(entity.z).toInt()))
                } else {
                    bodies.add(entity)
                }
            }
        }
        if (stands.isNotEmpty()) {
            for (body in bodies) {
                if (stands.none { tagAbove(it, body) }) continue
                feet.add(packXZ(floor(body.x).toInt(), floor(body.z).toInt()))
            }
        }
        if (feet.isEmpty()) return
        zone.clear()
        PitShapes.expand(feet, radius, zone)
        out.addAll(zone)
    }

    internal fun matches(rawName: String, names: List<String>): Boolean {
        val name = fold(Text.strip(rawName))
        return names.any { name.contains(fold(it)) }
    }

    /** Lower-cases and turns circled letters (Hypixel spells Typhoeus and Quazii "ⓉⓎⓅⒽⓄⒺⓊⓈ") into plain ones. */
    internal fun fold(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text) {
            out.append(
                when (ch) {
                    in 'Ⓐ'..'Ⓩ' -> 'a' + (ch - 'Ⓐ')
                    in 'ⓐ'..'ⓩ' -> 'a' + (ch - 'ⓐ')
                    else -> ch.lowercaseChar()
                },
            )
        }
        return out.toString()
    }

    private fun tagAbove(stand: ArmorStand, body: LivingEntity): Boolean {
        val rise = stand.y - body.y
        if (rise < -TAG_BELOW_FEET || rise > body.bbHeight + TAG_ABOVE_HEAD) return false
        val dx = stand.x - body.x
        val dz = stand.z - body.z
        return dx * dx + dz * dz <= TAG_HORIZONTAL_RANGE * TAG_HORIZONTAL_RANGE
    }
}
