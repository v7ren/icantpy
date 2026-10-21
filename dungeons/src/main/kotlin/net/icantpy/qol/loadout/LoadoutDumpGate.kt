package net.icantpy.qol.loadout

/**
 * Pure decision logic for the Loadouts container dump. Driven once per client tick with the current menu state.
 *
 * - [arm] opens a window in which the next populated Loadouts menu is dumped (and the arm is consumed).
 * - Without an arm, the first populated menu of the game session is dumped to the log only.
 * - A menu whose slots never fill within [POPULATE_TIMEOUT_MS] of opening is skipped; an armed dump still runs
 *   with whatever slots exist, so the user always gets output after arming.
 */
class LoadoutDumpGate(
    private val armWindowMs: Long = ARM_WINDOW_MS,
    private val populateTimeoutMs: Long = POPULATE_TIMEOUT_MS,
) {
    enum class Decision { NONE, DUMP_ARMED, DUMP_AUTO }

    /** Epoch-ms until which an arm is valid; 0 when disarmed. */
    var armedUntil: Long = 0L
        private set

    /** True once a menu has been dumped (automatically or armed) in this game session. */
    var sessionDumped: Boolean = false
        private set

    private var openedAt: Long? = null
    private var menuDone: Boolean = false

    fun arm(nowMs: Long) {
        armedUntil = nowMs + armWindowMs
    }

    fun disarm() {
        armedUntil = 0L
    }

    /** Called every tick. [menuOpen] is true while a Loadouts menu is shown; [populated] when a container slot holds an item. */
    fun update(nowMs: Long, menuOpen: Boolean, populated: Boolean): Decision {
        if (!menuOpen) {
            openedAt = null
            menuDone = false
            if (armedUntil != 0L && nowMs > armedUntil) armedUntil = 0L
            return Decision.NONE
        }
        if (openedAt == null) {
            openedAt = nowMs
            menuDone = false
        }
        if (menuDone) return Decision.NONE
        val armed = armedUntil != 0L && nowMs <= armedUntil
        val timedOut = nowMs - (openedAt ?: nowMs) >= populateTimeoutMs
        if (!populated && !timedOut) return Decision.NONE
        menuDone = true
        if (armed) {
            armedUntil = 0L
            sessionDumped = true
            return Decision.DUMP_ARMED
        }
        if (armedUntil != 0L && nowMs > armedUntil) armedUntil = 0L
        if (!sessionDumped && populated) {
            sessionDumped = true
            return Decision.DUMP_AUTO
        }
        return Decision.NONE
    }

    companion object {
        const val ARM_WINDOW_MS = 30_000L
        const val POPULATE_TIMEOUT_MS = 2_000L
    }
}
