package net.icantpy.hud

/** Everything that decides what a Skia HUD surface looks like for one frame. Compared by equality. */
data class SkiaFrameKey(val width: Int, val height: Int, val guiScale: Float, val content: Any)

/**
 * Remembers the key of the last redraw of a cached Skia surface. A frame may skip redrawing (and just blit the
 * existing texture) only when [needsRedraw] is false, which requires the same key as the last [markDrawn].
 */
class SkiaRedrawGate {
    private var drawnKey: Any? = null
    private var hasDrawn = false

    fun needsRedraw(key: Any): Boolean = !hasDrawn || drawnKey != key

    fun markDrawn(key: Any) {
        drawnKey = key
        hasDrawn = true
    }

    /** Forces the next frame to redraw: call on resize, surface recreate, close, or any unkeyed draw. */
    fun invalidate() {
        drawnKey = null
        hasDrawn = false
    }
}
