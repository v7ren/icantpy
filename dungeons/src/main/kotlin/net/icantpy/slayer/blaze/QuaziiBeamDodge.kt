package net.icantpy.slayer.blaze

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.Icantpy
import net.icantpy.compat.McCompat
import net.icantpy.dungeon.timer.AlertSounds
import net.icantpy.mixin.KeyMappingAccessor
import net.icantpy.util.Text
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.ElderGuardian
import net.minecraft.world.entity.monster.Guardian
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Quazii beam helper for the Inferno Demonlord fight. Each tick it finds Quazii bodies (a wither skeleton under a
 * name tag containing the circled Quazii text), reads the beam's bearing from the guardian group orbiting each body,
 * and predicts when either end of the beam line sweeps the local player (see [QuaziiBeam]).
 *
 * Warn shows an action-bar countdown and plays a sound once per crossing. Jump also jumps over the beam in place:
 * it presses the vanilla jump key inside the clearing window and keeps the movement keys released from that tick
 * until the player lands, so a sprint or knockback does not turn the dodge into a launch. It never jumps while the
 * fire pit helper is taking the player out of a pit (a pit hurts more than a beam), in the air, with a screen open,
 * or above the configured health. A jump also needs the prediction to have counted down steadily for a few ticks,
 * so a jittering guardian group cannot fire one.
 *
 * Body keep-out: inside Quazii's hitbox the beam hits again and again, so [keepOutMove] (called from the movement
 * guard) treats a circle around the nearest Quazii body as a wall and eases the player out when the body walks
 * into them. No packets are sent; keys are only set like held keys and restored to the keyboard's real state
 * afterwards, and movement is only changed on the client like a wall would.
 */
object QuaziiBeamDodge {
    private const val TAG = "ⓆⓊⒶⓏⒾⒾ"
    private const val BODY_RANGE = 16.0
    private const val TAG_HORIZONTAL_RANGE = 2.5
    private const val TAG_BELOW_FEET = 0.5
    private const val TAG_ABOVE_HEAD = 4.0
    private const val GUARDIAN_VERTICAL_RANGE = 1.5
    private const val HISTORY = 10
    private const val WARN_TICKS = 10.0
    private const val JUMP_HOLD_TICKS = 2

    /** A vanilla jump is about 12 ticks of air; a second one inside that only costs control. */
    private const val JUMP_COOLDOWN_TICKS = 12L

    /** Longest the movement keys stay released for one jump, in case the landing is never seen. */
    private const val MAX_STILL_TICKS = 16

    /** A prediction this many ticks later than the last one means the previous crossing has resolved. */
    private const val NEW_CROSSING_DEG = 30.0

    /** Bearings used for the rate: the newest few, so the estimate follows the arm speeding up or slowing down. */
    private const val RATE_WINDOW = 6

    /**
     * Steadiness: how far (deg) the gap between the real (not extrapolated) arm and the player may open between two
     * fresh position updates and still count as closing in. A beam that really turns back opens it by more.
     */
    private const val STEADY_MAX_OPEN_DEG = 3.0

    /** Ticks the last good rate is kept while the window's rate is rejected, so a prediction does not vanish. */
    private const val RATE_HOLD_TICKS = 5

    /** Ticks a stale arm bearing is carried forward at the measured rate before it stops being extrapolated. */
    private const val MAX_STALE_TICKS = 3

    /** Ticks in a row the prediction must count down steadily before it may trigger a jump. */
    private const val STEADY_TICKS = 2

    /** Gap kept between the player's and the body's hitboxes, in blocks. */
    private const val KEEP_OUT_MARGIN = 0.2

    /** Most the player is eased outward per tick when already inside, in blocks (walking is about 0.22). */
    private const val KEEP_OUT_PUSH = 0.15

    /** The keep-out only applies with the body this close vertically, in blocks. */
    private const val KEEP_OUT_VERTICAL = 2.5
    private const val CROSSING_LOG_FORMAT = "quazii beam: rate=%.2f deg/tick dist=%.2f predicted=%.1f ticks jump=%s"
    private const val JUMP_LOG_FORMAT =
        "quazii beam: JUMP impact=%.1f ticks arm=%.1f player=%.1f gap=%.1f deg rate=%.2f deg/tick dist=%.2f"
    private const val SKIP_LOG_FORMAT = "quazii beam: NO JUMP impact=%.1f ticks reason=%s"
    private const val STATE_LOG_FORMAT =
        "quazii beam: STATE body=%d guardians=%d arm=%.1f laser=%s player=%.1f dist=%.2f rate=%s impact=%s steady=%d %s"
    private const val HIT_LOG_FORMAT = "quazii beam: HIT \"%s\" mode=%s onGround=%s lastJump=%d ticks ago"

