package net.icantpy.hud

/**
 * Wraps Minecraft-style § colored strings so a HUD box can stay a fixed width.
 */
object HudText {
    fun wrap(text: String, maxWidth: Int, widthOf: (String) -> Int): List<String> {
        if (text.isEmpty()) return emptyList()
        if (text.contains('\n')) {
            return text.split('\n').flatMap { line -> wrap(line, maxWidth, widthOf) }
        }
        if (maxWidth <= 0 || widthOf(text) <= maxWidth) return listOf(text)
        val words = splitWords(text)
        if (words.isEmpty()) return listOf(text)
        val lines = ArrayList<String>()
        var current = ""
        var codes = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) codes + word.trimStart() else "$current $word"
            if (widthOf(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                codes = trailingCodes(current)
                current = codes + word.trimStart()
            }
            codes = trailingCodes(current)
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    fun plain(text: String): String {
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            if (text[index] == '§' && index + 1 < text.length) {
                index += 2
            } else {
                out.append(text[index])
                index += 1
            }
        }
        return out.toString()
    }

    fun stripColors(text: String): String {
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            if (text[index] == '§' && index + 1 < text.length) {
                val code = text[index + 1].lowercaseChar()
                if (code == 'l' || code == 'o' || code == 'n' || code == 'm' || code == 'k' || code == 'r') {
                    out.append('§').append(text[index + 1])
                }
                index += 2
            } else {
                out.append(text[index])
                index += 1
            }
        }
        return out.toString()
    }

    fun trailingCodes(text: String): String {
        val kept = StringBuilder()
        var index = 0
        while (index < text.length) {
            if (text[index] == '§' && index + 1 < text.length) {
                val code = text[index + 1]
                if (code == 'r' || code == 'R') {
                    kept.clear()
                } else {
                    kept.append('§').append(code)
                }
                index += 2
            } else {
                index += 1
            }
        }
        return kept.toString()
    }

    private fun splitWords(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }
}
