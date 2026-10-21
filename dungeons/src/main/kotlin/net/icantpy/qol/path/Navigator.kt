package net.icantpy.qol.path

import net.icantpy.api.IcantpyCommandPrefix
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

/**
 * Glue between the chat command, the pure [PathFinder] and the [PathWalker]. Settings live in [NavigatorStore].
 * Everything here runs on the client thread; the inactive path costs one flag check per call.
 */
object Navigator {
    /** Upper bound on search expansions for one `/icantpy goto`, so a bad goal cannot stall the client thread. */
    private const val MAX_SEARCH_ITERATIONS = 200_000

    private const val PATH_WIDTH = 1.8f
    private const val GOAL_STROKE = 1.8f
    private val ACTIVE_LINE: Int = 0xFF55FFFF.toInt()
    private val PASSED_LINE: Int = 0x6055FFFF
    private val GOAL_OUTLINE: Int = 0xFF55FFFF.toInt()
    private val GOAL_FILL: Int = 0x3355FFFF

    private var config: NavigatorConfig = NavigatorConfig()

    /** Identifies the current walk; callbacks from a replaced or stopped walk are ignored. */
    private var walkId: Long = 0L
    private var goal: PathNode? = null

    fun load() {
        applyConfig(NavigatorStore.load())
    }

    /** Ends any walk and releases the keys it pressed. Safe to call repeatedly. */
    fun unload() {
        walkId++
        goal = null
        PathWalker.stop()
        WorldSnapshot.clear()
    }

    fun config(): NavigatorConfig = config

    /** Stores [next] and writes it to disk. */
    fun commit(next: NavigatorConfig) {
        config = next
        NavigatorStore.save(next)
    }

    fun edit(change: (NavigatorConfig) -> NavigatorConfig) = commit(change(config))

    fun tick() {
        // Runs even when idle: the world snapshot keeps capturing so a later walk can use it.
        WorldSnapshot.tick()
        if (PathWalker.active) PathWalker.tick()
    }

    fun onFrameStart() {
        if (PathWalker.active) PathWalker.onFrameStart()
    }

    /** Draws the remaining path and the goal marker. Returns at once when idle or when rendering is off. */
    fun renderWorld() {
        if (!config.renderPath || !PathWalker.active) return
        val path = PathWalker.currentPath()
        if (path.size < 2) return
        val index = PathWalker.currentIndex().coerceIn(0, path.lastIndex)
        for (i in 0 until path.lastIndex) {
            val color = if (i < index) PASSED_LINE else ACTIVE_LINE
            Gizmos.line(center(path[i]), center(path[i + 1]), color, PATH_WIDTH)
        }
        goal?.let { drawGoal(it) }
    }

