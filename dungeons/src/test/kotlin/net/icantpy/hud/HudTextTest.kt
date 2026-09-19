package net.icantpy.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudTextTest {
    @Test
    fun stripColorsKeepsBold() {
        assertEquals("leave pad", HudText.plain("§e§lleave pad"))
        assertEquals("§lleave pad", HudText.stripColors("§e§lleave pad"))
    }

    @Test
    fun wrapBreaksLongAlertText() {
        val lines = HudText.wrap("§e§lcrush now or you will die in storm", 40) { it.replace("§e", "").replace("§l", "").length * 4 }
        assertTrue(lines.size >= 2)
        assertTrue(lines.all { it.contains("§") || it.isNotEmpty() })
    }

    @Test
    fun trailingCodesCarryBoldAndColor() {
        assertEquals("§e§l", HudText.trailingCodes("§e§lcrush"))
        assertEquals("", HudText.trailingCodes("§e§lcrush§r"))
    }

    @Test
    fun noWrapWhenUnderMaxWidth() {
        assertEquals(listOf("short"), HudText.wrap("short", 200) { it.length })
    }
}
