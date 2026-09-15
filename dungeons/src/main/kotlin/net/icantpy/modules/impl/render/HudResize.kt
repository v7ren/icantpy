package net.icantpy.modules.impl.render

import net.icantpy.gui.configUI.HudOverlaySettings
import kotlin.math.roundToInt

enum class HudHandle {
    NW,
    N,
    NE,
    E,
    SE,
    S,
    SW,
    W,
    MOVE,
}

data class HudBox(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    fun contains(mx: Int, my: Int): Boolean = mx >= x && mx < x + w && my >= y && my < y + h
}

data class HudDrag(
    val handle: HudHandle,
    val startX: Int,
    val startY: Int,
    val startW: Int,
    val startH: Int,
    val scale: Int,
    val width: Int,
)

data class HudResizeResult(
    val x: Int,
    val y: Int,
    val scale: Int,
    val width: Int,
)

object HudResize {
    const val HANDLE: Int = 8

    fun handleRects(box: HudBox, size: Int = HANDLE): Map<HudHandle, HudBox> {
        val half = size / 2
        val midX = box.x + box.w / 2 - half
        val midY = box.y + box.h / 2 - half
        return mapOf(
            HudHandle.NW to HudBox(box.x - half, box.y - half, size, size),
            HudHandle.N to HudBox(midX, box.y - half, size, size),
            HudHandle.NE to HudBox(box.x + box.w - half, box.y - half, size, size),
            HudHandle.E to HudBox(box.x + box.w - half, midY, size, size),
            HudHandle.SE to HudBox(box.x + box.w - half, box.y + box.h - half, size, size),
            HudHandle.S to HudBox(midX, box.y + box.h - half, size, size),
            HudHandle.SW to HudBox(box.x - half, box.y + box.h - half, size, size),
            HudHandle.W to HudBox(box.x - half, midY, size, size),
        )
    }

    fun hit(mx: Int, my: Int, box: HudBox, body: Boolean): HudHandle? {
        handleRects(box, HANDLE).entries.firstOrNull { it.value.contains(mx, my) }?.let { return it.key }
        if (body && box.contains(mx, my)) return HudHandle.MOVE
        return null
    }

    fun apply(drag: HudDrag, mx: Int, my: Int, notify: Boolean): HudResizeResult {
        val startH = drag.startH.coerceAtLeast(1)
        val startW = drag.startW.coerceAtLeast(1)
        var scale = drag.scale
        var width = drag.width
        when (drag.handle) {
            HudHandle.MOVE -> {
                return HudResizeResult(mx, my, scale, width)
            }
            HudHandle.E, HudHandle.W -> {
                if (notify) {
                    width = horizontalWidth(drag, mx)
                } else {
                    scale = scaleFromWidth(drag, mx)
                }
            }
            HudHandle.N, HudHandle.S -> {
                scale = scaleFromHeight(drag, my, startH)
            }
            HudHandle.NE, HudHandle.SE, HudHandle.NW, HudHandle.SW -> {
                scale = scaleFromHeight(drag, my, startH)
                if (notify) width = horizontalWidth(drag, mx)
            }
        }
        scale = scale.coerceIn(HudOverlaySettings.MIN_SCALE, HudOverlaySettings.MAX_SCALE)
        width = width.coerceIn(HudOverlaySettings.MIN_WIDTH, HudOverlaySettings.MAX_WIDTH)
        val ratio = scale / drag.scale.toFloat().coerceAtLeast(1f)
        val newW = if (notify) width else (startW * ratio).roundToInt().coerceAtLeast(8)
        val newH = (startH * ratio).roundToInt().coerceAtLeast(8)
        val x = when (drag.handle) {
            HudHandle.NW, HudHandle.W, HudHandle.SW -> drag.startX + startW - newW
            else -> drag.startX
        }
        val y = when (drag.handle) {
            HudHandle.NW, HudHandle.N, HudHandle.NE -> drag.startY + startH - newH
            else -> drag.startY
        }
        return HudResizeResult(x, y, scale, width)
    }

    private fun horizontalWidth(drag: HudDrag, mx: Int): Int {
        val raw = when (drag.handle) {
            HudHandle.W, HudHandle.NW, HudHandle.SW -> drag.startX + drag.startW - mx
            else -> mx - drag.startX
        }
        return raw.coerceIn(HudOverlaySettings.MIN_WIDTH, HudOverlaySettings.MAX_WIDTH)
    }

    private fun scaleFromHeight(drag: HudDrag, my: Int, startH: Int): Int {
        val newH = when (drag.handle) {
            HudHandle.N, HudHandle.NW, HudHandle.NE -> drag.startY + startH - my
            else -> my - drag.startY
        }.coerceAtLeast(8)
        return (drag.scale * newH / startH.toFloat()).roundToInt()
    }

    private fun scaleFromWidth(drag: HudDrag, mx: Int): Int {
        val newW = when (drag.handle) {
            HudHandle.W, HudHandle.NW, HudHandle.SW -> drag.startX + drag.startW - mx
            else -> mx - drag.startX
        }.coerceAtLeast(8)
        return (drag.scale * newW / drag.startW.coerceAtLeast(1).toFloat()).roundToInt()
    }
}