    /** Handles `/icantpy goto ...` and `/icantpy copypf`. Returns null when the message is neither. */
    fun handleCommand(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw) ?: return null
        val tokens = body.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size == 1 && tokens[0].lowercase() == "copypf") return copyGotoCommand()
        if (tokens.firstOrNull()?.lowercase() != "goto") return null
        return when {
            tokens.size == 1 -> status()
            tokens.size == 2 && tokens[1].lowercase() == "stop" -> stop()
            tokens.size == 4 -> parseCoordinates(tokens.drop(1))?.let { goTo(it.x, it.y, it.z) }
                ?: USAGE
            else -> USAGE
        }
    }

    /** Copies `/icantpy goto x y z` for the block the player is standing in to the system clipboard. */
    fun copyGotoCommand(): String {
        val mc = Minecraft.getInstance()
        val feet = mc.player?.blockPosition() ?: return "not in a world"
        val command = gotoCommand(feet.x, feet.y, feet.z)
        mc.keyboardHandler.setClipboard(command)
        return "Copied: $command"
    }

    /** Plans a ground path from the player's feet to (x, y, z) and starts walking it. Returns a chat message. */
    fun goTo(x: Int, y: Int, z: Int): String {
        val player = Minecraft.getInstance().player ?: return "not in a world"
        val start = player.blockPosition()
        val dx = (x - start.x).toDouble()
        val dz = (z - start.z).toDouble()
        val distance = hypot(dx, dz)
        if (distance > config.maxSearchBlocks) {
            return "goal is ${distance.toInt()} blocks away; limit is ${config.maxSearchBlocks}"
        }
        val goalNode = PathNode(x, y, z)
        val finder = PathFinder(WorldBlockGrid, MAX_SEARCH_ITERATIONS)
        val path = finder.findPath(PathNode(start.x, start.y, start.z), goalNode)
            ?: return "No path found to $x $y $z: ${explain(finder.lastOutcome, x, z)}"
        val partial = if (finder.lastOutcome == SearchOutcome.FOUND) "" else
            ", partial route: ${explain(finder.lastOutcome, x, z)}"
        // Take the new id before stopping: the replaced walk's callback then sees a stale id and stays silent.
        val id = ++walkId
        PathWalker.stop()
        goal = goalNode
        PathWalker.start(goalNode, path, walkSettings()) { success -> onWalkDone(id, goalNode, success) }
        return "Walking to $x $y $z (${path.size} nodes$partial)"
    }

    /** Player-facing reason for a search outcome. */
    private fun explain(outcome: SearchOutcome, x: Int, z: Int): String = when (outcome) {
        SearchOutcome.GOAL_UNKNOWN -> {
            val known = WorldSnapshot.isCached(x shr 4, z shr 4)
            if (known) "no walkable ground near the goal" else "the goal's area isn't loaded or cached yet (walk near it once)"
        }
        SearchOutcome.START_STUCK -> "nothing walkable around you"
        SearchOutcome.EXHAUSTED -> "goal not reachable from here (or through unknown terrain)"
        SearchOutcome.ITERATION_LIMIT -> "search too large, try a closer point"
        SearchOutcome.TIME_LIMIT -> "search ran out of time, try a closer point"
        SearchOutcome.FOUND -> "found"
    }

    /** Cancels the current walk without an arrival or failure message. */
    fun stop(): String {
        if (!PathWalker.active) return "goto: not walking"
        walkId++
        goal = null
        PathWalker.stop()
        return "goto: stopped"
    }

    fun status(): String {
        val target = goal
        if (!PathWalker.active || target == null) {
            return if (config.cacheWorld) "goto: idle (${WorldSnapshot.chunkCount} chunks cached)" else "goto: idle"
        }
        val path = PathWalker.currentPath()
        val index = PathWalker.currentIndex()
        return "goto: walking to ${target.x} ${target.y} ${target.z} (node ${index + 1}/${path.size})"
    }

    private fun onWalkDone(id: Long, target: PathNode, success: Boolean) {
        if (id != walkId) return
        goal = null
        val text = if (success) {
            "Arrived at ${target.x} ${target.y} ${target.z}"
        } else {
            "Navigation to ${target.x} ${target.y} ${target.z} failed"
        }
        Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(text))
    }

    private fun applyConfig(next: NavigatorConfig) {
        config = next
    }

    private fun walkSettings(): WalkSettings = WalkSettings(
        rotateCamera = config.rotateCamera,
        sprint = config.sprint,
        jump = config.jump,
    )

    private fun parseCoordinates(tokens: List<String>): PathNode? {
        val numbers = tokens.map { it.toIntOrNull() ?: return null }
        return PathNode(numbers[0], numbers[1], numbers[2])
    }

    private fun drawGoal(node: PathNode) {
        val box = AABB(node.x + 0.35, node.y.toDouble(), node.z + 0.35, node.x + 0.65, node.y + 0.3, node.z + 0.65)
        Gizmos.cuboid(box, GizmoStyle.strokeAndFill(GOAL_OUTLINE, GOAL_STROKE, GOAL_FILL))
    }

    private fun center(node: PathNode): Vec3 = Vec3(node.x + 0.5, node.y + 0.1, node.z + 0.5)

    private const val USAGE =
        "usage: /icantpy goto <x> <y> <z> | /icantpy goto stop | /icantpy goto | /icantpy copypf"
}

/** The command that walks back to block (x, y, z); what `/icantpy copypf` puts on the clipboard. */
internal fun gotoCommand(x: Int, y: Int, z: Int): String = "/icantpy goto $x $y $z"
