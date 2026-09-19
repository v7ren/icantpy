package net.icantpy.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudResizeTest {
    @Test
    fun southEastCornerGrowsAlertScaleAndWidth() {
        val drag = HudDrag(
            handle = HudHandle.SE,
            startX = 10,
            startY = 20,
            startW = 100,
            startH = 40,
            scale = 100,
            width = 100,
        )
        val next = HudResize.apply(drag, 10 + 200, 20 + 80, notify = true)
        assertEquals(200, next.width)
        assertEquals(200, next.scale)
        assertEquals(10, next.x)
        assertEquals(20, next.y)
    }

    @Test
    fun northWestCornerPinsOppositeAndShrinks() {
        val drag = HudDrag(
            handle = HudHandle.NW,
            startX = 50,
            startY = 50,
            startW = 100,
            startH = 50,
            scale = 200,
            width = 100,
        )
        val next = HudResize.apply(drag, 75, 75, notify = true)
        assertTrue(next.scale < 200)
        assertTrue(next.width < 100)
        assertTrue(next.x > 50)
        assertTrue(next.y > 50)
    }

    @Test
    fun eastEdgeChangesWidthOnlyForAlerts() {
        val drag = HudDrag(HudHandle.E, 0, 0, 120, 30, 150, 120)
        val next = HudResize.apply(drag, 300, 10, notify = true)
        assertEquals(150, next.scale)
        assertEquals(300, next.width)
    }

    @Test
    fun handleHitPrefersCornerOverBody() {
        val box = HudBox(10, 10, 80, 40)
        assertEquals(HudHandle.MOVE, HudResize.hit(40, 16, HudBox(10, 10, 80, 12), body = true))
        assertEquals(HudHandle.SE, HudResize.hit(10 + 80, 10 + 40, box, body = true))
        assertEquals(HudHandle.MOVE, HudResize.hit(40, 30, box, body = true))
    }
}
