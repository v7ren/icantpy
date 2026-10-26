package net.icantpy.slayer.blaze

import net.icantpy.util.Text
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import kotlin.math.floor

/**
 * Pit zone around Quazii. Finds each Quazii body the way the beam helper does (a wither skeleton under his name tag)
 * and turns the column he stands on, plus every column within the configured radius, into hazard columns that the
 * fire pit helper treats like a pit, so the player is told (or walked) to step back.
 */
object QuaziiStand {
    private const val BODY_RANGE = 32.0
    private val stands = ArrayList<ArmorStand>()
    private val bodies = ArrayList<WitherSkeleton>()
    private val feet = HashSet<Long>()

    /** Replaces the contents of [out] with the zone columns of every Quazii body near [player]. */
    fun zone(level: ClientLevel, player: LocalPlayer, radius: Float, out: MutableSet<Long>) {
        out.clear()
        stands.clear()
        bodies.clear()
        val rangeSq = BODY_RANGE * BODY_RANGE
        for (entity in level.entitiesForRendering()) {
            when (entity) {
                is ArmorStand -> if (Text.strip(entity.name.string).contains(QuaziiBeamDodge.TAG)) stands.add(entity)
                is WitherSkeleton -> if (entity.isAlive && entity.distanceToSqr(player) <= rangeSq) bodies.add(entity)
            }
        }
        if (stands.isEmpty() || bodies.isEmpty()) return
        feet.clear()
        for (body in bodies) {
            if (stands.none { QuaziiBeamDodge.tagAbove(it, body) }) continue
            feet.add(packXZ(floor(body.x).toInt(), floor(body.z).toInt()))
        }
        PitShapes.expand(feet, radius, out)
    }
}
