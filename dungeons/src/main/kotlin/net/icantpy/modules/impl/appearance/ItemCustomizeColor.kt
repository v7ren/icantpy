package net.icantpy.modules.impl.appearance

/**
 * Immutable appearance colour used by the item customizer.
 *
 * [rgb] is packed `0xRRGGBB`, [alpha] and [chromaSpeed] are `0..255`.
 * A [chromaSpeed] of `0` disables chroma animation, matching NEU's encoding where the
 * speed is the first of five decimal fields (`speed:alpha:red:green:blue`).
 */
data class ItemCustomizeColor(
    val rgb: Int,
    val alpha: Int = MAX_CHANNEL,
    val chromaSpeed: Int = 0,
) {
    init {
        require(rgb in 0..0xFFFFFF) { "rgb out of range: $rgb" }
        require(alpha in 0..MAX_CHANNEL) { "alpha out of range: $alpha" }
        require(chromaSpeed in 0..MAX_CHANNEL) { "chromaSpeed out of range: $chromaSpeed" }
    }

    /** Resolves this colour to ARGB using [elapsedSeconds] of animation. */
    fun argbAt(elapsedSeconds: Float): Int =
        ItemCustomizeColorEvaluator.argbAt(this, elapsedSeconds)

    /** Opaque ARGB (alpha forced to 255); used where vanilla expects `ARGB.opaque`. */
    val opaqueArgb: Int get() = 0xFF000000.toInt() or rgb

    val red: Int get() = (rgb shr 16) and 0xFF
    val green: Int get() = (rgb shr 8) and 0xFF
    val blue: Int get() = rgb and 0xFF

    companion object {
        const val MAX_CHANNEL: Int = 255

        /** NEU's default glint colour: `0:204:100:25:255`, i.e. `#6419FF` at alpha 204. */
        val DEFAULT_GLINT: ItemCustomizeColor = ItemCustomizeColor(0x6419FF, alpha = 204, chromaSpeed = 0)

        /** The encoded form NEU writes for its default glint colour. */
        const val DEFAULT_GLINT_ENCODED: String = "0:204:100:25:255"
    }
}

/**
 * NEU-compatible colour evaluation. Chroma cycles hue while keeping saturation,
 * brightness and alpha. NEU's period is `(255 - speed) / 254 * 59 + 1` seconds.
 */
object ItemCustomizeColorEvaluator {
    private const val MIN_CHROMA_SECONDS: Float = 1f
    private const val MAX_CHROMA_SECONDS: Float = 60f

    fun secondsForSpeed(speed: Int): Float {
        val clamped = speed.coerceIn(0, ItemCustomizeColor.MAX_CHANNEL)
        return (ItemCustomizeColor.MAX_CHANNEL - clamped) / 254f *
            (MAX_CHROMA_SECONDS - MIN_CHROMA_SECONDS) + MIN_CHROMA_SECONDS
    }

    fun argbAt(color: ItemCustomizeColor, elapsedSeconds: Float): Int {
        if (color.chromaSpeed == 0) {
            return (color.alpha shl 24) or color.rgb
        }
        val hsb = rgbToHsb(color.rgb)
        var hue = hsb[0] + elapsedSeconds / secondsForSpeed(color.chromaSpeed)
        hue %= 1f
        if (hue < 0f) hue += 1f
        val rotated = hsbToRgb(hue, hsb[1], hsb[2])
        return (color.alpha shl 24) or (rotated and 0xFFFFFF)
    }

