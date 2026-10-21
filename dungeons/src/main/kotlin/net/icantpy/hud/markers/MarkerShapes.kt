package net.icantpy.hud.markers

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One primitive of an arrow shape: an axis-aligned box (drawn as a cuboid) or a straight segment (drawn as a line). */
sealed interface MarkerPart {
    data class Box(val minX: Double, val minY: Double, val minZ: Double, val maxX: Double, val maxY: Double, val maxZ: Double) : MarkerPart
    data class Seg(val x1: Double, val y1: Double, val z1: Double, val x2: Double, val y2: Double, val z2: Double) : MarkerPart
}

/**
 * Arrow shapes as geometry, in blocks, around a base point. Pure so it can be tested without Minecraft.
 * Box-only shapes ignore spin; shapes built from segments rotate their segments by [spinRad] around the base's vertical axis.
 */
object MarkerShapes {
    const val CLASSIC = "CLASSIC"
    const val CHEVRON = "CHEVRON"
    const val DIAMOND = "DIAMOND"
    const val PIN = "PIN"
    const val RING = "RING"
    const val POINTER = "POINTER"

    val SHAPES: List<String> = listOf(CLASSIC, CHEVRON, DIAMOND, PIN, RING, POINTER)

    /** Labels for the Shape select, in [SHAPES] order. */
    fun label(shape: String): String = when (shape) {
        CHEVRON -> "Chevron"
        DIAMOND -> "Diamond"
        PIN -> "Pin"
        RING -> "Ring"
        POINTER -> "Pointer"
        else -> "Classic"
    }

    /** Next shape in [SHAPES], wrapping. Unknown values restart at the first shape. */
    fun next(shape: String): String {
        val i = SHAPES.indexOf(shape)
        return if (i < 0) SHAPES.first() else SHAPES[(i + 1) % SHAPES.size]
    }

    /**
     * Geometry of [shape] with its tip/base at ([cx], [baseY], [cz]). [sizePercent] scales everything,
     * [extraScale] scales once more (used for the marker-glow halo), [spinRad] rotates segment shapes.
     */
    fun parts(
        shape: String,
        sizePercent: Int,
        cx: Double,
        baseY: Double,
        cz: Double,
        spinRad: Double = 0.0,
        extraScale: Double = 1.0,
    ): List<MarkerPart> {
        val s = MarkerLook.scale(sizePercent) * extraScale
        return when (shape) {
            CHEVRON -> chevron(cx, baseY, cz, s)
            DIAMOND -> diamond(cx, baseY, cz, s, spinRad)
            PIN -> pin(cx, baseY, cz, s, spinRad)
            RING -> ring(cx, baseY, cz, s, spinRad)
            POINTER -> pointer(cx, baseY, cz, s)
            else -> classic(cx, baseY, cz, s)
        }
    }

    /** Head slabs from the tip upward, then a shaft. */
    private fun classic(cx: Double, y0: Double, cz: Double, s: Double): List<MarkerPart> {
        val out = mutableListOf<MarkerPart>()
        var y = y0
        for (hw in listOf(0.12, 0.45, 0.8).map { it * s }) {
            out += MarkerPart.Box(cx - hw, y, cz - hw, cx + hw, y + 0.35 * s, cz + hw)
            y += 0.35 * s
        }
        val h = 0.12 * s
        out += MarkerPart.Box(cx - h, y, cz - h, cx + h, y + 1.5 * s, cz + h)
        return out
    }

    /** Two downward chevrons stacked, each a narrow-to-wide set of slabs. */
    private fun chevron(cx: Double, y0: Double, cz: Double, s: Double): List<MarkerPart> {
        val out = mutableListOf<MarkerPart>()
        for (layer in 0..1) {
            var y = y0 + layer * 0.9 * s
            for (hw in listOf(0.15, 0.4, 0.7).map { it * s }) {
                out += MarkerPart.Box(cx - hw, y, cz - hw, cx + hw, y + 0.3 * s, cz + hw)
                y += 0.3 * s
            }
        }
        return out
    }

    /** Octahedron-like crystal: top and bottom points joined to a rotating four-point equator. */
    private fun diamond(cx: Double, y0: Double, cz: Double, s: Double, spin: Double): List<MarkerPart> {
        val cy = y0 + 1.2 * s
        val top = Triple(cx, cy + 0.8 * s, cz)
        val bottom = Triple(cx, cy - 0.8 * s, cz)
        val eq = (0 until 4).map { k ->
            val a = spin + k * PI / 2
            Triple(cx + 0.4 * s * cos(a), cy, cz + 0.4 * s * sin(a))
        }
        val out = mutableListOf<MarkerPart>()
        for (k in 0 until 4) {
            val e = eq[k]
            val n = eq[(k + 1) % 4]
            out += seg(top, e)
            out += seg(bottom, e)
            out += seg(e, n)
        }
        return out
    }

    /** Map pin: a thin stem from the tip up to a ball drawn as a horizontal ring and a vertical meridian. */
    private fun pin(cx: Double, y0: Double, cz: Double, s: Double, spin: Double): List<MarkerPart> {
        val out = mutableListOf<MarkerPart>()
        val stemTop = Triple(cx, y0 + 1.3 * s, cz)
        out += seg(Triple(cx, y0, cz), stemTop)
        val ballY = y0 + 1.6 * s
        val r = 0.3 * s
        out += circle(cx, ballY, cz, r, 8, spin, horizontal = true)
        out += circle(cx, ballY, cz, r, 8, spin, horizontal = false)
        return out
    }

    /** Horizontal halo ring above the head, with a smaller inner ring. */
    private fun ring(cx: Double, y0: Double, cz: Double, s: Double, spin: Double): List<MarkerPart> {
        val y = y0 + 1.0 * s
        return circle(cx, y, cz, 0.7 * s, 16, spin, horizontal = true) +
            circle(cx, y, cz, 0.5 * s, 16, spin, horizontal = true)
    }

    /** Thin triangle pointing down: slabs narrowing toward the tip, then a short stem. */
    private fun pointer(cx: Double, y0: Double, cz: Double, s: Double): List<MarkerPart> {
        val out = mutableListOf<MarkerPart>()
        var y = y0
        for (hw in listOf(0.04, 0.1, 0.18, 0.25).map { it * s }) {
            out += MarkerPart.Box(cx - hw, y, cz - hw, cx + hw, y + 0.3 * s, cz + hw)
            y += 0.3 * s
        }
        val h = 0.04 * s
        out += MarkerPart.Box(cx - h, y, cz - h, cx + h, y + 1.0 * s, cz + h)
        return out
    }

    private fun seg(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): MarkerPart.Seg =
        MarkerPart.Seg(a.first, a.second, a.third, b.first, b.second, b.third)

    /** Closed polygon of [n] segments. Horizontal circles lie in the XZ plane; vertical ones in the YZ plane. */
    private fun circle(cx: Double, y: Double, cz: Double, r: Double, n: Int, spin: Double, horizontal: Boolean): List<MarkerPart> {
        val pts = (0 until n).map { k ->
            val a = spin + 2 * PI * k / n
            if (horizontal) Triple(cx + r * cos(a), y, cz + r * sin(a))
            else Triple(cx, y + r * cos(a), cz + r * sin(a))
        }
        return (0 until n).map { k -> seg(pts[k], pts[(k + 1) % n]) }
    }
}
