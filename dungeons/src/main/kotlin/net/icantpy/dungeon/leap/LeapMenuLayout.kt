package net.icantpy.dungeon.leap

import kotlin.math.abs
import kotlin.math.roundToInt

data class LeapMenuBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2
    val centerY: Float get() = top + height / 2

    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom
}

object LeapMenuLayout {
    const val CORNER_WIDTH = 200
    const val CORNER_HEIGHT = 75
    const val CENTER_WIDTH = 180
    const val CENTER_HEIGHT = 64

    fun scale(screenW: Int, screenH: Int, cfg: LeapOrientSettings): Float {
        val requested = cfg.scale.takeIf { it.isFinite() }?.coerceIn(0.1f, 2f) ?: 1f
        val gap = cfg.menuGap.coerceIn(0, 64)
        val fitX = (screenW - 16).coerceAtLeast(1).toFloat() / (2 * CORNER_WIDTH + CENTER_WIDTH + 2 * gap)
        val fitY = (screenH - 48).coerceAtLeast(1).toFloat() / (2 * CORNER_HEIGHT + CENTER_HEIGHT + 2 * gap)
        return minOf(requested, fitX, fitY)
    }

    fun hit(screenW: Int, screenH: Int, x: Int, y: Int, cfg: LeapOrientSettings, centerActive: Boolean): Int? {
        if (x !in 0 until screenW || y !in 0 until screenH) return null
        val boxes = boxes(screenW, screenH, cfg)
        val card = layers(cfg).asReversed().firstOrNull { index ->
            (index != 4 || centerActive) && boxes[index].contains(x, y)
        }
        if (card != null) return card
        if (cfg.boxesOnly || customized(cfg)) return null
        val gap = cfg.menuGap.coerceIn(0, 64) * scale(screenW, screenH, cfg)
        val center = boxes[4]
        if (centerActive && x >= center.left - gap && x < center.right + gap &&
            y >= center.top - gap && y < center.bottom + gap
        ) return null
        if (abs(x - screenW / 2f) < gap / 2 || abs(y - screenH / 2f) < gap / 2) return null
        return LeapOrientSpots.cursorQuadrant(screenW, screenH, x, y)
    }

    fun boxes(screenW: Int, screenH: Int, cfg: LeapOrientSettings): List<LeapMenuBox> {
        val scale = scale(screenW, screenH, cfg)
        val gap = cfg.menuGap.coerceIn(0, 64) * scale
        val middleX = screenW / 2f
        val middleY = screenH / 2f
        val maxW = screenW.toFloat().coerceAtLeast(1f)
        val maxH = screenH.toFloat().coerceAtLeast(1f)
        val minW = LeapMenuEditGeometry.MIN_WIDTH.coerceAtMost(maxW)
        val minH = LeapMenuEditGeometry.MIN_HEIGHT.coerceAtMost(maxH)
        return (0..4).map { index ->
            val defaultW = (if (index == 4) CENTER_WIDTH else CORNER_WIDTH) * scale
            val defaultH = (if (index == 4) CENTER_HEIGHT else CORNER_HEIGHT) * scale
            val savedSize = size(cfg.boxSizes[index.toString()])
            val w = (savedSize?.first ?: defaultW).coerceIn(minW, maxW)
            val h = (savedSize?.second ?: defaultH).coerceIn(minH, maxH)
            val defaultX = if (index == 4) middleX else middleX +
                (if (index % 2 == 0) -1 else 1) * (CENTER_WIDTH * scale / 2 + gap + defaultW / 2)
            val defaultY = if (index == 4) middleY else middleY +
                (if (index < 2) -1 else 1) * (CENTER_HEIGHT * scale / 2 + gap + defaultH / 2)
            val saved = position(cfg.boxPositions[index.toString()])
            val cx = (saved?.first?.times(screenW) ?: defaultX).coerceIn(w / 2, (maxW - w / 2).coerceAtLeast(w / 2))
            val cy = (saved?.second?.times(screenH) ?: defaultY).coerceIn(h / 2, (maxH - h / 2).coerceAtLeast(h / 2))
            LeapMenuBox(cx - w / 2, cy - h / 2, w, h)
        }
    }

    fun write(
        current: LeapOrientSettings,
        boxes: List<LeapMenuBox>,
        screenW: Int,
        screenH: Int,
        indices: Set<Int>,
        includeSizes: Boolean,
    ): LeapOrientSettings {
        if (screenW <= 0 || screenH <= 0) return current
        val positions = current.boxPositions.toMutableMap()
        val sizes = current.boxSizes.toMutableMap()
        indices.forEach { index ->
            val box = boxes.getOrNull(index) ?: return@forEach
            positions[index.toString()] = "${encode(box.centerX / screenW)},${encode(box.centerY / screenH)}"
            if (includeSizes) sizes[index.toString()] = "${encode(box.width)},${encode(box.height)}"
        }
        return current.copy(boxPositions = positions, boxSizes = sizes)
    }

    fun clear(current: LeapOrientSettings, indices: Set<Int>): LeapOrientSettings {
        val keys = indices.map { it.toString() }.toSet()
        return current.copy(
            boxPositions = current.boxPositions.filterKeys { it !in keys },
            boxSizes = current.boxSizes.filterKeys { it !in keys },
        )
    }

    fun layers(cfg: LeapOrientSettings): List<Int> {
        val seen = linkedSetOf<Int>()
        cfg.boxOrder.forEach { token ->
            val index = token.toIntOrNull() ?: return@forEach
            if (index in 0..4) seen += index
        }
        (0..4).forEach { if (it !in seen) seen += it }
        return seen.toList()
    }

    fun bringToFront(cfg: LeapOrientSettings, index: Int): LeapOrientSettings {
        if (index !in 0..4) return cfg
        val next = layers(cfg).filter { it != index } + index
        return cfg.copy(boxOrder = next.map(Int::toString))
    }

    private fun customized(cfg: LeapOrientSettings): Boolean =
        (0..4).any {
            position(cfg.boxPositions[it.toString()]) != null || size(cfg.boxSizes[it.toString()]) != null
        }

    private fun size(raw: String?): Pair<Float, Float>? {
        val parts = raw?.split(',')?.takeIf { it.size == 2 } ?: return null
        val w = parts[0].trim().toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
        val h = parts[1].trim().toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
        return w to h
    }

    private fun position(raw: String?): Pair<Float, Float>? {
        val parts = raw?.split(',')?.takeIf { it.size == 2 } ?: return null
        val x = parts[0].toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..1f } ?: return null
        val y = parts[1].toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..1f } ?: return null
        return x to y
    }

    private fun encode(value: Float): String {
        val rounded = value.roundToInt()
        return if (abs(value - rounded) < 0.001f) rounded.toString() else value.toString()
    }
}
