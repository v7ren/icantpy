package net.icantpy.qol.auto

/** What a queued auto action operates on. [key] identifies the target for duplicate suppression. */
sealed interface AutoTarget {
    val kind: AutoActionKind
    val key: String

    data class Sort(val presetName: String) : AutoTarget {
        override val kind: AutoActionKind get() = AutoActionKind.SORT_INVENTORY
        override val key: String get() = "sort:$presetName"
    }

    data class Stats(val presetId: String) : AutoTarget {
        override val kind: AutoActionKind get() = AutoActionKind.EQUIP_STATS
        override val key: String get() = "stats:$presetId"
    }

    data class Loadout(val number: Int) : AutoTarget {
        override val kind: AutoActionKind get() = AutoActionKind.SWITCH_LOADOUT
        override val key: String get() = "loadout:$number"
    }
}

/** A started multi-step routine. [tick] returns true once the routine has finished for any reason. */
interface AutoRun {
    fun tick(nowMs: Long): Boolean

    /** Ends the routine at the next tick. The player's own input takes over; no further clicks are made. */
    fun cancel(reason: String)

    /**
     * Why the routine ended early (cancelled, or closed by the player before it began), or null when it
     * ran to completion. A non-null value makes the queue drop the rest of the trigger's batch.
     */
    val abortReason: String?
}
