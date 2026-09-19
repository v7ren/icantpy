package net.icantpy.cosmetics.morph

import org.joml.Matrix4fc
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.math.sqrt

internal data class MorphCrosshairOffset(val x: Float, val y: Float)

/** Projects the existing vanilla hit point, never a replacement pick ray. */
internal object MorphCrosshairProjection {
    /** Vanilla multiplies camera shake and portal/nausea distortion into projection before view rotation. */
    fun worldMatrix(
        projection: Matrix4fc,
        cameraEffects: Matrix4fc,
        viewRotation: Matrix4fc,
        distortion: Float,
        spinDegrees: Float,
    ): Matrix4f {
        val result = Matrix4f(projection).mul(cameraEffects)
        if (distortion > 0f) {
            val factor = 5f / (distortion * distortion + 5f) - distortion * 0.04f
            val axis = Vector3f(0f, sqrt(2f) / 2f, sqrt(2f) / 2f)
            val angle = spinDegrees * (PI / 180).toFloat()
            result.rotate(angle, axis).scale(1f / (factor * factor), 1f, 1f).rotate(-angle, axis)
        }
        return result.mul(viewRotation)
    }

    fun offset(
        viewProjection: Matrix4fc,
        relativeX: Float,
        relativeY: Float,
        relativeZ: Float,
        width: Int,
        height: Int,
    ): MorphCrosshairOffset? {
        if (width <= 0 || height <= 0) return null
        val clip = viewProjection.transform(Vector4f(relativeX, relativeY, relativeZ, 1f))
        if (!clip.x.isFinite() || !clip.y.isFinite() || !clip.w.isFinite() || clip.w <= 0.0001f) return null
        val x = clip.x / clip.w * width / 2f
        val y = (0f - clip.y / clip.w) * height / 2f
        return if (x.isFinite() && y.isFinite()) MorphCrosshairOffset(x, y) else null
    }
}
