package net.icantpy.gui.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ScorePageThresholdTest {
    @Test
    fun cycleStepsThroughTheRangeAndWraps() {
        assertEquals(210, ScorePage.cycleThreshold(200))
        assertEquals(270, ScorePage.cycleThreshold(260))
        assertEquals(300, ScorePage.cycleThreshold(290))
        assertEquals(320, ScorePage.cycleThreshold(310))
        assertEquals(200, ScorePage.cycleThreshold(320))
    }

    @Test
    fun offStepAndOutOfRangeValuesSnapIntoRange() {
        assertEquals(320, ScorePage.cycleThreshold(315))
        assertEquals(200, ScorePage.cycleThreshold(150))
        assertEquals(200, ScorePage.cycleThreshold(400))
    }
}
