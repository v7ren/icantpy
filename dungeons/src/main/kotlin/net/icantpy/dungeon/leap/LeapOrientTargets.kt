package net.icantpy.dungeon.leap

data class PendingLeap(
    val sender: String,
    val location: OrientSpot,
    val clazz: LeapDungeonClass,
    val expiresAtMs: Long,
    val atMs: Long,
    val targetName: String = sender,
    val targetClass: LeapDungeonClass? = null,
    val priority: Int = 0,
    val label: String = location.token(),
    val zone: String = "",
    val reason: String = "",
    val phase: LeapPhase = LeapPhase.UNKNOWN,
    val sourceKind: LeapSourceKind = LeapSourceKind.CUSTOM,
    val sourceTriggerId: String = "",
    val section: Int = 0,
    override val holdMode: LeapHoldMode = LeapHoldMode.TIMED,
    override val holdLocked: Boolean = false,
) : LeapHoldable<PendingLeap> {
    fun matchesClass(want: LeapDungeonClass): Boolean {
        if (!want.isReal()) return false
        if (targetClass == want || clazz == want) return true
        return LeapDungeonClass.fromToken(targetName)?.takeIf { it.isReal() } == want
    }

    fun centerTitle(): String {
        val who = targetClass?.takeIf { it.isReal() }?.displayName
            ?: targetName.ifBlank { sender }
        val at = zone.trim()
        return if (at.isEmpty()) who else "$who @ $at"
    }

    fun centerReason(): String = reason.ifBlank { sourceKind.label() }

    override val holdTitle: String get() = centerTitle()
    override val holdKey: String get() = LeapOrientTargets.keyOf(targetName, targetClass, sender)
    override val holdPriority: Int get() = priority
    override val holdArmedAtMs: Long get() = atMs
    override val holdExpiresAtMs: Long get() = expiresAtMs
    override fun withExpiry(expiresAtMs: Long): PendingLeap = copy(expiresAtMs = expiresAtMs)
}

object LeapOrientTargets {
    private val hold = LeapTargetHold<PendingLeap> { old, next -> old.isBossOrClock() && next.isBossOrClock() }

    /** Every live target, overrides first, then the fallback. */
    fun all(nowMs: Long = System.currentTimeMillis()): List<PendingLeap> {
        prune(nowMs)
        return hold.all()
    }

    fun arm(
        sender: String,
        location: OrientSpot,
        clazz: LeapDungeonClass,
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        targetName: String = sender,
        targetClass: LeapDungeonClass? = null,
        priority: Int = 0,
        label: String = location.token(),
        zone: String = "",
        reason: String = "",
        phase: LeapPhase = LeapPhase.UNKNOWN,
        sourceKind: LeapSourceKind = LeapSourceKind.CUSTOM,
        sourceTriggerId: String = "",
        section: Int = 0,
        holdMode: LeapHoldMode = LeapHoldMode.TIMED,
        locked: Boolean = false,
    ): PendingLeap? {
        prune(nowMs)
        val next = PendingLeap(
            sender = sender,
            location = location,
            clazz = clazz,
            expiresAtMs = nowMs + if (holdMode == LeapHoldMode.TIMED) durationMs else LEAP_HOLD_NEVER_MS,
            atMs = nowMs,
            targetName = targetName,
            targetClass = targetClass,
            priority = priority,
            label = label,
            zone = zone,
            reason = reason,
            phase = phase,
            sourceKind = sourceKind,
            sourceTriggerId = sourceTriggerId,
            section = section,
            holdMode = holdMode,
            holdLocked = locked,
        )
        return if (hold.arm(next)) next else null
    }

    /** Drains the hold events (armed, blocked by lock, fallback restored, ...) for the debug log. */
    fun drainEvents(): List<LeapHoldEvent> = hold.drainEvents()

    /** The base target shown whenever no override is active. */
    fun fallback(): PendingLeap? = hold.fallback()

    fun clear() {
        hold.reset()
    }

    fun removeSources(ids: Set<String>) {
        hold.dropWhere { it.sourceTriggerId in ids }
    }

    fun refreshSource(id: String, durationMs: Long, nowMs: Long = System.currentTimeMillis()) {
        hold.extend({ it.sourceTriggerId == id }, nowMs + durationMs)
    }

    fun prune(nowMs: Long = System.currentTimeMillis()) {
        hold.tick(nowMs)
    }

    fun dropIfPhaseChanged(phase: LeapPhase, nowMs: Long = System.currentTimeMillis()) {
        prune(nowMs)
        if (phase == LeapPhase.UNKNOWN || phase == LeapPhase.ANY) return
        hold.dropWhere { lock ->
            lock.phase != LeapPhase.UNKNOWN && lock.phase != LeapPhase.ANY && lock.phase != phase
        }
    }

    fun dropIfSectionChanged(section: Int, nowMs: Long = System.currentTimeMillis()) {
        prune(nowMs)
        if (section !in 1..4) return
        hold.dropWhere { lock ->
            lock.section in 1..4 && lock.section != section
        }
    }

    fun dropIfTargetMissing(aliveNames: Set<String>, nowMs: Long = System.currentTimeMillis()) {
        prune(nowMs)
        if (aliveNames.isEmpty()) return
        hold.dropWhere { lock ->
            val name = lock.targetName
            name.isNotBlank() && aliveNames.none { it.equals(name, ignoreCase = true) }
        }
    }

    /**
     * The target the leap menu shows. Preferred overrides win; otherwise the strongest override;
     * with no override at all, the fallback.
     */
    fun pick(
        gameStateId: String,
        settings: LeapOrientSettings,
        nowMs: Long = System.currentTimeMillis(),
    ): PendingLeap? {
        prune(nowMs)
        val live = hold.overrides()
        if (live.isEmpty()) return hold.fallback()
        val preferred = settings.preferred(gameStateId)
        preferredMatch(live, preferred)?.let { return it }
        return live.maxWithOrNull(compareBy<PendingLeap> { it.priority }.thenBy { it.atMs })
    }

    fun remainingMs(target: PendingLeap, nowMs: Long = System.currentTimeMillis()): Long =
        (target.expiresAtMs - nowMs).coerceAtLeast(0)

    private fun preferredMatch(live: List<PendingLeap>, preferred: LeapPriorityRule?): PendingLeap? {
        if (preferred == null) return null
        val matches = when (preferred.kind) {
            LeapPreferKind.LOCATION -> {
                val spot = OrientSpot.fromToken(preferred.value) ?: return null
                if (spot == OrientSpot.ANY) return null
                live.filter { OrientSpot.matches(it.location, spot) }
            }
            LeapPreferKind.CLASS -> {
                val want = LeapDungeonClass.fromToken(preferred.value)?.takeIf { it.isReal() } ?: return null
                live.filter { it.matchesClass(want) }
            }
        }
        return matches.maxWithOrNull(compareBy<PendingLeap> { it.priority }.thenBy { it.atMs })
    }

    private fun PendingLeap.isBossOrClock(): Boolean =
        sourceKind == LeapSourceKind.BOSS || sourceKind == LeapSourceKind.CLOCK

    internal fun keyOf(targetName: String, targetClass: LeapDungeonClass?, sender: String): String {
        val clazz = targetClass?.takeIf { it.isReal() }
        if (clazz != null && targetName.isBlank()) return "class:${clazz.name}"
        if (targetName.isNotBlank()) return "name:$targetName"
        return "sender:$sender"
    }
}
