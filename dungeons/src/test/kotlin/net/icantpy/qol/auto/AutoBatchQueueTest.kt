package net.icantpy.qol.auto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoBatchQueueTest {
    private val sort = AutoTarget.Sort("P1")
    private val stats = AutoTarget.Stats("helm")
    private val loadout = AutoTarget.Loadout(3)

    private fun drain(queue: AutoBatchQueue): List<QueuedAutoStep> =
        generateSequence { queue.pop() }.toList()

    @Test
    fun batchStepsStartInEnqueueOrder() {
        val queue = AutoBatchQueue()
        val id = queue.enqueue(listOf(sort, stats, loadout), reason = "Maxor dies")
        val drained = drain(queue)
        assertEquals(listOf(sort, stats, loadout), drained.map { it.target })
        assertTrue(drained.all { it.batchId == id && it.reason == "Maxor dies" })
    }

    @Test
    fun peekShowsTheFrontStepWithoutRemovingIt() {
        val queue = AutoBatchQueue()
        queue.enqueue(listOf(sort, loadout), reason = "x")
        assertEquals(sort, queue.peek()?.target)
        assertEquals(sort, queue.peek()?.target)
        assertEquals(2, queue.size)
    }

    @Test
    fun separateCardBatchesRunInTheOrderTheyWereQueued() {
        val queue = AutoBatchQueue()
        val first = queue.enqueue(listOf(sort, stats), reason = "card A")
        val second = queue.enqueue(listOf(loadout), reason = "card B")
        val drained = drain(queue)
        assertEquals(listOf(first, first, second), drained.map { it.batchId })
        assertEquals(listOf(sort, stats, loadout), drained.map { it.target })
    }

    @Test
    fun abortDropsOnlyTheRemainingStepsOfThatBatch() {
        val queue = AutoBatchQueue()
        val aborted = queue.enqueue(listOf(sort, stats, loadout), reason = "A")
        val other = queue.enqueue(listOf(loadout), reason = "B")
        queue.pop() // the first step of A already started
        assertEquals(2, queue.removeBatch(aborted))
        val remaining = drain(queue)
        assertEquals(listOf(other), remaining.map { it.batchId })
    }

    @Test
    fun removingAnUnknownBatchDropsNothing() {
        val queue = AutoBatchQueue()
        queue.enqueue(listOf(sort), reason = "A")
        assertEquals(0, queue.removeBatch(99L))
        assertEquals(1, queue.size)
    }

    @Test
    fun capacityRefusesWholeBatchesThatWouldOverflow() {
        val queue = AutoBatchQueue(capacity = 3)
        queue.enqueue(listOf(sort, stats), reason = "A")
        assertTrue(queue.hasRoom(1))
        assertFalse(queue.hasRoom(2), "a batch of two must not be split when one slot is left")
    }

    @Test
    fun emptyQueueHasNoFrontStep() {
        val queue = AutoBatchQueue()
        assertNull(queue.peek())
        assertNull(queue.pop())
    }

    @Test
    fun dedupeIgnoresTheSameTriggerInsideTheWindowOnly() {
        val dedupe = AutoRequestDedupe(windowMs = 3_000L)
        assertTrue(dedupe.accept("trigger-1", nowMs = 0L))
        assertFalse(dedupe.accept("trigger-1", nowMs = 2_999L))
        assertTrue(dedupe.accept("trigger-1", nowMs = 3_000L))
    }

    @Test
    fun dedupeKeepsDifferentTriggersIndependent() {
        val dedupe = AutoRequestDedupe(windowMs = 3_000L)
        assertTrue(dedupe.accept("trigger-1", nowMs = 0L))
        assertTrue(dedupe.accept("trigger-2", nowMs = 10L))
        assertFalse(dedupe.accept("trigger-2", nowMs = 20L))
    }

    @Test
    fun dedupeClearForgetsAcceptedKeys() {
        val dedupe = AutoRequestDedupe(windowMs = 3_000L)
        dedupe.accept("trigger-1", nowMs = 0L)
        dedupe.clear()
        assertTrue(dedupe.accept("trigger-1", nowMs = 1L))
    }
}
