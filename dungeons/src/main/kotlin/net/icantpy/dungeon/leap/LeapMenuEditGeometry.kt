package net.icantpy.dungeon.leap

import net.icantpy.hud.HudHandle
import kotlin.math.abs

/** Pure editor transforms; dimensions and viewport are in the same rendered pixel units. */
object LeapMenuEditGeometry {
    const val MIN_WIDTH = 48f
    const val MIN_HEIGHT = 24f

    fun bounds(boxes: List<LeapMenuBox>, selected: Set<Int>): LeapMenuBox? {
        val cards = selection(boxes, selected).map { boxes[it] }
        if (cards.isEmpty() || cards.any { !valid(it) }) return null
        val left = cards.minOf { it.left }
        val top = cards.minOf { it.top }
        return LeapMenuBox(left, top, cards.maxOf { it.right } - left, cards.maxOf { it.bottom } - top)
    }

    fun move(
        boxes: List<LeapMenuBox>, selected: Set<Int>, dx: Float, dy: Float,
        screenW: Int, screenH: Int
    ): List<LeapMenuBox> {
        if (!dx.isFinite() || !dy.isFinite() || screenW <= 0 || screenH <= 0) return boxes
        val group = bounds(boxes, selected) ?: return boxes
        val moveX = boundedDelta(dx, -group.left, screenW - group.right)
        val moveY = boundedDelta(dy, -group.top, screenH - group.bottom)
        val indices = selection(boxes, selected)
        return boxes.mapIndexed { index, box ->
            if (index in indices) box.copy(left = box.left + moveX, top = box.top + moveY) else box
        }
    }

    /**
     * Scale selected cards from the dragged handle: the opposite edge stays put. A proportional
     * drag uses whichever axis moved furthest from scale 1, then applies one factor to both axes.
     * MOVE (the default) still scales around the group's center. The viewport takes precedence
     * when it is too small to allow the minimum card dimensions.
     */
    fun resize(
        boxes: List<LeapMenuBox>, selected: Set<Int>, scaleX: Float, scaleY: Float,
        screenW: Int, screenH: Int, proportional: Boolean = false,
        handle: HudHandle = HudHandle.MOVE
    ): List<LeapMenuBox> {
        if (!scaleX.isFinite() || !scaleY.isFinite() || screenW <= 0 || screenH <= 0) return boxes
        val group = bounds(boxes, selected) ?: return boxes
        val indices = selection(boxes, selected)
        val minX = indices.maxOf { MIN_WIDTH / boxes[it].width }
        val minY = indices.maxOf { MIN_HEIGHT / boxes[it].height }
        val maxX = screenW / group.width
        val maxY = screenH / group.height
        val scales = if (proportional) {
            val requested = if (abs(scaleX - 1) >= abs(scaleY - 1)) scaleX else scaleY
            val maximum = minOf(maxX, maxY)
            val factor = requested.coerceIn(maxOf(minX, minY).coerceAtMost(maximum), maximum)
            factor to factor
        } else {
            scaleX.coerceIn(minX.coerceAtMost(maxX), maxX) to
                scaleY.coerceIn(minY.coerceAtMost(maxY), maxY)
        }
        return scaleGroup(boxes, indices, group, scales.first, scales.second, screenW, screenH, handle)
    }

    fun setSize(
        boxes: List<LeapMenuBox>,
        selected: Set<Int>,
        width: Float?,
        height: Float?,
        screenW: Int,
        screenH: Int,
    ): List<LeapMenuBox> {
        if (screenW <= 0 || screenH <= 0) return boxes
        if (width != null && (!width.isFinite() || width <= 0f)) return boxes
        if (height != null && (!height.isFinite() || height <= 0f)) return boxes
        val indices = selection(boxes, selected)
        if (indices.isEmpty()) return boxes
        val maxW = screenW.toFloat()
        val maxH = screenH.toFloat()
        val minW = MIN_WIDTH.coerceAtMost(maxW)
        val minH = MIN_HEIGHT.coerceAtMost(maxH)
        return boxes.mapIndexed { index, box ->
            if (index !in indices) return@mapIndexed box
            val nextW = (width ?: box.width).coerceIn(minW, maxW)
            val nextH = (height ?: box.height).coerceIn(minH, maxH)
            val left = box.left.coerceIn(0f, (screenW - nextW).coerceAtLeast(0f))
            val top = box.top.coerceIn(0f, (screenH - nextH).coerceAtLeast(0f))
            LeapMenuBox(left, top, nextW, nextH)
        }
    }

    fun scaleFromHandle(bounds: LeapMenuBox, handle: HudHandle, mx: Float, my: Float): Pair<Float, Float> {
        if (!valid(bounds) || handle == HudHandle.MOVE) return 1f to 1f
        val width = when (handle) {
            HudHandle.W, HudHandle.NW, HudHandle.SW -> bounds.right - mx
            HudHandle.E, HudHandle.NE, HudHandle.SE -> mx - bounds.left
            else -> bounds.width
        }
        val height = when (handle) {
            HudHandle.N, HudHandle.NW, HudHandle.NE -> bounds.bottom - my
            HudHandle.S, HudHandle.SW, HudHandle.SE -> my - bounds.top
            else -> bounds.height
        }
        val scaleX = if (bounds.width > 0f) width / bounds.width else 1f
        val scaleY = if (bounds.height > 0f) height / bounds.height else 1f
        return (if (scaleX.isFinite()) scaleX else 1f) to (if (scaleY.isFinite()) scaleY else 1f)
    }

