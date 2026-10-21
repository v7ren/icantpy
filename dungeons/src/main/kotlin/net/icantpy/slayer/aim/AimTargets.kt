package net.icantpy.slayer.aim

import net.icantpy.location.SkyblockLocation
import net.icantpy.slayer.SlayerCarryCounter
import net.icantpy.util.Text
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Picks the aim target. The selection runs every [SCAN_INTERVAL_TICKS] client ticks and caches the entity id; the
 * per-frame aim point is recomputed from that entity, so the camera follows a moving boss.
 */
object AimTargets {
    private const val SCAN_INTERVAL_TICKS = 5
    private const val OWNER_STAND_RANGE = 6.0
    private const val LABEL_ABOVE_RANGE = 3.0
    private const val CHEST_HEIGHT_FRACTION = 0.7

    /** A new candidate must be this many degrees closer to the crosshair than the current target to take over. */
    private const val SWITCH_MARGIN_DEG = 15f

    /** How far sideways (blocks) a hologram may be from the mob it labels. */
    private const val TAG_HORIZONTAL_RANGE = 1.5

    /** Second, wider pass for a hologram lagging behind its moving mob. */
    private const val TAG_RELAXED_RANGE = 4.0

    /** Hologram height window relative to the mob: from slightly below its feet to this far above its head. */
    private const val TAG_BELOW_FEET = 0.5
    private const val TAG_ABOVE_HEAD = 4.0

    private var clock = 0
    private var targetId: Int? = null

    fun clear() {
        clock = 0
        targetId = null
    }

    /** Client tick. Rescans for a target on the first tick and then every [SCAN_INTERVAL_TICKS] ticks. */
    fun tick(level: ClientLevel, player: LocalPlayer, config: AimConfig) {
        val due = clock % SCAN_INTERVAL_TICKS == 0
        clock++
        if (!due) return
        targetId = pick(level, player, config)?.id
    }

    /** Cached target when it still exists and is alive, otherwise null. */
    fun current(level: ClientLevel): LivingEntity? {
        val id = targetId ?: return null
        val entity = level.getEntity(id) as? LivingEntity ?: return null
        return entity.takeIf { it.isAlive }
    }

    /** Aim point on [entity] for [point], using the interpolated position at [partialTick]. */
    fun aimPointFor(entity: Entity, point: AimPoint, partialTick: Float): Vec3 {
        if (point == AimPoint.HEAD) return entity.getEyePosition(partialTick)
        val base = entity.getPosition(partialTick)
        val fraction = if (point == AimPoint.CENTER) 0.5 else CHEST_HEIGHT_FRACTION
        return Vec3(base.x, base.y + entity.bbHeight * fraction, base.z)
    }

    private fun pick(level: ClientLevel, player: LocalPlayer, config: AimConfig): LivingEntity? {
        val eye = player.getEyePosition(1f)
        val own = if (config.ownSlayerBoss) ownSlayerBosses(level, player) else emptyList()
        val extra = if (config.extraTargets.isEmpty()) emptyList() else extraTargets(level, player, config)
        val hold = holdContext(level, player)
        val ownChoice = closestToCrosshair(level, player, eye, own, config, hold.attunement, hold.shields)
        val choice = ownChoice ?: closestToCrosshair(level, player, eye, extra, config, hold.attunement, hold.shields)
        if (choice == null) lastMiss = describeMiss(level, player, eye, own + extra, config, hold)
        return choice
    }

    /**
     * What the player holds for Hellion Shield matching. [attunement] is set only when the held item is a Blaze dagger
     * whose lore reads "Attuned: <Name>" with a name that dagger can carry. [shields] maps a mob id to the attunement
     * on the name tag under it (or on its own name); it is filled only while an attunement is known.
     */
    private class HoldContext(
        val dagger: Dagger?,
        val attunement: HellionAttunement?,
        val shields: Map<Int, HellionAttunement>,
    )

    private fun holdContext(level: ClientLevel, player: LocalPlayer): HoldContext {
        val item = player.mainHandItem
        if (item.isEmpty) return HoldContext(null, null, emptyMap())
        val dagger = Dagger.daggerOf(Text.strip(item.hoverName.string)) ?: return HoldContext(null, null, emptyMap())
        val lore = item.get(DataComponents.LORE)?.lines()?.map { Text.strip(it.string) }.orEmpty()
        val attunement = HellionAttunement.fromLore(lore)?.takeIf { it in dagger.attunements }
            ?: return HoldContext(dagger, null, emptyMap())
        return HoldContext(dagger, attunement, shieldsOf(level))
    }

