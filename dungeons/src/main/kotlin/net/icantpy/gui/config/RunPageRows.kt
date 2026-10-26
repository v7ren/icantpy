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

    /** Padding added around a chip label, so a row of them can be measured before it is drawn. */
    const val CHIP_PAD = 32
    private const val CHIP_GAP = 4
    private const val CHIP_HEIGHT = 28

    /**
     * A title with a row of text chips. When the chips fit beside the title they sit at the right like any other row;
     * when they do not, the card grows and the chips wrap onto lines of their own under the title, so nothing runs
     * over the title or off the card.
     */
    fun chips(
        draw: MenuDraw,
        m: ConfigMetrics,
        y: Int,
        title: String,
        subtitle: String,
        labels: List<String>,
        onPick: (Int) -> Unit,
    ): Int {
        val widths = labels.map { draw.measure(it, draw.buttonFont) + CHIP_PAD }
        val total = widths.sumOf { it + CHIP_GAP }
        val titleZone = (draw.measure(title, draw.bodyFont) + 18 + 24).coerceAtLeast(120)
        if (total <= m.innerWidth - titleZone - 18) {
            return row(draw, m, y, title, subtitle) { cy ->
                var x = m.rightX(total - CHIP_GAP)
                labels.forEachIndexed { index, label ->
                    x += draw.button(label, x, cy, ButtonStyle.GHOST) { onPick(index) } + CHIP_GAP
                }
            }
        }
        val left = m.innerLeft
        val usable = m.innerWidth - 36
        val lines = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var used = 0
        widths.forEachIndexed { index, w ->
            if (current.isNotEmpty() && used + w > usable) {
                lines += current
                current = ArrayList()
                used = 0
            }
            current += index
            used += w + CHIP_GAP
        }
        if (current.isNotEmpty()) lines += current
        val headerH = 34
        val cardHeight = headerH + lines.size * (CHIP_HEIGHT + 4) + 6
        val corner = draw.chrome.cardRadius
        draw.round(left, y, m.innerWidth, cardHeight, corner, draw.palette.group)
        draw.outline(left, y, m.innerWidth, cardHeight, corner, draw.palette.line)
        if (draw.hovered(left, y, m.innerWidth, headerH)) draw.requestTooltip(subtitle, m.innerWidth - 28)
        draw.text(draw.fit(title, m.innerWidth - 36, draw.bodyFont), left + 18, y + 8, draw.palette.text, draw.bodyFont)
        var lineY = y + headerH
        lines.forEach { line ->
            var x = left + 18
            line.forEach { index ->
                x += draw.button(labels[index], x, lineY, ButtonStyle.GHOST) { onPick(index) } + CHIP_GAP
            }
            lineY += CHIP_HEIGHT + 4
        }
        return y + cardHeight + 4
    }
}
