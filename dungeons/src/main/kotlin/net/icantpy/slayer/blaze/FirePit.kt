package net.icantpy.slayer.blaze

import net.icantpy.Icantpy
import net.icantpy.api.MovementHook
import net.icantpy.dungeon.timer.AlertSounds
import net.icantpy.location.SkyblockLocation
import net.icantpy.qol.path.PathFinder
import net.icantpy.qol.path.PathNode
import net.icantpy.compat.McCompat
import net.icantpy.qol.path.PathWalker
import net.icantpy.qol.path.WalkSettings
import net.icantpy.qol.path.WorldBlockGrid
import net.icantpy.util.Text
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.monster.Guardian
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.floor
import kotlin.math.sqrt

/** Immutable render snapshot, replaced whole on each scan so the render thread never sees a half-built state. */
private class FirePitRender(val columns: LongArray, val floorY: Int, val target: FirePitEscape.Escape?) {
    companion object {
        val EMPTY = FirePitRender(LongArray(0), 0, null)
    }
}

/**
 * Fire pit helper. Scans nearby floor columns for pit blocks, tracks which columns just caught fire, and when the
 * player stands in a hazard, notifies with a direction or walks them out via [EscapeWalker].
 *
 * Gate: while [FirePitConfig.onlyInBossFight] is set, work happens only when the sidebar shows "Slay the boss!" and
 * an "Inferno Demonlord" entity is within [BOSS_RANGE_BLOCKS] blocks. The sidebar line comes from
 * [SkyblockLocation]; the entity lookup is cached for [BOSS_CHECK_INTERVAL_TICKS] ticks.
 */
object FirePit {
    /** Every tick, so fire appearing under the player is reacted to on the very next tick. */
    private const val SCAN_INTERVAL_TICKS = 1L

    /** Fire blocks are hazards the moment they are seen, even without being seen forming first. */
    private val FIRE_BLOCKS: Set<Block> = setOf(Blocks.FIRE, Blocks.SOUL_FIRE)

    /**
     * Vertical band scanned around the player's feet. Pits form in the floor (red, yellow, then black terracotta)
     * and then fire appears on top, so the band must reach the floor even while the player is jumping or standing
     * on a slab, which moves the feet block up by one or two.
     */
    private const val SCAN_BELOW = 4
    private const val SCAN_ABOVE = 1
    private const val NOTIFY_INTERVAL_TICKS = 10L
    private const val BOSS_CHECK_INTERVAL_TICKS = 10L
    private const val BOSS_NAME = "Inferno Demonlord"
    private const val BOSS_RANGE_BLOCKS = 30.0
    private const val TRACKER_WINDOW_TICKS = 60
    private const val DEBUG_LINES_PER_SECOND = 40
    private const val DEBUG_STATE_LIMIT = 4000
    private const val NO_PRIORITY = -1

    /** The beam countdown shows from this many ticks out, so its action bar line competes with the pit line. */
    private const val BEAM_NOTICE_TICKS = 10.0

    private const val AIR_EXTRA_MARGIN = 0.15

    /** The eight W/A/S/D directions the air strafe can add. */
    private val AIR_COMBOS = listOf(
        KeyCombo(forward = true, back = false, left = false, right = false),
        KeyCombo(forward = true, back = false, left = false, right = true),
        KeyCombo(forward = false, back = false, left = false, right = true),
        KeyCombo(forward = false, back = true, left = false, right = true),
        KeyCombo(forward = false, back = true, left = false, right = false),
        KeyCombo(forward = false, back = true, left = true, right = false),
        KeyCombo(forward = false, back = false, left = true, right = false),
        KeyCombo(forward = true, back = false, left = true, right = false),
    )
    private const val AIR_FLOOR_SLACK = 0.5
    private const val AIR_FLOOR_SCAN = 8
    private const val HAZARD_STROKE = 1.5f
    private const val ESCAPE_LINE_WIDTH = 2f
    private val HAZARD_OUTLINE: Int = 0xCCFF3030.toInt()
    private val HAZARD_FILL: Int = 0x44FF3030
    private val ESCAPE_LINE: Int = 0xFF40FF60.toInt()

