package net.icantpy.gui.config

import org.jetbrains.skia.Rect

/** Sidebar geometry in pixels. A row is 22px tall with a 2px gap between rows. */
internal const val SIDEBAR_ROW_HEIGHT = 22
internal const val SIDEBAR_ROW_PITCH = 24
private const val SIDEBAR_HEADING_HEIGHT = 20
private const val SIDEBAR_GROUP_GAP = 8
private const val SIDEBAR_TOP = 44
private const val SIDEBAR_BOTTOM_PAD = 8
private const val SIDEBAR_TEXT_INSET = 12

internal const val SEARCH_FIELD_ID = "nav:search"
private const val SEARCH_MIN_WIDTH = 140
private const val SEARCH_MAX_WIDTH = 200
private const val SEARCH_FIELD_HEIGHT = 28
private const val SEARCH_RESULT_HEIGHT = 26
private const val SEARCH_MAX_RESULTS = 8
private const val SEARCH_MENU_MIN_WIDTH = 260

/** Total height of the sidebar list, used to clamp its own scroll position. */
internal fun sidebarContentHeight(): Int = ConfigNav.sidebarGroups().sumOf { (section, pages) ->
    val heading = if (section == ConfigSection.HOME) 0 else SIDEBAR_HEADING_HEIGHT
    heading + pages.size * SIDEBAR_ROW_PITCH + SIDEBAR_GROUP_GAP
}

internal fun sidebarTop(m: ConfigMetrics): Int = m.panelY + SIDEBAR_TOP

internal fun sidebarBottom(m: ConfigMetrics): Int = m.panelY + m.panelH - SIDEBAR_BOTTOM_PAD

/** Paints the panel, sidebar and header background behind everything else. */
internal fun MenuDraw.paintShellFrame(m: ConfigMetrics) {
    round(0, 0, m.screenW, m.screenH, 0f, palette.scrim)
    ambientGlow(m)
    unclipped {
        round(m.panelX, m.panelY, m.panelW, m.panelH, radius, palette.window)
        outline(m.panelX, m.panelY, m.panelW, m.panelH, radius, 0x2AFFFFFF)
        round(m.panelX, m.panelY, m.sidebar, m.panelH, radius, palette.sidebar)
        round(m.panelX + m.sidebar - 8, m.panelY, 8, m.panelH, 0f, palette.sidebar)
        line(m.panelX + m.sidebar, m.panelY, 1, palette.line)
        aetherBrand(m)
        round(m.contentLeft, m.panelY, m.panelW - m.sidebar, m.header, 0f, palette.header)
    }
}

/**
 * Grouped page list, clipped to its own area and scrolled by [scroll]. Section headings are
 * skipped for HOME so the overview row sits alone at the top.
 */
internal fun MenuDraw.paintSidebar(m: ConfigMetrics, selected: ConfigPage, scroll: Int, onSelect: (ConfigPage) -> Unit) {
    val top = sidebarTop(m)
    val bottom = sidebarBottom(m)
    val left = m.panelX + 8
    val width = m.sidebar - 16
    clippedTo(m.panelX, top, m.contentLeft, bottom) {
        var y = top - scroll
        ConfigNav.sidebarGroups().forEach { (section, pages) ->
            if (section != ConfigSection.HOME) {
                text(section.label.uppercase(), left + 4, y + 4, palette.tertiary, headingFont)
                y += SIDEBAR_HEADING_HEIGHT
            }
            pages.forEach { page ->
                sidebarRow(page.title, left, y, width, page == selected) { onSelect(page) }
                y += SIDEBAR_ROW_PITCH
            }
            y += SIDEBAR_GROUP_GAP
        }
    }
    val viewport = bottom - top
    if (sidebarContentHeight() > viewport && scroll < sidebarContentHeight() - viewport) {
        fadeBottom(m.panelX, bottom, m.sidebar, palette.sidebar)
    }
}

private fun MenuDraw.sidebarRow(label: String, x: Int, y: Int, w: Int, selected: Boolean, action: () -> Unit) {
    val h = SIDEBAR_ROW_HEIGHT
    val corner = chrome.controlRadius.coerceAtMost(8f)
    if (selected) {
        round(x, y, w, h, corner, palette.accentSoft)
        round(x, y + 4, 2, h - 8, 0f, palette.activeAccent)
    } else if (hovered(x, y, w, h)) {
        round(x, y, w, h, corner, palette.rowHover)
    }
    val textY = y + (h - lineHeight(bodyFont)) / 2
    val color = if (selected) palette.text else palette.secondary
    text(fit(label, w - SIDEBAR_TEXT_INSET * 2, bodyFont), x + SIDEBAR_TEXT_INSET, textY, color, bodyFont)
    clickable(x, y, w, h, action)
}

/** Enable toggle for a page's primary feature, shown in the header. */
internal data class HeaderToggle(val on: Boolean, val action: () -> Unit)

/** Title and caption on the left, optional feature toggle, and the page search on the right. */
internal fun MenuDraw.paintHeader(
    m: ConfigMetrics,
    page: ConfigPage,
    toggle: HeaderToggle?,
    searchText: String,
    searchFocused: Boolean,
) {
    val mainX = m.contentLeft
    val mainW = m.panelW - m.sidebar
    line(mainX, m.panelY + m.header - 1, mainW, palette.line)
    val titleY = m.panelY + 7
    text(fit(page.title, mainW / 2, displayFont), mainX + 16, titleY, palette.text, displayFont)
    if (toggle != null) {
        val titleW = measure(page.title, displayFont)
        val labelX = mainX + 16 + titleW + 14
        text("Enabled", labelX, titleY + 3, palette.secondary, buttonFont)
        val toggleX = labelX + measure("Enabled", buttonFont) + 8
        toggle(toggleX, titleY + 2, toggle.on, toggle.action)
    }
    text(fit(page.caption, mainW - 32, captionFont), mainX + 16, m.panelY + m.header - 17, palette.tertiary, captionFont)
    val searchW = searchWidth(mainW)
    val searchX = mainX + mainW - searchW - 14
    val searchY = m.panelY + (m.header - SEARCH_FIELD_HEIGHT) / 2
    field(SEARCH_FIELD_ID, searchText, "Search features…", searchX, searchY, searchW, searchFocused, SEARCH_FIELD_HEIGHT)
}

