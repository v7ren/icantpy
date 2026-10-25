package net.icantpy.gui.config

import org.jetbrains.skia.Rect

/** Sidebar geometry in pixels. A row is 22px tall with a 2px gap between rows. */
internal const val SIDEBAR_ROW_HEIGHT = 22
internal const val SIDEBAR_ROW_PITCH = 24
private const val SIDEBAR_HEADING_HEIGHT = 20
private const val SIDEBAR_GROUP_GAP = 8
private const val SIDEBAR_TOP = 90
private const val SIDEBAR_BOTTOM_PAD = 40
private const val SIDEBAR_TEXT_INSET = 12
private const val SIDEBAR_PAD = 10

internal const val SEARCH_FIELD_ID = "nav:search"
private const val SEARCH_FIELD_HEIGHT = 24
private const val SEARCH_RESULT_HEIGHT = 26
private const val SEARCH_MAX_RESULTS = 8
private const val SEARCH_MENU_MIN_WIDTH = 260
private const val PILL_HEIGHT = 18
private const val FOOTER_BUTTON_HEIGHT = 20
private const val TOC_ROW_HEIGHT = 18

/** Everything the sidebar needs from the screen: filter, per-page state and footer actions. */
internal data class SidebarState(
    val onlyEnabled: Boolean = false,
    val onFilter: (Boolean) -> Unit = {},
    /** Whether a page's feature is switched on; null for pages without a master switch. */
    val pageOn: (ConfigPage) -> Boolean? = { null },
    val enabledCount: Int = 0,
    val lightTheme: Boolean = false,
    val onTheme: () -> Unit = {},
    val onAppearance: () -> Unit = {},
)

/** Pages grouped by section, optionally only those switched on (the overview and switch-less pages always stay). */
internal fun sidebarGroups(state: SidebarState? = null): List<Pair<ConfigSection, List<ConfigPage>>> {
    val groups = ConfigNav.sidebarGroups()
    if (state == null || !state.onlyEnabled) return groups
    return groups.map { (section, pages) ->
        section to pages.filter { it == ConfigPage.OVERVIEW || state.pageOn(it) != false }
    }.filter { (_, pages) -> pages.isNotEmpty() }
}

/** Total height of the sidebar list, used to clamp its own scroll position. */
internal fun sidebarContentHeight(state: SidebarState? = null): Int = sidebarGroups(state).sumOf { (section, pages) ->
    val heading = if (section == ConfigSection.HOME) 0 else SIDEBAR_HEADING_HEIGHT
    heading + pages.size * SIDEBAR_ROW_PITCH + SIDEBAR_GROUP_GAP
}

internal fun sidebarTop(m: ConfigMetrics): Int = m.panelY + SIDEBAR_TOP

internal fun sidebarBottom(m: ConfigMetrics): Int = m.panelY + m.panelH - SIDEBAR_BOTTOM_PAD

/** Width of the main column, between the sidebar and the page outline. */
internal fun mainWidth(m: ConfigMetrics): Int = m.panelW - m.sidebar - m.toc

private fun searchRect(m: ConfigMetrics): Rect =
    Rect.makeXYWH((m.panelX + SIDEBAR_PAD).toFloat(), (m.panelY + 34).toFloat(), (m.sidebar - SIDEBAR_PAD * 2).toFloat(), SEARCH_FIELD_HEIGHT.toFloat())

/** Paints the panel, sidebar and header background behind everything else. */
internal fun MenuDraw.paintShellFrame(m: ConfigMetrics) {
    round(0, 0, m.screenW, m.screenH, 0f, palette.scrim)
    if (!palette.glass) ambientGlow(m)
    unclipped {
        round(m.panelX, m.panelY, m.panelW, m.panelH, radius, palette.window)
        outline(m.panelX, m.panelY, m.panelW, m.panelH, radius, if (palette.glass) palette.line else 0x2AFFFFFF)
        round(m.panelX, m.panelY, m.sidebar, m.panelH, radius, palette.sidebar)
        round(m.panelX + m.sidebar - 8, m.panelY, 8, m.panelH, 0f, palette.sidebar)
        line(m.panelX + m.sidebar, m.panelY, 1, palette.line)
        round(m.contentLeft, m.panelY, mainWidth(m), m.header, 0f, palette.header)
        if (m.toc > 0) line(m.contentLeft + mainWidth(m), m.panelY + m.header, 1, palette.line)
    }
}

/**
 * Brand, search, All/On filter, the grouped page list (clipped and scrolled by [scroll]) and the footer.
 * Section headings are skipped for HOME so the overview row sits alone at the top.
 */
