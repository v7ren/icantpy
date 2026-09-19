package net.icantpy.render

import org.joml.Matrix3x2f
import org.joml.Matrix3x2fStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HudRenderTransformTest {
    @Test
    fun translationAppliesOnlyInsideTheElementAndRestoresAnExistingPose() {
        val pose = Matrix3x2fStack(4).apply { translate(2f, 3f) }
        val before = Matrix3x2f(pose)
        HudRenderTransform.render(pose, floatArrayOf(7f, -11f)) {
            assertEquals(9f, pose.m20())
            assertEquals(-8f, pose.m21())
        }
        assertEquals(before, Matrix3x2f(pose))
    }

    @Test
    fun rendererFailureStillRestoresThePose() {
        val pose = Matrix3x2fStack(4)
        val before = Matrix3x2f(pose)
        assertFailsWith<IllegalStateException> {
            HudRenderTransform.render(pose, floatArrayOf(7f, -11f)) {
                assertEquals(7f, pose.m20())
                error("render failure")
            }
        }
        assertEquals(before, Matrix3x2f(pose))
    }

    @Test
    fun unprojectableElementsAreSuppressed() {
        var rendered = false
        HudRenderTransform.render(Matrix3x2fStack(4), false) { rendered = true }
        assertFalse(rendered)
    }

    @Test
    fun absentOrInvalidOverridesLeaveVanillaRenderingUntouched() {
        for (override in listOf(null, "unexpected", floatArrayOf(1f), floatArrayOf(Float.NaN, 0f),
            floatArrayOf(0f, Float.POSITIVE_INFINITY))) {
            val pose = Matrix3x2fStack(4)
            var rendered = false
            HudRenderTransform.render(pose, override) {
                rendered = true
                assertEquals(Matrix3x2f(), Matrix3x2f(pose))
            }
            assertTrue(rendered)
        }
    }
}
