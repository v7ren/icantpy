package net.icantpy.gui

import kotlin.test.Test
import kotlin.test.assertEquals

class GuiInputTest {
    @Test
    fun toggleOpensOnTheNextPollWhenNoScreenIsOurs() {
        val queue = GuiOpenQueue()
        queue.requestToggle(isOurScreen = false)
        assertEquals(GuiPending.OpenConfig, queue.poll())
        assertEquals(GuiPending.None, queue.poll())
    }

    @Test
    fun toggleClosesWhenTheConfigIsAlreadyOpen() {
        val queue = GuiOpenQueue()
        queue.requestToggle(isOurScreen = true)
        assertEquals(GuiPending.Close, queue.poll())
    }

    @Test
    fun theLastToggleBeforePollWins() {
        val queue = GuiOpenQueue()
        queue.requestToggle(isOurScreen = false)
        queue.requestToggle(isOurScreen = true)
        assertEquals(GuiPending.Close, queue.poll())
    }

    @Test
    fun clearDropsAPendingOpen() {
        val queue = GuiOpenQueue()
        queue.requestToggle(isOurScreen = false)
        queue.clear()
        assertEquals(GuiPending.None, queue.poll())
    }

    @Test
    fun framebufferPointerPassesThroughWhenWindowAndFramebufferMatch() {
        val (x, y) = framebufferPointer(320.0, 180.0, 1920, 1080, 1920, 1080)
        assertEquals(320f, x)
        assertEquals(180f, y)
    }

    @Test
    fun framebufferPointerMapsWindowCoordsOntoALargerFramebuffer() {
        val (x, y) = framebufferPointer(100.0, 50.0, 1920, 1080, 960, 540)
        assertEquals(200f, x)
        assertEquals(100f, y)
    }

    @Test
    fun skiaGuiScaleIsFramebufferOverGui() {
        assertEquals(3f, skiaGuiScale(1920, 640))
        assertEquals(1f, skiaGuiScale(1920, 0))
    }

    @Test
    fun clampScrollStopsAtTheBottomOfTheView() {
        assertEquals(0, clampScroll(-10, 100, 200))
        assertEquals(40, clampScroll(99, 140, 100))
        assertEquals(0, clampScroll(20, 50, 80))
    }

    @Test
    fun guiRectContainsInclusiveOrigin() {
        val rect = GuiRect(10, 20, 30, 12)
        assertEquals(true, rect.contains(10, 20))
        assertEquals(true, rect.contains(39, 31))
        assertEquals(false, rect.contains(40, 20))
        assertEquals(false, rect.contains(10, 32))
    }

    @Test
    fun contentViewIgnoresHeaderAndFooter() {
        assertEquals(true, inContentView(50, 40, 10, 30, 200, 80))
        assertEquals(false, inContentView(50, 20, 10, 30, 200, 80))
        assertEquals(false, inContentView(50, 80, 10, 30, 200, 80))
        assertEquals(false, inContentView(5, 40, 10, 30, 200, 80))
    }

    @Test
    fun wrapLinesBreaksOnWidth() {
        val lines = wrapLines("walk onto a box to send a command", 48) { it.length * 6 }
        assertEquals(true, lines.size >= 2)
        assertEquals(true, lines.all { it.length * 6 <= 48 || it.split(' ').size == 1 })
    }
}
