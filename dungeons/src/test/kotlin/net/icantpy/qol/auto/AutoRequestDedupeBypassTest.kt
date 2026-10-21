package net.icantpy.qol.auto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoRequestDedupeBypassTest {
    @Test
    fun repeatInsideWindowIsRefusedWithoutBypass() {
        val dedupe = AutoRequestDedupe()
        assertTrue(dedupe.acceptUnlessBypassed("batch:t", nowMs = 0L, bypass = false))
        assertFalse(dedupe.acceptUnlessBypassed("batch:t", nowMs = 1_000L, bypass = false))
    }

    @Test
    fun bypassAcceptsRepeatInsideWindow() {
        val dedupe = AutoRequestDedupe()
        assertTrue(dedupe.acceptUnlessBypassed("batch:t", nowMs = 0L, bypass = false))
        assertTrue(dedupe.acceptUnlessBypassed("batch:t", nowMs = 1_000L, bypass = true))
        assertTrue(dedupe.acceptUnlessBypassed("batch:t", nowMs = 1_500L, bypass = true))
    }

    @Test
    fun bypassLeavesTheWindowUntouched() {
        val dedupe = AutoRequestDedupe()
        assertTrue(dedupe.acceptUnlessBypassed("batch:t", nowMs = 0L, bypass = true))
        assertTrue(dedupe.acceptUnlessBypassed("batch:t", nowMs = 100L, bypass = false))
    }
}
