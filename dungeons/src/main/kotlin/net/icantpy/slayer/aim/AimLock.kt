package net.icantpy.slayer.aim

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.compat.McCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

/**
 * Debug lock-on. Pressing [AimConfig.lockKey] locks onto the mob under the crosshair (or, failing that, the mob
 * closest to the crosshair inside a small cone); Boss Aim then follows it until it dies or despawns, or the key is
 * pressed again. While locked, the normal activation and target filters are skipped.
 */
object AimLock {
    private const val LOCK_RANGE = 48.0

    /** Widest angle (degrees) from the crosshair a mob may be at to be picked when nothing is directly targeted. */
    private const val LOCK_CONE_DEG = 8f

    private var lockedId: Int? = null
    private var keyWasDown = false

    val locked: Boolean
        get() = lockedId != null

    /** Client tick: handles the key's press edge and drops a lock whose mob is gone. */
    fun tick(mc: Minecraft, level: ClientLevel, player: LocalPlayer, config: AimConfig) {
        if (lockedId != null && current(level) == null) release(player, "Lock released: target gone")
        if (config.lockKey == AimConfig.UNBOUND) {
            keyWasDown = false
            return
        }
        val down = InputConstants.isKeyDown(mc.window, config.lockKey)
        val pressed = down && !keyWasDown && McCompat.currentScreen(mc) == null
        keyWasDown = down
        if (!pressed) return
        if (lockedId != null) {
            release(player, "Lock released")
            return
        }
        val target = pick(mc, level, player)
        if (target == null) {
            player.sendOverlayMessage(Component.literal("Lock-on: no mob under the crosshair"))
            return
        }
        lockedId = target.id
        player.sendOverlayMessage(Component.literal("Locked on ${target.name.string}"))
    }

    /** The locked mob while it still exists and is alive, otherwise null. */
    fun current(level: ClientLevel): LivingEntity? {
        val id = lockedId ?: return null
        return (level.getEntity(id) as? LivingEntity)?.takeIf { it.isAlive && !it.isRemoved }
    }

    fun clear() {
        lockedId = null
        keyWasDown = false
    }

    private fun release(player: LocalPlayer, message: String) {
        lockedId = null
        player.sendOverlayMessage(Component.literal(message))
    }

    private fun pick(mc: Minecraft, level: ClientLevel, player: LocalPlayer): LivingEntity? {
        val direct = mc.crosshairPickEntity as? LivingEntity
        if (direct != null && isLockable(direct, player)) return direct
        val eye = player.getEyePosition(1f)
        val look = AimPose(player.getYRot(), player.getXRot())
        var best: LivingEntity? = null
        var bestAngle = LOCK_CONE_DEG
        for (entity in level.entitiesForRendering()) {
            val mob = entity as? LivingEntity ?: continue
            if (!isLockable(mob, player) || mob.distanceTo(player) > LOCK_RANGE) continue
            val center = mob.boundingBox.center
            val angle = AimMath.angularDistance(look, AimMath.lookAt(eye.x, eye.y, eye.z, center.x, center.y, center.z))
            if (angle <= bestAngle) {
                bestAngle = angle
                best = mob
            }
        }
        return best
    }

    private fun isLockable(mob: LivingEntity, player: LocalPlayer): Boolean =
        mob !== player && mob !is ArmorStand && mob.isAlive
}