    /** Ticks between two STATE lines for one body while its guardians are out. */
    private const val STATE_LOG_INTERVAL = 10L

    private class Track {
        private val arms = DoubleArray(HISTORY)
        private var armCount = 0
        private var armHead = 0
        var seenTick = 0L
        var lastImpact = Double.NaN
        var lastGap = Double.NaN
        var lastRawGap = Double.NaN
        var rateAge = Int.MAX_VALUE
        var steady = 0
        var rawArm = Double.NaN
        var staleTicks = 0
        var skipLogged = false

        /** True while the prediction uses [QuaziiBeam.PRIOR_RATE_DEG_PER_TICK] instead of a measured rate. */
        var prior = false
        var status = ""
        var nextStateLog = 0L
        var arm = 0.0
        var playerBearing = 0.0
        var rate = 0.0
        var distance = 0.0
        var warned = false
        var jumped = false
        var logRate = 0.0
        var logDistance = 0.0
        var logPredicted = Double.NaN

        fun push(bearing: Double) {
            arms[armHead] = bearing
            armHead = (armHead + 1) % HISTORY
            if (armCount < HISTORY) armCount++
        }

        fun clearArms() {
            armCount = 0
            armHead = 0
        }

        /** Copies the arm bearings into [out], oldest first, and returns how many were copied. */
        fun ordered(out: DoubleArray): Int {
            val start = if (armCount < HISTORY) 0 else armHead
            for (i in 0 until armCount) out[i] = arms[(start + i) % HISTORY]
            return armCount
        }
    }

    private val tracks = HashMap<Int, Track>()
    private val bodies = ArrayList<WitherSkeleton>()
    private val guardians = ArrayList<Guardian>()
    private val stands = ArrayList<ArmorStand>()
    private val history = DoubleArray(HISTORY)
    private val recent = DoubleArray(RATE_WINDOW)
    private val steps = DoubleArray(HISTORY)
    private var ticks = 0L
    private var lastJumpTick = Long.MIN_VALUE / 2
    private var debugLog = false
    private var mode = BeamDodge.OFF
    private var armGuardians = 0
    private var laserBearing = Double.NaN

    /** The soonest predicted beam crossing this tick, for the boss strafe; [threatImpact] is NaN when none. */
    var threatImpact = Double.NaN
        private set
    var threatBodyX = 0.0
        private set
    var threatBodyZ = 0.0
        private set
    var threatRate = 0.0
        private set

    /** True while the dodge owns the jump or movement keys; other helpers must not press keys meanwhile. */
    val holdingKeys: Boolean get() = jumpOwned || stillOwned
    private var jumpTrack: Track? = null
    private var jumpImpact = 0.0

    /** Nearest Quazii body this tick, for the keep-out; read by the movement guard during the player tick. */
    @Volatile
    private var guardBody: WitherSkeleton? = null

    @Volatile
    private var keepOutOn = false
    private val keepOutOut = DoubleArray(2)

    private var jumpOwned = false
    private var jumpTicksLeft = 0

    /** True from the jump until landing: movement keys are held released at the start of every tick. */
    private var stillOwned = false
    private var stillTicks = 0
    private var leftGround = false

    /**
     * Start of the client tick, before the player reads its input. Key-repeat events between ticks press a held
     * movement key again, so the release has to be re-applied here rather than at the end of the previous tick.
     */
    fun startTick() {
        if (!stillOwned) return
        val mc = Minecraft.getInstance()
        if (McCompat.currentScreen(mc) != null) {
            releaseStill(mc)
            return
        }
        for (key in movementKeys(mc)) key.setDown(false)
    }

