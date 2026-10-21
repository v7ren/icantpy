package net.icantpy.dungeon.leap

/**
 * A card's follow-up leaps that are waiting for the leap they follow. [remaining] lists the follow-up steps
 * still to arm, the first being next. [expectedName] or [expectedClass] identify the target the player must
 * leap to: the name when one is known, otherwise the class. [armedAtMs] and [expiresAtMs] belong to that
 * target and drive the "Wait up to" window.
 */
data class PendingFollowUp(
    val card: LeapOrientTrigger,
    val remaining: List<LeapAutoStep>,
    val expectedName: String,
    val expectedClass: LeapDungeonClass?,
    val armedAtMs: Long,
    val expiresAtMs: Long,
) {
    val next: LeapAutoStep get() = remaining.first()

    /** When the next follow-up stops waiting. 0 seconds waits until the expecting target ends. */
    fun deadlineMs(): Long = next.followWithinSeconds.takeIf { it > 0 }
        ?.let { armedAtMs + it * 1000L }
        ?: expiresAtMs
}

/** What happened to a pending follow-up when the player leapt or time passed. */
sealed interface LeapFollowUpOutcome {
    /** The leap matched the expected target: the next follow-up should arm now. */
    data class Hit(val pending: PendingFollowUp) : LeapFollowUpOutcome

    /** The follow-up was dropped, with [reason] for the debug log. */
    data class Discard(val card: LeapOrientTrigger, val reason: String) : LeapFollowUpOutcome
}

/** Pure rules for follow-up leaps. Free of Minecraft types. */
object LeapFollowUp {
    /** A card holds at most this many follow-up leaps. */
    const val MAX_FOLLOW_UPS = 3

    /** The follow-up steps of [trigger] that can run: none unless the card has a leap target. */
    fun steps(trigger: LeapOrientTrigger): List<LeapAutoStep> =
        if (!trigger.armLeap) {
            emptyList()
        } else {
            trigger.steps.filter { it.kind == LeapAutoKind.FOLLOWUP_LEAP }.take(MAX_FOLLOW_UPS)
        }

    /** Destination text of a follow-up step, such as "Tank" or "Sender". */
    fun destLabel(step: LeapAutoStep): String = LeapOrientTrigger(
        id = "",
        event = LeapTriggerEvent.SELF_LEAP,
        target = step.followTarget,
        targetValue = step.followTargetValue,
        targetPlayer = step.followTargetPlayer,
    ).destLabel()

    /**
     * The leap a follow-up step arms, shaped as a card leap: same card (priority, source, zone and reason),
     * with the step's own target and hold settings. It carries no rows and no lock requirement.
     */
    fun asLeapTrigger(card: LeapOrientTrigger, step: LeapAutoStep): LeapOrientTrigger = card.copy(
        target = step.followTarget,
        targetValue = step.followTargetValue,
        targetPlayer = step.followTargetPlayer,
        holdMode = step.followHoldMode,
        holdSeconds = step.followHoldSeconds,
        holdLocked = step.followHoldLocked,
        requiresTargetClass = LeapDungeonClass.EMPTY,
        steps = emptyList(),
        chainDelaySeconds = 0,
        chainDurationSeconds = 0,
    )

    /**
     * Whether a leap to [leapedName] (class [leapedClass]) is the expected one. A known name must match by name;
     * a class-only expectation matches by class.
     */
    fun leapMatches(expectedName: String, expectedClass: LeapDungeonClass?, leapedName: String?, leapedClass: LeapDungeonClass?): Boolean {
        if (expectedName.isNotBlank()) return leapedName != null && leapedName.equals(expectedName, ignoreCase = true)
        return expectedClass != null && expectedClass.isReal() && leapedClass == expectedClass
    }

