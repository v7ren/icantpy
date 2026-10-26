package net.icantpy.dungeon.terminal

/** Mouse button the solver UI sends for a click on any terminal except the colour cycle. */
enum class TerminalClickButton(val label: String) {
    /** Hypixel accepts a middle click on every terminal slot and it never puts the item on the cursor. */
    MIDDLE("Middle"),
    LEFT("Left"),
}

/** How the colour-cycle terminal labels each pane. */
enum class RubixDisplay(val label: String) {
    /** Fewest clicks: a positive number is left clicks, a negative one right clicks. */
    SHORTEST("Shortest"),

    /** Left clicks only, for players who never right click. */
    LEFT_ONLY("Left only"),
}

/** How each cell is drawn. */
enum class TerminalCellStyle(val label: String) {
    FILLED("Filled"),
    OUTLINE("Outline"),
    FILLED_OUTLINE("Filled + outline"),
}

/** Where the title and clicks-left line sit. */
enum class TerminalHeaderPosition(val label: String) {
    TOP("Top"),
    BOTTOM("Bottom"),
}

/** How the header is aligned. Centred puts the title and the counter on one line. */
enum class TerminalTitleAlign(val label: String) {
    SPLIT("Split"),
    CENTER("Centred"),
}

/**
 * Font for the panel's text. [fontId] names a vanilla font or one bundled under assets/icantpy/font/terminal (all four
 * are SIL Open Font License fonts from Google Fonts); null is Minecraft's own font.
 */
enum class TerminalFont(val label: String, val fontId: String?) {
    MINECRAFT("Minecraft", null),
    UNICODE("Unicode", "minecraft:uniform"),
    POPPINS("Poppins", "icantpy:terminal/poppins"),
    SPACE_MONO("Space Mono", "icantpy:terminal/space_mono"),
    VT323("Retro terminal", "icantpy:terminal/vt323"),
    PRESS_START("Arcade", "icantpy:terminal/press_start"),

    /** A file from the player's fonts folder, named by [TerminalSettings.customFont]. */
    CUSTOM("Your font", null),
}

