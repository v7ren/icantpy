package net.icantpy.util

object Text {
    private val COLOR = Regex("§.")

    fun strip(raw: String): String =
        COLOR.replace(raw, "").trim()
}
