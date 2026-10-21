package net.icantpy.qol.auto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoBatchInFlightTest {
    private val steps = listOf(AutoTarget.Loadout(4), AutoTarget.Sort("P3"))

    @Test
    fun identicalBatchIsRefusedWhileItWaits() {
        val queue = AutoBatchQueue()
        val inFlight = AutoBatchInFlight()
        inFlight.add(queue.enqueue(steps, "Storm dies"), "card:a", steps)

        assertTrue(inFlight.contains("card:a", steps))
        assertFalse(inFlight.contains("card:b", steps))
        assertFalse(inFlight.contains("card:a", listOf(AutoTarget.Loadout(4))))
    }

    @Test
    fun identicalBatchIsAcceptedAgainOnceEveryStepHasFinished() {
        val queue = AutoBatchQueue()
        val inFlight = AutoBatchInFlight()
        inFlight.add(queue.enqueue(steps, "Storm dies"), "card:a", steps)

        generateSequence { queue.pop() }.toList()
        inFlight.prune { queue.hasBatch(it) }

        assertFalse(inFlight.contains("card:a", steps))
    }

    @Test
    fun batchStillRunningWithRemainingStepsStaysInFlight() {
        val queue = AutoBatchQueue()
        val inFlight = AutoBatchInFlight()
        val id = queue.enqueue(steps, "Storm dies")
        inFlight.add(id, "card:a", steps)

        queue.pop() // first step started, second still waiting
        inFlight.prune { queue.hasBatch(it) }

        assertTrue(inFlight.contains("card:a", steps))
    }

    @Test
    fun otherBatchesDoNotBlockEachOther() {
        val queue = AutoBatchQueue()
        val inFlight = AutoBatchInFlight()
        inFlight.add(queue.enqueue(steps, "Storm dies"), "card:a", steps)
        inFlight.add(queue.enqueue(steps, "Maxor dies"), "card:b", steps)

        assertTrue(inFlight.contains("card:a", steps))
        assertTrue(inFlight.contains("card:b", steps))
        assertFalse(inFlight.contains("card:c", steps))
    }
}
