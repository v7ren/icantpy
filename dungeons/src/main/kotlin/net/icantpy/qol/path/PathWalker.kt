package net.icantpy.qol.path

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.compat.McCompat
import net.icantpy.mixin.KeyMappingAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import org.lwjgl.glfw.GLFW
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Walk tuning. [arriveDistance] is the normal node arrival radius. Turns use it minus 0.2, matching senura's 0.85 / 0.65.
 */
data class WalkSettings(
    val rotateCamera: Boolean = true,
    val sprint: Boolean = true,
    val jump: Boolean = true,
    val arriveDistance: Double = 0.85,
    /** World view used when re-planning mid-walk, e.g. one that also blocks hazards. */
    val grid: BlockGrid = WorldBlockGrid,
    /** Search cap for re-planning; the search runs on the client thread, so keep it small. */
    val searchIterations: Int = 20_000,
)

/**
 * Walks the player along a [PathNode] route by pressing vanilla movement KeyMappings. Ported from senura's current
 * PathWalker, which runs on a 10 ms coroutine loop; here one step runs per client tick (50 ms) and one camera-easing
 * step per render frame. Timers use wall-clock milliseconds like senura. It only presses keys it owns, turns the camera
 * through Entity.turn, and never sends or alters packets.
 */
object PathWalker {
    private const val KEY_COUNT = 6
    private const val FORWARD = 0
    private const val LEFT = 1
    private const val RIGHT = 2
    private const val SPRINT = 3
    private const val JUMP = 4
    private const val BACK = 5

    /** Stuck detection (senura): moving 0.4 blocks resets the clock; nudge after 450 ms on ground; stuck after 900 ms. */
    private const val STUCK_MOVE_BLOCKS = 0.4
    private const val NUDGE_AFTER_MS = 450L
    private const val NUDGE_MS = 250L
    private const val STUCK_MS = 900L

    /** Repath cooldown: starts at 750 ms, doubles on each failed repath up to 4000 ms, reset on a successful one. */
    private const val REPATH_COOLDOWN_START_MS = 750L
    private const val REPATH_COOLDOWN_MAX_MS = 4000L
    private const val MAX_FAILED_REPATHS = 5

    /**
     * Added on top of senura: senura keeps repathing forever while repaths succeed but the player never reaches a node.
     * If no node advances for this long, the walk ends with onDone(false).
     */
    private const val NO_PROGRESS_LIMIT_MS = 15_000L

    /** Resume after a repath: search the first few nodes for the closest one, skip it if already within 0.85 blocks. */
    private const val RESUME_SEARCH_NODES = 4
    private const val RESUME_SKIP_BLOCKS = 0.85

    /** Node reached only when the feet are less than this many blocks above the node (senura dyNode < 1.25). */
    private const val NODE_HEIGHT_TOLERANCE = 1.25
    private const val TURN_ANGLE_DEG = 25.0
    private const val TURN_ARRIVE_SHRINK = 0.2
    private const val OVERSHOOT_RANGE = 1.2
    private const val OVERSHOOT_SLACK = 0.6
    private const val BEHIND_NODE_DOT = -0.1
    private const val OFF_TRACK_BLOCKS = 2.8
    private const val OFF_TRACK_SEGMENT_MIN = 0.1
    private const val LOOK_DISTANCE = 5.0

    /** senura: forward when the look target is under 85 degrees off the view, strafe past 45 degrees with cross > 0.3. */
    private const val FORWARD_ANGLE_DEG = 85.0
    private const val STRAFE_ANGLE_DEG = 45.0
    private const val STRAFE_CROSS = 0.3
    private const val SPRINT_ANGLE_DEG = 35.0
    private const val SPRINT_MIN_FINAL_BLOCKS = 1.5

    /** Jump rules (senura WorldUtils.shouldJump). */
    private const val JUMP_LOOKAHEAD_BLOCKS = 0.65
    private const val JUMP_TARGET_RISE = 0.65
    private const val JUMP_TARGET_RANGE = 2.0

    /** 8-way steering (rotateCamera false): sector limits in degrees. */
    private const val EIGHT_WAY_FORWARD_DEG = 67.5
    private const val EIGHT_WAY_BACK_DEG = 112.5
    private const val EIGHT_WAY_SIDE_MIN_DEG = 22.5
    private const val EIGHT_WAY_SIDE_MAX_DEG = 157.5

