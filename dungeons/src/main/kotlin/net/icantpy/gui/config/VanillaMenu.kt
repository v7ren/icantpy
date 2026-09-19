package net.icantpy.gui.config

import net.icantpy.gui.GuiHit
import net.icantpy.gui.GuiRect
import net.icantpy.gui.wrapLines
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect

internal enum class ConfigTab(
    private val title: String,
    private val detail: String,
    private val compact: String,
) {
    DUNGEONS("Dungeons", "Timers, leap, alerts", "DUNG"),
    COSMETICS("Cosmetics", "Items and morph", "COSM"),
    QOL("QoL", "Camera, boxes, stats, combat", "QOL"),
    SLAYER("Slayer", "Carry counter", "SLAY"),
    GUI("GUI", "Layout and theme", "GUI"),
    ;

    fun label(): String = title
    fun caption(): String = detail
    fun shortLabel(): String = compact

    fun pages(): List<String> = when (this) {
        DUNGEONS -> DungeonPage.entries.map { it.label() }
        COSMETICS -> CosmeticsPage.entries.map { it.label() }
        QOL -> QolPage.entries.map { it.label() }
        SLAYER -> SlayerPage.entries.map { it.label() }
        GUI -> listOf("Theme")
    }
}

internal enum class DungeonPage(val title: String) {
    TIMERS("Timer"),
    ALERTS("Alerts"),
    LEAP("Leap"),
    ;

    fun label(): String = title
}

internal enum class CosmeticsPage(val title: String) {
    ITEMS("Items"),
    MORPH("Morph"),
    ;

    fun label(): String = title
}

internal enum class QolPage(val title: String) {
    CAMERA("Camera"),
    BOXES("Boxes"),
    STATS("Stats"),
    COMBAT("Combat"),
    SHARDS("Shards"),
    ;

    fun label(): String = title
}

internal enum class SlayerPage(val title: String) {
    CARRIES("Carries"),
    ;

    fun label(): String = title
}

internal enum class ButtonStyle {
    PRIMARY,
    GHOST,
    DANGER,
}

