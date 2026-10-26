package net.icantpy.dungeon.terminal

/**
 * Look presets for the solver panel. A preset first resets every look field (see [TerminalStyleShare.LOOK_KEYS]),
 * then sets its own; which terminals are on, the click rules, scale and position stay as the player set them.
 */
enum class TerminalTheme(val label: String) {
    DEFAULT("Default"),
    ODIN("Odin"),
    MINIMAL("Minimal"),
    NEON("Neon"),
    SOFT("Soft"),
    LIGHT("Light"),
    CATPPUCCIN("Catppuccin"),
    DRACULA("Dracula"),
    NORD("Nord"),
    GRUVBOX("Gruvbox"),
    OCEAN("Ocean"),
    SAKURA("Sakura"),
    MONO("Mono"),
    GOLD("Gold"),
    GLASS("Glass"),
    VANILLA("Vanilla"),
    RETRO("Retro"),
    ARCADE("Arcade"),
    ;

    fun applyTo(settings: TerminalSettings): TerminalSettings {
        val base = TerminalStyleShare.resetLook(settings)
        return when (this) {
            DEFAULT -> base
            ODIN -> base.copy(
                panelRgb = 0x141414, panelAlpha = 0.9f, targetRgb = 0x3CB371, doneRgb = 0x262626, wrongRgb = 0x1A1A1A,
                orderSecondRgb = 0x2E8B57, orderThirdRgb = 0x1F5F3B, panelRadius = 4, cellRadius = 2,
                panelBorder = true, borderRgb = 0x3CB371,
            )
            MINIMAL -> base.copy(
                panelAlpha = 0f, hideDone = true, hideWrong = true, cellRadius = 3, textShadow = false,
                titleAlign = TerminalTitleAlign.CENTER, showHover = false, font = TerminalFont.POPPINS,
            )
            NEON -> base.copy(
                panelRgb = 0x07060F, panelAlpha = 0.85f, targetRgb = 0x00F0FF, orderSecondRgb = 0xFF00E6,
                orderThirdRgb = 0x8A6CFF, doneRgb = 0x1B1A2E, wrongRgb = 0x0E0D18, rubixLeftRgb = 0x00F0FF,
                rubixRightRgb = 0xFF00E6, cellStyle = TerminalCellStyle.FILLED_OUTLINE, cellAlpha = 0.25f,
                panelBorder = true, borderRgb = 0x8A6CFF, panelRadius = 3, cellRadius = 2, hoverRgb = 0xFF00E6,
                pulseNext = true,
            )
            SOFT -> base.copy(
                panelRgb = 0x1C1B22, panelAlpha = 0.92f, targetRgb = 0x8BD5A4, orderSecondRgb = 0xF2C57C,
                orderThirdRgb = 0xE88C8C, doneRgb = 0x2C2B33, wrongRgb = 0x24232A, panelRadius = 8, cellRadius = 5,
                textScale = 1.1f, padding = 8,
            )
            LIGHT -> base.copy(
                panelRgb = 0xF2F2F2, panelAlpha = 0.95f, targetRgb = 0x2E9E5B, orderSecondRgb = 0xE0912B,
                orderThirdRgb = 0xD9534F, doneRgb = 0xD6D6D6, wrongRgb = 0xE6E6E6, textRgb = 0x1E1E1E,
                rubixLeftRgb = 0x1E7A3F, rubixRightRgb = 0x2763B8, textShadow = false, panelRadius = 6,
                cellRadius = 3, panelBorder = true, borderRgb = 0xBDBDBD, hoverRgb = 0x1E1E1E,
            )
            CATPPUCCIN -> base.copy(
                panelRgb = 0x1E1E2E, panelAlpha = 0.95f, targetRgb = 0xA6E3A1, orderSecondRgb = 0xF9E2AF,
                orderThirdRgb = 0xF38BA8, doneRgb = 0x313244, wrongRgb = 0x181825, textRgb = 0xCDD6F4,
                rubixLeftRgb = 0xA6E3A1, rubixRightRgb = 0x89B4FA, borderRgb = 0xCBA6F7, panelBorder = true,
                panelRadius = 8, cellRadius = 4, hoverRgb = 0xCBA6F7, textShadow = false,
            )
            DRACULA -> base.copy(
                panelRgb = 0x282A36, panelAlpha = 0.95f, targetRgb = 0x50FA7B, orderSecondRgb = 0xF1FA8C,
                orderThirdRgb = 0xFF5555, doneRgb = 0x44475A, wrongRgb = 0x21222C, textRgb = 0xF8F8F2,
                rubixLeftRgb = 0x50FA7B, rubixRightRgb = 0x8BE9FD, borderRgb = 0xBD93F9, panelBorder = true,
                panelRadius = 4, cellRadius = 2, hoverRgb = 0xFF79C6,
            )
            NORD -> base.copy(
                panelRgb = 0x2E3440, panelAlpha = 0.95f, targetRgb = 0xA3BE8C, orderSecondRgb = 0xEBCB8B,
                orderThirdRgb = 0xBF616A, doneRgb = 0x3B4252, wrongRgb = 0x272C36, textRgb = 0xECEFF4,
                rubixLeftRgb = 0xA3BE8C, rubixRightRgb = 0x88C0D0, borderRgb = 0x4C566A, panelBorder = true,
                panelRadius = 6, cellRadius = 3, hoverRgb = 0x88C0D0, textShadow = false,
            )
            GRUVBOX -> base.copy(
                panelRgb = 0x282828, panelAlpha = 0.95f, targetRgb = 0xB8BB26, orderSecondRgb = 0xFABD2F,
                orderThirdRgb = 0xFB4934, doneRgb = 0x3C3836, wrongRgb = 0x1D2021, textRgb = 0xEBDBB2,
                rubixLeftRgb = 0xB8BB26, rubixRightRgb = 0x83A598, borderRgb = 0xD65D0E, panelBorder = true,
                panelRadius = 2, cellRadius = 1, hoverRgb = 0xFE8019,
            )
            OCEAN -> base.copy(
                panelRgb = 0x0B1D2E, panelAlpha = 0.9f, targetRgb = 0x2EC4B6, orderSecondRgb = 0x90E0EF,
                orderThirdRgb = 0x0077B6, doneRgb = 0x13304A, wrongRgb = 0x0A1724, textRgb = 0xE0FBFC,
                rubixLeftRgb = 0x2EC4B6, rubixRightRgb = 0x90E0EF, panelRadius = 10, cellRadius = 6,
                cellStyle = TerminalCellStyle.FILLED_OUTLINE, cellAlpha = 0.55f, hoverRgb = 0x90E0EF, pulseNext = true,
            )
            SAKURA -> base.copy(
                panelRgb = 0x2B1B24, panelAlpha = 0.92f, targetRgb = 0xFFB7C5, orderSecondRgb = 0xF7D6E0,
                orderThirdRgb = 0xC97B94, doneRgb = 0x3D2733, wrongRgb = 0x22151C, textRgb = 0xFFF0F5,
                rubixLeftRgb = 0xFFB7C5, rubixRightRgb = 0xB5E2FA, borderRgb = 0xFFB7C5, panelBorder = true,
                panelRadius = 10, cellRadius = 5, hoverRgb = 0xFFFFFF,
            )
            MONO -> base.copy(
                panelRgb = 0x000000, panelAlpha = 0.85f, targetRgb = 0xFFFFFF, orderSecondRgb = 0xA0A0A0,
                orderThirdRgb = 0x606060, doneRgb = 0x1A1A1A, wrongRgb = 0x0D0D0D, textRgb = 0xFFFFFF,
                rubixLeftRgb = 0xFFFFFF, rubixRightRgb = 0xA0A0A0, cellStyle = TerminalCellStyle.OUTLINE,
                outlineWidth = 2, hoverRgb = 0x808080, font = TerminalFont.SPACE_MONO,
            )
            GOLD -> base.copy(
                panelRgb = 0x1A1408, panelAlpha = 0.92f, targetRgb = 0xFFAA00, orderSecondRgb = 0xFFFF55,
                orderThirdRgb = 0xAA5500, doneRgb = 0x2E2410, wrongRgb = 0x120E06, textRgb = 0xFFE7A0,
                rubixLeftRgb = 0xFFAA00, rubixRightRgb = 0xFFFF55, borderRgb = 0xFFAA00, panelBorder = true,
                panelRadius = 3, cellRadius = 1, hoverRgb = 0xFFFF55,
            )
            GLASS -> base.copy(
                panelRgb = 0xFFFFFF, panelAlpha = 0.08f, targetRgb = 0x7CFFC5, orderSecondRgb = 0xFFD37C,
                orderThirdRgb = 0xFF7C9C, doneRgb = 0xFFFFFF, wrongRgb = 0x000000, cellAlpha = 0.3f,
                cellStyle = TerminalCellStyle.FILLED_OUTLINE, panelBorder = true, borderRgb = 0xFFFFFF,
                panelRadius = 10, cellRadius = 6, screenDim = 0.25f, hideDone = true,
            )
            VANILLA -> base.copy(
                panelRgb = 0xC6C6C6, panelAlpha = 1f, targetRgb = 0x55FF55, orderSecondRgb = 0xFFFF55,
                orderThirdRgb = 0xFF5555, doneRgb = 0x8B8B8B, wrongRgb = 0x8B8B8B, textRgb = 0x404040,
                rubixLeftRgb = 0x00AA00, rubixRightRgb = 0x0000AA, textShadow = false, panelBorder = true,
                borderRgb = 0x373737, hoverRgb = 0xFFFFFF, padding = 7,
            )
            RETRO -> base.copy(
                panelRgb = 0x020A04, panelAlpha = 0.94f, targetRgb = 0x33FF66, orderSecondRgb = 0x1FA346,
                orderThirdRgb = 0x0F5C26, doneRgb = 0x0B2412, wrongRgb = 0x06140A, textRgb = 0x66FF8C,
                rubixLeftRgb = 0x33FF66, rubixRightRgb = 0xB6FFC8, cellStyle = TerminalCellStyle.FILLED_OUTLINE,
                cellAlpha = 0.35f, panelBorder = true, borderRgb = 0x1FA346, hoverRgb = 0xB6FFC8, textScale = 1.2f,
                font = TerminalFont.VT323, pulseNext = true,
            )
            ARCADE -> base.copy(
                panelRgb = 0x12002B, panelAlpha = 0.95f, targetRgb = 0xFFE600, orderSecondRgb = 0xFF2E88,
                orderThirdRgb = 0x00D1FF, doneRgb = 0x2A1450, wrongRgb = 0x1A0838, textRgb = 0xFFFFFF,
                rubixLeftRgb = 0xFFE600, rubixRightRgb = 0x00D1FF, panelBorder = true, borderRgb = 0xFF2E88,
                hoverRgb = 0x00D1FF, textScale = 0.85f, font = TerminalFont.PRESS_START,
            )
        }
    }
}
