package net.icantpy.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DragonFlameFilterTest {
    @Test
    fun acceptsFlameBurstAtBothDragonHeights() {
        assertTrue(isDragonFlame(true, 20, 19.0))
        assertTrue(isDragonFlame(true, 20, 27.0))
        assertTrue(isDragonFlame(true, 20, 18.5))
        assertTrue(isDragonFlame(true, 20, 19.5))
        assertTrue(isDragonFlame(true, 20, 26.5))
        assertTrue(isDragonFlame(true, 20, 27.5))
    }

    @Test
    fun rejectsNonFlameParticles() {
        assertFalse(isDragonFlame(false, 20, 19.0))
    }

    @Test
    fun rejectsWrongCount() {
        assertFalse(isDragonFlame(true, 19, 19.0))
        assertFalse(isDragonFlame(true, 21, 27.0))
    }

    @Test
    fun rejectsHeightsOutsideDragonBands() {
        assertFalse(isDragonFlame(true, 20, 18.4))
        assertFalse(isDragonFlame(true, 20, 19.6))
        assertFalse(isDragonFlame(true, 20, 22.0))
        assertFalse(isDragonFlame(true, 20, 26.4))
        assertFalse(isDragonFlame(true, 20, 27.6))
    }
}