    /** Mob id -> attunement shown for it: its own name, or a name tag resolved to it with [mobUnder]. */
    private fun shieldsOf(level: ClientLevel): Map<Int, HellionAttunement> {
        val stands = ArrayList<ArmorStand>()
        val mobs = ArrayList<LivingEntity>()
        for (entity in level.entitiesForRendering()) {
            when {
                entity is ArmorStand -> stands += entity
                entity is LivingEntity && entity !is Player && entity.isAlive -> mobs += entity
            }
        }
        val shields = HashMap<Int, HellionAttunement>()
        for (mob in mobs) {
            HellionAttunement.fromTag(Text.strip(mob.name.string))?.let { shields[mob.id] = it }
        }
        for (stand in stands) {
            val attunement = HellionAttunement.fromTag(Text.strip(stand.name.string)) ?: continue
            val mob = mobUnder(mobs, stand) ?: continue
            shields.putIfAbsent(mob.id, attunement)
        }
        return shields
    }

    /**
     * The viable candidate nearest the crosshair, but the current target is kept unless another one is at least
     * [SWITCH_MARGIN_DEG] closer, so aim doesn't flick between a boss and its demons standing side by side.
     * With a known attunement, only candidates whose shield matches are considered; when none match (or none is
     * viable among them) the whole viable set is used as before.
     */
    private fun closestToCrosshair(
        level: ClientLevel,
        player: LocalPlayer,
        eye: Vec3,
        candidates: List<LivingEntity>,
        config: AimConfig,
        attunement: HellionAttunement?,
        shields: Map<Int, HellionAttunement>,
    ): LivingEntity? {
        val viable = candidates.filter { viable(level, player, eye, it, config) }
        val matching = if (attunement == null) viable else viable.filter { shields[it.id] == attunement }
        val pool = matching.ifEmpty { viable }
        val best = pool.minByOrNull { angleTo(player, eye, it, config) } ?: return null
        val current = pool.firstOrNull { it.id == targetId } ?: return best
        val keep = angleTo(player, eye, current, config) <= angleTo(player, eye, best, config) + SWITCH_MARGIN_DEG
        return if (keep) current else best
    }

    /** Why the last scan found nothing, for the rate-limited diagnostic log. Only built when a scan misses. */
    var lastMiss: String = ""
        private set

    private fun describeMiss(
        level: ClientLevel,
        player: LocalPlayer,
        eye: Vec3,
        candidates: List<LivingEntity>,
        config: AimConfig,
        hold: HoldContext,
    ): String {
        val tags = level.entitiesForRendering().asSequence()
            .filterIsInstance<ArmorStand>()
            .map { Text.strip(it.name.string) }
            .filter { it.contains("Spawned by", ignoreCase = true) || SlayerBossNames.isBossTag(it) }
            .take(6).toList()
        val reasons = candidates.take(4).joinToString { mob ->
            val aim = aimPointFor(mob, config.aimPoint, 1f)
            val distance = eye.distanceTo(aim)
            val angle = angleTo(player, eye, mob, config)
            val sight = !config.requireLineOfSight || hasLineOfSight(level, player, eye, aim)
            val shield = hold.shields[mob.id]?.name ?: "-"
            "${mob.type.toShortString()} d=%.1f a=%.0f los=$sight shield=$shield".format(distance, angle)
        }
        val held = "held=${hold.dagger?.name ?: "none"} attunement=${hold.attunement?.name ?: "unknown"}"
        val fight = SkyblockLocation.snapshot.slayerBossVisible
        return "you=${player.gameProfile.name} slayBoss=$fight $held tags=$tags candidates=[$reasons]"
    }

    private fun viable(level: ClientLevel, player: LocalPlayer, eye: Vec3, entity: LivingEntity, config: AimConfig): Boolean {
        if (entity === player || !entity.isAlive) return false
        val aim = aimPointFor(entity, config.aimPoint, 1f)
        if (eye.distanceTo(aim) > config.maxRange) return false
        if (angleTo(player, eye, entity, config) > config.fovLimit / 2f) return false
        return !config.requireLineOfSight || hasLineOfSight(level, player, eye, aim)
    }

    private fun angleTo(player: LocalPlayer, eye: Vec3, entity: LivingEntity, config: AimConfig): Float {
        val aim = aimPointFor(entity, config.aimPoint, 1f)
        val wanted = AimMath.lookAt(eye.x, eye.y, eye.z, aim.x, aim.y, aim.z)
        val current = AimPose(player.getYRot(), player.getXRot())
        return AimMath.angularDistance(current, wanted)
    }

