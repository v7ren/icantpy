package net.icantpy.api

import net.minecraft.client.KeyMapping

/**
 * Resident KeyMappings. The loader (or standalone payload) registers these once;
 * reloadable features read [isDown] instead of polling GLFW, so binds live in
 * Minecraft's Controls screen.
 */
object IcantpyKeyBindings {
    const val OPEN_CONFIG: String = "open_config"
    const val ADD_WAYPOINT: String = "add_waypoint"
    const val OPEN_STATS: String = "open_stats"
    const val OPEN_LOADOUT: String = "open_loadout"
    const val FREELOOK: String = "freelook"
    const val ACTION_SLOTS: Int = 12

    fun actionId(slot: Int): String = "action.$slot"

    private val mappings = LinkedHashMap<String, KeyMapping>()

    fun put(id: String, mapping: KeyMapping): KeyMapping {
        mappings[id] = mapping
        return mapping
    }

    fun mapping(id: String): KeyMapping? = mappings[id]

    fun isDown(id: String): Boolean = mappings[id]?.isDown == true

    fun ids(): Set<String> = mappings.keys
}
