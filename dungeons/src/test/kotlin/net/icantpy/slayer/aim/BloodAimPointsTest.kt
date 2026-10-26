package net.icantpy.slayer.aim

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BloodAimPointsTest {
    @Test
    fun barelyMovedStandHasNoEndpoint() {
        assertNull(BloodAimPoints.endpoint(Vec3(10.0, 70.0, 10.0), Vec3(10.1, 70.0, 10.0), true))
    }

    @Test
    fun endpointRunsAlongTheTravelDirection() {
        val end = BloodAimPoints.endpoint(Vec3(0.0, 70.0, 0.0), Vec3(1.0, 70.0, 0.0), true)!!
        assertEquals(16.1, end.x, 1e-9)
        assertEquals(70.0, end.y, 1e-9)
        assertEquals(0.0, end.z, 1e-9)
    }

    @Test
    fun laterGroupsTravelLess() {
        val first = BloodAimPoints.endpoint(Vec3.ZERO, Vec3(0.0, 0.0, 2.0), true)!!
        val later = BloodAimPoints.endpoint(Vec3.ZERO, Vec3(0.0, 0.0, 2.0), false)!!
        assertEquals(16.1, first.z, 1e-9)
        assertEquals(11.9, later.z, 1e-9)
    }

    @Test
    fun diagonalTravelKeepsTheFullDistance() {
        val end = BloodAimPoints.endpoint(Vec3.ZERO, Vec3(3.0, 0.0, 4.0), false)!!
        assertTrue(abs(end.length() - 11.9) < 1e-9)
    }

    @Test
    fun aimPointIsTheBoxCentreAboveTheEndpoint() {
        val p = BloodAimPoints.aimPoint(Vec3(5.0, 70.0, 5.0))
        assertEquals(72.0, p.y, 1e-9)
        assertEquals(5.0, p.x, 1e-9)
    }
}
