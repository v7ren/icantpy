package net.icantpy.dungeon.leap

import net.icantpy.dungeon.dragons.DragonEvent

/**
 * Maps wither dragon tracker events to leap trigger events. The captured name is the dragon color,
 * which the Color filter row compares with the trigger's match text.
 */
object LeapDragonEvents {
    data class Mapped(val event: LeapTriggerEvent, val color: String, val message: String)

    /** Lost dragons have no leap trigger: they were not confirmed dead. */
    fun map(event: DragonEvent): Mapped? = when (event) {
        is DragonEvent.Spawning -> mapped(LeapTriggerEvent.DRAGON_SPAWNING, event.dragon.displayName, "spawning")
        is DragonEvent.Spawned -> mapped(LeapTriggerEvent.DRAGON_SPAWNED, event.dragon.displayName, "spawned")
        is DragonEvent.Killed -> mapped(LeapTriggerEvent.DRAGON_KILLED, event.dragon.displayName, "killed")
        is DragonEvent.PriorityDecided -> mapped(LeapTriggerEvent.DRAGON_PRIORITY, event.dragon.displayName, "priority")
        is DragonEvent.Lost -> null
    }

    private fun mapped(event: LeapTriggerEvent, color: String, verb: String) =
        Mapped(event, color, "$color dragon $verb")
}
