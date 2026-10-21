package net.icantpy.dungeon.leap

/** Grouping and numeric choices for the trigger editor and debug page. Pure, so it can be tested. */
object LeapTriggerEventMenu {
    /** Events grouped by [LeapTriggerEvent.group], in enum order. Each event appears in exactly one group. */
    fun groups(): List<Pair<String, List<LeapTriggerEvent>>> {
        val out = mutableListOf<Pair<String, MutableList<LeapTriggerEvent>>>()
        for (event in LeapTriggerEvent.entries) {
            val last = out.lastOrNull()
            if (last != null && last.first == event.group) {
                last.second += event
            } else {
                out += event.group to mutableListOf(event)
            }
        }
        return out
    }

    /** Values offered for the "At least" row of a progress event. */
    fun thresholdLevels(event: LeapTriggerEvent): List<Int> = when (event) {
        LeapTriggerEvent.SECRETS_REACHED -> (1..100).toList()
        LeapTriggerEvent.CRYPTS_REACHED -> (1..10).toList()
        LeapTriggerEvent.CLEARED_REACHED -> (5..100 step 5).toList()
        else -> emptyList()
    }

    fun defaultThreshold(event: LeapTriggerEvent): Int = when (event) {
        LeapTriggerEvent.SECRETS_REACHED -> 10
        LeapTriggerEvent.CRYPTS_REACHED -> 3
        LeapTriggerEvent.CLEARED_REACHED -> 50
        else -> 0
    }

    /** Label for a threshold level, such as "at least 10". */
    fun thresholdLabel(event: LeapTriggerEvent, value: Int): String = when (event) {
        LeapTriggerEvent.CLEARED_REACHED -> "at least $value%"
        else -> "at least $value"
    }
}
