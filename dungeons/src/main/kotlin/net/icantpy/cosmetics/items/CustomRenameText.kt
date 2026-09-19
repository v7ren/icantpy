package net.icantpy.cosmetics.items

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

object CustomRenameText {
    const val MAX_LENGTH: Int = 128

    /** NEU's `*1`..`*9` glyphs are U+278A..U+2792. */
    const val MASTER_STAR_FIRST: Char = '\u278A'
    private const val MASTER_STAR_LAST: Char = '\u2792'
    const val MASTER_STAR_GLYPH: Char = '\u272A'

    private val FORMAT_CODES = "0123456789abcdefklmnor".toSet()

    /**
     * The master stars / `✪` are not guaranteed by the default font once a server resource pack is
     * active, so render those glyphs with our own Unifont-backed font.
     */
    private val STAR_FONT = FontDescription.Resource(Identifier.fromNamespaceAndPath("icantpy", "neurename"))

    /** NEU's name-chroma colour order, indexed by the animated position. */
    val CHROMA_CODES: List<Char> = listOf('c', '6', 'e', 'a', 'b', 'd', '5')

    /** Matches NEU's chat-friendly ampersand and shortcut conventions (command form). */
    fun normalize(raw: String): String = bounded(
        raw
            .replace("\\&", "{amp}")
            .replace("&&", "§")
            .replace('&', '§')
            .replace("{amp}", "&")
            .replace("**", MASTER_STAR_GLYPH.toString())
            .replace(Regex("\\*([1-9])")) {
                (MASTER_STAR_FIRST.code + (it.groupValues[1][0].code - '1'.code)).toChar().toString()
            }
            .filter { it != '\n' && it != '\r' && !it.isISOControl() }
            .trim(),
    )

    /**
     * NEU's editor only expands `&&`, `**` and master stars; a single `&` is left untouched.
     */
    fun normalizeEditor(raw: String): String = bounded(
        raw
            .replace("&&", "§")
            .replace("**", MASTER_STAR_GLYPH.toString())
            .replace(Regex("\\*([1-9])")) {
                (MASTER_STAR_FIRST.code + (it.groupValues[1][0].code - '1'.code)).toChar().toString()
            }
            .filter { it != '\n' && it != '\r' && !it.isISOControl() },
    )

    /**
     * Caps the length without splitting a surrogate pair or a `§x` formatting pair, so an edit at
     * the limit never leaves a broken Unicode character or a dangling section sign.
     */
    fun bounded(value: String): String {
        if (value.length <= MAX_LENGTH) return value
        var end = MAX_LENGTH
        if (end < value.length && value[end].isLowSurrogate()) end--
        if (end > 0 && value[end - 1] == '§') end--
        return value.substring(0, end)
    }

    fun hasFormatting(raw: String): Boolean = raw.contains('§')

    fun isFormatCode(code: Char): Boolean = code.lowercaseChar() in FORMAT_CODES

    fun component(raw: String, base: Style): Component = parse(normalize(raw), base)

    fun componentChroma(
        raw: String,
        base: Style,
        elapsedMillis: Long,
        speed: Int,
        widthOf: (Char) -> Int,
    ): Component = parse(expandChroma(normalize(raw), elapsedMillis, speed, widthOf), base)

