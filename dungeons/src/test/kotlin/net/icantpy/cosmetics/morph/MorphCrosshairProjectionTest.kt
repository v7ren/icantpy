package net.icantpy.cosmetics.morph

import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MorphCrosshairProjectionTest {
    private fun perspective() = Matrix4f().perspective((PI / 2).toFloat(), 2f, 0.05f, 100f)

    @Test
    fun unshiftedEyeKeepsCrosshairCentered() {
        assertEquals(MorphCrosshairOffset(0f, 0f), project(0f, 0f, -4f))
    }

    @Test
    fun shortMorphMovesCrosshairUpToThePlayerEyeTarget() {
        val offset = project(0f, 1.27f, -4f)!!
        assertEquals(0f, offset.x, 0.001f)
        assertEquals(-31.75f, offset.y, 0.001f)
    }

    @Test
    fun tallMorphMovesCrosshairDownToThePlayerEyeTarget() {
        assertEquals(32f, project(0f, -1.28f, -4f)!!.y, 0.001f)
    }

    @Test
    fun closerTargetsNeedMoreParallaxAndGuiScaleIsRespected() {
        assertEquals(-63.5f, project(0f, 1.27f, -2f)!!.y, 0.001f)
        val scaled = MorphCrosshairProjection.offset(perspective(), 0f, 1.27f, -4f, 800, 400)!!
        assertEquals(-63.5f, scaled.y, 0.001f)
    }

    @Test
    fun sidewaysAndRotatedTargetsUseTheFullViewTransform() {
        assertEquals(25f, project(1f, 0f, -4f)!!.x, 0.001f)
        val rotated = Matrix4f(perspective()).rotateZ((PI / 2).toFloat())
        val offset = MorphCrosshairProjection.offset(rotated, 0f, 1f, -4f, 400, 200)!!
        assertEquals(-25f, offset.x, 0.001f)
        assertEquals(0f, offset.y, 0.001f)
    }

    @Test
    fun targetsOffscreenAreNotClampedOntoAnUnrelatedVisibleBlock() {
        assertEquals(-500f, project(0f, 20f, -4f)!!.y, 0.001f)
    }

    @Test
    fun behindCameraAndInvalidInputsAreRejected() {
        assertNull(project(0f, 1f, 4f))
        assertNull(project(0f, 1f, 0f))
        assertNull(project(Float.NaN, 1f, -4f))
        assertNull(project(0f, Float.POSITIVE_INFINITY, -4f))
        assertNull(MorphCrosshairProjection.offset(perspective(), 0f, 1f, -4f, 0, 200))
    }

    private fun project(x: Float, y: Float, z: Float) =
        MorphCrosshairProjection.offset(perspective(), x, y, z, 400, 200)

    @Test
    fun worldEffectsAreAppliedInVanillaOrderWithoutChangingTheInputMatrices() {
        val projection = perspective()
        val effects = Matrix4f().translate(0.2f, -0.1f, 0f).rotateZ(0.1f)
        val view = Matrix4f().rotateX(0.2f)
        val expected = Matrix4f(projection).mul(effects).mul(view)
        assertEquals(expected, MorphCrosshairProjection.worldMatrix(projection, effects, view, 0f, 0f))
        assertEquals(perspective(), projection)
        assertEquals(Matrix4f().translate(0.2f, -0.1f, 0f).rotateZ(0.1f), effects)
        assertEquals(Matrix4f().rotateX(0.2f), view)
    }

    @Test
    fun portalDistortionChangesTheProjectionWithoutChangingPlayerAim() {
        val factor = 5f / 6f - 0.04f
        val expected = Matrix4f(perspective()).scale(1f / (factor * factor), 1f, 1f)
        assertEquals(expected, MorphCrosshairProjection.worldMatrix(perspective(), Matrix4f(), Matrix4f(), 1f, 0f))
    }
}
