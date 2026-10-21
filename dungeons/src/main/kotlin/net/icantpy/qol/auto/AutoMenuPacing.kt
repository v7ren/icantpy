package net.icantpy.qol.auto

/**
 * Free of Minecraft types. Timing rules for the command menu runs (Stats, Loadout): how long to wait for
 * a menu's content, how a click or page flip is spaced, and how many steps one client tick may chain.
 */
object AutoMenuPacing {
    /** Steps one tick may chain in instant mode; each step that needs a later tick ends the chain. */
    const val MAX_STEPS_PER_TICK = 12

    /** Longest wait for the target slot to be filled after the menu opened, in instant mode. */
    const val POPULATE_WAIT_MS = 1_000L

    /** What one step of a run asked for this tick. */
    enum class StepOutcome { ENDED, WAITING, PROGRESSED }

    /** Instant mode retries a missing slot until the container is populated, up to [POPULATE_WAIT_MS]. */
    fun keepWaitingForPopulate(instant: Boolean, waitedMs: Long): Boolean = instant && waitedMs < POPULATE_WAIT_MS

    /** Non-instant runs wait one tick so the cursor hover is drawn before a click; instant runs click at once. */
    fun settleBeforeClickMs(instant: Boolean, settleMs: Long): Long = if (instant) 0L else settleMs

    fun pollMs(instant: Boolean, pollMs: Long): Long = if (instant) 0L else pollMs

    /**
     * Runs [step] once per tick, or until it asks to wait or end. Instant runs keep going while steps make
     * progress, so a click that needs no settle is followed in the same tick. Returns true when the run ended.
     */
    fun runSteps(instant: Boolean, step: () -> StepOutcome): Boolean {
        repeat(if (instant) MAX_STEPS_PER_TICK else 1) {
            when (step()) {
                StepOutcome.ENDED -> return true
                StepOutcome.WAITING -> return false
                StepOutcome.PROGRESSED -> Unit
            }
        }
        return false
    }
}