    /** End of the client tick. Runs on its own: it needs a Quazii in view, not the fire pit helper. */
    fun tick(config: FirePitConfig, level: ClientLevel?, player: LocalPlayer?, escaping: Boolean) {
        val mc = Minecraft.getInstance()
        keepOutOn = config.beamDodge != BeamDodge.OFF && config.avoidQuaziiBody
        debugLog = config.debugLog
        mode = config.beamDodge
        releaseJumpIfDue(mc)
        updateStill(mc, player)
        if (config.beamDodge == BeamDodge.OFF || level == null || player == null ||
            player.isSpectator || !player.isAlive
        ) {
            if (tracks.isNotEmpty() || jumpOwned || stillOwned) reset()
            return
        }
        ticks++
        val soonest = observeBodies(config, level, player, mc, escaping)
        if (soonest != null && soonest >= 0.0 && soonest <= WARN_TICKS) {
            player.sendOverlayMessage(Component.literal("Quazii beam in ${ceil(soonest).toInt()}"))
        }
    }

    /** Releases every key this helper pressed and drops all tracks. Safe to call repeatedly. */
    fun reset() {
        guardBody = null
        threatImpact = Double.NaN
        val mc = Minecraft.getInstance()
        releaseJump(mc)
        releaseStill(mc)
        tracks.clear()
    }

    /** Updates every Quazii body's track and returns the smallest predicted impact in ticks, or null. */
    private fun observeBodies(
        config: FirePitConfig,
        level: ClientLevel,
        player: LocalPlayer,
        mc: Minecraft,
        escaping: Boolean,
    ): Double? {
        bodies.clear()
        guardians.clear()
        stands.clear()
        val rangeSq = BODY_RANGE * BODY_RANGE
        for (entity in level.entitiesForRendering()) {
            when (entity) {
                is ArmorStand -> if (Text.strip(entity.name.string).contains(TAG)) stands.add(entity)
                is WitherSkeleton -> if (entity.distanceToSqr(player) <= rangeSq) bodies.add(entity)
                is Guardian -> if (entity !is ElderGuardian) guardians.add(entity)
            }
        }
        var soonest: Double? = null
        var nearest: WitherSkeleton? = null
        var nearestSq = Double.MAX_VALUE
        jumpTrack = null
        threatImpact = Double.NaN
        if (bodies.isNotEmpty()) {
            for (body in bodies) {
                // The name tag can trail a moving body; a guardian group orbiting it is just as sure a sign of
                // Quazii (Typhoeus never has one).
                if (stands.none { tagAbove(it, body) } && !hasArm(body)) continue
                val sq = body.distanceToSqr(player)
                if (sq < nearestSq) {
                    nearestSq = sq
                    nearest = body
                }
                val impact = observeBody(config, body, player) ?: continue
                if (soonest == null || impact < soonest) {
                    soonest = impact
                    threatImpact = impact
                    threatBodyX = body.x
                    threatBodyZ = body.z
                    threatRate = tracks[body.id]?.rate ?: 0.0
                }
            }
        }
        guardBody = nearest
        pruneTracks(config)
        if (config.beamDodge == BeamDodge.JUMP) decideJump(config, mc, player, escaping)
        return soonest
    }

    /** Jumps for the soonest crossing in its window, or logs (once per crossing) why it could not. */
    private fun decideJump(config: FirePitConfig, mc: Minecraft, player: LocalPlayer, escaping: Boolean) {
        val early = config.beamJumpEarlyTicks.toDouble()
        var candidate: Track? = null
        for (track in tracks.values) {
            if (track.jumped || track.lastImpact.isNaN() || !QuaziiBeam.jumpWindow(track.lastImpact, early)) continue
            if (candidate == null || track.lastImpact < candidate.lastImpact) candidate = track
        }
        val track = candidate ?: return
        val reason = when {
            !track.prior && track.steady < STEADY_TICKS -> "prediction not steady (${track.steady}/$STEADY_TICKS)"
            ticks - lastJumpTick < JUMP_COOLDOWN_TICKS -> "cooldown"
            else -> jumpBlocker(config, mc, player, escaping)
        }
        if (reason == null) {
            lastJumpTick = ticks
            jumpTrack = track
            jumpImpact = track.lastImpact
            jumpInPlace(mc)
            track.jumped = true
            if (config.debugLog) logJump(track)
        } else if (config.debugLog && !track.skipLogged) {
            track.skipLogged = true
            Icantpy.LOGGER.info(String.format(Locale.ROOT, SKIP_LOG_FORMAT, track.lastImpact, reason))
        }
    }