    /** Mirrors vanilla Entity.turn, which applies `delta * 0.15` per call. */
    private const val TURN_SCALE = 0.15
    private const val NORMALIZE_EPSILON = 1e-4

    /** True while a walk is running. Inactive callers pay only this check. */
    var active: Boolean = false
        private set

    private var path: List<PathNode> = emptyList()
    private var goal: PathNode? = null
    private var index = 0
    private var settings = WalkSettings()
    private var onDone: ((Boolean) -> Unit)? = null
    private var lookTarget: Vec? = null
    private val look = SmoothLook()
    private val owned = BooleanArray(KEY_COUNT)

    private var lastRepathMs = 0L
    private var repathCooldownMs = REPATH_COOLDOWN_START_MS
    private var failedRepaths = 0
    private var stuckPos: Vec? = null
    private var stuckMs = 0L
    private var nudgeUntilMs = 0L
    private var lastAdvanceMs = 0L

    /** Starts walking [path] (first node = start). [onDone] is called once with true on arrival, false on failure or stop. */
    fun start(goal: PathNode, path: List<PathNode>, settings: WalkSettings, onDone: (Boolean) -> Unit) {
        stop()
        val player = Minecraft.getInstance().player
        // A screen being open is fine: /icantpy goto runs while the chat screen is still up. tick() simply waits
        // (keys released) until the screen closes, then walks.
        if (player == null || path.isEmpty()) {
            onDone(false)
            return
        }
        val now = System.currentTimeMillis()
        this.path = path
        this.goal = goal
        this.settings = settings
        this.onDone = onDone
        index = 0
        lookTarget = null
        look.reset()
        lastRepathMs = 0L
        repathCooldownMs = REPATH_COOLDOWN_START_MS
        failedRepaths = 0
        stuckPos = playerPos(player)
        stuckMs = now
        nudgeUntilMs = 0L
        lastAdvanceMs = now
        active = true
    }

    /** Ends the walk, releases only the keys it pressed and reports failure to the pending callback. Safe when inactive. */
    fun stop() {
        if (active) finish(false)
    }

    fun currentPath(): List<PathNode> = path

    fun currentIndex(): Int = index

    /** Client tick: stuck clock, repath, arrival and key presses. One call is one senura loop iteration. */
    fun tick() {
        if (!active) return
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player == null || !player.isAlive) return finish(false)
        val now = System.currentTimeMillis()
        if (McCompat.currentScreen(mc) != null) {
            releaseOwned(mc)
            freezeClocks(now)
            return
        }
        if (userMovementKeys(mc).any { physicallyDown(mc, it) }) return finish(false)

        val pos = playerPos(player)
        updateStuckClock(pos, now, player.onGround())
        if (now - lastAdvanceMs > NO_PROGRESS_LIMIT_MS) return finish(false)
        // senura `continue`s after a successful repath, so no movement is applied on that tick.
        if (needsRepath(pos, now) && repath(pos, now)) return
        if (updateArrival(pos, now)) return

