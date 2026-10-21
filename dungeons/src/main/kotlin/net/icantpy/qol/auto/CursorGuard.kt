package net.icantpy.qol.auto

import net.icantpy.qol.invsort.InvSortPlanner

/**
 * Free of Minecraft types. Decides when a container click has been confirmed by the server and what to do
 * when a held item must be put back. Callers observe once per client tick; [Sync] counts those observations.
 *
 * A click is confirmed when the observed state (carried key plus the touched slots' keys) equals the
 * predicted state and stays unchanged for [Sync.settleTicks] observations (at least one). A stable mismatch
 * means the server resynced the menu, and a timeout means it never answered; both lead to recovery.
 */
object CursorGuard {
    /** Observations that must agree before a click counts as confirmed in strict mode (and by default). */
    const val SETTLE_TICKS = 2

    /** Stable mismatching observations needed before a divergence is reported. */
    const val DIVERGE_TICKS = 2

    /** Longest wait for one click to be confirmed. */
    const val STEP_TIMEOUT_MS = 2_000L

    /** Ping above this doubles the timeout. */
    const val SLOW_PING_MS = 200

    const val SLOW_TIMEOUT_SCALE = 2L

    /** Put-back attempts before the held item is left for the player. */
    const val MAX_RECOVER_ATTEMPTS = 3

    /** Player main inventory and hotbar in the player's own inventory menu, excluding the SkyBlock star (44). */
    val INVENTORY_SLOTS: List<Int> = (9..43).toList()

    /**
     * How long the server must agree before the runner continues. [sequenceSettle] applies after a swap
     * sequence that leaves the cursor empty, [closeSettle] before finishing or closing. Clicks inside a
     * sequence only need the prediction to match the client view at once, unless [everyClickSettle] is set.
     */
    enum class SafetyLevel(val sequenceSettle: Int, val closeSettle: Int, val everyClickSettle: Boolean) {
        FAST(sequenceSettle = 0, closeSettle = 1, everyClickSettle = false),
        BALANCED(sequenceSettle = 1, closeSettle = 2, everyClickSettle = false),
        STRICT(sequenceSettle = SETTLE_TICKS, closeSettle = 3, everyClickSettle = true);

        /** Settle ticks for one click; [endsEmpty] when the predicted cursor is empty after it. */
        fun clickSettle(endsEmpty: Boolean): Int = when {
            everyClickSettle -> SETTLE_TICKS
            endsEmpty -> sequenceSettle
            else -> 0
        }

        fun next(): SafetyLevel = entries[(ordinal + 1) % entries.size]

        companion object {
            fun parse(name: String?): SafetyLevel = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BALANCED
        }
    }

    /** What the client shows: the carried key, keys of watched slots, and the menu's state id when readable. */
    data class Snapshot(val carried: String?, val slotKeys: Map<Int, String?>, val stateId: Int?)

    /** The predicted state after a click: the carried key, and the keys of the slots the click touched. */
    data class Expectation(val carried: String?, val slotKeys: Map<Int, String?>)

    enum class Verdict { WAITING, SYNCED, DIVERGED, TIMED_OUT }

    /** A screen may close only when nothing is held on the cursor. */
    fun mayClose(carried: String?): Boolean = carried == null

    /**
     * Longest wait for the server to clear a ghost (a menu item the vanilla client predicted onto the cursor
     * after a click on a container slot). The server's cursor is empty, so after this the menu is closed.
     */
    const val GHOST_WAIT_MS = 1_500L

    /**
     * True when [carried] is a ghost of a container click: it equals the clicked item or any item of the
     * menu's container slots. Only meaningful after an automated click on a container slot.
     */
    fun isGhost(carried: String?, clickedKey: String?, containerKeys: Collection<String>): Boolean =
        carried != null && (carried == clickedKey || carried in containerKeys)

    /** A menu may close when nothing is held, or when the held item is one of the [ghostKeys]. */
    fun mayCloseOverGhost(carried: String?, ghostKeys: Set<String>): Boolean =
        carried == null || carried in ghostKeys

    /** What to do before closing a menu: close now, keep waiting for the server to clear a ghost, close over it, or put a real item back. */
    enum class HeldItemPlan { CLOSE_NOW, WAIT_FOR_SERVER, CLOSE_OVER_GHOST, RECOVER }

