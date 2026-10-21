package net.icantpy.qol.auto

import net.icantpy.qol.auto.PostClickCloseWindow.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostClickCloseWindowTest {
    @Test
    fun menuReSentWithinWindowIsClosedAndWindowWaits() {
        val window = PostClickCloseWindow()
        window.begin(0L)
        assertEquals(Action.CLOSE, window.observe(100L, menuOpen = true, holdsItem = false))
        window.closed()
        assertEquals(Action.WAIT, window.observe(120L, menuOpen = false, holdsItem = false))
        assertEquals(Action.WAIT, window.observe(1_000L, menuOpen = false, holdsItem = false))
        assertEquals(1, window.closes)
    }

    @Test
    fun noReSendFinishesAfterWindowQuietly() {
        val window = PostClickCloseWindow()
        window.begin(0L)
        assertEquals(Action.WAIT, window.observe(1_000L, menuOpen = false, holdsItem = false))
        assertEquals(Action.DONE, window.observe(1_500L, menuOpen = false, holdsItem = false))
        assertFalse(window.isActive)
        assertEquals(0, window.closes)
    }

    @Test
    fun windowNeedsQuietPeriodAfterLastReOpen() {
        val window = PostClickCloseWindow()
        window.begin(0L)
        assertEquals(Action.CLOSE, window.observe(1_400L, menuOpen = true, holdsItem = false))
        window.closed()
        // Window time is over, but the menu was last open at 1400 ms and is closed from 1500 ms: quiet for 300 ms.
        assertEquals(Action.WAIT, window.observe(1_500L, menuOpen = false, holdsItem = false))
        assertEquals(Action.WAIT, window.observe(1_790L, menuOpen = false, holdsItem = false))
        assertEquals(Action.DONE, window.observe(1_800L, menuOpen = false, holdsItem = false))
        assertEquals(1, window.closes)
    }

    @Test
    fun heldItemKeepsMenuOpenAndWaits() {
        val window = PostClickCloseWindow()
        window.begin(0L)
        assertEquals(Action.WAIT, window.observe(100L, menuOpen = true, holdsItem = true))
        assertEquals(0, window.closes)
    }

    @Test
    fun windowAlwaysEndsByMaxEvenWhenMenuKeepsReturning() {
        val window = PostClickCloseWindow()
        window.begin(0L)
        assertEquals(Action.CLOSE, window.observe(2_000L, menuOpen = true, holdsItem = false))
        window.closed()
        assertEquals(Action.DONE, window.observe(PostClickCloseWindow.MAX_MS, menuOpen = true, holdsItem = false))
    }

    @Test
    fun defaultTimingsMatchTheSpec() {
        assertEquals(1_500L, PostClickCloseWindow.WINDOW_MS)
        assertEquals(300L, PostClickCloseWindow.QUIET_MS)
        assertTrue(PostClickCloseWindow.MAX_MS > PostClickCloseWindow.WINDOW_MS)
    }
}
