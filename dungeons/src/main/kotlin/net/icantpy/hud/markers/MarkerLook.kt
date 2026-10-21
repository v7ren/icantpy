package net.icantpy.hud.markers

/**
 * Adjustable look of one marker, per mark step. Pure (no Minecraft types) so it can be unit tested.
 * Percent values are stored as the player chose them; the helpers clamp anything out of range.
 */
object MarkerLook {
    const val DEFAULT_OPACITY = 80
    const val DEFAULT_SIZE = 100
    const val DEFAULT_HEIGHT = 0
    const val DEFAULT_BRIGHTNESS = 100

    val OPACITY_CHOICES: List<Int> = listOf(20, 30, 40, 50, 60, 70, 80, 90, 100)
    val SIZE_CHOICES: List<Int> = listOf(50, 75, 100, 150, 200, 300)
    val HEIGHT_CHOICES: List<Int> = listOf(0, 1, 2, 3, 4, 5)
    val BRIGHTNESS_CHOICES: List<Int> = listOf(50, 75, 100, 125, 150, 200, 300)

    fun opacityLabel(percent: Int): String = "$percent%"

    fun sizeLabel(percent: Int): String = "$percent%"

    fun heightLabel(blocks: Int): String = if (blocks <= 0) "Off" else "+$blocks"

    fun brightnessLabel(percent: Int): String = "$percent%"

    /** Brightness percent clamped to the range the choices cover. */
    fun clampBrightness(percent: Int): Int = percent.coerceIn(50, 300)

    /**
     * RGB (0xRRGGBB, no alpha) of [argb] at [brightnessPercent]. Below 100 scales the channels down;
     * above 100 mixes toward white, reaching half way at 300. 100 returns the color unchanged.
     */
    fun brightenRgb(argb: Int, brightnessPercent: Int): Int {
        val p = clampBrightness(brightnessPercent)
        fun channel(c: Int): Int = when {
            p == 100 -> c
            p < 100 -> (c * p / 100).coerceIn(0, 255)
            else -> (c + (255 - c) * (p - 100) / 400).coerceIn(0, 255)
        }
        val r = channel(argb shr 16 and 0xFF)
        val g = channel(argb shr 8 and 0xFF)
        val b = channel(argb and 0xFF)
        return (r shl 16) or (g shl 8) or b
    }

    /** Alpha boosted by brightness above 100 percent, capped at 255. Below 100 the alpha is unchanged. */
    fun boostAlpha(alpha: Int, brightnessPercent: Int): Int {
        val p = clampBrightness(brightnessPercent)
        val a = alpha.coerceIn(0, 255)
        return if (p <= 100) a else (a * p / 100).coerceIn(0, 255)
    }

    /** Next choice after [current] in [choices], wrapping. Unknown values restart at the first choice. */
    fun cycle(choices: List<Int>, current: Int): Int {
        val index = choices.indexOf(current)
        return if (index < 0) choices.first() else choices[(index + 1) % choices.size]
    }

    /** Geometry multiplier for a size percent. Clamped to [0.1, 10]. */
    fun scale(sizePercent: Int): Double = sizePercent.coerceIn(10, 1000) / 100.0

    /** Beam half width in blocks for a size percent. */
    fun beamHalfWidth(sizePercent: Int): Double = BASE_BEAM_HALF_WIDTH * scale(sizePercent)

    /** Beam height in blocks for a size percent. */
    fun beamHeight(sizePercent: Int): Double = BASE_BEAM_HEIGHT * scale(sizePercent)

    /** Extra grow around the player box for BOX style, in blocks per side. */
    fun boxGrow(sizePercent: Int): Double = BOX_BASE_GROW * scale(sizePercent)

    /**
     * Alpha (0..255) of a color channel after opacity and pulse. [pulseFactor] is the value from
     * [PlayerMarkers.pulse] in [0.7, 1.0]; with [pulseOn] false the pulse is ignored.
     */
    fun alpha(baseAlpha: Int, opacityPercent: Int, pulseOn: Boolean, pulseFactor: Float): Int {
        val opacity = opacityPercent.coerceIn(0, 100) / 100f
        val pulse = if (pulseOn) pulseFactor.coerceIn(0f, 1f) else 1f
        return (baseAlpha.coerceIn(0, 255) * opacity * pulse).toInt().coerceIn(0, 255)
    }

    private const val BASE_BEAM_HALF_WIDTH = 0.18
    private const val BASE_BEAM_HEIGHT = 24.0
    private const val BOX_BASE_GROW = 0.05
}