    private fun hasLineOfSight(level: ClientLevel, player: LocalPlayer, eye: Vec3, aim: Vec3): Boolean {
        val hit = level.clip(ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        return hit.type == HitResult.Type.MISS
    }

    /**
     * The player's own boss. First any mob under a "Spawned by: <local name>" hologram (the owner tag the carry
     * counter reads). Hypixel does not show the owner that line on their own boss, so while the sidebar says
     * "Slay the boss!" the fallback is any mob under a slayer boss name tag that another player's "Spawned by"
     * does not claim.
     */
    private fun ownSlayerBosses(level: ClientLevel, player: LocalPlayer): List<LivingEntity> {
        val name = player.gameProfile.name
        val ownTags = ArrayList<ArmorStand>()
        val otherTags = ArrayList<ArmorStand>()
        val bossTags = ArrayList<ArmorStand>()
        val mobs = ArrayList<LivingEntity>()
        for (entity in level.entitiesForRendering()) {
            when {
                entity is ArmorStand -> {
                    val label = entity.name.string
                    val owner = SlayerCarryCounter.ownerFromTag(label)
                    when {
                        owner == null -> if (SlayerBossNames.isBossTag(Text.strip(label))) bossTags += entity
                        owner.equals(name, ignoreCase = true) -> ownTags += entity
                        else -> otherTags += entity
                    }
                }
                entity is LivingEntity && entity !is Player && entity.isAlive -> mobs += entity
            }
        }
        val owned = ownTags.mapNotNull { mobUnder(mobs, it) }
        if (!SkyblockLocation.snapshot.slayerBossVisible) return owned.distinct()
        // During the fight, unclaimed boss tags count too: the owner may see no "Spawned by" line, and the
        // Inferno Demonlord's split demons (Quazii, Typhoeus) carry only a name tag. [pick] then chooses among
        // them by how close each is to the crosshair, so looking at a demon targets it.
        val claimedSq = OWNER_STAND_RANGE * OWNER_STAND_RANGE
        val unclaimed = bossTags
            .filter { tag -> otherTags.none { it.distanceToSqr(tag) <= claimedSq } }
            .mapNotNull { mobUnder(mobs, it) }
        // Some bosses (the demons) carry the name on the mob itself rather than on a hologram above it.
        val named = mobs.filter { mob ->
            SlayerBossNames.isBossTag(Text.strip(mob.name.string)) &&
                otherTags.none { it.distanceToSqr(mob) <= claimedSq }
        }
        return (owned + unclaimed + named).distinct()
    }

    /**
     * The mob a hologram belongs to. Hypixel stacks a boss's name and "Spawned by" tags straight above it, so only a
     * mob nearly under the tag counts (within [TAG_HORIZONTAL_RANGE] blocks sideways, tag between its feet and a few
     * blocks over its head). The most directly-under mob wins, so a miniboss standing next to the boss is ignored.
     */
    private fun mobUnder(mobs: List<LivingEntity>, stand: ArmorStand): LivingEntity? =
        mobUnder(mobs, stand, TAG_HORIZONTAL_RANGE)
            // Holograms trail a fast-moving boss, so fall back to a wider (still most-directly-under) match.
            ?: mobUnder(mobs, stand, TAG_RELAXED_RANGE)

    private fun mobUnder(mobs: List<LivingEntity>, stand: ArmorStand, range: Double): LivingEntity? {
        var best: LivingEntity? = null
        var bestSq = range * range
        for (mob in mobs) {
            val rise = stand.y - mob.y
            if (rise < -TAG_BELOW_FEET || rise > mob.bbHeight + TAG_ABOVE_HEAD) continue
            val dx = stand.x - mob.x
            val dz = stand.z - mob.z
            val sideSq = dx * dx + dz * dz
            if (sideSq <= bestSq) {
                bestSq = sideSq
                best = mob
            }
        }
        return best
    }

    /** Mobs whose own name, or the name of an armor stand a few blocks above them, contains a filter. */
    private fun extraTargets(level: ClientLevel, player: LocalPlayer, config: AimConfig): List<LivingEntity> {
        val filters = config.extraTargets.map { it.lowercase() }
        val stands = ArrayList<ArmorStand>()
        val mobs = ArrayList<LivingEntity>()
        for (entity in level.entitiesForRendering()) {
            when {
                entity is ArmorStand -> stands += entity
                entity is LivingEntity && entity !is Player && entity.isAlive -> mobs += entity
            }
        }
        return mobs.filter { mob ->
            val labels = ArrayList<String>(2)
            labels += Text.strip(mob.name.string)
            for (stand in stands) {
                if (isLabelAbove(stand, mob)) labels += Text.strip(stand.name.string)
            }
            labels.any { label -> filters.any { label.lowercase().contains(it) } }
        }
    }

    private fun isLabelAbove(stand: ArmorStand, mob: LivingEntity): Boolean {
        val dy = stand.y - mob.y
        if (dy < 0.0 || dy > LABEL_ABOVE_RANGE) return false
        val dx = stand.x - mob.x
        val dz = stand.z - mob.z
        return dx * dx + dz * dz <= LABEL_ABOVE_RANGE * LABEL_ABOVE_RANGE
    }
}
