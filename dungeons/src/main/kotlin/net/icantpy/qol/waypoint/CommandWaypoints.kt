package net.icantpy.qol.waypoint

import net.icantpy.api.IcantpyClientActions
import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.dungeon.timer.TickTimers
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

object CommandWaypoints {
    private const val COOLDOWN_TICKS: Int = 8

    private val inside = HashSet<String>()
    private val spent = HashSet<String>()
    private val cooldown = HashMap<String, Int>()
    private var tickCount: Int = 0

    /** Client level and world key of the last tick, to tell when a new visit to a world has started. */
    private var lastLevel: Any? = null
    private var lastWorldKey: String? = null

    /** World resolved once per client tick; render reuses it so frames do not rescan the sidebar. */
    private var tickWorld: WaypointWorld? = null

    fun tick() {
        tickCount += 1
        val settings = TickTimers.settings
        if (!settings.waypointsEnabled) {
            inside.clear()
            tickWorld = null
            return
        }
        val world = SkyblockWorlds.current()
        tickWorld = world
        val mc = Minecraft.getInstance()
        newVisitReset(mc.level, world)
        val player = mc.player ?: return
        val occupied = HashSet<String>()
        for (waypoint in settings.waypoints) {
            if (!waypoint.enabled) continue
            if (!waypoint.inWorld(world)) continue
            val on = waypoint.occupies(player.x, player.y, player.z)
            if (!on) continue
            occupied += waypoint.id
            val firstStep = waypoint.id !in inside
            val cooling = (cooldown[waypoint.id] ?: 0) > tickCount
            val usedUp = waypoint.once && waypoint.id in spent
            if (firstStep && !cooling && !usedUp) {
                fire(waypoint)
            }
        }
        inside.clear()
        inside.addAll(occupied)
    }

    /**
     * "Once" lasts for one visit to a world, not the whole session. A new visit starts when the client level changes
     * (Hypixel sends a fresh level on every server or island transfer) or when the world settles on a different known
     * one. An unknown world, as the sidebar sometimes reads for a moment, never counts as a change.
     */
    private fun newVisitReset(level: Any?, world: WaypointWorld) {
        val levelChanged = level !== lastLevel
        val worldChanged = world.key != WaypointWorld.UNKNOWN && lastWorldKey != null && world.key != lastWorldKey
        lastLevel = level
        if (world.key != WaypointWorld.UNKNOWN) lastWorldKey = world.key
        if (!levelChanged && !worldChanged) return
        spent.clear()
        inside.clear()
        cooldown.clear()
    }

    fun render() {
        val settings = TickTimers.settings
        if (!settings.waypointsEnabled || !settings.showWaypoints) return
        val world = tickWorld ?: return
        for (waypoint in settings.waypoints) {
            if (!waypoint.enabled) continue
            if (!waypoint.inWorld(world)) continue
            val active = waypoint.id in inside
            val outline = if (active) 0xFFFFFF55.toInt() else 0xFF55FFFF.toInt()
            val fill = (outline and 0x00FFFFFF) or 0x33000000
            val box = AABB(
                waypoint.x.toDouble(),
                waypoint.y.toDouble(),
                waypoint.z.toDouble(),
                waypoint.x + 1.0,
                waypoint.y + 1.0,
                waypoint.z + 1.0,
            )
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(outline, 1.8f, fill))
        }
    }

    fun reset() {
        lastLevel = null
        lastWorldKey = null
        inside.clear()
        spent.clear()
        cooldown.clear()
    }

    fun handleCommand(raw: String): String? {
        val body = commandBody(raw) ?: return null
        val lower = body.lowercase()
        if (!lower.startsWith("wp") && !lower.startsWith("waypoint")) return null
        val rest = body.substringAfter(' ', missingDelimiterValue = "").trim()
        val action = rest.substringBefore(' ').lowercase()
        val arg = rest.substringAfter(' ', missingDelimiterValue = "").trim()
        return when (action) {
            "", "help" -> "wp add|/look|/del|/list|/clear  worlds: dungeon hub island garden. example: /icantpy wp add /pc at pads"
            "add" -> addAt(look = false, command = arg)
            "look" -> addAt(look = true, command = arg)
            "list" -> listText()
            "del", "delete", "remove" -> removeNearest()
            "clear" -> {
                TickTimers.replaceSettings(TickTimers.settings.copy(waypoints = emptyList()))
                reset()
                "cleared waypoints"
            }
            else -> "wp add|/look|/del|/list|/clear"
        }
    }

    fun addAtFeet(command: String = ""): String = addAt(look = false, command = command)

    fun addAtLook(command: String = ""): String = addAt(look = true, command = command)

    fun update(waypoint: CommandWaypoint) {
        TickTimers.replaceSettings(TickTimers.settings.upsertWaypoint(waypoint))
    }

    fun remove(id: String) {
        TickTimers.replaceSettings(TickTimers.settings.removeWaypoint(id))
        inside.remove(id)
        spent.remove(id)
        cooldown.remove(id)
    }

    fun tell(text: String) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(Component.literal(text))
    }

    private fun addAt(look: Boolean, command: String): String {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return "no player"
        val pos = if (look) lookPos(mc) ?: player.blockPosition() else player.blockPosition()
        val waypoint = CommandWaypoint.create(pos.x, pos.y, pos.z, command = command, world = SkyblockWorlds.placeWorld())
        TickTimers.replaceSettings(TickTimers.settings.upsertWaypoint(waypoint))
        val where = if (look) "look" else "feet"
        return "waypoint $where ${pos.x} ${pos.y} ${pos.z} ${waypoint.worldKey().label()}"
    }

    private fun lookPos(mc: Minecraft): BlockPos? {
        val hit = mc.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        return hit.blockPos
    }

    private fun removeNearest(): String {
        val player = Minecraft.getInstance().player ?: return "no player"
        val world = SkyblockWorlds.current()
        val nearest = TickTimers.settings.waypoints
            .filter { it.inWorld(world) }
            .minByOrNull { waypoint ->
            val dx = player.x - (waypoint.x + 0.5)
            val dy = player.y - (waypoint.y + 0.5)
            val dz = player.z - (waypoint.z + 0.5)
            dx * dx + dy * dy + dz * dz
        } ?: return "no waypoints"
        remove(nearest.id)
        return "removed ${nearest.x} ${nearest.y} ${nearest.z}"
    }

    private fun listText(): String {
        val waypoints = TickTimers.settings.waypoints
        if (waypoints.isEmpty()) return "no waypoints"
        return waypoints.joinToString(" | ") {
            "${it.worldKey().label()} ${it.x} ${it.y} ${it.z} ${it.command}".trim()
        }
    }

    private fun fire(waypoint: CommandWaypoint) {
        val payload = CommandWaypoint.parseDispatch(waypoint.command) ?: return
        val connection = Minecraft.getInstance().connection ?: return
        val (asCommand, text) = payload
        if (text.isEmpty()) return
        if (asCommand) IcantpyClientActions.sendCommand(text) else IcantpyClientActions.sendChat(text)
        cooldown[waypoint.id] = tickCount + COOLDOWN_TICKS
        if (waypoint.once) spent += waypoint.id
    }

    private fun commandBody(raw: String): String? = IcantpyCommandPrefix.body(raw)
}
