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
) {
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
}

object LeapOrientTargets {
    private val pending = ArrayList<PendingLeap>()

    fun all(nowMs: Long = System.currentTimeMillis()): List<PendingLeap> {
        prune(nowMs)
        return pending.toList()
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
    ): PendingLeap? {
        prune(nowMs)
        val next = PendingLeap(
            sender = sender,
            location = location,
            clazz = clazz,
            expiresAtMs = nowMs + durationMs,
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
        )
        val current = pending.maxWithOrNull(compareBy<PendingLeap> { it.priority }.thenBy { it.atMs })
        if (current != null && !shouldReplace(current, next)) return null
        pending.removeAll {
            it.priority < next.priority ||
                (it.priority == next.priority && next.sourceKind in setOf(LeapSourceKind.BOSS, LeapSourceKind.CLOCK) &&
                    it.sourceKind in setOf(LeapSourceKind.BOSS, LeapSourceKind.CLOCK))
        }
        val key = armKey(targetName, targetClass, sender)
        pending.removeAll { armKey(it.targetName, it.targetClass, it.sender).equals(key, ignoreCase = true) }
        pending += next
        return next
    }

    fun shouldReplace(current: PendingLeap, next: PendingLeap): Boolean {
        if (next.priority < current.priority) return false
        return true
    }

    fun clear() {
        pending.clear()
    }

    fun removeSources(ids: Set<String>) {
        pending.removeAll { it.sourceTriggerId in ids }
    }

    fun refreshSource(id: String, durationMs: Long, nowMs: Long = System.currentTimeMillis()) {
        pending.indices.forEach { index ->
            if (pending[index].sourceTriggerId == id) {
                pending[index] = pending[index].copy(expiresAtMs = nowMs + durationMs)
            }
        }
    }

    fun prune(nowMs: Long = System.currentTimeMillis()) {
        pending.removeAll { it.expiresAtMs <= nowMs }
    }

    fun dropIfPhaseChanged(phase: LeapPhase, nowMs: Long = System.currentTimeMillis()) {
        prune(nowMs)
        if (phase == LeapPhase.UNKNOWN || phase == LeapPhase.ANY) return
        pending.removeAll { lock ->
            lock.phase != LeapPhase.UNKNOWN && lock.phase != LeapPhase.ANY && lock.phase != phase
        }
    }

    fun dropIfSectionChanged(section: Int, nowMs: Long = System.currentTimeMillis()) {
        prune(nowMs)
        if (section !in 1..4) return
        pending.removeAll { lock ->
            lock.section in 1..4 && lock.section != section
        }
    }

    fun dropIfTargetMissing(aliveNames: Set<String>, nowMs: Long = System.currentTimeMillis()) {
        prune(nowMs)
        if (aliveNames.isEmpty()) return
        pending.removeAll { lock ->
            val name = lock.targetName
            name.isNotBlank() && aliveNames.none { it.equals(name, ignoreCase = true) }
        }
    }

    fun pick(
        gameStateId: String,
        settings: LeapOrientSettings,
        nowMs: Long = System.currentTimeMillis(),
    ): PendingLeap? {
        prune(nowMs)
        if (pending.isEmpty()) return null
        val preferred = settings.preferred(gameStateId)
        preferredMatch(preferred)?.let { return it }
        return pending.maxWithOrNull(compareBy<PendingLeap> { it.priority }.thenBy { it.atMs })
    }

    fun remainingMs(target: PendingLeap, nowMs: Long = System.currentTimeMillis()): Long =
        (target.expiresAtMs - nowMs).coerceAtLeast(0)

    private fun preferredMatch(preferred: LeapPriorityRule?): PendingLeap? {
        if (preferred == null) return null
        val matches = when (preferred.kind) {
            LeapPreferKind.LOCATION -> {
                val spot = OrientSpot.fromToken(preferred.value) ?: return null
                if (spot == OrientSpot.ANY) return null
                pending.filter { OrientSpot.matches(it.location, spot) }
            }
            LeapPreferKind.CLASS -> {
                val want = LeapDungeonClass.fromToken(preferred.value)?.takeIf { it.isReal() } ?: return null
                pending.filter { it.matchesClass(want) }
            }
        }
        return matches.maxWithOrNull(compareBy<PendingLeap> { it.priority }.thenBy { it.atMs })
    }

    private fun armKey(targetName: String, targetClass: LeapDungeonClass?, sender: String): String {
        val clazz = targetClass?.takeIf { it.isReal() }
        if (clazz != null && targetName.isBlank()) return "class:${clazz.name}"
        if (targetName.isNotBlank()) return "name:$targetName"
        return "sender:$sender"
    }
}
