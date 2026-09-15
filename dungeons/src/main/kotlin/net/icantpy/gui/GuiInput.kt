package net.icantpy.gui

internal enum class GuiPending {
    None,
    OpenConfig,
    OpenItemCustomize,
    Close,
}

internal class GuiOpenQueue {
    private var pending: GuiPending = GuiPending.None

    fun requestToggle(isOurScreen: Boolean) {
        pending = if (isOurScreen) GuiPending.Close else GuiPending.OpenConfig
    }

    fun requestOpen() {
        pending = GuiPending.OpenConfig
    }

    fun requestItemCustomize() {
        pending = GuiPending.OpenItemCustomize
    }

    fun clear() {
        pending = GuiPending.None
    }

    fun poll(): GuiPending {
        val next = pending
        pending = GuiPending.None
        return next
    }
}

internal data class GuiRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun contains(mx: Int, my: Int): Boolean =
        mx >= x && mx < x + w && my >= y && my < y + h
}

internal data class GuiHit(
    val rect: GuiRect,
    val fieldId: String? = null,
    val action: (() -> Unit)? = null,
)

internal fun clampScroll(scroll: Int, contentHeight: Int, viewHeight: Int): Int {
    val max = (contentHeight - viewHeight).coerceAtLeast(0)
    return scroll.coerceIn(0, max)
}

internal fun inContentView(mx: Int, my: Int, x: Int, top: Int, width: Int, bottom: Int): Boolean =
    mx >= x && mx < x + width && my >= top && my < bottom

internal fun wrapLines(text: String, maxWidth: Int, widthOf: (String) -> Int): List<String> {
    if (text.isEmpty() || maxWidth <= 0) return emptyList()
    val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val lines = ArrayList<String>()
    var current = ""
    for (word in words) {
        val next = if (current.isEmpty()) word else "$current $word"
        if (widthOf(next) <= maxWidth || current.isEmpty()) {
            current = next
        } else {
            lines += current
            current = word
        }
    }
    if (current.isNotEmpty()) lines += current
    return lines
}

internal fun skiaGuiScale(framebufferWidth: Int, guiWidth: Int): Float {
    if (guiWidth <= 0) return 1f
    return framebufferWidth.toFloat() / guiWidth.toFloat()
}

internal fun framebufferPointer(
    xpos: Double,
    ypos: Double,
    framebufferWidth: Int,
    framebufferHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
): Pair<Float, Float> {
    val width = screenWidth.coerceAtLeast(1)
    val height = screenHeight.coerceAtLeast(1)
    return (xpos * framebufferWidth / width).toFloat() to
        (ypos * framebufferHeight / height).toFloat()
}