    private var config = FirePitConfig()
    private var pitBlocks: Set<Block> = emptySet()
    private val tracker = FirePitTracker(TRACKER_WINDOW_TICKS)
    private var ticks = 0L
    private var hasState = false
    private var bossActive = false
    private var nextBossCheckTick = 0L
    private var nextNotifyTick = 0L
    private var inside = false
    private var walkStarted = false
    private var walkTargetX = 0.0
    private var walkTargetZ = 0.0

    /** True while the current escape uses the pathfinder ([PathWalker]) rather than [EscapeWalker]. */
    private var pathEscape = false

    /** True while the running [PathWalker] walk is this escape's own; bumping [escapeWalkId] orphans its callback. */
    private var ownsPathWalk = false
    private var escapeWalkId = 0

    /** Search budget for an escape route: escapes are a few blocks, and this runs on the client thread. */
    private const val ESCAPE_SEARCH_ITERATIONS = 4000
    private const val ESCAPE_ARRIVE_DISTANCE = 0.5

    /** Squared distance (blocks²) the safe point must move before a running walk is redirected. */
    private const val RETARGET_DISTANCE_SQ = 0.75 * 0.75
    private var escape: FirePitEscape.Escape? = null

    /** Highest y at which a pit block was last seen, used to draw the hazard boxes at the pit itself. */
    private var pitFloorY = 0

    /** Columns holding fire in the latest scan, and the union with the tracker's hazards. Both reused per scan. */
    private val fireColumns = HashSet<Long>()
    private val currentHazards = HashSet<Long>()

    /** Columns showing a pit block in the latest scan, and those in a pit-sized group (see [PitShapes]). Reused per scan. */
    private val pitColumns = HashSet<Long>()
    private val plusColumns = HashSet<Long>()

    /** Columns around Quazii's feet in the latest scan (see [QuaziiStand]); hazards like a pit. Reused per scan. */
    private val quaziiZone = HashSet<Long>()

    /** Pit blocks that count as a pit at the chosen [PitPhase]; [pitBlocks] still decides what is safe to stand on. */
    private var detectBlocks: Set<Block> = emptySet()

    /** Detection speed limits, one per hazard kind. [fireActive] is the fire that has passed its delay. */
    private val pitDelay = DetectDelay()
    private val fireDelay = DetectDelay()
    private val fireActive = HashSet<Long>()
    private val pitRaw = HashSet<Long>()

    /** The hazard kinds behind [combinedHazards], kept apart so [priorityAt] can rank them. Reused per call. */
    private val pitActive = HashSet<Long>()
    private val fireZone = HashSet<Long>()

    /** Pit colour (0 red, 1 yellow, 2 black) seen in each pit column in the latest scan. */
    private val pitPhaseAt = HashMap<Long, Int>()

    /** Priority of the strongest hazard under the player this tick, or [NO_PRIORITY] when in none. */
    private var standingPriority = NO_PRIORITY
    private val blockIdCache = HashMap<Block, String>()

    /** Entity ids already logged by the debug entity log; reset with the rest of the state. */
    private val seenEntities = HashSet<Int>()
    private const val ENTITY_LOG_RANGE = 24.0

    /** Last beam target id per guardian id, for the debug beam log. */
    private val guardianTargets = HashMap<Int, Int>()
    private const val GUARDIAN_LOG_RANGE = 64.0
    private const val NO_BEAM_TARGET = -1
    private const val SEEN_ENTITY_LIMIT = 4000
    private val lastStates = HashMap<Long, BlockState>()
    private var debugWindowMs = 0L
    private var debugLinesThisWindow = 0

    @Volatile
    private var render: FirePitRender = FirePitRender.EMPTY

    fun load() {
        applyConfig(FirePitStore.load())
    }

    /** Releases any held movement keys and drops all state. Safe to call repeatedly. */
    fun unload() {
        MovementHook.install(null)
        stopEscapeWalk()
        resetState()
        pitBlocks = emptySet()
        detectBlocks = emptySet()
    }

    /**
     * Called by the movement guard mixin with the local player's own move for this tick. First keeps the player out
     * of Quazii's body, then hard avoid shortens the move so the hitbox can't step into fire (or the fire avoid
     * radius), so fire always wins. Fire is only active during the fight while fire is seen.
     */
    private val movementGuard = MovementHook.Provider { player, movement ->
        val p = player as? LocalPlayer ?: return@Provider null
        var move = movement as? Vec3 ?: return@Provider null
        var changed = false
        val kept = QuaziiBeamDodge.keepOutMove(p, move)
        if (kept != null) {
            move = kept
            changed = true
        }
        val c = config
        if (!c.enabled || !c.hardAvoid || !hasState || fireBlocked.isEmpty() || (move.x == 0.0 && move.z == 0.0)) {
            return@Provider if (changed) move else null
        }
        val box = p.boundingBox
        if (!HazardClip.clip(box.minX, box.minZ, box.maxX, box.maxZ, move.x, move.z, fireBlocked, clipOut)) {
            return@Provider if (changed) move else null
        }
        Vec3(clipOut[0], move.y, clipOut[1])
    }

