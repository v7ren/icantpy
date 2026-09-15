package net.icantpy.modules.impl.appearance

object HexColor {
    private val HEX = Regex("^#?[0-9a-fA-F]{6}$")

    fun parse(raw: String): Result<Int> {
        val trimmed = raw.trim()
        if (!HEX.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Expected RRGGBB or #RRGGBB, got '$raw'"))
        }
        val hex = trimmed.removePrefix("#")
        return Result.success(hex.toInt(16))
    }

    fun format(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)
}
