package net.icantpy.modules.impl.waypoint

import java.util.UUID
import kotlin.math.floor

data class CommandWaypoint(
    val id: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val command: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val once: Boolean = false,
    val world: String = WaypointWorld.ANY,
) {
    fun occupies(px: Double, py: Double, pz: Double): Boolean {
        val feetX = floor(px).toInt()
        val feetY = floor(py + 0.01).toInt()
        val feetZ = floor(pz).toInt()
        if (feetX == x && feetY == y && feetZ == z) return true
        return feetX == x && feetY - 1 == y && feetZ == z
    }

    fun label(): String {
        val custom = name.trim()
        if (custom.isNotEmpty()) return custom
        val cmd = command.trim()
        if (cmd.isNotEmpty()) return cmd
        return "$x $y $z"
    }

    fun worldKey(): WaypointWorld = WaypointWorld.parse(world)

    fun inWorld(current: WaypointWorld): Boolean = worldKey().matches(current)

    companion object {
        fun create(
            x: Int,
            y: Int,
            z: Int,
            command: String = "",
            name: String = "",
            world: String = WaypointWorld.ANY,
        ): CommandWaypoint = CommandWaypoint(
            id = UUID.randomUUID().toString(),
            x = x,
            y = y,
            z = z,
            command = command,
            name = name,
            world = WaypointWorld.parse(world).key,
        )

        fun parseDispatch(raw: String): Pair<Boolean, String>? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return if (trimmed.startsWith("/")) {
                true to trimmed.removePrefix("/").trim()
            } else {
                false to trimmed
            }
        }
    }
}