    /** Movement keys AirSteerer is holding for the air strafe, so the next tick can keep or change the choice. */
    private var airSteer: KeyCombo? = null
    private val clipOut = DoubleArray(2)

    /** Fire columns expanded by the fire avoid radius, rebuilt every scan; read by [movementGuard]. */
    private val fireBlocked = HashSet<Long>()

    /** Hazard columns of the latest tick, for other helpers that must not walk into a pit. */
    private var hazardsNow: Set<Long> = emptySet()

    /** True while the helper is taking the player out of a pit; other movement helpers should stand down. */
    val escaping: Boolean get() = config.enabled && inside

    /** True when a hitbox of half-width [margin] centred at ([x], [z]) would touch a known pit or fire column. */
    fun hazardNear(x: Double, z: Double, margin: Double): Boolean =
        FirePitEscape.isInside(x, z, hazardsNow, margin)

    fun config(): FirePitConfig = config

    /** Hazard columns currently tracked, for the UI and status displays. */
    fun hazardCount(): Int = if (hasState) tracker.hazardCount(ticks) else 0

    /** Stores [next], writes it to disk and applies it. */
    fun commit(next: FirePitConfig) {
        config = next
        FirePitStore.save(next)
        applyConfig(next)
    }

    /** Shorthand for [commit] with a copy of the current config. */
    fun edit(change: (FirePitConfig) -> FirePitConfig) = commit(change(config))

    /** Client tick. Returns at once unless the feature is enabled. */
    fun tick() {
        EscapeWalker.tick()
        val c = config
        val mc = Minecraft.getInstance()
        // The beam dodge gates itself on a Quazii in view, so it runs with or without the pit helper.
        // Only a hazard that outranks the beam holds the beam helper back (no jump, no countdown line).
        QuaziiBeamDodge.tick(c, mc.level, mc.player, escaping = c.enabled && inside && standingPriority > c.priorityBeam)
        if (!c.enabled) return
        val level = mc.level
        val player = mc.player
        ticks++
        if (level == null || player == null || player.isSpectator || !player.isAlive ||
            !bossGateOpen(c, level, player)
        ) {
            idle()
            return
        }
        val scanned = !hasState || ticks % SCAN_INTERVAL_TICKS == 0L
        if (scanned) scan(c, level, player)
        if (c.debugLog) {
            logNewEntities(level, player)
            logGuardianBeams(level, player)
        }
        val hazards = combinedHazards()
        hazardsNow = hazards
        val steerFree = !EscapeWalker.active && !PathWalker.active && McCompat.currentScreen(mc) == null
        airSteer = if (c.airAvoid && steerFree && hazards.isNotEmpty()) planAirSteer(level, player, hazards, c) else null
        AirSteerer.hold(airSteer)
        val inHazard = hazards.isNotEmpty() &&
            FirePitEscape.isInside(player.x, player.z, hazards, c.margin.toDouble())
        standingPriority = if (inHazard) priorityAt(player.x, player.z, c.margin.toDouble(), c) else NO_PRIORITY
        if (inHazard) {
            if (scanned) escape = solveEscape(level, player, hazards, c)
            enterHazard(c, player)
        } else {
            leaveHazard()
        }
        if (scanned) publishRender(hazards, if (hazards.isEmpty()) player.blockPosition().y - 1 else pitFloorY)
    }

