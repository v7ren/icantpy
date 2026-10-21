package net.icantpy.dungeon.leap

/** How long an armed leap target stays in effect. */
enum class LeapHoldMode(private val text: String) {
    /** Expires after its duration. */
    TIMED("For N seconds"),

    /** Stays until another target replaces it, or the run/phase clears it. */
    UNTIL_REPLACED("Until another target"),

    /**
     * Base target. Later targets override it temporarily; when the overrides end it comes back.
     * Only one fallback is kept: the newest one replaces the older one.
     */
    FALLBACK("Fallback"),
    ;

    fun label(): String = text

    fun next(): LeapHoldMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromToken(token: String?): LeapHoldMode? = entries.firstOrNull { it.name.equals(token, ignoreCase = true) }
    }
}

/** Expiry for targets that never time out on their own. Far enough ahead that `now + value` cannot overflow. */
const val LEAP_HOLD_NEVER_MS: Long = Long.MAX_VALUE / 4

/**
 * Hold settings copied from a leap card onto each target it arms.
 * [seconds] is used only by [LeapHoldMode.TIMED]; 0 means the global default duration.
 */
data class LeapHoldSpec(
    val mode: LeapHoldMode = LeapHoldMode.TIMED,
    val seconds: Int = 0,
    val locked: Boolean = false,
) {
    /** Milliseconds from arming until expiry. Non-timed modes never expire. */
    fun durationMs(defaultSeconds: Int): Long = when (mode) {
        LeapHoldMode.TIMED -> (if (seconds > 0) seconds else defaultSeconds).coerceIn(1, 60) * 1000L
        LeapHoldMode.UNTIL_REPLACED, LeapHoldMode.FALLBACK -> LEAP_HOLD_NEVER_MS
    }

    companion object {
        /** Durations offered in the card. A default of 0 is added in front by the UI. */
        val CHOICES_SECONDS: List<Int> = listOf(3, 5, 8, 10, 15, 20, 30, 45, 60)
    }
}

/** A target the hold machine stores. [withExpiry] returns a copy with a new expiry time. */
interface LeapHoldable<T : LeapHoldable<T>> {
    val holdTitle: String

    /** Identity of the target. Re-arming the same key refreshes it instead of stacking a second copy. */
    val holdKey: String
    val holdPriority: Int
    val holdMode: LeapHoldMode
    val holdLocked: Boolean
    val holdArmedAtMs: Long
    val holdExpiresAtMs: Long
    fun withExpiry(expiresAtMs: Long): T
}

enum class LeapHoldEventKind { ARMED, FALLBACK_SET, BLOCKED_BY_LOCK, LOWER_PRIORITY, EXPIRED, FALLBACK_RESTORED }

data class LeapHoldEvent(val kind: LeapHoldEventKind, val title: String) {
    /** One line for the debug event log. */
    fun describe(): String = when (kind) {
        LeapHoldEventKind.ARMED -> "armed $title"
        LeapHoldEventKind.FALLBACK_SET -> "fallback set: $title"
        LeapHoldEventKind.BLOCKED_BY_LOCK -> "blocked by lock: $title"
        LeapHoldEventKind.LOWER_PRIORITY -> "lower priority than current: $title"
        LeapHoldEventKind.EXPIRED -> "expired: $title"
        LeapHoldEventKind.FALLBACK_RESTORED -> "fallback restored: $title"
    }
}

/**
 * Pure state for armed leap targets: timed and until-replaced overrides, one fallback base, and locks.
 *
 * Rules:
 * - The active set is every override; with no override, the fallback is active.
 * - A locked active target blocks any arrival whose priority is equal or lower (same key excepted).
 * - Without a lock, an arrival with lower priority than the current override is refused.
 * - A new override replaces lower-priority overrides, and same-tier ones when [sameTier] says so.
 * - A new fallback replaces the old fallback. It does not interrupt an active override.
 * - When the last override expires or is dropped, the fallback becomes active again.
 *
 * Callers pass the time in; nothing here reads a clock or touches Minecraft.
 */
