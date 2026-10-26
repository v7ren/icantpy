package net.icantpy.slayer.aim

import net.icantpy.Icantpy
import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.api.IcantpyDispatchResult
import net.icantpy.compat.McCompat
import net.icantpy.qol.camera.Freelook
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW

/**
 * Boss aim. While the activation is held, the camera eases toward the chosen target through the same
 * Entity.turn path the mouse uses. It never sends or changes packets and never clicks or attacks.
 *
 * Each frame only the change since the last applied pose is turned, so the player's own mouse movement stays
 * additive. Freelook and open screens take precedence: aim stands down while either is in use.
 */
object BossAim {
    /** Vanilla Entity.turn multiplies both arguments by 0.15 (verified with javap on 26.2 and 26.1.2). */
    private const val TURN_SCALE: Double = 0.15

    /** A target farther than this from the motion's current end restarts the curve instead of retargeting it. */
    private const val RETARGET_THRESHOLD_DEG: Float = 25f

    /** Camera drift (degrees) between frames beyond what aim applied, treated as the user moving the mouse. */
    private const val USER_OVERRIDE_DEG: Float = 1.5f

    private const val MISS_LOG_INTERVAL_MS = 3000L

    /** Lazy dead zone grows the hitbox slightly so aim doesn't twitch on and off at its exact edge. */
    private const val DEADZONE_INFLATE = 0.05

    private val lazyPoint = LazyAimPoint(seed = System.nanoTime())
    private val lazyScratch = FloatArray(3)
    private var nextMissLogMs = 0L

    private const val HIGHLIGHT_COLOR: Int = 0xFFFFD040.toInt()
    private const val HIGHLIGHT_STROKE: Float = 1.5f

    private var config = AimConfig()
    private val motion = AimMotion()

    /** Aim-space pose last written to the player. Null while aim is not engaged. */
    private var lastApplied: AimPose? = null

    /** End pose most recently given to [motion]. */
    private var lastTarget: AimPose? = null
    private var lastTargetId = -1

    /** Blood drop point being aimed at, for the highlight; null when blood aim has no target. */
    private var bloodPoint: Vec3? = null
    private var bloodId = -1

    private const val BLOOD_SWITCH_MARGIN_DEG: Float = 10f

    private var toggled = false
    private var toggleKeyWasDown = false

    fun load() {
        config = AimStore.load()
        resetState()
    }

    /** Drops all state. Safe to call repeatedly. */
    fun unload() {
        resetState()
        AimTargets.clear()
        AimLock.clear()
        BloodAimPoints.clear()
        toggled = false
        toggleKeyWasDown = false
    }

    fun config(): AimConfig = config

    /** Stores [next], writes it to disk and applies it. */
    fun commit(next: AimConfig) {
        config = next
        AimStore.save(next)
        resetState()
    }

    /** Shorthand for [commit] with a copy of the current config. */
    fun edit(change: (AimConfig) -> AimConfig) = commit(change(config))

    /** True while the camera is being driven toward a target. For status displays. */
    fun engaged(): Boolean = lastApplied != null

    /** Entity id of the target being aimed at, or -1 when not engaged. */
    fun targetId(): Int = if (lastApplied != null) lastTargetId else -1

