package net.icantpy.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkiaRedrawGateTest {
    @Test
    fun firstFrameAlwaysRedraws() {
        val gate = SkiaRedrawGate()
        assertTrue(gate.needsRedraw("a"))
    }

    @Test
    fun sameKeyAfterDrawSkipsRedraw() {
        val gate = SkiaRedrawGate()
        gate.markDrawn("a")
        assertFalse(gate.needsRedraw("a"))
        assertFalse(gate.needsRedraw("a"))
    }

    @Test
    fun changedKeyRedraws() {
        val gate = SkiaRedrawGate()
        gate.markDrawn("a")
        assertTrue(gate.needsRedraw("b"))
    }

    @Test
    fun equalButNewInstanceKeyCountsAsSame() {
        val gate = SkiaRedrawGate()
        gate.markDrawn(SkiaFrameKey(800, 600, 2f, listOf(1, 2)))
        assertFalse(gate.needsRedraw(SkiaFrameKey(800, 600, 2f, listOf(1, 2))))
    }

    @Test
    fun windowSizeOrScaleChangeRedraws() {
        val gate = SkiaRedrawGate()
        gate.markDrawn(SkiaFrameKey(800, 600, 2f, "x"))
        assertTrue(gate.needsRedraw(SkiaFrameKey(801, 600, 2f, "x")))
        assertTrue(gate.needsRedraw(SkiaFrameKey(800, 600, 3f, "x")))
    }

    @Test
    fun invalidateForcesRedrawOfSameKey() {
        val gate = SkiaRedrawGate()
        gate.markDrawn("a")
        gate.invalidate()
        assertTrue(gate.needsRedraw("a"))
    }

    @Test
    fun redrawingWithNewKeyReplacesRememberedKey() {
        val gate = SkiaRedrawGate()
        gate.markDrawn("a")
        gate.markDrawn("b")
        assertFalse(gate.needsRedraw("b"))
        assertEquals(true, gate.needsRedraw("a"))
    }
}
