package net.icantpy.dungeon.terminal

/**
 * The font list the editors cycle through: the built-in fonts, then each file in the player's fonts folder. Pure;
 * the caller passes the folder's file names.
 */
object TerminalFontChoice {
    private val BUILT_IN = TerminalFont.entries.filter { it != TerminalFont.CUSTOM }

    fun next(settings: TerminalSettings, files: List<String>): TerminalSettings {
        val choices = BUILT_IN.map { it to "" } + files.map { TerminalFont.CUSTOM to it }
        val current = choices.indexOf(settings.font to if (settings.font == TerminalFont.CUSTOM) settings.customFont else "")
        // A missing file (deleted, or from someone else's style) starts the list over.
        val (font, file) = choices[(current + 1) % choices.size]
        return settings.copy(font = font, customFont = if (font == TerminalFont.CUSTOM) file else settings.customFont)
    }

    /** "Poppins", or a font file's name without its extension. */
    fun label(settings: TerminalSettings): String =
        if (settings.font == TerminalFont.CUSTOM) settings.customFont.substringBeforeLast('.') else settings.font.label
}