    fun applyDrag(
        boxes: List<LeapMenuBox>,
        selected: Set<Int>,
        handle: HudHandle,
        startBounds: LeapMenuBox,
        grabX: Float,
        grabY: Float,
        mx: Float,
        my: Float,
        screenW: Int,
        screenH: Int,
    ): List<LeapMenuBox> {
        if (handle == HudHandle.MOVE) {
            return move(boxes, selected, mx - grabX - startBounds.left, my - grabY - startBounds.top, screenW, screenH)
        }
        val scales = scaleFromHandle(startBounds, handle, mx, my)
        val corner = handle == HudHandle.NW || handle == HudHandle.NE || handle == HudHandle.SW || handle == HudHandle.SE
        return resize(boxes, selected, scales.first, scales.second, screenW, screenH, corner, handle)
    }

    fun sharedDimension(boxes: List<LeapMenuBox>, selected: Set<Int>, width: Boolean): Float? {
        val indices = selection(boxes, selected)
        if (indices.isEmpty()) return null
        val values = indices.map { if (width) boxes[it].width else boxes[it].height }
        val first = values.first()
        return first.takeIf { values.all { abs(it - first) < 0.5f } }
    }

    private fun scaleGroup(
        boxes: List<LeapMenuBox>, indices: Set<Int>, group: LeapMenuBox,
        scaleX: Float, scaleY: Float, screenW: Int, screenH: Int, handle: HudHandle
    ): List<LeapMenuBox> {
        var width = group.width * scaleX
        var height = group.height * scaleY
        var left: Float
        var top: Float
        when (handle) {
            HudHandle.E, HudHandle.NE, HudHandle.SE -> {
                left = group.left
                width = width.coerceAtMost((screenW - group.left).coerceAtLeast(0f))
            }
            HudHandle.W, HudHandle.NW, HudHandle.SW -> {
                left = group.right - width
                if (left < 0f) {
                    width = group.right.coerceAtLeast(0f)
                    left = 0f
                }
            }
            else -> left = (group.centerX - width / 2).coerceIn(0f, (screenW - width).coerceAtLeast(0f))
        }
        when (handle) {
            HudHandle.S, HudHandle.SE, HudHandle.SW -> {
                top = group.top
                height = height.coerceAtMost((screenH - group.top).coerceAtLeast(0f))
            }
            HudHandle.N, HudHandle.NE, HudHandle.NW -> {
                top = group.bottom - height
                if (top < 0f) {
                    height = group.bottom.coerceAtLeast(0f)
                    top = 0f
                }
            }
            else -> top = (group.centerY - height / 2).coerceIn(0f, (screenH - height).coerceAtLeast(0f))
        }
        val nextX = if (group.width > 0f) width / group.width else 1f
        val nextY = if (group.height > 0f) height / group.height else 1f
        return boxes.mapIndexed { index, box ->
            if (index !in indices) box else LeapMenuBox(
                left + (box.left - group.left) * nextX,
                top + (box.top - group.top) * nextY,
                box.width * nextX, box.height * nextY
            )
        }
    }

    private fun selection(boxes: List<LeapMenuBox>, selected: Set<Int>): Set<Int> =
        selected.filterTo(linkedSetOf()) { it in 0..4 && it in boxes.indices }

    private fun valid(box: LeapMenuBox): Boolean =
        box.left.isFinite() && box.top.isFinite() && box.right.isFinite() && box.bottom.isFinite() &&
            box.width.isFinite() && box.height.isFinite() && box.width > 0 && box.height > 0

    private fun boundedDelta(requested: Float, minimum: Float, maximum: Float): Float =
        if (minimum <= maximum) requested.coerceIn(minimum, maximum) else 0f
}

data class LeapMenuLayoutSnapshot(
    val boxPositions: Map<String, String>,
    val boxSizes: Map<String, String>,
    val boxOrder: List<String>,
) {
    fun applyTo(current: LeapOrientSettings): LeapOrientSettings =
        current.copy(boxPositions = boxPositions, boxSizes = boxSizes, boxOrder = boxOrder)

    companion object {
        fun of(settings: LeapOrientSettings): LeapMenuLayoutSnapshot =
            LeapMenuLayoutSnapshot(settings.boxPositions, settings.boxSizes, settings.boxOrder)
    }
}

class LeapMenuEditHistory {
    private val undo = ArrayDeque<LeapMenuLayoutSnapshot>()
    private val redo = ArrayDeque<LeapMenuLayoutSnapshot>()

    fun record(current: LeapOrientSettings) {
        undo.addLast(LeapMenuLayoutSnapshot.of(current))
        while (undo.size > 64) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: LeapOrientSettings): LeapOrientSettings? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(LeapMenuLayoutSnapshot.of(current))
        return previous.applyTo(current)
    }

    fun redo(current: LeapOrientSettings): LeapOrientSettings? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(LeapMenuLayoutSnapshot.of(current))
        return next.applyTo(current)
    }

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
}
