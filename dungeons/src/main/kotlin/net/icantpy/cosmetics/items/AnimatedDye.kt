package net.icantpy.cosmetics.items

/**
 * Client-side evaluator for custom animated dyes. Keeps icantpy's plain RGB interpolation (no
 * OkLab dependency) and a fixed playback model so the editor and renderer always agree.
 *
 * @param keyframes colour stops sorted by [ItemCustomizeKeyframe.time], first at `0`, last at `1`.
 * @param cycleBack whether the animation reverses instead of looping.
 * @param delay seconds to hold at the start colour before animating.
 * @param duration seconds for one full forward sweep.
 */
object AnimatedDyeEvaluator {
    fun colorAt(
        keyframes: List<ItemCustomizeKeyframe>,
        cycleBack: Boolean,
        delay: Float,
        duration: Float,
        elapsedSeconds: Float,
    ): Int {
        if (keyframes.isEmpty()) return 0xFF0000
        if (keyframes.size == 1) return keyframes[0].color
        val span = duration.coerceAtLeast(0.05f)
        val cycle = if (cycleBack) 2f * span else span
        val raw = (elapsedSeconds - delay.coerceAtLeast(0f)) % cycle
        val progress = when {
            cycleBack -> {
                val half = span
                if (raw < half) raw / half else 2f - raw / half
            }
            else -> raw / cycle
        }.coerceIn(0f, 1f)

        var index = 0
        while (index < keyframes.size - 2 && keyframes[index + 1].time < progress) index++
        val current = keyframes[index]
        val next = keyframes[index + 1]
        val delta = next.time - current.time
        val local = if (delta <= 0f) 0f else ((progress - current.time) / delta).coerceIn(0f, 1f)
        return lerpRgb(current.color, next.color, local)
    }

    /** Linear interpolation in sRGB, opaque. */
    fun lerpRgb(from: Int, to: Int, amount: Float): Int {
        val r = ((from shr 16) and 0xFF) + ((((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * amount).toInt()
        val g = ((from shr 8) and 0xFF) + ((((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * amount).toInt()
        val b = (from and 0xFF) + (((to and 0xFF) - (from and 0xFF)) * amount).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    /** Builds a simple red-to-blue two-stop animation, matching Skyblocker's default. */
    fun defaultKeyframes(): List<ItemCustomizeKeyframe> = listOf(
        ItemCustomizeKeyframe(0xFFAA0000.toInt(), 0f),
        ItemCustomizeKeyframe(0xFF0000AA.toInt(), 1f),
    )
}