    /** Whether a live armed target is the one [pending] is waiting on. */
    fun targetIsLive(pending: PendingFollowUp, live: PendingLeap): Boolean {
        if (pending.expectedName.isNotBlank()) return live.targetName.equals(pending.expectedName, ignoreCase = true)
        return pending.expectedClass != null && live.targetClass == pending.expectedClass
    }
}

/**
 * Follow-ups waiting on a leap. The leap handler, tick and arm code in [LeapOrient] call into this; it holds
 * no Minecraft state and reads no clock, so every decision takes the time it is made at.
 */
object LeapFollowUps {
    private val pending = ArrayList<PendingFollowUp>()

    /** Registers the follow-ups of [card] after its leap target [armed] was armed from [request]. */
    fun register(card: LeapOrientTrigger, armed: PendingLeap, expectedName: String, expectedClass: LeapDungeonClass?) {
        val steps = LeapFollowUp.steps(card)
        pending.removeAll { it.card.id == card.id }
        if (steps.isEmpty()) return
        pending += PendingFollowUp(
            card = card,
            remaining = steps,
            expectedName = expectedName,
            expectedClass = expectedClass,
            armedAtMs = armed.atMs,
            expiresAtMs = armed.expiresAtMs,
        )
    }

    /**
     * Resolves every pending follow-up against a leap to [leapedName] of class [leapedClass]. Matching chains come
     * back as hits and everything else as discards; all of them leave the pending list.
     */
    fun onLeap(leapedName: String?, leapedClass: LeapDungeonClass?): List<LeapFollowUpOutcome> {
        val outcomes = pending.map { p ->
            if (LeapFollowUp.leapMatches(p.expectedName, p.expectedClass, leapedName, leapedClass)) {
                LeapFollowUpOutcome.Hit(p)
            } else {
                LeapFollowUpOutcome.Discard(p.card, "leaped to someone else")
            }
        }
        pending.clear()
        return outcomes
    }

    /**
     * Keeps a chain going after its follow-up armed: the next step now waits for [armed]. Nothing is kept when
     * the armed step was the last one.
     */
    fun continueWith(hit: PendingFollowUp, armed: PendingLeap, expectedName: String, expectedClass: LeapDungeonClass?) {
        val rest = hit.remaining.drop(1)
        if (rest.isEmpty()) return
        pending += hit.copy(
            remaining = rest,
            expectedName = expectedName,
            expectedClass = expectedClass,
            armedAtMs = armed.atMs,
            expiresAtMs = armed.expiresAtMs,
        )
    }

    /**
     * Drops chains whose window passed, whose expected target is gone, or whose previous target ended.
     * [live] holds every target the hold machine keeps, including a fallback that has come back.
     */
    fun tick(nowMs: Long, live: List<PendingLeap>): List<LeapFollowUpOutcome.Discard> {
        if (pending.isEmpty()) return emptyList()
        val discards = ArrayList<LeapFollowUpOutcome.Discard>()
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val windowSeconds = p.next.followWithinSeconds
            val reason = when {
                windowSeconds > 0 && nowMs >= p.deadlineMs() -> "wait window passed"
                live.none { LeapFollowUp.targetIsLive(p, it) } ->
                    if (nowMs >= p.expiresAtMs) "previous target ended" else "main target replaced"
                else -> null
            }
            if (reason != null) {
                discards += LeapFollowUpOutcome.Discard(p.card, reason)
                iterator.remove()
            }
        }
        return discards
    }

    /** Drops every pending chain, for example when the phase changes. */
    fun discardAll(reason: String): List<LeapFollowUpOutcome.Discard> {
        val out = pending.map { LeapFollowUpOutcome.Discard(it.card, reason) }
        pending.clear()
        return out
    }

    /** Text for the leap menu's center card, such as "next: Tank", or null when nothing is pending. */
    fun nextLabel(): String? = pending.firstOrNull()?.let { "next: " + LeapFollowUp.destLabel(it.next) }

    fun clear() {
        pending.clear()
    }

    fun pendingCount(): Int = pending.size
}
