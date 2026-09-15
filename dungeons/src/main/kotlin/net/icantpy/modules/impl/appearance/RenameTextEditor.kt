package net.icantpy.modules.impl.appearance

/**
 * Pure single-line text editor used by the item customizer name field. Selection is an anchor
 * plus a cursor, matching NEU's formatting-aware field. The stored value uses `§` colour codes;
 * the editor displays them as `¶` so they remain visible and typeable.
 */
data class RenameTextEditor(
    val text: String = "",
    val cursor: Int = 0,
    val anchor: Int = cursor,
) {
    val selectionStart: Int get() = minOf(cursor, anchor)
    val selectionEnd: Int get() = maxOf(cursor, anchor)
    val hasSelection: Boolean get() = cursor != anchor

    fun selectedText(): String = text.substring(selectionStart, selectionEnd)

    fun moveTo(position: Int, extend: Boolean): RenameTextEditor {
        val clamped = position.coerceIn(0, text.length)
        return copy(cursor = clamped, anchor = if (extend) anchor else clamped)
    }

    fun moveHorizontal(delta: Int, extend: Boolean): RenameTextEditor = moveTo(cursor + delta, extend)

    fun moveHome(extend: Boolean): RenameTextEditor = moveTo(0, extend)

    fun moveEnd(extend: Boolean): RenameTextEditor = moveTo(text.length, extend)

    fun selectAll(): RenameTextEditor = copy(cursor = text.length, anchor = 0)

    fun clearSelection(): RenameTextEditor = copy(anchor = cursor)

    fun withText(value: String): RenameTextEditor = RenameTextEditor(value).moveTo(value.length, false)

    fun deleteSelection(): RenameTextEditor {
        if (!hasSelection) return this
        val next = text.removeRange(selectionStart, selectionEnd)
        return RenameTextEditor(next, selectionStart, selectionStart)
    }

    fun insert(value: String): RenameTextEditor {
        val base = deleteSelection()
        val next = base.text.substring(0, base.cursor) + value + base.text.substring(base.cursor)
        val position = base.cursor + value.length
        return RenameTextEditor(next, position, position)
    }

    fun backspace(): RenameTextEditor {
        if (hasSelection) return deleteSelection()
        if (cursor == 0) return this
        val next = text.removeRange(cursor - 1, cursor)
        return RenameTextEditor(next, cursor - 1, cursor - 1)
    }

    fun delete(): RenameTextEditor {
        if (hasSelection) return deleteSelection()
        if (cursor >= text.length) return this
        val next = text.removeRange(cursor, cursor + 1)
        return RenameTextEditor(next, cursor, cursor)
    }

    companion object {
        private const val SECTION = '§'
        private const val PILCROW = '¶'

        /** Stored `§x` codes render as `¶x` while editing. */
        fun toDisplay(name: String): String = name.replace(SECTION, PILCROW)

        /** Typed `¶x` is stored back as `§x`. */
        fun toStored(display: String): String = display.replace(PILCROW, SECTION)
    }
}
