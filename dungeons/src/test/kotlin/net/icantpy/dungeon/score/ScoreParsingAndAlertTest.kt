package net.icantpy.dungeon.score

import net.icantpy.dungeon.leap.LeapTriggerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoreParsingAndAlertTest {
    @Test
    fun elapsedParsesMinutesAndSeconds() {
        assertEquals(754, ScoreParsing.elapsedSeconds(listOf("Time Elapsed: 12m 34s")))
    }

    @Test
    fun elapsedParsesHoursAndIgnoresColorCodes() {
        assertEquals(3723, ScoreParsing.elapsedSeconds(listOf("§7Time Elapsed: §f1h 2m 3s")))
    }

    @Test
    fun elapsedIsNullWhenAbsentOrUnitless() {
        assertNull(ScoreParsing.elapsedSeconds(listOf("Time: 3s")))
        assertNull(ScoreParsing.elapsedSeconds(listOf("Time Elapsed: soon")))
    }

    @Test
    fun secretsPercentParsesTabPercentLine() {
        assertEquals(45.2, ScoreParsing.secretsPercent(listOf("Secrets Found: 45.2%")))
    }

    @Test
    fun secretsPercentIsNullForCountOnlyLine() {
        assertNull(ScoreParsing.secretsPercent(listOf("Secrets Found: 12")))
    }

    @Test
    fun alertFiresEachThresholdOncePerRun() {
        val policy = ScoreAlertPolicy()
        val targets = listOf(270 to true, 300 to true)
        assertEquals(emptyList(), policy.evaluate(260, targets))
        assertEquals(listOf(270), policy.evaluate(275, targets))
        assertEquals(emptyList(), policy.evaluate(280, targets))
        assertEquals(listOf(300), policy.evaluate(305, targets))
        assertEquals(emptyList(), policy.evaluate(310, targets))
    }

    @Test
    fun alertResetStartsANewRun() {
        val policy = ScoreAlertPolicy()
        val targets = listOf(270 to true, 300 to true)
        assertEquals(listOf(270), policy.evaluate(280, targets))
        policy.reset()
        assertEquals(listOf(270), policy.evaluate(280, targets))
    }

    @Test
    fun disabledThresholdIsMarkedReachedWithoutAlerting() {
        val policy = ScoreAlertPolicy()
        // 270 is disabled and crossed together with 300: only 300 is announced.
        assertEquals(listOf(300), policy.evaluate(305, listOf(270 to false, 300 to true)))
        // Re-enabling 270 later in the same run does not alert retroactively.
        assertEquals(emptyList(), policy.evaluate(310, listOf(270 to true, 300 to true)))
    }

    @Test
    fun runStateCountsEventsAndResetsByReplacement() {
        val state = ScoreRunState()
            .withEvent(LeapTriggerEvent.PUZZLE_FAIL)
            .withEvent(LeapTriggerEvent.PUZZLE_FAIL)
            .withEvent(LeapTriggerEvent.MIMIC_DEAD)
            .withEvent(LeapTriggerEvent.PRINCE_KILLED)
            .withEvent(LeapTriggerEvent.SELF_DEATH)
        assertEquals(ScoreRunState(mimicKilled = true, princeKilled = true, failedPuzzles = 2), state)
    }
}