/** Subtab bar under the header. Only called for pages that define subtabs. */
internal fun MenuDraw.paintSubtabs(m: ConfigMetrics, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val y = m.panelY + m.header
    val x = m.contentLeft
    val w = m.panelW - m.sidebar
    round(x, y, w, m.pageBar, 0f, palette.header)
    line(x, y + m.pageBar - 1, w, palette.line)
    var tabX = x + 12
    labels.forEachIndexed { index, label ->
        val tabW = measure(label, buttonFont) + TAB_PADDING * 2
        navTab(label, tabX, y, tabW, m.pageBar, index == selected) { onSelect(index) }
        tabX += tabW + 4
    }
}

/**
 * Dropdown of matching pages under the search field. Paint it unclipped after the content so it
 * sits above every card; its hits are added last and therefore win clicks.
 */
internal fun MenuDraw.paintSearchResults(m: ConfigMetrics, results: List<ConfigPage>, onOpen: (ConfigPage) -> Unit) {
    val mainW = m.panelW - m.sidebar
    val right = m.contentLeft + mainW - 14
    val width = maxOf(searchWidth(mainW), SEARCH_MENU_MIN_WIDTH).coerceAtMost(m.panelW - 24)
    val x = right - width
    val y = m.panelY + (m.header - SEARCH_FIELD_HEIGHT) / 2 + SEARCH_FIELD_HEIGHT + 4
    val shown = results.take(SEARCH_MAX_RESULTS)
    val h = (if (shown.isEmpty()) SEARCH_RESULT_HEIGHT else shown.size * SEARCH_RESULT_HEIGHT) + 8
    val corner = chrome.controlRadius.coerceAtMost(10f)
    round(x, y, width, h, corner, palette.window)
    outline(x, y, width, h, corner, palette.accent)
    if (shown.isEmpty()) {
        val textY = y + 4 + (SEARCH_RESULT_HEIGHT - lineHeight(bodyFont)) / 2
        text("No matching pages", x + 12, textY, palette.tertiary, bodyFont)
        return
    }
    shown.forEachIndexed { index, page ->
        val rowY = y + 4 + index * SEARCH_RESULT_HEIGHT
        if (hovered(x + 4, rowY, width - 8, SEARCH_RESULT_HEIGHT)) {
            round(x + 4, rowY, width - 8, SEARCH_RESULT_HEIGHT, 6f, palette.rowHover)
        }
        val label = "${page.section.label} · ${page.title}"
        val textY = rowY + (SEARCH_RESULT_HEIGHT - lineHeight(bodyFont)) / 2
        text(fit(label, width - 24, bodyFont), x + 12, textY, palette.text, bodyFont)
        clickable(x + 4, rowY, width - 8, SEARCH_RESULT_HEIGHT) { onOpen(page) }
    }
}

private fun searchWidth(mainW: Int): Int = (mainW * 0.36f).toInt().coerceIn(SEARCH_MIN_WIDTH, SEARCH_MAX_WIDTH)

/**
 * Runs [block] with the clip narrowed to the given rectangle for both pixels and hit tests.
 * The previous clip is restored even if [block] throws.
 */
internal fun MenuDraw.clippedTo(left: Int, top: Int, right: Int, bottom: Int, block: () -> Unit) {
    val saved = listOf(clipLeft, clipTop, clipRight, clipBottom)
    clipLeft = left
    clipTop = top
    clipRight = right
    clipBottom = bottom
    canvas.save()
    canvas.clipRect(Rect.makeLTRB(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()))
    try {
        block()
    } finally {
        canvas.restore()
        clipLeft = saved[0]
        clipTop = saved[1]
        clipRight = saved[2]
        clipBottom = saved[3]
    }
}

private fun MenuDraw.ambientGlow(m: ConfigMetrics) {
    val violet = 0x2AA68BF0
    val cyan = 0x189FE1E4
    repeat(7) { index ->
        val factor = (7 - index) / 7f
        circle(
            m.screenW * 0.88f,
            m.screenH * 0.02f,
            m.screenW * (0.16f + factor * 0.08f),
            (violet and 0x00FFFFFF) or (((violet ushr 24) * (factor * 0.45f)).toInt() shl 24),
        )
        circle(
            m.screenW * 0.08f,
            m.screenH * 0.92f,
            m.screenW * (0.12f + factor * 0.06f),
            (cyan and 0x00FFFFFF) or (((cyan ushr 24) * (factor * 0.45f)).toInt() shl 24),
        )
    }
}

private fun MenuDraw.aetherBrand(m: ConfigMetrics) {
    val x = m.panelX + (m.sidebar - 26) / 2
    val y = m.panelY + 6
    round(x, y, 26, 26, 7f, 0xFF2B3138.toInt())
    outline(x, y, 26, 26, 7f, 0x55C9BBFF)
    outline(x + 6, y + 6, 14, 14, 4f, palette.text)
    round(x + 12, y + 9, 3, 9, 1.5f, palette.accent)
    round(x + 9, y + 12, 9, 3, 1.5f, palette.accent)
    round(x + 9, y + 8, 3, 3, 1.5f, 0xFFD99BCF.toInt())
}