    /** Mirrors `java.awt.Color.RGBtoHSB` so chroma rotates from the same starting hue as NEU. */
    fun rgbToHsb(rgb: Int): FloatArray {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        var cmax = if (r > g) r else g
        if (b > cmax) cmax = b
        var cmin = if (r < g) r else g
        if (b < cmin) cmin = b
        val brightness = cmax / 255f
        val saturation = if (cmax != 0) (cmax - cmin).toFloat() / cmax.toFloat() else 0f
        val hue = if (saturation == 0f) {
            0f
        } else {
            val redc = (cmax - r).toFloat() / (cmax - cmin).toFloat()
            val greenc = (cmax - g).toFloat() / (cmax - cmin).toFloat()
            val bluec = (cmax - b).toFloat() / (cmax - cmin).toFloat()
            var raw = when (cmax) {
                r -> bluec - greenc
                g -> 2f + redc - bluec
                else -> 4f + greenc - redc
            }
            raw /= 6f
            if (raw < 0f) raw + 1f else raw
        }
        return floatArrayOf(hue, saturation, brightness)
    }

    /** Mirrors `java.awt.Color.HSBtoRGB` to stay visually identical to NEU. */
    fun hsbToRgb(hue: Float, saturation: Float, brightness: Float): Int {
        if (saturation == 0f) {
            val channel = (brightness * 255f + 0.5f).toInt()
            return (channel shl 16) or (channel shl 8) or channel
        }
        val h = (hue - kotlin.math.floor(hue)) * 6f
        val f = h - kotlin.math.floor(h)
        val p = brightness * (1f - saturation)
        val q = brightness * (1f - saturation * f)
        val t = brightness * (1f - saturation * (1f - f))
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple((brightness * 255f + 0.5f).toInt(), (t * 255f + 0.5f).toInt(), (p * 255f + 0.5f).toInt())
            1 -> Triple((q * 255f + 0.5f).toInt(), (brightness * 255f + 0.5f).toInt(), (p * 255f + 0.5f).toInt())
            2 -> Triple((p * 255f + 0.5f).toInt(), (brightness * 255f + 0.5f).toInt(), (t * 255f + 0.5f).toInt())
            3 -> Triple((p * 255f + 0.5f).toInt(), (q * 255f + 0.5f).toInt(), (brightness * 255f + 0.5f).toInt())
            4 -> Triple((t * 255f + 0.5f).toInt(), (p * 255f + 0.5f).toInt(), (brightness * 255f + 0.5f).toInt())
            else -> Triple((brightness * 255f + 0.5f).toInt(), (p * 255f + 0.5f).toInt(), (q * 255f + 0.5f).toInt())
        }
        return (r shl 16) or (g shl 8) or b
    }
}

/**
 * Codec for the stored/NEU colour representation. Reads the historical icantpy hex form and
 * NEU's five-field decimal form; always writes NEU's decimal form as the canonical format.
 */
object ItemCustomizeColorCodec {
    private val NEU = Regex("^\\d{1,3}:\\d{1,3}:\\d{1,3}:\\d{1,3}:\\d{1,3}$")
    private val HEX = Regex("^#?[0-9a-fA-F]{6}$")

    fun parse(raw: String?): Result<ItemCustomizeColor> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Result.failure(IllegalArgumentException("empty colour"))
        NEU.matchEntire(text)?.let { match ->
            val parts = match.value.split(':').map { it.toInt() }
            val speed = parts[0]
            val alpha = parts[1]
            val r = parts[2]
            val g = parts[3]
            val b = parts[4]
            if (parts.any { it !in 0..ItemCustomizeColor.MAX_CHANNEL }) {
                return Result.failure(IllegalArgumentException("channel out of range: '$raw'"))
            }
            return Result.success(ItemCustomizeColor((r shl 16) or (g shl 8) or b, alpha, speed))
        }
        HEX.matchEntire(text)?.let { match ->
            val rgb = match.value.removePrefix("#").toInt(16)
            return Result.success(ItemCustomizeColor(rgb))
        }
        return Result.failure(IllegalArgumentException("unrecognized colour: '$raw'"))
    }

    fun parseOrNull(raw: String?): ItemCustomizeColor? = parse(raw).getOrNull()

    fun format(color: ItemCustomizeColor): String =
        "${color.chromaSpeed}:${color.alpha}:${color.red}:${color.green}:${color.blue}"
}