    /**
     * Movement guard part: keeps the local player out of the nearest Quazii body (see [QuaziiBeam.keepOut]).
     * Returns the replacement move, or null to leave [move] alone.
     */
    fun keepOutMove(player: LocalPlayer, move: Vec3): Vec3? {
        if (!keepOutOn) return null
        val body = guardBody ?: return null
        if (body.isRemoved || !body.isAlive || abs(player.y - body.y) > KEEP_OUT_VERTICAL) return null
        val radius = body.bbWidth / 2.0 + player.bbWidth / 2.0 + KEEP_OUT_MARGIN
        val out = keepOutOut
        if (!QuaziiBeam.keepOut(player.x, player.z, move.x, move.z, body.x, body.z, radius, KEEP_OUT_PUSH, out)) {
            return null
        }
        return Vec3(out[0], move.y, out[1])
    }

    private fun logJump(track: Track) {
        Icantpy.LOGGER.info(
            String.format(
                Locale.ROOT,
                JUMP_LOG_FORMAT,
                jumpImpact,
                track.arm,
                track.playerBearing,
                QuaziiBeam.wrap(track.playerBearing - track.arm),
                track.rate,
                track.distance,
            ),
        )
    }

    /** Predicts one body's next crossing and warns for it. Returns the predicted impact in ticks, or null. */
    private fun observeBody(config: FirePitConfig, body: WitherSkeleton, player: LocalPlayer): Double? {
        val track = tracks.getOrPut(body.id) { Track() }
        track.seenTick = ticks
        val raw = armBearing(body)
        if (raw == null) {
            track.clearArms()
            track.rawArm = Double.NaN
            track.status = "no guardians"
            endCrossing(config, track)
            return null
        }
        if (raw == track.rawArm) track.staleTicks++ else track.staleTicks = 0
        track.rawArm = raw
        track.push(raw)
        val count = track.ordered(history)
        val window = minOf(count, RATE_WINDOW)
        System.arraycopy(history, count - window, recent, 0, window)
        val measured = QuaziiBeam.rate(recent, window, steps)
        track.prior = measured == null && count < QuaziiBeam.MIN_SAMPLES
        if (measured != null) track.rateAge = 0 else if (track.rateAge < Int.MAX_VALUE) track.rateAge++
        val held = track.rate.takeIf { measured == null && track.rateAge <= RATE_HOLD_TICKS && it != 0.0 }
        val rate = measured ?: held ?: if (track.prior) QuaziiBeam.PRIOR_RATE_DEG_PER_TICK else null
        // Guardian positions often skip a tick; carry the last bearing forward so the countdown keeps ticking.
        val arm = if (rate == null) raw else raw + rate * minOf(track.staleTicks, MAX_STALE_TICKS)
        val distance = QuaziiBeam.horizontalDistance(body.x, body.z, player.x, player.z)
        val playerBearing = QuaziiBeam.bearing(body.x, body.z, player.x, player.z)
        val crossing = rate?.let { QuaziiBeam.ticksUntilCrossing(arm, it, playerBearing, distance) }
        track.arm = arm
        track.playerBearing = playerBearing
        track.distance = distance
        if (rate == null || crossing == null) {
            track.status = when {
                count < QuaziiBeam.MIN_SAMPLES -> "warming up ($count/${QuaziiBeam.MIN_SAMPLES})"
                rate == null -> "rate rejected"
                distance > QuaziiBeam.REACH -> "out of reach"
                abs(rate) < QuaziiBeam.MIN_RATE_DEG_PER_TICK -> "arm stalled"
                else -> "beam passed, coming round"
            }
            if (rate != null) track.rate = rate
            logState(config, body, track, rate, null)
            endCrossing(config, track)
            return null
        }
        track.status = if (track.prior) "predicting (assumed rate, $count/${QuaziiBeam.MIN_SAMPLES})" else "predicting"
        val impact = QuaziiBeam.impactTicks(crossing)
        // Degrees the beam still has to turn to reach the player. Unlike the predicted time, this does not swing when
        // the arm speeds up or slows down (it does, a lot), so it is what steadiness and crossing ends are read from.
        val gap = crossing * abs(rate)
        // The gap jumping back up is the previous crossing resolving.
        if (!track.lastGap.isNaN() && gap > track.lastGap + NEW_CROSSING_DEG) endCrossing(config, track)
        // Steady while the beam keeps closing in. Read only on fresh position updates and from the real arm: the
        // extrapolated arm overshoots between updates and snaps back, which looked like the beam turning away.
        if (track.staleTicks == 0) {
            val rawGap = if (rate > 0.0) QuaziiBeam.wrap(playerBearing - raw) else QuaziiBeam.wrap(raw - playerBearing)
            val closed = track.lastRawGap - rawGap
            track.steady = if (!closed.isNaN() && closed > -STEADY_MAX_OPEN_DEG) track.steady + 1 else 0
            track.lastRawGap = rawGap
        }
        track.lastGap = gap
        track.lastImpact = impact
        track.rate = rate
        if (track.logPredicted.isNaN()) {
            track.logRate = rate
            track.logDistance = distance
            track.logPredicted = impact
        }
        if (!track.warned && impact <= WARN_TICKS) {
            track.warned = true
            if (config.sound) AlertSounds.play("pling")
        }
        logState(config, body, track, rate, impact)
        return impact
    }

