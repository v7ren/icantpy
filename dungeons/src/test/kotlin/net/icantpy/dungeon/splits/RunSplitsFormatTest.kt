package net.icantpy.dungeon.splits

import kotlin.test.Test
import kotlin.test.assertEquals

class RunSplitsFormatTest {
    @Test
    fun timesUseMinuteSecondTenths() {
        assertEquals("0:42.3", RunSplitsFormat.time(42_300L))
        assertEquals("1:01.0", RunSplitsFormat.time(61_000L))
        assertEquals("1:01:01.5", RunSplitsFormat.time(3_661_500L))
        assertEquals("--:--.-", RunSplitsFormat.time(null))
    }

    @Test
    fun deltasShowSignAndTenths() {
        assertEquals("-1.2", RunSplitsFormat.delta(-1_200L))
        assertEquals("+1.2", RunSplitsFormat.delta(1_200L))
        assertEquals("+0.0", RunSplitsFormat.delta(-30L))
        assertEquals("-1:40.0", RunSplitsFormat.delta(-100_000L))
        assertEquals("", RunSplitsFormat.delta(null))
    }
}
