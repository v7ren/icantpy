package net.icantpy.qol.auto

/**
 * Free of Minecraft types. The post-click window of a command menu run. After the run's last click the
 * server may re-send the menu (Hypixel refreshes Loadouts / Stats after a switch), so a menu that shows up
 * during the window is closed again. The window ends once [windowMs] has passed and no such menu has been
 * open for [quietMs]; it always ends by [maxMs] so a stuck menu cannot hold the run.
 */
class PostClickCloseWindow(
    private val windowMs: Long = WINDOW_MS,
    private val quietMs: Long = QUIET_MS,
    private val maxMs: Long = MAX_MS,
) {
    /** What the run does this tick: close the menu on screen, keep waiting, or finish the run. */
    enum class Action { CLOSE, WAIT, DONE }

    private var startMs = -1L
    private var closedSinceMs = -1L

    /** Menus closed by this window, reported as "reopened" in the debug line. */
    var closes = 0
        private set

    val isActive: Boolean get() = startMs >= 0L

    fun begin(nowMs: Long) {
        startMs = nowMs
        closedSinceMs = nowMs
        closes = 0
    }

    /**
     * Decides for one tick. [menuOpen] says a run menu is on screen; [holdsItem] says the cursor holds an item,
     * in which case the menu is left alone (CursorSafety would refuse the close) and the window keeps waiting.
     * After a [Action.CLOSE] the caller closes the menu and calls [closed].
     */
    fun observe(nowMs: Long, menuOpen: Boolean, holdsItem: Boolean): Action {
        if (!isActive) return Action.DONE
        val elapsed = nowMs - startMs
        if (elapsed >= maxMs) return finish()
        if (menuOpen) {
            closedSinceMs = -1L
            return if (holdsItem) Action.WAIT else Action.CLOSE
        }
        if (closedSinceMs < 0L) closedSinceMs = nowMs
        val quiet = nowMs - closedSinceMs >= quietMs
        return if (elapsed >= windowMs && quiet) finish() else Action.WAIT
    }

    fun closed() {
        closes++
    }

    private fun finish(): Action {
        startMs = -1L
        return Action.DONE
    }

    companion object {
        const val WINDOW_MS = 1_500L
        const val QUIET_MS = 300L
        const val MAX_MS = 2_500L
    }
}
