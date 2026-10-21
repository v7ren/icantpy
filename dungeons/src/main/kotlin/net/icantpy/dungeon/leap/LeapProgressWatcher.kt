package net.icantpy.dungeon.leap

import net.icantpy.location.DungeonTabStats

/** A progress trigger that should run now, with the value that caused it. */
data class LeapProgressHit(val trigger: LeapOrientTrigger, val event: LeapTriggerEvent, val value: String)

/**
 * Watches tab and sidebar counts for the progress events. Threshold events fire once per run per card,
 * when a count goes from below the threshold to at or above it. Team deaths fire on every increase.
 * The first reading is a baseline, so a mid-run login does not fire anything.
 */
class LeapProgressWatcher {
    private var previous: DungeonTabStats? = null
    private val firedThisRun = mutableSetOf<String>()

    fun reset() {
        previous = null
        firedThisRun.clear()
    }

    fun update(stats: DungeonTabStats, triggers: List<LeapOrientTrigger>): List<LeapProgressHit> {
        val before = previous
        previous = stats
        if (before == null) return emptyList()
        return triggers.filter { it.enabled }.mapNotNull { trigger -> hitFor(trigger, before, stats) }
    }

    private fun hitFor(trigger: LeapOrientTrigger, before: DungeonTabStats, now: DungeonTabStats): LeapProgressHit? {
        val event = trigger.event
        return when {
            event == LeapTriggerEvent.DEATHS_CHANGED -> {
                if (!increased(before.deaths, now.deaths)) return null
                LeapProgressHit(trigger, event, "team deaths ${now.deaths}")
            }
            event.usesThreshold() -> {
                val (was, count) = countsFor(event, before, now)
                if (!crossed(was, count, trigger.threshold)) return null
                if (!firedThisRun.add(trigger.id)) return null
                LeapProgressHit(trigger, event, "$count")
            }
            else -> null
        }
    }

    private fun countsFor(event: LeapTriggerEvent, before: DungeonTabStats, now: DungeonTabStats): Pair<Int?, Int?> =
        when (event) {
            LeapTriggerEvent.SECRETS_REACHED -> before.secrets to now.secrets
            LeapTriggerEvent.CRYPTS_REACHED -> before.crypts to now.crypts
            LeapTriggerEvent.CLEARED_REACHED -> before.cleared to now.cleared
            else -> null to null
        }

    companion object {
        /** True when [now] reached [threshold] from below it. A threshold of 0 never fires. */
        fun crossed(before: Int?, now: Int?, threshold: Int): Boolean {
            if (threshold <= 0 || before == null || now == null) return false
            return before < threshold && now >= threshold
        }

        fun increased(before: Int?, now: Int?): Boolean = before != null && now != null && now > before
    }
}