internal class MenuDraw(
    val canvas: Canvas,
    val clipTop: Int,
    val clipBottom: Int,
    val clipLeft: Int,
    val clipRight: Int,
    val mouseX: Int = Int.MIN_VALUE,
    val mouseY: Int = Int.MIN_VALUE,
    val chrome: GuiChrome,
) {
    val hits = mutableListOf<GuiHit>()
    val palette: GuiPalette = chrome.palette
    val radius: Float = chrome.radius
    val titleFont = sized(chrome.typefaces.mono, 16f)
    val headingFont = sized(chrome.typefaces.mono, 11f)
    val bodyFont = sized(chrome.typefaces.semibold, 13f)
    val descFont = sized(chrome.typefaces.regular, 11.5f)
    val buttonFont = sized(chrome.typefaces.mono, 11f)
    val smallFont = sized(chrome.typefaces.regular, 11.5f)
    val microFont = sized(chrome.typefaces.mono, 10.5f)
    private var ignoreClip = false
    private val fill = Paint()
    private val stroke = Paint().apply {
        setStroke(true)
        strokeWidth = 1f
    }

    fun unclipped(block: () -> Unit) {
        val previous = ignoreClip
        ignoreClip = true
        block()
        ignoreClip = previous
    }

    fun visible(y: Int, h: Int): Boolean = ignoreClip || (y + h > clipTop && y < clipBottom)

    fun round(x: Int, y: Int, w: Int, h: Int, corner: Float, color: Int) {
        if (w <= 0 || h <= 0 || !visible(y, h)) return
        fill.color = color
        fill.isAntiAlias = corner > 0.5f
        if (corner <= 0.5f) {
            canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()), fill)
        } else {
            canvas.drawRRect(RRect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), corner), fill)
        }
    }

    fun outline(x: Int, y: Int, w: Int, h: Int, corner: Float, color: Int) {
        if (w <= 0 || h <= 0 || !visible(y, h)) return
        stroke.color = color
        stroke.isAntiAlias = corner > 0.5f
        if (corner <= 0.5f) {
            canvas.drawRect(Rect.makeXYWH(x.toFloat() + 0.5f, y.toFloat() + 0.5f, w - 1f, h - 1f), stroke)
        } else {
            canvas.drawRRect(RRect.makeXYWH(x.toFloat() + 0.5f, y.toFloat() + 0.5f, w - 1f, h - 1f, corner), stroke)
        }
    }

    fun line(x: Int, y: Int, w: Int, color: Int = palette.line) {
        if (w <= 0 || !visible(y - 1, 2)) return
        fill.color = color
        fill.isAntiAlias = false
        canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), 1f), fill)
    }

    fun grid(step: Int, color: Int) {
        if (step <= 0) return
        fill.color = color
        fill.isAntiAlias = false
        var x = 0
        while (x <= clipRight) {
            canvas.drawRect(Rect.makeXYWH(x.toFloat(), clipTop.toFloat(), 1f, (clipBottom - clipTop).toFloat()), fill)
            x += step
        }
        var y = clipTop
        while (y <= clipBottom) {
            canvas.drawRect(Rect.makeXYWH(0f, y.toFloat(), clipRight.toFloat(), 1f), fill)
            y += step
        }
    }

    fun text(value: String, x: Int, y: Int, color: Int, font: Font = bodyFont) {
        if (value.isEmpty() || !visible(y, lineHeight(font))) return
        fill.color = color
        fill.isAntiAlias = true
        canvas.drawString(value, x.toFloat(), y - font.metrics.ascent, font, fill)
    }

    fun heading(label: String, x: Int, y: Int) {
        text(label.uppercase(), x, y, palette.accent, headingFont)
    }

    fun measure(value: String, font: Font = bodyFont): Int = font.measureText(value).width.toInt()

    fun lineHeight(font: Font): Int =
        (-font.metrics.ascent + font.metrics.descent + font.metrics.leading).toInt().coerceAtLeast(12)

    fun wrap(value: String, maxWidth: Int, font: Font = descFont): List<String> =
        wrapLines(value, maxWidth) { measure(it, font) }

    fun hovered(x: Int, y: Int, w: Int, h: Int): Boolean =
        visible(y, h) && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h

    fun clickable(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        if (w <= 0 || h <= 0) return
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun paintConfigChrome(
        m: ConfigMetrics,
        tab: ConfigTab,
        pageIndex: Int,
        onSelect: (ConfigTab) -> Unit,
        onSelectPage: (Int) -> Unit,
        onReload: () -> Unit,
    ) {
        round(0, 0, m.screenW, m.screenH, 0f, palette.scrim)
        unclipped {
            round(m.panelX, m.panelY, m.panelW, m.panelH, radius.coerceAtMost(2f), palette.window)
            outline(m.panelX, m.panelY, m.panelW, m.panelH, radius, palette.line)
            when (chrome.layout) {
                GuiLayoutId.RAIL -> {
                    round(m.panelX, m.panelY, m.sidebar, m.panelH, radius, palette.sidebar)
                    round(m.panelX + m.sidebar - 8, m.panelY, 8, m.panelH, 0f, palette.sidebar)
                    round(m.panelX, m.panelY, m.panelW, m.header, radius, palette.header)
                    round(m.panelX, m.panelY + 12, m.panelW, m.header - 12, 0f, palette.header)
                    round(m.panelX, m.panelY + m.panelH - m.footer, m.panelW, m.footer, radius, palette.header)
                    round(m.panelX, m.panelY + m.panelH - m.footer, m.panelW, 10, 0f, palette.header)
                    line(m.panelX + m.sidebar, m.viewTop, 1, palette.line)
                    brand(m)
                    val pitch = m.railNavPitch()
                    val showCaption = pitch >= 42
                    var navY = m.panelY + m.header + 4
                    ConfigTab.entries.forEach { item ->
                        navRail(
                            item.label(),
                            item.caption(),
                            m.panelX + 8,
                            navY,
                            m.sidebar - 16,
                            pitch - 4,
                            tab == item,
                            showCaption,
                        ) {
                            onSelect(item)
                        }
                        navY += pitch
                    }
                }
                GuiLayoutId.RIBBON -> {
                    round(m.panelX, m.panelY, m.panelW, m.header + m.tabBar, radius, palette.header)
                    round(m.panelX, m.panelY + 12, m.panelW, m.header + m.tabBar - 12, 0f, palette.header)
                    round(m.panelX, m.panelY + m.panelH - m.footer, m.panelW, m.footer, radius, palette.header)
                    round(m.panelX, m.panelY + m.panelH - m.footer, m.panelW, 10, 0f, palette.header)
                    line(m.panelX, m.viewTop, m.panelW, palette.line)
                    brand(m)
                    var tabX = m.panelX + 16
                    val tabY = m.panelY + m.header
                    val fullWidth = ConfigTab.entries.sumOf { measure(it.label(), buttonFont) + 22 }
                    val useShort = fullWidth > m.panelW - 24
                    ConfigTab.entries.forEach { item ->
                        val title = if (useShort) item.shortLabel() else item.label()
                        val w = measure(title, buttonFont) + 18
                        navTab(title, tabX, tabY, w, m.tabBar, tab == item) { onSelect(item) }
                        tabX += w + 4
                    }
                }
                GuiLayoutId.COMPACT -> {
                    round(m.panelX, m.panelY, m.sidebar, m.panelH, radius, palette.sidebar)
                    round(m.panelX + m.sidebar - 6, m.panelY, 6, m.panelH, 0f, palette.sidebar)
                    round(m.panelX + m.sidebar, m.panelY, m.panelW - m.sidebar, m.header, 0f, palette.header)
                    round(m.panelX + m.sidebar, m.panelY + m.panelH - m.footer, m.panelW - m.sidebar, m.footer, 0f, palette.header)
                    line(m.panelX + m.sidebar, m.panelY, 1, palette.line)
                    text("icantpy", m.panelX + m.sidebar + 12, m.panelY + 12, palette.text, titleFont)
                    text(tab.caption(), m.panelX + m.sidebar + 12, m.panelY + 28, palette.secondary, microFont)
                    val pitch = m.compactNavPitch()
                    var navY = m.panelY + 8
                    ConfigTab.entries.forEach { item ->
                        navIcon(item.shortLabel(), m.panelX + 4, navY, m.sidebar - 8, pitch - 4, tab == item) {
                            onSelect(item)
                        }
                        navY += pitch
                    }
                }
            }
            paintPageBar(m, tab, pageIndex, onSelectPage)
            val reloadW = measure("Reload", buttonFont) + 20
            button("Reload", m.panelX + m.panelW - reloadW - 12, m.panelY + 12, ButtonStyle.GHOST, onReload)
        }
    }

    private fun paintPageBar(
        m: ConfigMetrics,
        tab: ConfigTab,
        pageIndex: Int,
        onSelectPage: (Int) -> Unit,
    ) {
        val pages = tab.pages()
        if (pages.isEmpty() || m.pageBar <= 0) return
        val x = m.contentLeft
        val y = m.panelY + m.header + m.tabBar
        val w = m.panelW - m.sidebar
        round(x, y, w, m.pageBar, 0f, palette.header)
        line(x, y + m.pageBar - 1, w, palette.line)
        var tabX = x + 12
        pages.forEachIndexed { index, name ->
            val tabW = measure(name, buttonFont) + 18
            navTab(name, tabX, y, tabW, m.pageBar, index == pageIndex) { onSelectPage(index) }
            tabX += tabW + 4
        }
    }

    fun navRail(
        label: String,
        caption: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        selected: Boolean,
        showCaption: Boolean,
        action: () -> Unit,
    ) {
        val hot = selected || hovered(x, y, w, h)
        if (selected) {
            round(x, y, w, h, chrome.controlRadius, palette.accentSoft)
            round(x, y + 6, 2, h - 12, 0f, palette.accent)
        } else if (hot) {
            round(x, y, w, h, chrome.controlRadius, palette.rowHover)
        }
        val textY = if (showCaption) y + 6 else y + ((h - lineHeight(bodyFont)) / 2).coerceAtLeast(4)
        text(label, x + 12, textY, if (selected) palette.accent else palette.text, bodyFont)
        if (showCaption) {
            text(caption, x + 12, y + 22, palette.tertiary, microFont)
        }
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun navTab(label: String, x: Int, y: Int, w: Int, h: Int, selected: Boolean, action: () -> Unit) {
        val hot = selected || hovered(x, y, w, h)
        if (selected) {
            round(x, y + 4, w, h - 8, chrome.controlRadius, palette.accentSoft)
            round(x + 8, y + h - 3, w - 16, 2, 0f, palette.accent)
        } else if (hot) {
            round(x, y + 4, w, h - 8, chrome.controlRadius, palette.rowHover)
        }
        text(label, x + 9, y + 10, if (selected) palette.accent else palette.text, buttonFont)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun navIcon(label: String, x: Int, y: Int, w: Int, h: Int, selected: Boolean, action: () -> Unit) {
        val hot = selected || hovered(x, y, w, h)
        if (selected) {
            round(x, y, w, h, 0f, palette.accentSoft)
            round(x, y + 6, 2, h - 12, 0f, palette.accent)
        } else if (hot) {
            round(x, y, w, h, 0f, palette.rowHover)
        }
        val tx = x + (w - measure(label, microFont)) / 2
        val ty = y + ((h - lineHeight(microFont)) / 2).coerceAtLeast(4)
        text(label, tx.coerceAtLeast(x + 4), ty, if (selected) palette.accent else palette.text, microFont)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun button(label: String, x: Int, y: Int, style: ButtonStyle, action: () -> Unit): Int {
        val w = measure(label, buttonFont) + 20
        val h = 22
        val hot = hovered(x, y, w, h)
        val corner = chrome.controlRadius.coerceAtMost(12f)
        when (style) {
            ButtonStyle.PRIMARY -> {
                round(x, y, w, h, corner, if (hot) palette.knob else palette.accent)
                text(label, x + 10, y + 4, palette.accentInk, buttonFont)
            }
            ButtonStyle.GHOST -> {
                round(x, y, w, h, corner, if (hot) palette.rowHover else palette.group)
                outline(x, y, w, h, corner, palette.line)
                text(label, x + 10, y + 4, palette.text, buttonFont)
            }
            ButtonStyle.DANGER -> {
                round(x, y, w, h, corner, if (hot) palette.dangerSoft else palette.group)
                outline(x, y, w, h, corner, palette.danger)
                text(label, x + 10, y + 4, palette.danger, buttonFont)
            }
        }
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
        return w
    }

    fun dropdownMenu(
        x: Int,
        anchorY: Int,
        width: Int,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        if (options.isEmpty()) return
        val itemHeight = 22
        val menuHeight = options.size * itemHeight + 4
        val menuY = if (anchorY + menuHeight <= clipBottom) anchorY else anchorY - menuHeight
        unclipped {
            round(x, menuY, width, menuHeight, chrome.controlRadius.coerceAtMost(12f), palette.window)
            outline(x, menuY, width, menuHeight, chrome.controlRadius.coerceAtMost(12f), palette.accent)
            options.forEachIndexed { index, option ->
                val itemY = menuY + 2 + index * itemHeight
                val hot = hovered(x + 2, itemY, width - 4, itemHeight)
                if (hot || index == selected) {
                    round(
                        x + 2,
                        itemY,
                        width - 4,
                        itemHeight,
                        chrome.controlRadius.coerceAtMost(8f),
                        if (index == selected) palette.accentSoft else palette.rowHover,
                    )
                }
                text(
                    fit(option, width - 18, buttonFont),
                    x + 9,
                    itemY + 4,
                    if (index == selected) palette.accent else palette.text,
                    buttonFont,
                )
                hitRect(x + 2, itemY, width - 4, itemHeight)?.let {
                    hits += GuiHit(it, action = { onSelect(index) })
                }
            }
        }
    }

    fun toggle(x: Int, y: Int, on: Boolean, action: () -> Unit) {
        if (chrome.controlRadius < 1f) {
            val size = 18
            val hot = hovered(x, y, size, size)
            outline(x, y, size, size, 0f, when {
                on -> palette.accent
                hot -> palette.secondary
                else -> palette.line
            })
            if (on) round(x + 4, y + 4, 10, 10, 0f, palette.accent)
            if (visible(y, size) && inClipX(x, size)) hitRect(x, y, size, size)?.let { hits += GuiHit(it, action = action) }
            return
        }
        val w = 38
        val h = 20
        val hot = hovered(x, y, w, h)
        val corner = chrome.controlRadius.coerceAtMost(12f)
        round(x, y, w, h, corner, when {
            on -> palette.accent
            hot -> palette.trackOff
            else -> palette.trackOff
        })
        val knob = if (on) x + w - 18 else x + 2
        round(knob, y + 2, 16, 16, corner.coerceAtMost(3f), if (on) palette.accentInk else palette.knob)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun field(id: String, value: String, placeholder: String, x: Int, y: Int, w: Int, focused: Boolean) {
        val h = 22
        val hot = focused || hovered(x, y, w, h)
        val corner = chrome.controlRadius.coerceAtMost(8f)
        round(x, y, w, h, corner, palette.field)
        outline(x, y, w, h, corner, if (focused) palette.accent else if (hot) palette.secondary else palette.line)
        val shown = value.ifEmpty { placeholder }
        val color = when {
            focused -> palette.accent
            value.isEmpty() -> palette.tertiary
            else -> palette.text
        }
        text(fit(shown, w - 14, smallFont), x + 8, y + 4, color, smallFont)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, fieldId = id) }
    }

    fun swatch(x: Int, y: Int, rgb: Int, selected: Boolean, action: () -> Unit) {
        val size = 16
        round(x, y, size, size, chrome.controlRadius.coerceAtMost(6f), rgb or 0xFF000000.toInt())
        outline(x, y, size, size, chrome.controlRadius.coerceAtMost(6f), if (selected) palette.accent else palette.line)
        if (visible(y, size) && inClipX(x, size)) hitRect(x, y, size, size)?.let { hits += GuiHit(it, action = action) }
    }

    fun chip(label: String, x: Int, y: Int, selected: Boolean, action: () -> Unit): Int {
        val w = measure(label, buttonFont) + 16
        val h = 22
        val corner = chrome.controlRadius.coerceAtMost(8f)
        round(x, y, w, h, corner, if (selected) palette.accentSoft else palette.group)
        outline(x, y, w, h, corner, if (selected) palette.accent else palette.line)
        text(label, x + 8, y + 4, if (selected) palette.accent else palette.text, buttonFont)
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
        return w
    }

    fun pickCard(
        title: String,
        caption: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        selected: Boolean,
        action: () -> Unit,
    ) {
        val corner = chrome.cardRadius
        round(x, y, w, h, corner, if (selected) palette.accentSoft else palette.group)
        outline(x, y, w, h, corner, if (selected) palette.accent else palette.line)
        text(title, x + 10, y + 10, if (selected) palette.accent else palette.text, bodyFont)
        var ty = y + 28
        wrap(caption, w - 20).forEach { line ->
            text(line, x + 10, ty, palette.tertiary, microFont)
            ty += lineHeight(microFont)
        }
        if (visible(y, h) && inClipX(x, w)) hitRect(x, y, w, h)?.let { hits += GuiHit(it, action = action) }
    }

    fun scrollbar(x: Int, y: Int, h: Int, contentH: Int, scroll: Int) {
        if (contentH <= h) return
        round(x, y, 3, h, 0f, palette.line)
        val thumbH = (h.toFloat() * h / contentH).toInt().coerceIn(18, h)
        val max = (contentH - h).coerceAtLeast(1)
        val thumbY = y + ((h - thumbH) * scroll / max)
        round(x, thumbY, 3, thumbH, 0f, palette.accent)
    }

    fun fit(value: String, maxWidth: Int, font: Font): String {
        if (measure(value, font) <= maxWidth) return value
        var text = value
        while (text.isNotEmpty() && measure("$text...", font) > maxWidth) {
            text = text.dropLast(1)
        }
        return if (text.isEmpty()) "..." else "$text..."
    }

    private fun brand(m: ConfigMetrics) {
        round(m.panelX + 16, m.panelY + 18, 7, 7, 0f, palette.accent)
        text("icantpy", m.panelX + 30, m.panelY + 14, palette.text, titleFont)
    }

    private fun hitRect(x: Int, y: Int, w: Int, h: Int): GuiRect? {
        if (ignoreClip) return GuiRect(x, y, w, h)
        val x1 = maxOf(x, clipLeft)
        val y1 = maxOf(y, clipTop)
        val x2 = minOf(x + w, clipRight)
        val y2 = minOf(y + h, clipBottom)
        if (x2 <= x1 || y2 <= y1) return null
        return GuiRect(x1, y1, x2 - x1, y2 - y1)
    }

    private fun inClipX(x: Int, w: Int): Boolean = ignoreClip || (x + w > clipLeft && x < clipRight)

    private fun sized(typeface: org.jetbrains.skia.Typeface, size: Float): Font = Font(typeface, size).apply {
        edging = FontEdging.SUBPIXEL_ANTI_ALIAS
        hinting = FontHinting.SLIGHT
        isSubpixel = true
    }
}
