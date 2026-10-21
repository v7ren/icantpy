package net.icantpy.gui.config

/**
 * Row and heading helpers shared by the Splits and Score config pages. They mirror the look of the
 * legacy rows in IcantpyConfigScreen without reaching into that screen's private members.
 */
internal object RunPageRows {
    /** Vertical gap added after a group of rows, matching the config screen's group spacing. */
    const val GROUP_GAP: Int = 8

    fun heading(draw: MenuDraw, m: ConfigMetrics, y: Int, title: String): Int {
        draw.heading(title, m.innerLeft, y)
        return y + draw.lineHeight(draw.headingFont) + 10
    }

    fun help(draw: MenuDraw, m: ConfigMetrics, y: Int, text: String): Int {
        var ty = y
        draw.wrap(text, m.innerWidth - 24).forEach { line ->
            draw.text(line, m.innerLeft, ty, draw.palette.secondary, draw.descFont)
            ty += draw.lineHeight(draw.descFont)
        }
        return ty + GROUP_GAP
    }

    /** Title on the left, [controls] drawn at the right. Returns the next row's y. */
    fun row(
        draw: MenuDraw,
        m: ConfigMetrics,
        y: Int,
        title: String,
        subtitle: String,
        controls: (Int) -> Unit,
    ): Int {
        val left = m.innerLeft
        val width = m.innerWidth
        val cardHeight = m.row - 4
        val corner = draw.chrome.cardRadius
        draw.round(left, y, width, cardHeight, corner, draw.palette.group)
        draw.outline(left, y, width, cardHeight, corner, draw.palette.line)
        if (draw.hovered(left, y, width, m.row)) {
            draw.round(left, y, width, cardHeight, corner, draw.palette.rowHover)
            draw.requestTooltip(subtitle, width - 28)
        }
        draw.text(draw.fit(title, width - 200, draw.bodyFont), left + 18, y + 8, draw.palette.text, draw.bodyFont)
        controls(y + ((cardHeight - 28) / 2).coerceAtLeast(2))
        return y + m.row
    }

    fun toggle(
        draw: MenuDraw,
        m: ConfigMetrics,
        y: Int,
        title: String,
        subtitle: String,
        on: Boolean,
        action: () -> Unit,
    ): Int {
        draw.clickable(m.innerLeft, y, m.innerWidth, m.row, action)
        return row(draw, m, y, title, subtitle) { cy -> draw.toggle(m.rightX(40), cy + 5, on, action) }
    }

    /** A row with one text button at the right. */
    fun button(
        draw: MenuDraw,
        m: ConfigMetrics,
        y: Int,
        title: String,
        subtitle: String,
        label: String,
        style: ButtonStyle,
        width: Int,
        action: () -> Unit,
    ): Int = row(draw, m, y, title, subtitle) { cy ->
        draw.button(label, m.rightX(width), cy, style, action)
    }
}
