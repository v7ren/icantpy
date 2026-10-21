package net.icantpy.qol.visual

import kotlin.math.floor

/** Pure color helpers shared by the trail, china hat and ambient particles. No Minecraft types. */
object CosmeticColors {
    /** Hue advance per second for [rainbow]: one full cycle every five seconds. */
    private const val HUE_PER_SECOND = 0.2

    /** Fully saturated, full brightness RGB (0xRRGGBB) for the hue cycle at [timeSeconds] plus [offset]. */
    fun rainbow(timeSeconds: Double, offset: Float = 0f): Int {
        val hue = fract(timeSeconds * HUE_PER_SECOND + offset)
        return hsvToRgb(hue.toFloat(), 1f, 1f)
    }

    /** Converts hue (0..1, wrapped), saturation and value (0..1) to RGB 0xRRGGBB. */
    fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
        val h = fract(hue.toDouble()) * 6.0
        val s = saturation.coerceIn(0f, 1f).toDouble()
        val v = value.coerceIn(0f, 1f).toDouble()
        val sector = floor(h).toInt() % 6
        val f = h - floor(h)
        val p = v * (1.0 - s)
        val q = v * (1.0 - s * f)
        val t = v * (1.0 - s * (1.0 - f))
        val (r, g, b) = when (sector) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** Packs [rgb] (0xRRGGBB, the high byte is ignored) with [alpha] (0..255, clamped) into ARGB. */
    fun withAlpha(rgb: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /**
     * Alpha for a point along a trail. [t] is 0 at the oldest (tail) point and 1 at the newest (head).
     * The tail fades to [minAlpha] rather than fully transparent so it stays readable.
     */
    fun fadeAlpha(t: Float, maxAlpha: Int = 255, minAlpha: Int = 24): Int {
        val k = t.coerceIn(0f, 1f)
        return (minAlpha + (maxAlpha - minAlpha) * k).toInt()
    }

    private fun channel(v: Double): Int = (v * 255.0 + 0.5).toInt().coerceIn(0, 255)

    private fun fract(x: Double): Double = x - floor(x)
}
