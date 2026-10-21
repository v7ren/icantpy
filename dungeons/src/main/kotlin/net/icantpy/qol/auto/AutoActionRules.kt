package net.icantpy.qol.auto

/** Which routine a queued auto action runs. */
enum class AutoActionKind { SORT_INVENTORY, EQUIP_STATS, SWITCH_LOADOUT }

/** What is on screen when a queued auto action looks at the client. */
enum class AutoScreenState { NONE, PLAYER_INVENTORY, OTHER }

enum class AutoDecision { WAIT, GIVE_UP, START }

/**
 * Free of Minecraft types. Times are milliseconds. A queued action starts only when nothing else is
 * running, the human-like reaction delay has passed and no other screen is open.
 */
object AutoActionRules {
    /** The same action for the same trigger firing again inside this window is ignored. */
    const val DUPLICATE_WINDOW_MS = 3_000L

    /** Longest time to wait for some other screen (chest, chat, menu) to close before giving up. */
    const val SCREEN_WAIT_LIMIT_MS = 8_000L

    /** Human reaction time before an action begins. */
    const val REACTION_MIN_MS = 250
    const val REACTION_MAX_MS = 600

    /** Batches that would push the waiting steps past this many are refused as a whole. */
    const val MAX_WAITING = 16

    /**
     * True while the queue keeps the player input locked: a routine or sort is running, or any step is
     * still waiting (including waits for a menu to open). Waiting steps count from the moment they are queued.
     */
    fun holdsInput(activeRun: Boolean, activeSort: Boolean, sortBusy: Boolean, waitingSteps: Int): Boolean =
        activeRun || activeSort || sortBusy || waitingSteps > 0

    /** Longest time a running batch may go without a click or a state change before it is stopped. */
    const val STALL_LIMIT_MS = 5_000L

    /** Instant mode skips the human reaction delay before a queued step. */
    fun reactionDelayMs(instant: Boolean, sampledMs: Long): Long = if (instant) 0L else sampledMs

    /** Instant mode starts the next step in the same tick the previous one finished. */
    fun chainsSameTick(instant: Boolean, previousFinished: Boolean): Boolean = instant && previousFinished

    fun isDuplicate(lastAcceptedMs: Long?, nowMs: Long): Boolean =
        lastAcceptedMs != null && nowMs - lastAcceptedMs < DUPLICATE_WINDOW_MS

    /**
     * True when something is actively running (a menu routine or a sort) and nothing has progressed for
     * [STALL_LIMIT_MS]. Idle time while only waiting for a screen is governed by [decide], not this watchdog.
     */
    fun isStalled(running: Boolean, lastProgressMs: Long, nowMs: Long): Boolean =
        running && nowMs - lastProgressMs >= STALL_LIMIT_MS

    /**
     * True when the cursor should be held on its pending click target this tick: the run is still active,
     * the input lock is on, and the screen is still the one the pin was taken on. Holding never counts as progress.
     */
    fun holdsCursorPin(screenOwnsPin: Boolean, runActive: Boolean, lockInput: Boolean): Boolean =
        screenOwnsPin && runActive && lockInput

    /** True on the tick the batch stops holding input, i.e. when its last step finished, aborted or timed out. */
    fun batchEnded(wasActive: Boolean, nowActive: Boolean): Boolean = wasActive && !nowActive

    /**
     * Whether the screen is closed at batch end: only a screen the auto system uses (the player inventory,
     * or a Stats/Loadout menu), including one that was already open before the batch started.
     */
    fun closesAtBatchEnd(ownedScreen: Boolean, batchEnded: Boolean): Boolean = ownedScreen && batchEnded

    /**
     * A sort may begin inside an open player inventory (it is reused). Stats and loadout menus need
     * no screen at all, matching a player who waits before typing the command.
     */
    fun decide(
        kind: AutoActionKind,
        elapsedMs: Long,
        reactionDelayMs: Long,
        screen: AutoScreenState,
        busy: Boolean,
    ): AutoDecision {
        if (busy) return AutoDecision.WAIT
        if (elapsedMs < reactionDelayMs) return AutoDecision.WAIT
        if (screen == AutoScreenState.NONE) return AutoDecision.START
        if (kind == AutoActionKind.SORT_INVENTORY && screen == AutoScreenState.PLAYER_INVENTORY) {
            return AutoDecision.START
        }
        return if (elapsedMs >= SCREEN_WAIT_LIMIT_MS) AutoDecision.GIVE_UP else AutoDecision.WAIT
    }
}