    /** With the debug log on, writes the beam state of [body] every [STATE_LOG_INTERVAL] ticks. */
    private fun logState(config: FirePitConfig, body: WitherSkeleton, track: Track, rate: Double?, impact: Double?) {
        if (!config.debugLog || ticks < track.nextStateLog) return
        track.nextStateLog = ticks + STATE_LOG_INTERVAL
        Icantpy.LOGGER.info(
            String.format(
                Locale.ROOT,
                STATE_LOG_FORMAT,
                body.id,
                armGuardians,
                track.arm,
                if (laserBearing.isNaN()) "none" else String.format(Locale.ROOT, "%.1f", laserBearing),
                track.playerBearing,
                track.distance,
                rate?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "none",
                impact?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "none",
                track.steady,
                track.status,
            ),
        )
    }

    /** Chat hook: when Quazii's beam lands, logs what the dodge was thinking at that moment. */
    fun onChat(message: String) {
        if (!debugLog) return
        val text = Text.strip(message)
        if (!text.contains("Quazii", ignoreCase = true) || !text.contains("beam", ignoreCase = true)) return
        val player = Minecraft.getInstance().player
        Icantpy.LOGGER.info(
            String.format(
                Locale.ROOT,
                HIT_LOG_FORMAT,
                text,
                mode.id,
                player?.onGround() ?: false,
                ticks - lastJumpTick,
            ),
        )
        for ((id, track) in tracks) {
            Icantpy.LOGGER.info(
                String.format(
                    Locale.ROOT,
                    "quazii beam:   body=%d status=%s impact=%.1f steady=%d jumped=%s rate=%.2f arm=%.1f player=%.1f dist=%.2f",
                    id, track.status, track.lastImpact, track.steady, track.jumped, track.rate, track.arm,
                    track.playerBearing, track.distance,
                ),
            )
        }
    }

    /** True when at least one guardian sits on [body]'s orbit. No side effects, unlike [armBearing]. */
    private fun hasArm(body: WitherSkeleton): Boolean = guardians.any { guardian ->
        abs(guardian.y - body.y) <= GUARDIAN_VERTICAL_RANGE &&
            QuaziiBeam.horizontalDistance(body.x, body.z, guardian.x, guardian.z) in
            QuaziiBeam.ARM_MIN_RADIUS..QuaziiBeam.ARM_MAX_RADIUS
    }

    /** Mean position of the guardians orbiting [body], as a bearing from the body; null when there is no arm. */
    private fun armBearing(body: WitherSkeleton): Double? {
        var sumX = 0.0
        var sumZ = 0.0
        var count = 0
        laserBearing = Double.NaN
        for (guardian in guardians) {
            if (abs(guardian.y - body.y) > GUARDIAN_VERTICAL_RANGE) continue
            val r = QuaziiBeam.horizontalDistance(body.x, body.z, guardian.x, guardian.z)
            if (r < QuaziiBeam.ARM_MIN_RADIUS || r > QuaziiBeam.ARM_MAX_RADIUS) continue
            sumX += guardian.x - body.x
            sumZ += guardian.z - body.z
            count++
            // Diagnostic: where the guardian's own laser points, if it has a target.
            val target = guardian.activeAttackTarget
            if (target != null && laserBearing.isNaN()) laserBearing = QuaziiBeam.bearing(body.x, body.z, target.x, target.z)
        }
        armGuardians = count
        if (count == 0) return null
        return QuaziiBeam.bearing(0.0, 0.0, sumX / count, sumZ / count)
    }

