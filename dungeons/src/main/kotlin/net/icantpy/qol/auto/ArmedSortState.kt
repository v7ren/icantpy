package net.icantpy.qol.auto

/**
 * Free of Minecraft types. Decision state for an "armed" inventory sort: a trigger arms one request, and the
 * sort runs the next time the player opens their own inventory. After the run, the inventory can be locked
 * against reopening for a window. Times are System.currentTimeMillis values; 0 or less for a timeout means never.
 */
class ArmedSortState {
    /** A request waiting for the player to open the inventory. [expiresAtMs] is null when it never expires. */
    data class Request(
        val presetName: String,
        val cardKey: String,
        val expiresAtMs: Long?,
        val reopenBlockMs: Int,
        val lockUntilDone: Boolean,
    )

    /** The run started from a request. Its lock and reopen window are kept until the run finishes. */
    private class Running(val reopenBlockMs: Int, val lockUntilDone: Boolean)

    /** [close] is true when the reopened inventory must be closed now; [hintSeconds] is set once per window. */
    data class ReopenCheck(val close: Boolean, val hintSeconds: Int?)

    private var request: Request? = null
    private var running: Running? = null
    private var reopenBlockUntilMs: Long? = null
    private var hintShownForWindow = false

    /**
     * Stores [presetName] as the one armed request, replacing any earlier one. Returns false (and arms nothing)
     * when the Inventory Sort feature is off. [armTimeoutMs] of 0 or less means the request never expires.
     */
    fun arm(
        nowMs: Long,
        presetName: String,
        cardKey: String,
        armTimeoutMs: Int,
        reopenBlockMs: Int,
        lockUntilDone: Boolean,
        sortEnabled: Boolean,
    ): Boolean {
        if (!sortEnabled) return false
        val expires = if (armTimeoutMs > 0) nowMs + armTimeoutMs else null
        request = Request(presetName, cardKey, expires, reopenBlockMs.coerceAtLeast(0), lockUntilDone)
        return true
    }

    fun hasRequest(): Boolean = request != null

    /** Drops the pending request. Returns true when there was one. */
    fun cancelRequest(): Boolean {
        val had = request != null
        request = null
        return had
    }

    /** Removes and returns the pending request when its timeout has passed at [nowMs], otherwise null. */
    fun takeExpired(nowMs: Long): Boolean {
        val expires = request?.expiresAtMs ?: return false
        if (nowMs < expires) return false
        request = null
        return true
    }

    /**
     * Returns the request to start now, moving it to the running state, or null. A request starts only when the
     * player's own inventory is the current screen, no other auto run is active and the request has not expired.
     */
    fun startIfReady(nowMs: Long, inventoryOpen: Boolean, otherRunActive: Boolean): Request? {
        val pending = request ?: return null
        if (!inventoryOpen || otherRunActive) return null
        val expires = pending.expiresAtMs
        if (expires != null && nowMs >= expires) return null
        request = null
        running = Running(pending.reopenBlockMs, pending.lockUntilDone)
        return pending
    }

    fun isRunning(): Boolean = running != null

    /** True while the armed run should hold input with the lock forced on, whatever the global lock setting says. */
    fun lockActive(): Boolean = running?.lockUntilDone == true

    /**
     * Called once the armed run is no longer busy. Starts the reopen window when the run asked for one.
     * Returns true when a run had been in progress.
     */
    fun runFinished(nowMs: Long): Boolean {
        val finished = running ?: return false
        running = null
        if (finished.reopenBlockMs > 0) {
            reopenBlockUntilMs = nowMs + finished.reopenBlockMs
            hintShownForWindow = false
        }
        return true
    }

    /**
     * Decides whether an opened player inventory is closed again because the reopen window is still open.
     * Only the player's inventory is ever closed here; other screens are never reported.
     */
    fun reopenCheck(nowMs: Long, inventoryOpen: Boolean): ReopenCheck {
        val until = reopenBlockUntilMs ?: return ReopenCheck(close = false, hintSeconds = null)
        if (nowMs >= until) {
            reopenBlockUntilMs = null
            return ReopenCheck(close = false, hintSeconds = null)
        }
        if (!inventoryOpen) return ReopenCheck(close = false, hintSeconds = null)
        val hint = if (hintShownForWindow) null else secondsLeft(nowMs, until)
        hintShownForWindow = true
        return ReopenCheck(close = true, hintSeconds = hint)
    }

    fun clear() {
        request = null
        running = null
        reopenBlockUntilMs = null
        hintShownForWindow = false
    }

    private fun secondsLeft(nowMs: Long, untilMs: Long): Int = ((untilMs - nowMs + 999L) / 1000L).toInt()
}