/** User settings for the terminal solver UI. Colours are 0xRRGGBB; alphas are 0..1. */
data class TerminalSettings(
    val enabled: Boolean = true,
    val panes: Boolean = true,
    val inOrder: Boolean = true,
    val startsWith: Boolean = true,
    val selectColor: Boolean = true,
    val rubix: Boolean = true,
    val melody: Boolean = true,
    val scale: Float = DEFAULT_SCALE,
    val gap: Int = DEFAULT_GAP,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val showTitle: Boolean = true,
    val showRemaining: Boolean = true,
    val showItems: Boolean = true,
    val hideWrong: Boolean = false,
    val hideDone: Boolean = false,
    val clickButton: TerminalClickButton = TerminalClickButton.MIDDLE,
    val blockWrongClicks: Boolean = true,
    val predictClicks: Boolean = true,
    val rubixDisplay: RubixDisplay = RubixDisplay.SHORTEST,
    val panelRgb: Int = 0x101016,
    val panelAlpha: Float = DEFAULT_PANEL_ALPHA,
    val targetRgb: Int = 0x55FF55,
    val doneRgb: Int = 0x3A3A3A,
    val wrongRgb: Int = 0x1E1E1E,
    val orderSecondRgb: Int = 0xFFAA00,
    val orderThirdRgb: Int = 0xFF5555,
    val rubixLeftRgb: Int = 0x55FF55,
    val rubixRightRgb: Int = 0x55AAFF,
    val textRgb: Int = 0xFFFFFF,
    val cellStyle: TerminalCellStyle = TerminalCellStyle.FILLED,
    val cellAlpha: Float = 1f,
    val cellRadius: Int = 0,
    val outlineWidth: Int = 1,
    val panelRadius: Int = 0,
    val panelBorder: Boolean = false,
    val borderRgb: Int = 0xFFFFFF,
    val textScale: Float = 1f,
    val textShadow: Boolean = true,
    val font: TerminalFont = TerminalFont.MINECRAFT,
    val customFont: String = "",
    val customFontSize: Float = DEFAULT_CUSTOM_FONT_SIZE,
    val customFontShift: Float = 0f,
    val showHover: Boolean = true,
    val hoverRgb: Int = 0xFFFFFF,
    val pulseNext: Boolean = false,
    val fadeDone: Boolean = true,
    val headerPosition: TerminalHeaderPosition = TerminalHeaderPosition.TOP,
    val titleAlign: TerminalTitleAlign = TerminalTitleAlign.SPLIT,
    val padding: Int = DEFAULT_PADDING,
    val screenDim: Float = 0f,
    val firstClickDelayMs: Int = DEFAULT_FIRST_CLICK_DELAY_MS,
    val lagProtection: Boolean = false,
    val lagProtectionTicks: Int = DEFAULT_LAG_TICKS,
    val resolveTimeoutMs: Int = DEFAULT_RESOLVE_TIMEOUT_MS,
) {
    fun takesOver(type: TerminalType): Boolean = enabled && when (type) {
        TerminalType.PANES -> panes
        TerminalType.IN_ORDER -> inOrder
        TerminalType.STARTS_WITH -> startsWith
        TerminalType.SELECT_COLOR -> selectColor
        TerminalType.RUBIX -> rubix
        TerminalType.MELODY -> melody
    }

    fun withType(type: TerminalType, on: Boolean): TerminalSettings = when (type) {
        TerminalType.PANES -> copy(panes = on)
        TerminalType.IN_ORDER -> copy(inOrder = on)
        TerminalType.STARTS_WITH -> copy(startsWith = on)
        TerminalType.SELECT_COLOR -> copy(selectColor = on)
        TerminalType.RUBIX -> copy(rubix = on)
        TerminalType.MELODY -> copy(melody = on)
    }

    fun typeEnabled(type: TerminalType): Boolean = when (type) {
        TerminalType.PANES -> panes
        TerminalType.IN_ORDER -> inOrder
        TerminalType.STARTS_WITH -> startsWith
        TerminalType.SELECT_COLOR -> selectColor
        TerminalType.RUBIX -> rubix
        TerminalType.MELODY -> melody
    }

    companion object {
        const val DEFAULT_SCALE = 1.5f
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 3f
        const val DEFAULT_GAP = 2
        const val MIN_GAP = 0
        const val MAX_GAP = 8
        const val DEFAULT_PANEL_ALPHA = 0.8f
        const val MAX_OFFSET = 2_000
        const val MAX_CELL_RADIUS = 8
        const val MAX_PANEL_RADIUS = 12
        const val MIN_OUTLINE = 1
        const val MAX_OUTLINE = 3
        const val MIN_TEXT_SCALE = 0.5f
        const val MAX_TEXT_SCALE = 2f
        const val DEFAULT_PADDING = 6
        const val MAX_PADDING = 16
        const val DEFAULT_FIRST_CLICK_DELAY_MS = 500
        const val MIN_LAG_TICKS = 7
        const val MAX_LAG_TICKS = 16
        const val DEFAULT_LAG_TICKS = 8
        const val MIN_RESOLVE_TIMEOUT_MS = 300
        const val MAX_RESOLVE_TIMEOUT_MS = 1_200
        const val DEFAULT_RESOLVE_TIMEOUT_MS = 600
        const val MAX_FIRST_CLICK_DELAY_MS = 1_000
        const val DEFAULT_CUSTOM_FONT_SIZE = 10f
        const val MIN_CUSTOM_FONT_SIZE = 6f
        const val MAX_CUSTOM_FONT_SIZE = 24f
        const val MAX_CUSTOM_FONT_SHIFT = 4f
        const val MAX_FONT_NAME = 128
    }
}