internal fun MenuDraw.paintSidebar(
    m: ConfigMetrics,
    selected: ConfigPage,
    scroll: Int,
    state: SidebarState,
    searchText: String,
    searchFocused: Boolean,
    onSelect: (ConfigPage) -> Unit,
) {
    unclipped {
        text("icantpy", m.panelX + 14, m.panelY + 10, palette.text, displayFont)
        val count = "${state.enabledCount} on"
        text(count, m.panelX + m.sidebar - 12 - measure(count, captionFont), m.panelY + 14, palette.tertiary, captionFont)
        val box = searchRect(m)
        field(
            SEARCH_FIELD_ID, searchText, "Search settings",
            box.left.toInt(), box.top.toInt(), box.width.toInt(), searchFocused, SEARCH_FIELD_HEIGHT,
        )
        paintPills(m, state)
    }
    val top = sidebarTop(m)
    val bottom = sidebarBottom(m)
    val left = m.panelX + 8
    val width = m.sidebar - 16
    clippedTo(m.panelX, top, m.contentLeft, bottom) {
        var y = top - scroll
        sidebarGroups(state).forEach { (section, pages) ->
            if (section != ConfigSection.HOME) {
                text(section.label.uppercase(), left + 4, y + 4, palette.tertiary, headingFont)
                y += SIDEBAR_HEADING_HEIGHT
            }
            pages.forEach { page ->
                sidebarRow(page.title, left, y, width, page == selected, state.pageOn(page)) { onSelect(page) }
                y += SIDEBAR_ROW_PITCH
            }
            y += SIDEBAR_GROUP_GAP
        }
    }
    val viewport = bottom - top
    if (sidebarContentHeight(state) > viewport && scroll < sidebarContentHeight(state) - viewport) {
        if (!palette.glass) fadeBottom(m.panelX, bottom, m.sidebar, palette.sidebar)
    }
    unclipped { paintFooter(m, state) }
}

private fun MenuDraw.paintPills(m: ConfigMetrics, state: SidebarState) {
    val x = m.panelX + SIDEBAR_PAD
    val y = m.panelY + 64
    val w = m.sidebar - SIDEBAR_PAD * 2
    val half = w / 2
    round(x, y, w, PILL_HEIGHT, chrome.controlRadius.coerceAtMost(8f), palette.group)
    listOf("All" to false, "On" to true).forEachIndexed { index, (label, only) ->
        val cellX = x + index * half
        val on = state.onlyEnabled == only
        if (on) round(cellX + 1, y + 1, half - 2, PILL_HEIGHT - 2, chrome.controlRadius.coerceAtMost(7f), palette.accentSoft)
        val color = if (on) palette.text else palette.secondary
        text(label, cellX + (half - measure(label, captionFont)) / 2, y + (PILL_HEIGHT - lineHeight(captionFont)) / 2, color, captionFont)
        clickable(cellX, y, half, PILL_HEIGHT) { state.onFilter(only) }
    }
}

private fun MenuDraw.paintFooter(m: ConfigMetrics, state: SidebarState) {
    val y = m.panelY + m.panelH - FOOTER_BUTTON_HEIGHT - 10
    val x = m.panelX + SIDEBAR_PAD
    val total = m.sidebar - SIDEBAR_PAD * 2
    line(x, y - 8, total, palette.line)
    val themeLabel = if (state.lightTheme) "Dark" else "Light"
    val themeW = measure(themeLabel, captionFont) + 18
    footerButton("Appearance", x, y, total - themeW - 6, state.onAppearance)
    footerButton(themeLabel, x + total - themeW, y, themeW, state.onTheme)
}

private fun MenuDraw.footerButton(label: String, x: Int, y: Int, w: Int, action: () -> Unit) {
    val corner = chrome.controlRadius.coerceAtMost(8f)
    val hot = hovered(x, y, w, FOOTER_BUTTON_HEIGHT)
    round(x, y, w, FOOTER_BUTTON_HEIGHT, corner, if (hot) palette.rowHover else palette.group)
    text(label, x + 9, y + (FOOTER_BUTTON_HEIGHT - lineHeight(captionFont)) / 2, palette.text, captionFont)
    clickable(x, y, w, FOOTER_BUTTON_HEIGHT, action)
}

private fun MenuDraw.sidebarRow(label: String, x: Int, y: Int, w: Int, selected: Boolean, on: Boolean?, action: () -> Unit) {
    val h = SIDEBAR_ROW_HEIGHT
    val corner = chrome.controlRadius.coerceAtMost(8f)
    if (selected) {
        round(x, y, w, h, corner, palette.accentSoft)
        round(x, y + 5, 3, h - 10, 1.5f, palette.activeAccent)
    } else if (hovered(x, y, w, h)) {
        round(x, y, w, h, corner, palette.rowHover)
    }
    val textY = y + (h - lineHeight(bodyFont)) / 2
    val color = if (selected) palette.text else palette.secondary
    val dotRoom = if (on != null) 14 else 0
    text(fit(label, w - SIDEBAR_TEXT_INSET * 2 - dotRoom, bodyFont), x + SIDEBAR_TEXT_INSET, textY, color, bodyFont)
    if (on != null) circle((x + w - 12).toFloat(), (y + h / 2).toFloat(), 3f, if (on) palette.activeAccent else palette.trackOff)
    clickable(x, y, w, h, action)
}

