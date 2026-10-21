package net.icantpy.slayer.aim

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.compat.McCompat
import net.icantpy.mixin.KeyMappingAccessor
import net.icantpy.qol.path.PathWalker
import net.icantpy.slayer.blaze.EscapeWalker
import net.icantpy.slayer.blaze.FirePit
import net.icantpy.slayer.blaze.QuaziiBeam
import net.icantpy.slayer.blaze.QuaziiBeamDodge
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.LivingEntity
import org.lwjgl.glfw.GLFW

/**
 * Boss S-tap: while Boss Aim is on a target and the player attacks it, taps the back key for a few ticks after each
 * hit, like a PvP S-tap (it breaks the sprint and keeps the player from walking into the boss). Every tick of a tap
 * is checked:
 *  - when the path behind the player (up to [LOOKAHEAD] blocks) touches a pit or fire column the fire pit helper
 *    knows of, no tap starts and a running one stops;
 *  - when a Quazii beam sweep is due within [BEAM_QUIET_TICKS] and the player is in its reach, it does not tap, so
 *    the in-place beam jump is not disturbed.
 * It stands down while the beam dodge holds the keys, while a pit walk-out or path walk runs, with a screen open,
 * and while the player holds S themselves. No packets are sent: the key is set like a held key and given back in the
 * state the keyboard really has it.
 */
object BossStrafe {
    /** How far behind the player is checked for pits, in blocks: about one tap of walking plus a margin. */
    private const val LOOKAHEAD = 1.2

    /** Path samples along [LOOKAHEAD]. */
    private const val SAMPLES = 3

    /** Hitbox half-width used for the pit check: the player's 0.3 plus a little. */
    private const val PIT_MARGIN = 0.4

    /** No taps while a beam sweep is this close (ticks): the beam jump wants the player still. */
    private const val BEAM_QUIET_TICKS = 12.0

    /** Ticks with no key between two taps. */
    private const val GAP_TICKS = 2

    private var ticks = 0L
    private var lastAttackTick = Long.MIN_VALUE / 2
    private var lastTapEndTick = Long.MIN_VALUE / 2
    private var attackWasDown = false
    private var pressed = false
    private var tapLeft = 0
    private val dir = DoubleArray(2)

    /** Called when the player starts an attack (left click), from the attack hook. */
    fun onAttack() {
        lastAttackTick = ticks
    }

    /** End of client tick, after Boss Aim, the fire pit helper and the beam dodge have updated. */
    fun tick() {
        ticks++
        val mc = Minecraft.getInstance()
        val c = BossAim.config()
        val player = mc.player
        val level = mc.level
        if (!c.enabled || !c.strafe || player == null || level == null) {
            release(mc)
            return
        }
        val attackDown = physicallyDown(mc, mc.options.keyAttack)
        if (attackDown && !attackWasDown) lastAttackTick = ticks
        attackWasDown = attackDown
        val target = BossAim.targetId().takeIf { it >= 0 }?.let { level.getEntity(it) } as? LivingEntity
        if (target == null || !target.isAlive || !mayDrive(mc, player) || !clear(player)) {
            release(mc)
            return
        }
        if (pressed) {
            if (--tapLeft <= 0) release(mc)
            return
        }
        if (StrafeMath.tapDue(ticks, lastAttackTick, attackDown, lastTapEndTick, GAP_TICKS)) {
            mc.options.keyDown.setDown(true)
            pressed = true
            tapLeft = c.strafeTapTicks
        }
    }

    fun reset() {
        release(Minecraft.getInstance())
    }

    /** No pit behind the player and no beam sweep about to need a still player. */
    private fun clear(player: LocalPlayer): Boolean {
        val impact = QuaziiBeamDodge.threatImpact
        if (!impact.isNaN() && impact <= BEAM_QUIET_TICKS &&
            QuaziiBeam.horizontalDistance(QuaziiBeamDodge.threatBodyX, QuaziiBeamDodge.threatBodyZ, player.x, player.z) <=
            QuaziiBeam.REACH
        ) {
            return false
        }
        StrafeMath.backward(player.yRot, dir)
        for (i in 1..SAMPLES) {
            val d = LOOKAHEAD * i / SAMPLES
            if (FirePit.hazardNear(player.x + dir[0] * d, player.z + dir[1] * d, PIT_MARGIN)) return false
        }
        return true
    }

    /** Other helpers and the player's own S always win. */
    private fun mayDrive(mc: Minecraft, player: LocalPlayer): Boolean {
        if (!player.isAlive || player.isSpectator || McCompat.currentScreen(mc) != null) return false
        if (QuaziiBeamDodge.holdingKeys || FirePit.escaping || EscapeWalker.active || PathWalker.active) return false
        return !physicallyDown(mc, mc.options.keyDown)
    }

    /** Gives the back key back in its hardware state. Only acts on a press this helper made. */
    private fun release(mc: Minecraft) {
        if (!pressed) return
        pressed = false
        tapLeft = 0
        lastTapEndTick = ticks
        val key = mc.options.keyDown
        key.setDown(physicallyDown(mc, key))
    }

    /** Reads the hardware state, because KeyMapping.isDown is the flag this helper sets itself. */
    private fun physicallyDown(mc: Minecraft, mapping: KeyMapping): Boolean {
        if (mapping.isUnbound) return false
        val bound = boundKey(mapping) ?: return false
        val handle = mc.window.handle()
        return when (bound.type) {
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(handle, bound.value) == GLFW.GLFW_PRESS
            InputConstants.Type.KEYSYM -> GLFW.glfwGetKey(handle, bound.value) == GLFW.GLFW_PRESS
            else -> false
        }
    }

    private fun boundKey(mapping: KeyMapping): InputConstants.Key? = try {
        (mapping as KeyMappingAccessor).icantpyBoundKey()
    } catch (_: Throwable) {
        null
    }
}