    /** Client tick. Returns at once unless the feature is enabled. */
    fun tick() {
        val c = config
        if (!c.active) return
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null) {
            AimTargets.clear()
            AimLock.clear()
            toggled = false
            return
        }
        AimLock.tick(mc, level, player, c)
        AimTargets.tick(level, player, c)
        if (c.bloodAim) BloodAimPoints.tick(level) else BloodAimPoints.clear()
        if (c.activation == AimActivation.TOGGLE_KEY) handleToggleKey(mc, c)
    }

    /** Start of world render. Turns the camera toward the target by the eased step for this frame. */
    fun onFrameStart() {
        if (!config.active) return
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null) {
            stop()
            return
        }
        // A lock-on aims without the activation being held and skips the target filters.
        val lockedTarget = AimLock.current(level)
        if (lockedTarget != null) {
            if (canTurn(mc, player)) applyFrame(mc, player, lockedTarget) else stop()
            return
        }
        if (!wantsAim(mc, player)) {
            stop()
            return
        }
        if (config.bloodAim) {
            val eye = player.getEyePosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))
            val blood = BloodAimPoints.pick(player, eye, bloodId, config.fovLimit / 2f, BLOOD_SWITCH_MARGIN_DEG)
            if (blood != null) {
                bloodId = blood.first
                bloodPoint = blood.second
                driveToward(player, eye, blood.first, blood.second)
                return
            }
            bloodPoint = null
        }
        val entity = AimTargets.current(level)
        if (entity == null) {
            stop()
            logMiss()
            return
        }
        applyFrame(mc, player, entity)
    }

    /** While the trigger is held with no target, logs why at most every [MISS_LOG_INTERVAL_MS]. */
    private fun logMiss() {
        val now = System.currentTimeMillis()
        if (now < nextMissLogMs) return
        nextMissLogMs = now + MISS_LOG_INTERVAL_MS
        Icantpy.LOGGER.info("boss-aim: trigger held but no target. ${AimTargets.lastMiss}")
    }

    /** Outline around the current target. Drawn whenever aim is enabled and a target is cached. */
    fun renderWorld() {
        val c = config
        if (!c.active || !c.highlightTarget) return
        val level: ClientLevel = Minecraft.getInstance().level ?: return
        bloodPoint?.let {
            if (engaged()) Gizmos.cuboid(BloodAimPoints.box(it), GizmoStyle.stroke(HIGHLIGHT_COLOR, HIGHLIGHT_STROKE))
        }
        val entity: LivingEntity = AimLock.current(level) ?: AimTargets.current(level) ?: return
        Gizmos.cuboid(entity.boundingBox, GizmoStyle.stroke(HIGHLIGHT_COLOR, HIGHLIGHT_STROKE))
    }

    private fun applyFrame(mc: Minecraft, player: LocalPlayer, entity: LivingEntity) {
        val c = config
        val partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)
        val eye = player.getEyePosition(partial)
        val now = System.nanoTime() / 1_000_000L
        if (entity.id != lastTargetId) lazyPoint.reset()
        val actual = AimPose(player.getYRot(), player.getXRot())
        if (c.lazyAim && c.lazyDeadzone && crosshairOn(player, eye, partial, entity, c)) {
            // Already on the hitbox: hold still. The next pull starts fresh from wherever the camera is then.
            motion.reset()
            lastApplied = actual
            lastTarget = null
            lastTargetId = entity.id
            return
        }
        var aim = AimTargets.aimPointFor(entity, c.aimPoint, partial)
        if (c.lazyAim) aim = lazyOffset(aim, entity, now, c)
        drive(player, entity.id, AimMath.lookAt(eye.x, eye.y, eye.z, aim.x, aim.y, aim.z), now)
    }

    /** Eases toward a fixed world point (such as a predicted blood drop) with no entity behind it. */
    private fun driveToward(player: LocalPlayer, eye: Vec3, id: Int, point: Vec3) {
        if (id != lastTargetId) lazyPoint.reset()
        val now = System.nanoTime() / 1_000_000L
        drive(player, id, AimMath.lookAt(eye.x, eye.y, eye.z, point.x, point.y, point.z), now)
    }

    /** Shared motion step: restart or retarget the curve toward [targetPose] and turn by this frame's share. */
    private fun drive(player: LocalPlayer, id: Int, targetPose: AimPose, now: Long) {
        val c = config
        val actual = AimPose(player.getYRot(), player.getXRot())
        val applied = lastApplied
        val end = lastTarget
        // The user's own mouse moved the camera away from the curve: restart from where the camera really is,
        // so the offset is corrected instead of carried along to the target.
        val userMoved = applied != null && AimMath.angularDistance(actual, applied) > USER_OVERRIDE_DEG
        val restart = !motion.active || end == null || id != lastTargetId || userMoved ||
            AimMath.angularDistance(end, targetPose) > RETARGET_THRESHOLD_DEG
        val from = if (restart || applied == null) actual else applied
        if (restart) {
            motion.retarget(now, from, targetPose, c.settings(), seed = (now xor id.toLong()).toInt())
        } else {
            motion.updateTarget(targetPose)
        }
        lastTarget = targetPose
        lastTargetId = id

        // Never fall back to the target pose: that would snap the camera instantly.
        val pose = motion.sample(now) ?: return
        val step = AimMath.delta(from, pose)
        if (step.yaw != 0f || step.pitch != 0f) {
            player.turn(step.yaw / TURN_SCALE, step.pitch / TURN_SCALE)
        }
        lastApplied = pose
    }

    /** Aim runs only with a live, unobstructed player: no screen, no freelook, and the activation held. */
    private fun wantsAim(mc: Minecraft, player: LocalPlayer): Boolean = canTurn(mc, player) && activationHeld(mc)

    /** The camera may be turned: live player, no screen open, freelook not active. */
    private fun canTurn(mc: Minecraft, player: LocalPlayer): Boolean {
        if (!player.isAlive || player.isSpectator) return false
        if (McCompat.currentScreen(mc) != null) return false
        return Freelook.queryLook(McCompat.mainCamera(mc)).result == IcantpyDispatchResult.PASS
    }

    private fun activationHeld(mc: Minecraft): Boolean {
        val c = config
        return when (c.activation) {
            AimActivation.HOLD_LEFT_CLICK ->
                GLFW.glfwGetMouseButton(mc.window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
            AimActivation.HOLD_KEY -> c.key != AimConfig.UNBOUND && InputConstants.isKeyDown(mc.window, c.key)
            AimActivation.TOGGLE_KEY -> toggled
        }
    }

    /** Flips [toggled] on the press edge only, and never while a screen is open. */
    private fun handleToggleKey(mc: Minecraft, c: AimConfig) {
        if (c.key == AimConfig.UNBOUND) {
            toggleKeyWasDown = false
            return
        }
        val down = InputConstants.isKeyDown(mc.window, c.key)
        if (down && !toggleKeyWasDown && McCompat.currentScreen(mc) == null) toggled = !toggled
        toggleKeyWasDown = down
    }

    private fun stop() {
        if (lastApplied == null && !motion.active) return
        resetState()
    }

    /** [aim] moved by the wandering lazy offset, scaled to the target's hitbox half-extents. */
    private fun lazyOffset(aim: Vec3, entity: LivingEntity, now: Long, c: AimConfig): Vec3 {
        lazyPoint.sample(now, c.lazyStrength, lazyScratch)
        val box = entity.boundingBox
        return Vec3(
            aim.x + lazyScratch[0] * box.xsize * 0.5,
            aim.y + lazyScratch[1] * box.ysize * 0.5,
            aim.z + lazyScratch[2] * box.zsize * 0.5,
        )
    }

    /** True when the player's look ray (within aim range) passes through the target's hitbox. */
    private fun crosshairOn(player: LocalPlayer, eye: Vec3, partial: Float, entity: LivingEntity, c: AimConfig): Boolean {
        val box = entity.boundingBox.inflate(DEADZONE_INFLATE)
        if (box.contains(eye)) return true
        val reach = maxOf(c.maxRange.toDouble(), eye.distanceTo(box.center) + 2.0)
        val end = eye.add(player.getViewVector(partial).scale(reach))
        return box.clip(eye, end).isPresent
    }

    private fun resetState() {
        lazyPoint.reset()
        motion.reset()
        lastApplied = null
        lastTarget = null
        lastTargetId = -1
    }
}