/** Enable toggle for a page's primary feature, shown in the header. */
internal data class HeaderToggle(val on: Boolean, val action: () -> Unit)

/** Breadcrumb on the left; the page's enable switch, when it has one, on the right. */
internal fun MenuDraw.paintHeader(m: ConfigMetrics, page: ConfigPage, toggle: HeaderToggle?) {
    val mainX = m.contentLeft
    val mainW = mainWidth(m)
    line(mainX, m.panelY + m.header - 1, mainW, palette.line)
    val textY = m.panelY + (m.header - lineHeight(bodyFont)) / 2
    val prefix = "${page.section.label} / "
    val crumbX = mainX + 16
    if (page.section != ConfigSection.HOME) text(prefix, crumbX, textY, palette.tertiary, bodyFont)
    val titleX = crumbX + if (page.section != ConfigSection.HOME) measure(prefix, bodyFont) else 0
    text(fit(page.title, mainW - 140, bodyFont), titleX, textY, palette.text, bodyFont)
    if (toggle != null) {
        val toggleX = mainX + mainW - 16 - TOGGLE_WIDTH
        toggle(toggleX, m.panelY + (m.header - TOGGLE_HEIGHT) / 2, toggle.on, toggle.action)
        val label = if (toggle.on) "Enabled" else "Disabled"
        text(label, toggleX - 10 - measure(label, buttonFont), textY, palette.secondary, buttonFont)
    }
}

/** Large page title and its caption, drawn at the top of the scrolling content. Returns the height used. */
internal fun MenuDraw.paintHero(x: Int, y: Int, width: Int, page: ConfigPage): Int {
    text(fit(page.title, width, heroFont), x, y, palette.text, heroFont)
    val titleH = lineHeight(heroFont)
    text(fit(page.caption, width, descFont), x, y + titleH + 2, palette.secondary, descFont)
    return titleH + lineHeight(descFont) + 12
}

/** "On this page" outline in the right column. [active] is the highlighted entry. */
internal fun MenuDraw.paintToc(m: ConfigMetrics, titles: List<String>, active: Int, onPick: (Int) -> Unit) {
    if (m.toc <= 0) return
    val x = m.contentLeft + mainWidth(m)
    val top = m.panelY + m.header + 14
    unclipped {
        text("ON THIS PAGE", x + 12, top, palette.tertiary, headingFont)
        var y = top + lineHeight(headingFont) + 8
        titles.forEachIndexed { index, title ->
            val on = index == active
            if (on) round(x + 8, y, 2, TOC_ROW_HEIGHT - 4, 1f, palette.activeAccent)
            else if (hovered(x + 8, y - 2, m.toc - 12, TOC_ROW_HEIGHT)) round(x + 6, y - 2, m.toc - 12, TOC_ROW_HEIGHT, 5f, palette.rowHover)
            text(fit(title, m.toc - 30, captionFont), x + 16, y, if (on) palette.text else palette.secondary, captionFont)
            clickable(x + 6, y - 2, m.toc - 12, TOC_ROW_HEIGHT) { onPick(index) }
            y += TOC_ROW_HEIGHT
        }
    }
}

/** Subtab bar under the header. Only called for pages that define subtabs. */
internal fun MenuDraw.paintSubtabs(m: ConfigMetrics, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val y = m.panelY + m.header
    val x = m.contentLeft
    val w = mainWidth(m)
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
 * Dropdown of matching pages under the sidebar search field. Paint it unclipped after the content so it
 * sits above every card; its hits are added last and therefore win clicks.
 */
internal fun MenuDraw.paintSearchResults(m: ConfigMetrics, results: List<ConfigPage>, onOpen: (ConfigPage) -> Unit) {
    val box = searchRect(m)
    val width = maxOf(box.width.toInt(), SEARCH_MENU_MIN_WIDTH).coerceAtMost(m.panelW - 24)
    val x = box.left.toInt()
    val y = box.bottom.toInt() + 4
    val shown = results.take(SEARCH_MAX_RESULTS)
    val h = (if (shown.isEmpty()) SEARCH_RESULT_HEIGHT else shown.size * SEARCH_RESULT_HEIGHT) + 8
    val corner = chrome.controlRadius.coerceAtMost(10f)
    round(x, y, width, h, corner, palette.window or 0xFF000000.toInt())
    round(x, y, width, h, corner, palette.group)
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
