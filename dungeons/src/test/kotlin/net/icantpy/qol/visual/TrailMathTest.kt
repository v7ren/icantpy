package net.icantpy.qol.visual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrailMathTest {
    @Test
    fun rainbowStaysInRgbRange() {
        for (i in 0 until 500) {
            val rgb = CosmeticColors.rainbow(i * 0.37, i * 0.013f)
            assertTrue(rgb in 0..0xFFFFFF, "out of range: $rgb")
        }
    }

    @Test
    fun rainbowCyclesEveryFiveSeconds() {
        val a = CosmeticColors.rainbow(1.25, 0f)
        val b = CosmeticColors.rainbow(6.25, 0f)
        assertEquals(a, b)
    }

    @Test
    fun rainbowPrimaryHuesAtKnownPoints() {
        assertEquals(0xFF0000, CosmeticColors.hsvToRgb(0f, 1f, 1f))
        assertEquals(0x00FF00, CosmeticColors.hsvToRgb(1f / 3f, 1f, 1f))
        assertEquals(0x0000FF, CosmeticColors.hsvToRgb(2f / 3f, 1f, 1f))
        assertEquals(0xFFFFFF, CosmeticColors.hsvToRgb(0.5f, 0f, 1f))
        assertEquals(0x000000, CosmeticColors.hsvToRgb(0.5f, 1f, 0f))
    }

    @Test
    fun withAlphaPacksAndClamps() {
        assertEquals(0x80FF7AF0.toInt(), CosmeticColors.withAlpha(0xFF7AF0, 0x80))
        assertEquals(0xFF112233.toInt(), CosmeticColors.withAlpha(0xFF112233.toInt(), 300))
        assertEquals(0x00112233, CosmeticColors.withAlpha(0x112233, -5))
    }

    @Test
    fun fadeAlphaRunsFromMinToMax() {
        assertEquals(24, CosmeticColors.fadeAlpha(0f, 255, 24))
        assertEquals(255, CosmeticColors.fadeAlpha(1f, 255, 24))
        assertTrue(CosmeticColors.fadeAlpha(0.5f, 255, 24) in 24..255)
    }

    @Test
    fun ringKeepsLastNPointsInOrder() {
        val ring = TrailRing(3)
        assertEquals(0, ring.size)
        ring.add(1.0, 0.0, 0.0)
        ring.add(2.0, 0.0, 0.0)
        assertEquals(2, ring.size)
        assertEquals(1.0, ring.x(0))
        assertEquals(2.0, ring.x(1))

        for (v in 3..7) ring.add(v.toDouble(), v.toDouble(), v.toDouble())
        assertEquals(3, ring.size)
        assertEquals(5.0, ring.x(0))
        assertEquals(6.0, ring.x(1))
        assertEquals(7.0, ring.x(2))
        assertEquals(7.0, ring.z(2))
    }

    @Test
    fun ringClearResetsWithoutLeakingOldPoints() {
        val ring = TrailRing(2)
        ring.add(1.0, 1.0, 1.0)
        ring.add(2.0, 2.0, 2.0)
        ring.clear()
        assertEquals(0, ring.size)
        ring.add(9.0, 9.0, 9.0)
        assertEquals(1, ring.size)
        assertEquals(9.0, ring.y(0))
    }

    @Test
    fun zeroCapacityRingIgnoresAdds() {
        val ring = TrailRing(0)
        ring.add(1.0, 2.0, 3.0)
        assertEquals(0, ring.size)
    }
}
