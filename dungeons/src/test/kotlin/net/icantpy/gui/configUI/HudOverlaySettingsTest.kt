package net.icantpy.gui.configUI

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudOverlaySettingsTest {
    @Test
    fun jsonRoundTrip() {
        val hud = HudOverlaySettings(
            timerScale = 200,
            notifyScale = 250,
            notifyWidth = 160,
            timerFont = HudFontId.CONSOLAS,
            notifyFont = HudFontId.SEGOE,
            notifyFontSize = 18,
            notifyTextRgb = 0xFF5555,
            notifyBoxRgb = 0x112233,
            notifyBoxAlpha = 80,
            notifyBold = false,
            lockNotifyColor = false,
            alertSound = "pling",
        )
        assertEquals(hud, HudOverlaySettings.fromJson(HudOverlaySettings.toJson(hud)))
    }

    @Test
    fun clampsWidthAndScale() {
        val parsed = HudOverlaySettings.fromJson(
            JsonParser.parseString("""{"timerScale":9,"notifyScale":9000,"notifyWidth":10}""").asJsonObject,
        )
        assertEquals(25, parsed.timerScale)
        assertEquals(800, parsed.notifyScale)
        assertEquals(40, parsed.notifyWidth)
        assertEquals(8.0f, parsed.notifyScaleValue())
        assertTrue(parsed.lockNotifyColor)
        assertEquals(HudOverlaySettings.DEFAULT_NOTIFY_TEXT, parsed.notifyTextRgb)
    }

    @Test
    fun fontCycleIncludesMinecraft() {
        assertEquals(HudFontId.SEGOE, HudFontId.MINECRAFT.next())
        assertEquals(HudFontId.NUNITO, HudFontId.MINECRAFT.previous())
        assertEquals(null, HudFontId.MINECRAFT.guiFont())
        assertEquals(GuiFontId.ARIAL, HudFontId.ARIAL.guiFont())
    }
}