    private fun parse(text: String, base: Style): Component {
        val result = Component.empty()
        var style = base
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                result.append(Component.literal(plain.toString()).setStyle(style))
                plain.clear()
            }
        }
        var index = 0
        while (index < text.length) {
            if (text[index] == '§' && index + 1 < text.length) {
                val format = ChatFormatting.getByCode(text[index + 1])
                if (format != null) {
                    flush()
                    style = if (format == ChatFormatting.RESET) base else style.applyLegacyFormat(format)
                    index += 2
                    continue
                }
            }
            if (isStarGlyph(text[index])) {
                flush()
                result.append(Component.literal(text[index].toString()).setStyle(style.withFont(STAR_FONT)))
                index++
                continue
            }
            plain.append(text[index])
            index++
        }
        flush()
        return result
    }

    /**
     * Expands every `§z` span into per-character animated chroma, leaving surrounding formatting
     * intact. [widthOf] measures a character so adjacent spans drift at a consistent speed.
     */
    fun expandChroma(raw: String, elapsedMillis: Long, speed: Int, widthOf: (Char) -> Int): String {
        if (!raw.contains("§z")) return raw
        val clampedSpeed = speed.coerceIn(10, 5000)
        val builder = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            if (raw[index] == '§' && index + 1 < raw.length && raw[index + 1] == 'z') {
                val start = index + 2
                var end = start
                while (end < raw.length && !(raw[end] == '§' && end + 1 < raw.length)) end++
                builder.append(chromaSpan(raw.substring(start, end), elapsedMillis, clampedSpeed, widthOf))
                index = end
            } else {
                builder.append(raw[index])
                index++
            }
        }
        return builder.toString()
    }

    private fun chromaSpan(
        span: String,
        elapsedMillis: Long,
        speed: Int,
        widthOf: (Char) -> Int,
    ): String {
        val builder = StringBuilder(span.length * 2)
        var length = 0
        for (character in span) {
            val index = (((0f + length / 12f) - elapsedMillis / speed.toFloat()).toInt() % CHROMA_CODES.size +
                CHROMA_CODES.size) % CHROMA_CODES.size
            length += widthOf(character)
            builder.append('§').append(CHROMA_CODES[index]).append(character)
        }
        return builder.toString()
    }

    /** The 1-based master star a glyph represents, or null when [character] is not a master star. */
    fun masterStarIndex(character: Char): Int? =
        (character.code - MASTER_STAR_FIRST.code).takeIf { character in MASTER_STAR_FIRST..MASTER_STAR_LAST }?.plus(1)

    /** True for the master-star glyphs and the `✪` shortcut, which render in the star font. */
    fun isStarGlyph(character: Char): Boolean =
        character == MASTER_STAR_GLYPH || character in MASTER_STAR_FIRST..MASTER_STAR_LAST

    private val LEGACY_COLOR_RGB: List<Pair<Char, Int>> = listOf(
        '0' to 0x000000,
        '1' to 0x0000AA,
        '2' to 0x00AA00,
        '3' to 0x00AAAA,
        '4' to 0xAA0000,
        '5' to 0xAA00AA,
        '6' to 0xFFAA00,
        '7' to 0xAAAAAA,
        '8' to 0x555555,
        '9' to 0x5555FF,
        'a' to 0x55FF55,
        'b' to 0x55FFFF,
        'c' to 0xFF5555,
        'd' to 0xFF55FF,
        'e' to 0xFFFF55,
        'f' to 0xFFFFFF,
    )

    /** Converts styled text back to legacy `§` codes so it can be edited in the text field. */
    fun toLegacy(component: Component): String {
        val out = StringBuilder()
        var last: Style? = null
        component.visualOrderText.accept { _, style, codePoint ->
            if (style != last) {
                appendStyle(out, style)
                last = style
            }
            out.appendCodePoint(codePoint)
            true
        }
        return out.toString()
    }

    private fun appendStyle(out: StringBuilder, style: Style) {
        out.append("§r")
        style.color?.let { color ->
            val rgb = color.value
            val code = LEGACY_COLOR_RGB.firstOrNull { it.second == rgb }?.first
            if (code != null) {
                out.append('§').append(code)
            } else {
                out.append("§x")
                for (shift in intArrayOf(20, 16, 12, 8, 4, 0)) {
                    out.append('§').append("0123456789abcdef"[(rgb shr shift) and 0xF])
                }
            }
        }
        if (style.isObfuscated) out.append("§k")
        if (style.isBold) out.append("§l")
        if (style.isStrikethrough) out.append("§m")
        if (style.isUnderlined) out.append("§n")
        if (style.isItalic) out.append("§o")
    }
}
