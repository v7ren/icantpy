package net.icantpy.dungeon.leap

data class PendingAutoLeap(
    val targetName: String,
    val scopeId: String,
    val delayMs: Long,
    val requestedAtMs: Long,
    val openedAtMs: Long? = null,
    val screenId: Int? = null,
    val menuId: Int? = null,
)

data class LeapAutoLeapFrame(
    val nowMs: Long,
    val connected: Boolean,
    val enabledInScope: Boolean,
    val itemUnchanged: Boolean,
    val scopeId: String,
    val screenId: Int?,
    val menuId: Int?,
    val validatedLeapMenu: Boolean,
    val namedTargetSlot: Int?,
)

data class LeapAutoLeapStep(val pending: PendingAutoLeap? = null, val clickSlot: Int? = null)

/** A menu can arrive before its server-supplied player heads. Never substitute a classmate. */
object LeapAutoLeapState {
    const val OPEN_TIMEOUT_MS = 2_000L
    const val MENU_TIMEOUT_MS = 2_000L
    private val playerName = Regex("\\w{1,16}")

    fun request(targetName: String, scopeId: String, delayMs: Long, nowMs: Long): PendingAutoLeap? {
        if (!playerName.matches(targetName)) return null
        return PendingAutoLeap(targetName, scopeId, delayMs.coerceIn(0, 2_000), nowMs)
    }

    fun advance(pending: PendingAutoLeap?, frame: LeapAutoLeapFrame): LeapAutoLeapStep {
        pending ?: return LeapAutoLeapStep()
        if (!frame.connected || !frame.enabledInScope || !frame.itemUnchanged ||
            pending.scopeId != frame.scopeId
        ) return LeapAutoLeapStep()

        val openedAt = pending.openedAtMs
        if (openedAt == null) {
            if (frame.nowMs - pending.requestedAtMs >= OPEN_TIMEOUT_MS) return LeapAutoLeapStep()
            if (frame.screenId == null) return LeapAutoLeapStep(pending)
            if (!frame.validatedLeapMenu || frame.menuId == null) return LeapAutoLeapStep()
            return advance(
                pending.copy(openedAtMs = frame.nowMs, screenId = frame.screenId, menuId = frame.menuId),
                frame,
            )
        }
        if (!frame.validatedLeapMenu || frame.screenId != pending.screenId ||
            frame.menuId != pending.menuId
        ) return LeapAutoLeapStep()
        val elapsed = frame.nowMs - openedAt
        // Give the chosen delay its full duration, then bound missing-head wait to two seconds.
        val menuTimeout = pending.delayMs + MENU_TIMEOUT_MS
        if (elapsed > menuTimeout) return LeapAutoLeapStep()
        if (elapsed >= pending.delayMs && frame.namedTargetSlot != null) {
            return LeapAutoLeapStep(clickSlot = frame.namedTargetSlot)
        }
        if (elapsed >= menuTimeout) return LeapAutoLeapStep()
        return LeapAutoLeapStep(pending)
    }
}
