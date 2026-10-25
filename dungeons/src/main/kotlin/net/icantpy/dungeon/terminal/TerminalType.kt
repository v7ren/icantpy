package net.icantpy.dungeon.terminal

import net.icantpy.util.Text

/**
 * The F7/M7 phase 3 chest terminals the solver can take over, recognised by their window titles. Melody is a timing
 * test rather than a puzzle: the panel only highlights the moving pane, the target column and the button, and draws
 * from the same menu every frame as the vanilla screen would, so it adds no delay.
 */
enum class TerminalType(val displayName: String) {
    PANES("Correct all the panes"),
    IN_ORDER("Click in order"),
    STARTS_WITH("What starts with"),
    SELECT_COLOR("Select all the color"),
    RUBIX("Change all to same color"),
    MELODY("Click the button on time"),
    ;

    companion object {
        private val STARTS_WITH_TITLE = Regex("^What starts with: ['\"](.)['\"]\\?$")
        private val SELECT_COLOR_TITLE = Regex("^Select all the (.+?) items!$")

        fun fromTitle(rawTitle: String): TerminalType? {
            val title = Text.strip(rawTitle)
            return when {
                title == "Correct all the panes!" -> PANES
                title == "Click in order!" -> IN_ORDER
                title == "Change all to same color!" -> RUBIX
                title == "Click the button on time!" -> MELODY
                STARTS_WITH_TITLE.matches(title) -> STARTS_WITH
                SELECT_COLOR_TITLE.matches(title) -> SELECT_COLOR
                else -> null
            }
        }

        /** The letter a "What starts with" terminal asks for, or null for any other title. */
        fun startsWithLetter(rawTitle: String): String? =
            STARTS_WITH_TITLE.find(Text.strip(rawTitle))?.groupValues?.get(1)

        /** The colour words of a "Select all the ... items!" title, as written (for example "LIGHT BLUE"). */
        fun selectColorWords(rawTitle: String): String? =
            SELECT_COLOR_TITLE.find(Text.strip(rawTitle))?.groupValues?.get(1)
    }
}