class LeapTargetHold<T : LeapHoldable<T>>(
    private val sameTier: (old: T, next: T) -> Boolean = { _, _ -> false },
) {
    private val overrides = ArrayList<T>()
    private var fallback: T? = null
    private val events = ArrayList<LeapHoldEvent>()

    /** Live override targets, in arming order. The fallback is not included. */
    fun overrides(): List<T> = overrides.toList()

    fun fallback(): T? = fallback

    /** The target the leap menu should show: the strongest override, or the fallback when there is none. */
    fun current(): T? = overrides.maxWithOrNull(ORDER) ?: fallback

    /** Every stored target, overrides first. */
    fun all(): List<T> = overrides.toList() + listOfNotNull(fallback)

    /** Returns true when [next] was stored. A refused arrival leaves the state unchanged and logs an event. */
    fun arm(next: T): Boolean {
        val active = if (overrides.isNotEmpty()) overrides.toList() else listOfNotNull(fallback)
        val locked = active.any { it.holdLocked && it.holdKey != next.holdKey && next.holdPriority <= it.holdPriority }
        if (locked) return refuse(LeapHoldEventKind.BLOCKED_BY_LOCK, next)
        if (next.holdMode == LeapHoldMode.FALLBACK) return storeFallback(next)
        val strongest = overrides.maxWithOrNull(ORDER)
        if (strongest != null && next.holdPriority < strongest.holdPriority) {
            return refuse(LeapHoldEventKind.LOWER_PRIORITY, next)
        }
        overrides.removeAll { it.holdKey == next.holdKey }
        overrides.removeAll { old ->
            old.holdPriority < next.holdPriority || (old.holdPriority == next.holdPriority && sameTier(old, next))
        }
        overrides += next
        events += LeapHoldEvent(LeapHoldEventKind.ARMED, next.holdTitle)
        return true
    }

    /** Expires timed overrides that are due at [nowMs]; emits a restore event when the fallback comes back. */
    fun tick(nowMs: Long) {
        val hadOverrides = overrides.isNotEmpty()
        val iterator = overrides.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.holdExpiresAtMs <= nowMs) {
                iterator.remove()
                events += LeapHoldEvent(LeapHoldEventKind.EXPIRED, item.holdTitle)
            }
        }
        val base = fallback
        if (hadOverrides && overrides.isEmpty() && base != null) {
            events += LeapHoldEvent(LeapHoldEventKind.FALLBACK_RESTORED, base.holdTitle)
        }
    }

    /** Sets a new expiry on live timed overrides matching [predicate]. */
    fun extend(predicate: (T) -> Boolean, expiresAtMs: Long) {
        for (index in overrides.indices) {
            val item = overrides[index]
            if (item.holdMode == LeapHoldMode.TIMED && predicate(item)) {
                overrides[index] = item.withExpiry(expiresAtMs)
            }
        }
    }

    /** Drops overrides and the fallback that match [predicate], without events. Used for phase and target changes. */
    fun dropWhere(predicate: (T) -> Boolean) {
        overrides.removeAll(predicate)
        if (fallback?.let(predicate) == true) fallback = null
    }

    /** Forgets every target. Used when a run ends or the user clears the leap target. */
    fun reset() {
        overrides.clear()
        fallback = null
    }

    /** Returns and clears the events recorded since the last call. */
    fun drainEvents(): List<LeapHoldEvent> {
        val out = events.toList()
        events.clear()
        return out
    }

    private fun storeFallback(next: T): Boolean {
        fallback = next
        events += LeapHoldEvent(LeapHoldEventKind.FALLBACK_SET, next.holdTitle)
        return true
    }

    private fun refuse(kind: LeapHoldEventKind, next: T): Boolean {
        events += LeapHoldEvent(kind, next.holdTitle)
        return false
    }

    private companion object {
        val ORDER: Comparator<LeapHoldable<*>> = compareBy<LeapHoldable<*>> { it.holdPriority }.thenBy { it.holdArmedAtMs }
    }
}

/** Short text for the leap menu, HUD and card subtitles about how long a target lasts. */
object LeapHoldText {
    /** Such as "6s" for a timed target, "until replaced", or "fallback". Locked targets add "locked". */
    fun status(target: LeapHoldable<*>, remainingMs: Long): String {
        val base = when (target.holdMode) {
            LeapHoldMode.TIMED -> "${ceilSeconds(remainingMs)}s"
            LeapHoldMode.UNTIL_REPLACED -> "until replaced"
            LeapHoldMode.FALLBACK -> "fallback"
        }
        return if (target.holdLocked) "$base · locked" else base
    }

    /** "then: <title>" when an override is shown over a fallback, otherwise null. */
    fun thenLine(shown: LeapHoldable<*>, fallback: LeapHoldable<*>?): String? {
        if (fallback == null || shown.holdMode == LeapHoldMode.FALLBACK) return null
        return "then: ${fallback.holdTitle}"
    }

    /** Remaining whole seconds, rounded up so the count reads 1s until expiry. */
    fun ceilSeconds(remainingMs: Long): Long = (remainingMs.coerceAtLeast(0) + 999) / 1000

    /** Suffix for a card subtitle. Default timed targets add nothing, so existing cards keep their text. */
    fun cardSuffix(spec: LeapHoldSpec): String {
        val mode = when (spec.mode) {
            LeapHoldMode.TIMED -> if (spec.seconds > 0) " · ${spec.seconds}s" else ""
            LeapHoldMode.UNTIL_REPLACED -> " · until replaced"
            LeapHoldMode.FALLBACK -> " · fallback"
        }
        return mode + if (spec.locked) " · locked" else ""
    }
}
