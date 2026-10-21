package net.icantpy.dungeon.score

import net.icantpy.dungeon.leap.LeapTriggerEvent

/** Per-run facts that come from chat events. Reset on RUN_START and DUNGEON_LEFT. Immutable; update with [withEvent]. */
data class ScoreRunState(
    val mimicKilled: Boolean = false,
    val princeKilled: Boolean = false,
    val failedPuzzles: Int = 0,
) {
    fun withEvent(event: LeapTriggerEvent): ScoreRunState = when (event) {
        LeapTriggerEvent.MIMIC_DEAD -> copy(mimicKilled = true)
        LeapTriggerEvent.PRINCE_KILLED -> copy(princeKilled = true)
        LeapTriggerEvent.PUZZLE_FAIL -> copy(failedPuzzles = failedPuzzles + 1)
        else -> this
    }
}
