package net.icantpy.slayer.aim

import net.icantpy.dungeon.DungeonListener
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Where each blood mob will appear. A blood mob glides on a player-head armor stand in a straight line and
 * materialises after a fixed travel distance, so the drop point is the first seen position plus the travel
 * direction scaled by that distance (same model as the Odin and NoammAddons blood helpers). It only reads entity
 * positions the client already has; nothing is sent.
 */
object BloodAimPoints {
    /** Travel distance of the first group of spawns and of every later group, in blocks. */
    const val FIRST_SPAWN_DISTANCE = 16.1
    const val LATER_SPAWN_DISTANCE = 11.9

    /** A stand must have moved this far from where it was first seen before its direction is trusted. */
    const val MIN_TRAVEL = 0.3

    /** The Watcher must be within this many blocks of a stand for it to count as a blood mob. */
    private const val WATCHER_RANGE = 20.0

    /** The mob stands about this far above the stand's recorded position, so the drop box is centred here. */
    const val BOX_RISE = 2.0

    private const val BOX_SIZE = 1.0

    private const val FIRST_GROUP_LINE = "[BOSS] The Watcher: Let's see how you can handle this."

    private class Track(val start: Vec3, val firstSpawn: Boolean) {
        var current: Vec3 = start
    }

    private val tracks = HashMap<Int, Track>()
    private var firstSpawns = true

    /** The Watcher's first-group line: every spawn after it belongs to a later group. */
    fun onChat(message: String) {
        if (message.contains(FIRST_GROUP_LINE)) firstSpawns = false
    }

    fun clear() {
        tracks.clear()
        firstSpawns = true
    }

    /** Predicted spawn point for a stand first seen at [start] and now at [current], or null while it has barely moved. */
    fun endpoint(start: Vec3, current: Vec3, firstSpawn: Boolean): Vec3? {
        val delta = current.subtract(start)
        if (delta.lengthSqr() < MIN_TRAVEL * MIN_TRAVEL) return null
        return start.add(delta.normalize().scale(if (firstSpawn) FIRST_SPAWN_DISTANCE else LATER_SPAWN_DISTANCE))
    }

    /** The point to aim at for a drop at [endpoint]: the centre of the one-block box drawn above it. */
    fun aimPoint(endpoint: Vec3): Vec3 = endpoint.add(0.0, BOX_RISE, 0.0)

    fun box(aim: Vec3): AABB = AABB.ofSize(aim, BOX_SIZE, BOX_SIZE, BOX_SIZE)

    /** Client tick: starts tracking new blood stands near the Watcher and drops ones that are gone. */
    fun tick(level: ClientLevel) {
        val state = DungeonListener.state
        if (!state.inDungeon || state.inBoss) {
            if (tracks.isNotEmpty()) clear()
            return
        }
        val watchers = level.entitiesForRendering().filterIsInstance<Zombie>()
            .filter { it.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.PLAYER_HEAD) }
        val live = HashSet<Int>()
        if (watchers.isNotEmpty()) {
            for (entity in level.entitiesForRendering()) {
                if (entity !is ArmorStand || !entity.isAlive) continue
                if (!entity.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.PLAYER_HEAD)) continue
                val track = tracks[entity.id]
                if (track == null) {
                    if (watchers.none { it.distanceToSqr(entity) <= WATCHER_RANGE * WATCHER_RANGE }) continue
                    tracks[entity.id] = Track(entity.position(), firstSpawns)
                } else {
                    track.current = entity.position()
                }
                live += entity.id
            }
        }
        tracks.keys.retainAll(live)
    }

    /** Aim points of every tracked stand with a trusted direction, keyed by stand id. */
    fun points(): Map<Int, Vec3> {
        val out = HashMap<Int, Vec3>()
        for ((id, track) in tracks) {
            val end = endpoint(track.start, track.current, track.firstSpawn) ?: continue
            out[id] = aimPoint(end)
        }
        return out
    }

    /**
     * The aim point nearest the crosshair within [maxAngleDeg] of it, keeping [currentId] unless another is
     * [switchMarginDeg] closer. Returns the stand id and the point.
     */
    fun pick(player: LocalPlayer, eye: Vec3, currentId: Int, maxAngleDeg: Float, switchMarginDeg: Float): Pair<Int, Vec3>? {
        val pose = AimPose(player.getYRot(), player.getXRot())
        val pts = points()
        val angles = HashMap<Int, Float>()
        for ((id, p) in pts) {
            val a = AimMath.angularDistance(pose, AimMath.lookAt(eye.x, eye.y, eye.z, p.x, p.y, p.z))
            if (a <= maxAngleDeg) angles[id] = a
        }
        val best = angles.minByOrNull { it.value } ?: return null
        val keep = angles[currentId]?.takeIf { it <= best.value + switchMarginDeg }
        val id = if (keep != null) currentId else best.key
        return id to pts.getValue(id)
    }
}