    /** The name tag sits from a little below the feet to a few blocks over the head, nearly over the body. */
    private fun tagAbove(stand: ArmorStand, body: WitherSkeleton): Boolean {
        val rise = stand.y - body.y
        if (rise < -TAG_BELOW_FEET || rise > body.bbHeight + TAG_ABOVE_HEAD) return false
        return QuaziiBeam.horizontalDistance(stand.x, stand.z, body.x, body.z) <= TAG_HORIZONTAL_RANGE
    }

    /** Ends the current crossing of [track]: writes its debug line and clears the per-crossing flags. */
    private fun endCrossing(config: FirePitConfig, track: Track) {
        if (!track.logPredicted.isNaN() && config.debugLog) {
            Icantpy.LOGGER.info(
                String.format(
                    Locale.ROOT,
                    CROSSING_LOG_FORMAT,
                    track.logRate,
                    track.logDistance,
                    track.logPredicted,
                    if (track.jumped) "yes" else "no",
                ),
            )
        }
        track.warned = false
        track.jumped = false
        track.skipLogged = false
        track.steady = 0
        track.lastImpact = Double.NaN
        track.lastGap = Double.NaN
        track.lastRawGap = Double.NaN
        track.logPredicted = Double.NaN
    }

    /** Ends crossings of bodies not seen this tick and drops their tracks. */
    private fun pruneTracks(config: FirePitConfig) {
        val iterator = tracks.values.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            if (track.seenTick == ticks) continue
            endCrossing(config, track)
            iterator.remove()
        }
    }

    /** Why a jump cannot go out right now, or null when it can. */
    private fun jumpBlocker(config: FirePitConfig, mc: Minecraft, player: LocalPlayer, escaping: Boolean): String? {
        if (escaping) return "escaping a fire pit"
        if (!player.onGround()) return "in the air"
        if (McCompat.currentScreen(mc) != null) return "screen open"
        if (config.beamDodgeBelowHp >= FirePitConfig.MAX_BEAM_DODGE_BELOW_HP) return null
        val max = player.maxHealth
        if (max > 0f && player.health / max * 100f < config.beamDodgeBelowHp) return null
        return "health above ${config.beamDodgeBelowHp}%"
    }

    /** Presses jump and releases the movement keys until the player lands again. */
    private fun jumpInPlace(mc: Minecraft) {
        mc.options.keyJump.setDown(true)
        jumpOwned = true
        jumpTicksLeft = JUMP_HOLD_TICKS
        for (key in movementKeys(mc)) key.setDown(false)
        stillOwned = true
        stillTicks = 0
        leftGround = false
    }

    private fun releaseJumpIfDue(mc: Minecraft) {
        if (!jumpOwned) return
        jumpTicksLeft--
        if (jumpTicksLeft <= 0) releaseJump(mc)
    }

    /** Sets the jump key back to its hardware state. Only acts on a press this helper made. */
    private fun releaseJump(mc: Minecraft) {
        if (!jumpOwned) return
        jumpOwned = false
        jumpTicksLeft = 0
        val key = mc.options.keyJump
        key.setDown(physicallyDown(mc, key))
    }

    /** Ends the in-place hold once the player has left the ground and landed again, or after [MAX_STILL_TICKS]. */
    private fun updateStill(mc: Minecraft, player: LocalPlayer?) {
        if (!stillOwned) return
        stillTicks++
        if (player == null) {
            releaseStill(mc)
            return
        }
        if (!player.onGround()) leftGround = true
        if ((leftGround && player.onGround()) || stillTicks >= MAX_STILL_TICKS) releaseStill(mc)
    }

    /** Gives the movement keys back in the state the keyboard really has them. */
    private fun releaseStill(mc: Minecraft) {
        if (!stillOwned) return
        stillOwned = false
        stillTicks = 0
        leftGround = false
        for (key in movementKeys(mc)) key.setDown(physicallyDown(mc, key))
    }

    private var movement: Array<KeyMapping>? = null

    private fun movementKeys(mc: Minecraft): Array<KeyMapping> = movement ?: with(mc.options) {
        arrayOf(keyUp, keyDown, keyLeft, keyRight, keySprint).also { movement = it }
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