        val nodeTarget = center(path[index])
        val smoothed = updateLookTarget(player, pos)
        steer(player, pos, smoothed, nodeTarget, now)
    }

    /** Render-rate camera easing toward the current look target. Does nothing unless camera rotation is enabled. */
    fun onFrameStart() {
        if (!active || !settings.rotateCamera) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (McCompat.currentScreen(mc) != null) {
            look.reset()
            return
        }
        // senura updates the look spline in both tick and render; the smoothing runs at both rates.
        val target = updateLookTarget(player, playerPos(player))
        val eye = player.eyePosition
        val wanted = SmoothLook.rotationTo(target.x - eye.x, target.y - eye.y, target.z - eye.z)
        val currentYaw = player.getYRot()
        val currentPitch = player.getXRot()
        val next = look.step(
            currentYaw, currentPitch, wanted[0], wanted[1],
            look.frameSeconds(System.nanoTime()),
        )
        val deltaYaw = next[0] - currentYaw
        val deltaPitch = next[1] - currentPitch
        if (deltaYaw == 0f && deltaPitch == 0f) return
        player.turn(deltaYaw / TURN_SCALE, deltaPitch / TURN_SCALE)
    }

    private fun finish(success: Boolean) {
        releaseOwned(Minecraft.getInstance())
        active = false
        path = emptyList()
        goal = null
        index = 0
        lookTarget = null
        stuckPos = null
        val callback = onDone
        onDone = null
        callback?.invoke(success)
    }

    /** Keeps the stuck and progress clocks from running while a screen is open, so they restart cleanly afterwards. */
    private fun freezeClocks(now: Long) {
        stuckMs = now
        lastAdvanceMs = now
        nudgeUntilMs = 0L
    }

    private fun updateStuckClock(pos: Vec, now: Long, onGround: Boolean) {
        val last = stuckPos
        if (last == null || dist(pos, last) >= STUCK_MOVE_BLOCKS) {
            stuckPos = pos
            stuckMs = now
        }
        val stuckFor = now - stuckMs
        if (stuckFor > NUDGE_AFTER_MS && onGround && nudgeUntilMs < now) {
            nudgeUntilMs = now + NUDGE_MS
        }
    }

    private fun needsRepath(pos: Vec, now: Long): Boolean {
        val stuck = now - stuckMs > STUCK_MS
        return (offTrack(pos) || stuck) && now - lastRepathMs > repathCooldownMs
    }

    /** Returns true when the tick is consumed (a new path was applied, or the walk ended). False when repathing failed but the walk goes on. */
    private fun repath(pos: Vec, now: Long): Boolean {
        lastRepathMs = now
        stuckMs = now
        stuckPos = pos
        val destination = goal ?: return false
        val start = PathNode(floor(pos.x).toInt(), floor(pos.y).toInt(), floor(pos.z).toInt())
        val found = PathFinder(settings.grid, settings.searchIterations).findPath(start, destination)
        if (found.isNullOrEmpty()) {
            failedRepaths++
            repathCooldownMs = min(REPATH_COOLDOWN_MAX_MS, repathCooldownMs * 2)
            if (failedRepaths >= MAX_FAILED_REPATHS) {
                finish(false)
                return true
            }
            return false
        }
        failedRepaths = 0
        repathCooldownMs = REPATH_COOLDOWN_START_MS
        path = found
        index = resumeIndex(found, pos)
        lastAdvanceMs = now
        return true
    }

    private fun resumeIndex(newPath: List<PathNode>, pos: Vec): Int {
        val closest = closestNodeIndex(newPath, pos, RESUME_SEARCH_NODES)
        if (closest < newPath.lastIndex) {
            val node = newPath[closest]
            if (hypot(pos.x - (node.x + 0.5), pos.z - (node.z + 0.5)) < RESUME_SKIP_BLOCKS) return closest + 1
        }
        return closest
    }

    private fun closestNodeIndex(nodes: List<PathNode>, pos: Vec, limit: Int): Int {
        var best = 0
        var bestDist = Double.MAX_VALUE
        for (i in 0 until min(limit, nodes.size)) {
            val node = nodes[i]
            val d = hypot(pos.x - (node.x + 0.5), pos.z - (node.z + 0.5))
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    /** senura's offTrack: the current node must have clearance and the player must stay near the segment from the previous node. */
    private fun offTrack(pos: Vec): Boolean {
        val idx = index.coerceIn(0, path.lastIndex)
        val node = path[idx]
        if (!hasClearance(node)) return true
        val target = center(node)
        val prev = center(path[(idx - 1).coerceAtLeast(0)])
        val segment = sub(target, prev)
        val segLen = sqrt(dot(segment, segment))
        val distance = if (segLen > OFF_TRACK_SEGMENT_MIN) {
            val t = (dot(sub(pos, prev), segment) / (segLen * segLen)).coerceIn(0.0, 1.0)
            dist(pos, add(prev, scale(segment, t)))
        } else {
            dist(pos, target)
        }
        return distance >= OFF_TRACK_BLOCKS
    }

    private fun hasClearance(node: PathNode): Boolean =
        WorldBlockGrid.passable(node.x, node.y, node.z) && WorldBlockGrid.passable(node.x, node.y + 1, node.z)

    /** Advances to the next node when reached. Returns true when the walk finished. */
    private fun updateArrival(pos: Vec, now: Long): Boolean {
        val node = path[index]
        val nodeCenter = center(node)
        val next = path.getOrNull(index + 1)
        val nextCenter = next?.let { center(it) }
        val isTurn = nextCenter != null && isTurnAhead(pos, nodeCenter, nextCenter)
        val threshold = settings.arriveDistance - if (isTurn) TURN_ARRIVE_SHRINK else 0.0
        val withinHeight = pos.y - node.y < NODE_HEIGHT_TOLERANCE
        val reached = withinHeight && (
            hypot(pos.x - nodeCenter.x, pos.z - nodeCenter.z) < threshold ||
                (nextCenter != null && passedSegment(pos, nodeCenter, nextCenter))
            )
        if (!reached) return false
        if (next == null) {
            finish(true)
            return true
        }
        index++
        lastAdvanceMs = now
        return false
    }

    private fun isTurnAhead(pos: Vec, node: Vec, next: Vec): Boolean {
        val toNode = flatUnit(node.x - pos.x, node.z - pos.z)
        val segment = flatUnit(next.x - node.x, next.z - node.z)
        val angle = Math.toDegrees(acos(dot(toNode, segment).coerceIn(-1.0, 1.0)))
        return angle > TURN_ANGLE_DEG
    }

    /** senura's segment projection: the player is past the node and moving along the next segment. */
    private fun passedSegment(pos: Vec, node: Vec, next: Vec): Boolean {
        val toCurrent = dist(pos, node)
        val toNext = dist(pos, next)
        val segmentLength = dist(node, next)
        val nearing = toNext < toCurrent || (toCurrent < OVERSHOOT_RANGE && toNext < segmentLength + OVERSHOOT_SLACK)
        if (!nearing) return false
        val segmentDir = unit(sub(next, node))
        return dot(sub(pos, node), segmentDir) > BEHIND_NODE_DOT
    }

    /** Updates and returns the smoothed look-ahead target (senura's getLookTarget: spline 5 blocks ahead, then lerp 0.28). */
    private fun updateLookTarget(player: LocalPlayer, pos: Vec): Vec {
        val eyeY = pos.y + player.getEyeHeight(player.pose).toDouble()
        val raw = PathSpline.lookAhead(path, index, pos.x, eyeY, pos.z, LOOK_DISTANCE)
        val smoothed = PathSpline.smooth(lookTarget, raw)
        lookTarget = smoothed
        return smoothed
    }

    private fun steer(player: LocalPlayer, pos: Vec, aim: Vec, nodeTarget: Vec, now: Long) {
        val keys = movementKeys(Minecraft.getInstance())
        val view = player.getViewVector(1.0f)
        val forward = flatUnit(view.x, view.z)
        val toLook = flatUnit(aim.x - pos.x, aim.z - pos.z)
        val crossY = forward.z * toLook.x - forward.x * toLook.z
        val angle = Math.toDegrees(acos(dot(forward, toLook).coerceIn(-1.0, 1.0)))
        val isFinal = index >= path.lastIndex
        val distToFinal = path.lastOrNull()?.let { dist(pos, center(it)) } ?: 0.0

        if (settings.rotateCamera) {
            steerWithCamera(keys, angle, crossY)
        } else {
            steerEightWay(keys, angle, crossY)
        }
        press(keys, SPRINT, settings.sprint && angle < SPRINT_ANGLE_DEG && (!isFinal || distToFinal > SPRINT_MIN_FINAL_BLOCKS))
        press(keys, JUMP, settings.jump && (now < nudgeUntilMs || shouldJump(player, pos, nodeTarget)))
    }

    /** senura's steering with the camera turned toward the path: forward plus light strafing, never back. */
    private fun steerWithCamera(keys: Array<KeyMapping>, angle: Double, crossY: Double) {
        press(keys, FORWARD, angle < FORWARD_ANGLE_DEG)
        press(keys, LEFT, angle > STRAFE_ANGLE_DEG && crossY > STRAFE_CROSS)
        press(keys, RIGHT, angle > STRAFE_ANGLE_DEG && crossY < -STRAFE_CROSS)
        press(keys, BACK, false)
    }

    /** Camera stays where the player put it: W/A/S/D combination (back included) toward the path from the current facing. */
    private fun steerEightWay(keys: Array<KeyMapping>, angle: Double, crossY: Double) {
        val side = angle > EIGHT_WAY_SIDE_MIN_DEG && angle < EIGHT_WAY_SIDE_MAX_DEG
        press(keys, FORWARD, angle < EIGHT_WAY_FORWARD_DEG)
        press(keys, BACK, angle > EIGHT_WAY_BACK_DEG)
        press(keys, LEFT, side && crossY > 0.0)
        press(keys, RIGHT, side && crossY < 0.0)
    }

    /**
     * senura's WorldUtils.shouldJump on the BlockGrid. Slab and stair checks are not available (BlockGrid has no
     * shape information), so the slab/stair exemptions are treated as false. Solid tests use the grid's solidFloor.
     */
    private fun shouldJump(player: LocalPlayer, pos: Vec, target: Vec): Boolean {
        if (player.isInWater || player.isInLava) return true
        if (!player.onGround()) return false

        val toTargetX = target.x - pos.x
        val toTargetZ = target.z - pos.z
        val hDist = hypot(toTargetX, toTargetZ)
        val dir = if (hDist > 0.01) {
            Vec(toTargetX / hDist, 0.0, toTargetZ / hDist)
        } else {
            val view = player.getViewVector(1.0f)
            unit(Vec(view.x, view.y, view.z))
        }
        val frontX = floor(pos.x + dir.x * JUMP_LOOKAHEAD_BLOCKS).toInt()
        val frontY = floor(pos.y + 0.1).toInt()
        val frontZ = floor(pos.z + dir.z * JUMP_LOOKAHEAD_BLOCKS).toInt()
        if (WorldBlockGrid.passable(frontX, frontY, frontZ)) return false

        val headX = floor(pos.x).toInt()
        val headY = floor(pos.y + 2.0).toInt()
        val headZ = floor(pos.z).toInt()
        val headRoomOpen = WorldBlockGrid.passable(headX, headY, headZ)
        if (target.y > pos.y + JUMP_TARGET_RISE && hDist < JUMP_TARGET_RANGE && headRoomOpen) return true

        val frontSolid = WorldBlockGrid.solidFloor(frontX, frontY, frontZ)
        return frontSolid &&
            WorldBlockGrid.passable(frontX, frontY + 1, frontZ) &&
            WorldBlockGrid.passable(frontX, frontY + 2, frontZ) &&
            headRoomOpen
    }

    /** Presses or releases a key this walker owns. Keys it never pressed are left alone. */
    private fun press(keys: Array<KeyMapping>, slot: Int, wanted: Boolean) {
        if (wanted) {
            keys[slot].setDown(true)
            owned[slot] = true
        } else if (owned[slot]) {
            keys[slot].setDown(false)
            owned[slot] = false
        }
    }

    /** Releases only the keys this walker pressed; each goes back to the physical state of its binding. */
    private fun releaseOwned(mc: Minecraft) {
        if (owned.none { it }) return
        val keys = movementKeys(mc)
        for (i in 0 until KEY_COUNT) {
            if (!owned[i]) continue
            owned[i] = false
            keys[i].setDown(physicallyDown(mc, keys[i]))
        }
    }

    /** Order matches the FORWARD, LEFT, RIGHT, SPRINT, JUMP, BACK indices. */
    private fun movementKeys(mc: Minecraft): Array<KeyMapping> {
        val options = mc.options
        return arrayOf(
            options.keyUp, options.keyLeft, options.keyRight, options.keySprint, options.keyJump, options.keyDown,
        )
    }

    /** Physically holding any of these cancels the walk. */
    private fun userMovementKeys(mc: Minecraft): Array<KeyMapping> {
        val options = mc.options
        return arrayOf(options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump)
    }

    /** Reads hardware state, because KeyMapping.isDown is the flag this walker sets itself. */
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

    private fun playerPos(player: LocalPlayer): Vec = Vec(player.x, player.y, player.z)

    private fun center(node: PathNode): Vec = Vec(node.x + 0.5, node.y + 0.5, node.z + 0.5)

    private fun flatUnit(x: Double, z: Double): Vec = unit(Vec(x, 0.0, z))

    /** Returns the zero vector for near-zero input, as Vec3.normalize does in senura. */
    private fun unit(v: Vec): Vec {
        val length = sqrt(dot(v, v))
        return if (length < NORMALIZE_EPSILON) Vec(0.0, 0.0, 0.0) else scale(v, 1.0 / length)
    }

    private fun add(a: Vec, b: Vec) = Vec(a.x + b.x, a.y + b.y, a.z + b.z)

    private fun sub(a: Vec, b: Vec) = Vec(a.x - b.x, a.y - b.y, a.z - b.z)

    private fun scale(v: Vec, s: Double) = Vec(v.x * s, v.y * s, v.z * s)

    private fun dot(a: Vec, b: Vec): Double = a.x * b.x + a.y * b.y + a.z * b.z

    private fun dist(a: Vec, b: Vec): Double = sqrt(dot(sub(a, b), sub(a, b)))
}