    /** Draws hazard columns in red and the escape line in green. Does nothing when there is nothing to draw. */
    fun renderWorld() {
        val state = render
        if (state === FirePitRender.EMPTY) return
        val floor = state.floorY.toDouble()
        for (key in state.columns) {
            val x = unpackX(key).toDouble()
            val z = unpackZ(key).toDouble()
            val box = AABB(x, floor, z, x + 1.0, floor + 1.0, z + 1.0)
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(HAZARD_OUTLINE, HAZARD_STROKE, HAZARD_FILL))
        }
        val target = state.target ?: return
        val player = Minecraft.getInstance().player ?: return
        val from = Vec3(player.x, player.y + 0.1, player.z)
        val to = Vec3(target.targetX, floor + 1.1, target.targetZ)
        Gizmos.line(from, to, ESCAPE_LINE, ESCAPE_LINE_WIDTH)
    }

    private fun applyConfig(next: FirePitConfig) {
        config = next
        val guardFire = next.enabled && next.hardAvoid
        val guardQuazii = next.beamDodge != BeamDodge.OFF && next.avoidQuaziiBody
        MovementHook.install(if (guardFire || guardQuazii) movementGuard else null)
        pitBlocks = resolvePitBlocks(next.pitBlocks)
        val skipped = resolvePitBlocks(next.pitPhase.earlier)
        detectBlocks = pitBlocks.filterTo(HashSet()) { it !in skipped && it !in FIRE_BLOCKS }
        if (!next.airAvoid) {
            airSteer = null
            AirSteerer.release()
        }
        if (!next.debugLog) lastStates.clear()
        if (next.mode != EscapeMode.WALK) stopEscapeWalk()
        if (!next.enabled) {
            stopEscapeWalk()
            resetState()
        }
    }

    private fun resolvePitBlocks(ids: List<String>): Set<Block> {
        val resolved = HashSet<Block>()
        for (raw in ids) {
            val id = Identifier.tryParse(raw) ?: continue
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                Icantpy.LOGGER.warn("fire-pit: unknown pit block {}", raw)
                continue
            }
            resolved.add(BuiltInRegistries.BLOCK.getValue(id))
        }
        return resolved
    }

    /** Drops tracked state when the world or feature goes away. Cheap when there is nothing to drop. */
    private fun idle() {
        if (!hasState) return
        stopEscapeWalk()
        resetState()
    }

    private fun resetState() {
        hazardsNow = emptySet()
        fireBlocked.clear()
        hasState = false
        inside = false
        walkStarted = false
        escape = null
        bossActive = false
        nextBossCheckTick = 0L
        tracker.clear()
        fireColumns.clear()
        currentHazards.clear()
        pitColumns.clear()
        plusColumns.clear()
        quaziiZone.clear()
        fireActive.clear()
        pitRaw.clear()
        pitActive.clear()
        fireZone.clear()
        pitPhaseAt.clear()
        standingPriority = NO_PRIORITY
        pitDelay.clear()
        fireDelay.clear()
        airSteer = null
        AirSteerer.release()
        lastStates.clear()
        seenEntities.clear()
        guardianTargets.clear()
        render = FirePitRender.EMPTY
    }

    private fun bossGateOpen(c: FirePitConfig, level: ClientLevel, player: LocalPlayer): Boolean {
        if (!c.onlyInBossFight) return true
        if (ticks >= nextBossCheckTick) {
            nextBossCheckTick = ticks + BOSS_CHECK_INTERVAL_TICKS
            // The sidebar's quest line stays "Inferno Demonlord" for the whole fight; the boss's own name tag can
            // vanish (e.g. while its demons are out), which used to turn pit detection off mid-fight.
            val snapshot = SkyblockLocation.snapshot
            bossActive = snapshot.slayerBossVisible && (snapshot.blazeSlayerQuest || demonlordNearby(level, player))
        }
        return bossActive
    }

    private fun demonlordNearby(level: ClientLevel, player: LocalPlayer): Boolean {
        val rangeSq = BOSS_RANGE_BLOCKS * BOSS_RANGE_BLOCKS
        for (entity in level.entitiesForRendering()) {
            if (entity.distanceToSqr(player) > rangeSq) continue
            val name = entity.customName?.string ?: entity.name.string
            if (Text.strip(name).contains(BOSS_NAME)) return true
        }
        return false
    }

    /** Pits seen forming (tracker), pit-sized groups however they appeared, and any column with fire right now. */
    private fun combinedHazards(): Set<Long> {
        val tracked = tracker.hazards(ticks)
        pitActive.clear()
        fireZone.clear()
        if (tracked.isEmpty() && plusColumns.isEmpty() && fireActive.isEmpty() && quaziiZone.isEmpty()) {
            pitDelay.clear()
            return tracked
        }
        pitRaw.clear()
        pitRaw.addAll(tracked)
        pitRaw.addAll(plusColumns)
        pitDelay.filter(pitRaw, ticks, DetectDelay.ticksOf(config.pitDelayMs), pitActive)
        // Fire keeps the configured berth: columns within fireRadius of a fire block count as fire too.
        PitShapes.expand(fireActive, config.fireRadius, fireZone)
        currentHazards.clear()
        currentHazards.addAll(pitActive)
        currentHazards.addAll(quaziiZone)
        currentHazards.addAll(fireZone)
        return currentHazards
    }

    private fun scan(c: FirePitConfig, level: ClientLevel, player: LocalPlayer) {
        fireColumns.clear()
        pitColumns.clear()
        plusColumns.clear()
        pitPhaseAt.clear()
        val origin = player.blockPosition()
        val radius = c.scanRadius
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                scanColumn(level, origin.x + dx, origin.y, origin.z + dz, c.debugLog)
            }
        }
        PitShapes.pitGroups(pitColumns, plusColumns)
        if (c.quaziiStand) QuaziiStand.zone(level, player, c.quaziiStandRadius, quaziiZone) else quaziiZone.clear()
        if (c.avoidMobs) AvoidMobs.addZone(level, player, AvoidMobs.parse(c.avoidMobNames), c.avoidMobRadius, quaziiZone)
        if (c.avoidPillars) AvoidMobs.addPillars(level, player, c.pillarRadius, quaziiZone)
        fireActive.clear()
        fireDelay.filter(fireColumns, ticks, DetectDelay.ticksOf(c.fireDelayMs), fireActive)
        fireBlocked.clear()
        PitShapes.expand(fireActive, c.fireRadius, fireBlocked)
        tracker.prune(ticks)
        hasState = true
    }

    /**
     * A column is a pit when any block in the band around [feetY] is a pit block. The tracker only flags columns
     * that change into pit blocks, so terracotta that is part of the arena never counts.
     */
    private fun scanColumn(level: ClientLevel, x: Int, feetY: Int, z: Int, debug: Boolean) {
        if (!level.isLoaded(BlockPos(x, feetY, z))) return
        var pit = false
        for (y in feetY + SCAN_ABOVE downTo feetY - SCAN_BELOW) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            if (!pit && state.block in detectBlocks) {
                pit = true
                pitFloorY = y
                pitPhaseAt[packXZ(x, z)] = phaseOf(state.block)
            }
            if (state.block in FIRE_BLOCKS) fireColumns.add(packXZ(x, z))
            if (debug) logStateChange(pos, state)
        }
        if (pit) pitColumns.add(packXZ(x, z))
        tracker.observe(ticks, x, z, pit)
    }

    /**
     * While airborne, predicts the landing column (see [AirLanding]). When it is a hazard, picks the W/A/S/D keys,
     * out of the eight directions held on top of the keys the player is already pressing, whose air acceleration
     * moves the landing out of every hazard, preferring the smallest shift. The current choice is kept while it still
     * works, and released once the landing is safe without it, so the keys do not flicker. Null when on the ground,
     * landing safely, or no key helps. Input only: no packets and no camera change.
     */
    private fun planAirSteer(
        level: ClientLevel,
        player: LocalPlayer,
        hazards: Set<Long>,
        c: FirePitConfig,
    ): KeyCombo? {
        if (player.onGround() || player.isInWater || player.isFallFlying || player.isSpectator) return null
        val v = player.deltaMovement
        val floorTop = { x: Int, z: Int, y: Double -> floorTop(level, x, z, y) }
        val held = AirSteerer.physical()
        val speed = if (player.isSprinting) AirLanding.AIR_SPEED_SPRINT else AirLanding.AIR_SPEED
        val yaw = player.yRot.toDouble()
        fun landing(extra: KeyCombo?): AirLanding.Landing? {
            val k = if (extra == null) held else KeyCombo(
                held.forward || extra.forward,
                held.back || extra.back,
                held.left || extra.left,
                held.right || extra.right,
            )
            val a = AirLanding.inputAccel(k.forward, k.back, k.left, k.right, yaw, speed)
            return AirLanding.predict(player.x, player.y, player.z, v.x, v.y, v.z, a.x, a.z, floorTop)
        }
        val margin = c.margin.toDouble()
        fun safe(land: AirLanding.Landing?, extraMargin: Double) =
            land != null && !FirePitEscape.isInside(land.x, land.z, hazards, margin + extraMargin)
        val base = landing(null) ?: return null
        val current = airSteer
        if (current == null) {
            if (!FirePitEscape.isInside(base.x, base.z, hazards, margin)) return null
        } else {
            if (safe(base, AIR_EXTRA_MARGIN)) return null
            if (safe(landing(current), AIR_EXTRA_MARGIN)) return current
        }
        var best: KeyCombo? = null
        var bestShift = Double.MAX_VALUE
        for (combo in AIR_COMBOS) {
            val land = landing(combo)
            if (!safe(land, AIR_EXTRA_MARGIN) || land == null) continue
            val shift = (land.x - base.x) * (land.x - base.x) + (land.z - base.z) * (land.z - base.z)
            if (shift < bestShift) {
                bestShift = shift
                best = combo
            }
        }
        return best
    }

    /** Top of the highest solid block at or just under [fromY] in the column, or NaN when none within the scan. */
    private fun floorTop(level: ClientLevel, x: Int, z: Int, fromY: Double): Double {
        val top = floor(fromY + AIR_FLOOR_SLACK).toInt()
        for (y in top downTo top - AIR_FLOOR_SCAN) {
            val pos = BlockPos(x, y, z)
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return y + 1.0
        }
        return Double.NaN
    }

    private fun solveEscape(
        level: ClientLevel,
        player: LocalPlayer,
        hazards: Set<Long>,
        c: FirePitConfig,
    ): FirePitEscape.Escape? {
        val feetY = player.blockPosition().y
        return FirePitEscape.solve(
            player.x,
            player.z,
            hazards,
            c.margin.toDouble(),
            c.maxEscapeDistance.toDouble(),
            // Never cross a pit that outranks what the player stands in to dodge something milder: from a gap
            // between pits, staying put beats walking into the next pit.
            blocked = { key -> columnPriority(key, c) > standingPriority },
        ) { x, z -> isStandable(level, x, feetY, z) }
    }

    /**
     * The first solid block below the player's feet (within the scan band) is the floor; the column is standable
     * when that floor is not a pit block and the two blocks above it are open. Searching down instead of assuming
     * feet - 1 keeps this right while the player is mid-jump.
     */
    private fun isStandable(level: ClientLevel, x: Int, feetY: Int, z: Int): Boolean {
        if (!level.isLoaded(BlockPos(x, feetY, z))) return false
        for (y in feetY downTo feetY - SCAN_BELOW) {
            val floor = BlockPos(x, y, z)
            val floorState = level.getBlockState(floor)
            if (isPit(floorState)) return false
            if (floorState.getCollisionShape(level, floor).isEmpty()) continue
            val feet = floor.above()
            val head = feet.above()
            val feetState = level.getBlockState(feet)
            return !isPit(feetState) && feetState.block !in FIRE_BLOCKS &&
                feetState.getCollisionShape(level, feet).isEmpty() &&
                level.getBlockState(head).getCollisionShape(level, head).isEmpty()
        }
        return false
    }

    private fun enterHazard(c: FirePitConfig, player: LocalPlayer) {
        if (!inside) {
            inside = true
            if (c.sound) AlertSounds.play("pling")
        }
        val found = escape
        if (found == null) {
            // Every way out crosses something worse than where the player stands: hold still.
            if (walkStarted) {
                stopEscapeWalk()
                walkStarted = false
            }
            return
        }
        if (ticks >= nextNotifyTick) {
            nextNotifyTick = ticks + NOTIFY_INTERVAL_TICKS
            notify(player, found)
        }
        if (c.mode != EscapeMode.WALK) return
        // Restart when the walk ended while still in danger, or when new fire moved the safe point.
        val moved = (found.targetX - walkTargetX) * (found.targetX - walkTargetX) +
            (found.targetZ - walkTargetZ) * (found.targetZ - walkTargetZ) > RETARGET_DISTANCE_SQ
        val walking = if (pathEscape) PathWalker.active else EscapeWalker.active
        if (!walkStarted || !walking || moved) {
            startEscapeWalk(player, found, c)
            walkTargetX = found.targetX
            walkTargetZ = found.targetZ
        }
    }

    /**
     * Routes to [found] with the ported pathfinder over a world where hazard columns are walls, so the walk goes
     * around pits and can step up, drop and jump on the way. The camera is left alone (8-way W/A/S/D steering).
     * Falls back to the straight-line [EscapeWalker] when no route is found within the small search budget.
     */
    private fun startEscapeWalk(player: LocalPlayer, found: FirePitEscape.Escape, c: FirePitConfig) {
        val grid = HazardGrid(WorldBlockGrid, HashSet(combinedHazards()))
        val feet = player.blockPosition()
        val start = PathNode(feet.x, feet.y, feet.z)
        val target = PathNode(floor(found.targetX).toInt(), feet.y, floor(found.targetZ).toInt())
        val route = PathFinder(grid, ESCAPE_SEARCH_ITERATIONS).findPath(start, target)
        if (route == null || route.size < 2) {
            pathEscape = false
            if (PathWalker.active && ownsPathWalk) PathWalker.stop()
            EscapeWalker.start(found.targetX, found.targetZ, c.walkTimeoutMs)
            walkStarted = EscapeWalker.active
            return
        }
        EscapeWalker.stop()
        val settings = WalkSettings(
            rotateCamera = false,
            sprint = true,
            jump = true,
            arriveDistance = ESCAPE_ARRIVE_DISTANCE,
            grid = grid,
            searchIterations = ESCAPE_SEARCH_ITERATIONS,
        )
        val walkId = ++escapeWalkId
        PathWalker.start(route.last(), route, settings) { if (walkId == escapeWalkId) ownsPathWalk = false }
        pathEscape = true
        ownsPathWalk = PathWalker.active
        walkStarted = PathWalker.active
    }

    /** Strongest configured priority among the hazard columns the player's footprint (point inflated by [margin]) touches. */
    private fun priorityAt(px: Double, pz: Double, margin: Double, c: FirePitConfig): Int {
        var best = NO_PRIORITY
        for (x in floor(px - margin).toInt()..floor(px + margin).toInt()) {
            for (z in floor(pz - margin).toInt()..floor(pz + margin).toInt()) {
                best = maxOf(best, columnPriority(packXZ(x, z), c))
            }
        }
        return best
    }

    private fun columnPriority(key: Long, c: FirePitConfig): Int {
        var best = NO_PRIORITY
        if (key in pitActive) best = maxOf(best, c.pitPriority(pitPhaseAt[key] ?: 0))
        if (key in fireZone) best = maxOf(best, c.priorityFire)
        if (key in quaziiZone) best = maxOf(best, c.priorityQuaziiZone)
        return best
    }

    private fun phaseOf(block: Block): Int = when (blockId(block)) {
        "minecraft:yellow_terracotta" -> 1
        "minecraft:black_terracotta" -> 2
        else -> 0
    }

    private fun leaveHazard() {
        inside = false
        standingPriority = NO_PRIORITY
        walkStarted = false
        escape = null
        stopEscapeWalk()
    }

    /** Stops whichever escape walk is running. Never stops a walk someone else started (e.g. /icantpy goto). */
    private fun stopEscapeWalk() {
        EscapeWalker.stop()
        AirSteerer.release()
        if (ownsPathWalk) {
            escapeWalkId++
            ownsPathWalk = false
            PathWalker.stop()
        }
        pathEscape = false
    }

    /** Action bar text: direction relative to the player's facing and distance to the safe point. */
    private fun notify(player: LocalPlayer, found: FirePitEscape.Escape) {
        val direction = FirePitEscape.relativeDirection(player.yRot, found.bearingDeg)
        val distance = String.format(Locale.ROOT, "%.1f", found.distance)
        // The beam countdown shares the action bar; when the beam outranks what you stand in, it keeps the line.
        val beam = QuaziiBeamDodge.threatImpact
        if (!beam.isNaN() && beam in 0.0..BEAM_NOTICE_TICKS && config.priorityBeam > standingPriority) return
        val atQuazii = FirePitEscape.isInside(player.x, player.z, quaziiZone, config.margin.toDouble())
        val what = if (atQuazii) "Too close to a boss!" else "Fire pit!"
        player.sendOverlayMessage(
            Component.literal("$what Move ${direction.arrow} ${direction.label} $distance blocks"),
        )
    }

    private fun publishRender(hazards: Set<Long>, floorY: Int) {
        val columns = LongArray(hazards.size)
        var index = 0
        for (key in hazards) columns[index++] = key
        render = FirePitRender(columns, floorY, escape)
    }

    private fun isPit(state: BlockState): Boolean = pitBlocks.contains(state.block)

    /** Debug aid: logs block changes at feet levels, bounded to [DEBUG_LINES_PER_SECOND] lines per second. */
    private fun logStateChange(pos: BlockPos, state: BlockState) {
        if (lastStates.size > DEBUG_STATE_LIMIT) lastStates.clear()
        val previous = lastStates.put(pos.asLong(), state) ?: return
        if (previous === state || !debugLineAllowed()) return
        val oldBlock = blockId(previous.block)
        val newBlock = blockId(state.block)
        val pitTag = if (isPit(previous) || isPit(state)) " [pit]" else ""
        val detail = if (oldBlock == newBlock) " (state)" else ""
        Icantpy.LOGGER.info("fire-pit debug: (${pos.x},${pos.y},${pos.z}) $oldBlock -> $newBlock$detail$pitTag")
    }

    /**
     * Debug aid for mechanics not handled yet (e.g. Quazii's beam): logs every entity that newly appears within
     * [ENTITY_LOG_RANGE] blocks during the fight, with its type, name, position and velocity. Shares the debug rate limit.
     */
    private fun logNewEntities(level: ClientLevel, player: LocalPlayer) {
        if (seenEntities.size > SEEN_ENTITY_LIMIT) seenEntities.clear()
        val rangeSq = ENTITY_LOG_RANGE * ENTITY_LOG_RANGE
        for (entity in level.entitiesForRendering()) {
            if (entity === player || entity.distanceToSqr(player) > rangeSq) continue
            if (!seenEntities.add(entity.id) || !debugLineAllowed()) continue
            val type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
            val name = Text.strip(entity.name.string)
            val motion = entity.deltaMovement
            Icantpy.LOGGER.info(
                String.format(
                    Locale.ROOT,
                    "fire-pit debug: entity %s \"%s\" at (%.1f, %.1f, %.1f) d=%.1f v=(%.2f, %.2f, %.2f)",
                    type, name, entity.x, entity.y, entity.z, sqrt(entity.distanceToSqr(player)),
                    motion.x, motion.y, motion.z,
                ),
            )
        }
    }

    /**
     * Debug aid for Quazii's beam, which looks like a guardian laser: logs when any guardian within
     * [GUARDIAN_LOG_RANGE] blocks starts or stops beaming, who it targets, and how long its charge is.
     * Always logged (not rate-limited) since these events are rare and are the point of the log.
     */
    private fun logGuardianBeams(level: ClientLevel, player: LocalPlayer) {
        val rangeSq = GUARDIAN_LOG_RANGE * GUARDIAN_LOG_RANGE
        for (entity in level.entitiesForRendering()) {
            val guardian = entity as? Guardian ?: continue
            if (guardian.distanceToSqr(player) > rangeSq) continue
            val target = guardian.activeAttackTarget
            val targetId = target?.id ?: NO_BEAM_TARGET
            val previous = guardianTargets.put(guardian.id, targetId)
            if (previous == targetId || (previous == null && targetId == NO_BEAM_TARGET)) continue
            val who = when {
                target == null -> "none"
                target === player -> "YOU"
                else -> "${BuiltInRegistries.ENTITY_TYPE.getKey(target.type)} \"${Text.strip(target.name.string)}\""
            }
            Icantpy.LOGGER.info(
                String.format(
                    Locale.ROOT,
                    "fire-pit debug: guardian %d \"%s\" at (%.1f, %.1f, %.1f) d=%.1f beam %s target=%s charge=%dt",
                    guardian.id, Text.strip(guardian.name.string), guardian.x, guardian.y, guardian.z,
                    sqrt(guardian.distanceToSqr(player)), if (target == null) "STOP" else "START", who,
                    guardian.attackDuration,
                ),
            )
        }
        if (guardianTargets.size > SEEN_ENTITY_LIMIT) guardianTargets.clear()
    }

    private fun debugLineAllowed(): Boolean {
        val now = System.currentTimeMillis()
        if (now - debugWindowMs >= 1000L) {
            debugWindowMs = now
            debugLinesThisWindow = 0
        }
        if (debugLinesThisWindow >= DEBUG_LINES_PER_SECOND) return false
        debugLinesThisWindow++
        return true
    }

    private fun blockId(block: Block): String =
        blockIdCache.getOrPut(block) { BuiltInRegistries.BLOCK.getKey(block).toString() }
}
