package net.icantpy.qol.auto

/**
 * One waiting auto step. Steps of the same batch share [batchId]; the queue keeps them in the order
 * they were enqueued. [waitStartMs] and [reactionDelayMs] are set by the queue once the step reaches the front.
 */
class QueuedAutoStep(val target: AutoTarget, val reason: String, val batchId: Long) {
    var waitStartMs: Long = -1L
    var reactionDelayMs: Long = 0L
}

/**
 * Free of Minecraft types. Strict FIFO: only the front step may start, so a batch runs its steps in
 * order, and batches of different trigger cards run in the order they were enqueued.
 */
class AutoBatchQueue(private val capacity: Int = AutoActionRules.MAX_WAITING) {
    private val steps = ArrayDeque<QueuedAutoStep>()
    private var lastBatchId = 0L

    val size: Int get() = steps.size

    fun hasRoom(count: Int): Boolean = steps.size + count <= capacity

    /** Appends [targets] as one batch and returns its id. Callers check [hasRoom] first. */
    fun enqueue(targets: List<AutoTarget>, reason: String): Long {
        lastBatchId++
        targets.forEach { steps.addLast(QueuedAutoStep(it, reason, lastBatchId)) }
        return lastBatchId
    }

    fun peek(): QueuedAutoStep? = steps.firstOrNull()

    /** True while any step of [batchId] still waits to start. */
    fun hasBatch(batchId: Long): Boolean = steps.any { it.batchId == batchId }

    fun pop(): QueuedAutoStep? = steps.removeFirstOrNull()

    /** Drops every waiting step of [batchId]. Returns how many were dropped. */
    fun removeBatch(batchId: Long): Int {
        val before = steps.size
        steps.removeAll { it.batchId == batchId }
        return before - steps.size
    }

    fun clear() {
        steps.clear()
    }
}

/**
 * Batches that are still waiting or running, keyed by card key and steps. An identical request is refused
 * until every step of the earlier batch has finished, even when a Test button bypasses dedupe.
 */
class AutoBatchInFlight {
    private class Entry(val key: String, val targets: List<AutoTarget>)

    private val batches = LinkedHashMap<Long, Entry>()

    fun add(batchId: Long, key: String, targets: List<AutoTarget>) {
        batches[batchId] = Entry(key, targets)
    }

    /** Forgets batches that [stillActive] no longer reports as waiting or running. */
    fun prune(stillActive: (Long) -> Boolean) {
        batches.keys.removeAll { !stillActive(it) }
    }

    fun contains(key: String, targets: List<AutoTarget>): Boolean =
        batches.values.any { it.key == key && it.targets == targets }

    fun clear() {
        batches.clear()
    }
}

/** Suppresses a repeat of the same key (for example one trigger card) inside [windowMs]. */
class AutoRequestDedupe(private val windowMs: Long = AutoActionRules.DUPLICATE_WINDOW_MS) {
    private val lastAcceptedMs = mutableMapOf<String, Long>()

    /** Records and returns true when [key] was not accepted within the window. */
    fun accept(key: String, nowMs: Long): Boolean {
        lastAcceptedMs.entries.removeAll { nowMs - it.value >= windowMs }
        if (AutoActionRules.isDuplicate(lastAcceptedMs[key], nowMs)) return false
        lastAcceptedMs[key] = nowMs
        return true
    }

    /** Like [accept], but a [bypass] request is always accepted and leaves the window untouched (Test buttons). */
    fun acceptUnlessBypassed(key: String, nowMs: Long, bypass: Boolean): Boolean =
        bypass || accept(key, nowMs)

    fun clear() {
        lastAcceptedMs.clear()
    }
}