    /** [ghost] and [held] describe the cursor; [waitedMs] is how long the ghost has been held. */
    fun planForHeldItem(held: Boolean, ghost: Boolean, waitedMs: Long): HeldItemPlan = when {
        !held -> HeldItemPlan.CLOSE_NOW
        !ghost -> HeldItemPlan.RECOVER
        waitedMs < GHOST_WAIT_MS -> HeldItemPlan.WAIT_FOR_SERVER
        else -> HeldItemPlan.CLOSE_OVER_GHOST
    }

    /**
     * True when the menu after a terminal click is not the one that was clicked: the server replaced it,
     * so the run is complete and must not click again.
     */
    fun menuReplacedAfterClick(clickedContainerId: Int?, currentContainerId: Int): Boolean =
        clickedContainerId != null && clickedContainerId != currentContainerId

    /** Expectation after a vanilla left click on [slot] that currently holds [slotKey] while carrying [carried]. */
    fun expectAfterClick(carried: String?, slotKey: String?, slot: Int): Expectation {
        val result = InvSortPlanner.applyPickup(mapOf(slot to slotKey), carried, slot)
        return Expectation(result.carried, mapOf(slot to result.slots.getValue(slot)))
    }

    /** Timeout for one click; doubled when the connection is slow. */
    fun timeoutMs(pingMs: Int?): Long =
        if (pingMs != null && pingMs > SLOW_PING_MS) STEP_TIMEOUT_MS * SLOW_TIMEOUT_SCALE else STEP_TIMEOUT_MS

    /** Waits for one click to be confirmed. Create one per click; call [observe] once per tick. */
    class Sync(
        val expectation: Expectation,
        private val armedMs: Long,
        private val timeoutMs: Long,
        /** Agreeing observations needed; 0 means the prediction only has to match the view right now. */
        val settleTicks: Int = SETTLE_TICKS,
    ) {
        /** Slots whose keys the caller must include in each snapshot. */
        val watchedSlots: Set<Int> = expectation.slotKeys.keys

        private var last: Snapshot? = null
        private var stableTicks = 0

        /**
         * Counts observations whose carried key and watched slot keys agree. The menu state id is not part of
         * the comparison: some menus change it every tick, which would otherwise never settle.
         * A match is confirmed after [settleTicks] stable observations; a mismatch is a divergence only after
         * [DIVERGE_TICKS] stable observations, so one transient snapshot never aborts a run.
         */
        fun observe(nowMs: Long, snapshot: Snapshot): Verdict {
            val previous = last
            stableTicks = if (previous != null && sameKeys(previous, snapshot)) stableTicks + 1 else 1
            last = snapshot
            val matched = matches(snapshot)
            if (matched && stableTicks >= settleTicks) return Verdict.SYNCED
            if (!matched && stableTicks >= maxOf(settleTicks, DIVERGE_TICKS)) return Verdict.DIVERGED
            if (nowMs - armedMs >= timeoutMs) return Verdict.TIMED_OUT
            return Verdict.WAITING
        }

        private fun sameKeys(a: Snapshot, b: Snapshot): Boolean =
            a.carried == b.carried && a.slotKeys == b.slotKeys

        fun matches(snapshot: Snapshot): Boolean =
            snapshot.carried == expectation.carried &&
                expectation.slotKeys.all { (slot, key) -> snapshot.slotKeys[slot] == key }
    }

    /** First empty slot of [candidates], or [source] when it is a candidate and still empty. */
    fun recoverSlot(source: Int?, candidates: List<Int>, isEmpty: (Int) -> Boolean): Int? =
        source?.takeIf { it in candidates && isEmpty(it) } ?: candidates.firstOrNull(isEmpty)

    sealed interface RecoverAction {
        data class Click(val slot: Int) : RecoverAction
        data object Done : RecoverAction
        data object NoRoom : RecoverAction
        data object Exhausted : RecoverAction
    }

    /**
     * Puts a held item back into an empty slot. Ask [next] before every click; it returns [RecoverAction.Done]
     * once the cursor is empty, [RecoverAction.NoRoom] when no candidate is empty, and
     * [RecoverAction.Exhausted] after [maxAttempts] clicks.
     */
    class Recovery(
        private val source: Int?,
        private val candidates: List<Int>,
        private val maxAttempts: Int = MAX_RECOVER_ATTEMPTS,
    ) {
        var attempts = 0
            private set

        fun next(carriedEmpty: Boolean, isEmpty: (Int) -> Boolean): RecoverAction {
            if (carriedEmpty) return RecoverAction.Done
            if (attempts >= maxAttempts) return RecoverAction.Exhausted
            val slot = recoverSlot(source, candidates, isEmpty) ?: return RecoverAction.NoRoom
            attempts++
            return RecoverAction.Click(slot)
        }
    }
}
