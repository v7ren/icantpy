package net.icantpy.cosmetics.morph

import kotlin.math.*
import kotlin.test.*
import org.joml.Matrix4f
import org.joml.Quaternionf

class MorphLookDirectionTest {
    @Test
    fun fixedReferenceUsesExactVanillaYawWithoutHitBlockDistance() {
        for (yaw in listOf(-430f, -90f, 0f, 125f, 450f)) {
            for (pitch in listOf(-90f, -35f, 0f, 60f, 90f)) {
                val angles = MorphLookDirection.fromVanillaLook(yaw, pitch, 1.27)!!
                assertEquals(yaw, angles.yaw)
                val pitchRadians = pitch * PI / 180
                val expected = -atan2(-sin(pitchRadians) * 4 + 1.27, abs(cos(pitchRadians)) * 4) * 180 / PI
                assertEquals(expected.toFloat(), angles.pitch, 0.0001f)
            }
        }
    }

    @Test
    fun fixedReferenceMatchesVanillaWithoutEyeHeightDifferenceAndRejectsInvalidInput() {
        for (pitch in listOf(-90f, -30f, 0f, 60f, 90f)) {
            assertEquals(pitch, MorphLookDirection.fromVanillaLook(125f, pitch, 0.0)!!.pitch, 0.0001f)
        }
        assertNull(MorphLookDirection.fromVanillaLook(Float.NaN, 0f, 0.0))
        assertNull(MorphLookDirection.fromVanillaLook(0f, Float.POSITIVE_INFINITY, 0.0))
        assertNull(MorphLookDirection.fromVanillaLook(0f, 91f, 0.0))
        assertNull(MorphLookDirection.fromVanillaLook(0f, 0f, Double.NaN))
    }

    @Test
    fun correctedMobEyeViewProjectsTheVanillaTargetAtScreenCenter() {
        for (eyeHeight in listOf(0.35, 1.62, 2.9)) {
            for ((x, targetY, z) in listOf(Triple(0.0, 1.62, 4.0),
                Triple(3.0, 0.0, -4.0), Triple(-4.0, 3.0, 0.0))) {
                val y = targetY - eyeHeight
                val angles = MorphLookDirection.toward(x, y, z, 0f)!!
                // Camera.setRotation / getViewRotationMatrix conventions, verified against vanilla.
                val radians = (PI / 180).toFloat()
                val rotation = Quaternionf().rotationYXZ(
                    PI.toFloat() - angles.yaw * radians, -angles.pitch * radians, 0f,
                )
                val view = Matrix4f().rotation(rotation.conjugate(Quaternionf()))
                val projection = Matrix4f().perspective((PI / 2).toFloat(), 16f / 9f, 0.05f, 100f)
                val screen = MorphCrosshairProjection.offset(
                    projection.mul(view), x.toFloat(), y.toFloat(), z.toFloat(), 1920, 1080,
                )!!
                assertEquals(0f, screen.x, 0.002f)
                assertEquals(0f, screen.y, 0.002f)
            }
        }
    }

    @Test
    fun shortAndTallEyesConvergeOnTheSameVanillaTarget() {
        for (eyeHeight in listOf(0.35, 1.62, 2.9)) {
            val direction = MorphLookDirection.toward(0.0, 1.62 - eyeHeight, 4.0, 0f)!!
            assertEquals(0f, direction.yaw, 0.0001f)
            assertEquals((-atan2(1.62 - eyeHeight, 4.0) * 180 / PI).toFloat(), direction.pitch, 0.0001f)
        }
    }

    @Test
    fun returnedAnglesPointExactlyAlongArbitraryRelativeTargets() {
        for (point in listOf(Triple(3.0, 1.2, 4.0), Triple(-3.0, -2.0, -4.0), Triple(8.0, 0.0, 0.0))) {
            val (x, y, z) = point
            val direction = MorphLookDirection.toward(x, y, z, 40f)!!
            val yaw = direction.yaw * PI / 180
            val pitch = direction.pitch * PI / 180
            val length = sqrt(x*x + y*y + z*z)
            assertEquals(x / length, -sin(yaw) * cos(pitch), 0.000001)
            assertEquals(y / length, -sin(pitch), 0.000001)
            assertEquals(z / length, cos(yaw) * cos(pitch), 0.000001)
        }
    }

    @Test
    fun distanceChangesOnlyCosmeticConvergenceAngle() {
        val near = MorphLookDirection.toward(0.0, 1.27, 1.0, 0f)!!
        val far = MorphLookDirection.toward(0.0, 1.27, 6.0, 0f)!!
        assertTrue(near.pitch < far.pitch)
    }

    @Test
    fun verticalTargetsRetainYawAndHaveFinitePitch() {
        assertEquals(MorphLookAngles(125f, -90f), MorphLookDirection.toward(0.0, 2.0, 0.0, 125f))
        assertEquals(MorphLookAngles(125f, 90f), MorphLookDirection.toward(0.0, -2.0, 0.0, 125f))
    }

    @Test
    fun invalidOrCoincidentTargetsDoNotOverrideVanillaCamera() {
        for (point in listOf(Triple(0.0, 0.0, 0.0), Triple(1e-8, 0.0, 0.0),
            Triple(Double.NaN, 1.0, 1.0), Triple(1.0, Double.POSITIVE_INFINITY, 1.0),
            Triple(1.0, 1.0, Double.NEGATIVE_INFINITY))) {
            assertNull(MorphLookDirection.toward(point.first, point.second, point.third, 0f))
        }
        assertNull(MorphLookDirection.toward(1.0, 1.0, 1.0, Float.NaN))
    }
}
