package net.icantpy.gui.configUI

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuiLookTest {
    @Test
    fun unknownNamesFallBackToDefaults() {
        val look = GuiLookSettings.fromJson(
            JsonParser.parseString("""{"layout":"nope","theme":"","font":"comic","radius":99,"blur":true}""").asJsonObject,
        )
        assertEquals(GuiLayoutId.RAIL, look.layout)
        assertEquals(GuiThemeId.MIDNIGHT, look.theme)
        assertEquals(GuiFontId.SEGOE, look.font)
        assertEquals(12, look.radius)
        assertTrue(look.backgroundBlur)
    }

    @Test
    fun jsonRoundTripKeepsLayoutThemeAndFont() {
        val look = GuiLookSettings(
            layout = GuiLayoutId.COMPACT,
            theme = GuiThemeId.PAPER,
            font = GuiFontId.CONSOLAS,
            accentHex = "1F5EFF",
            radius = 0,
            controlRadius = 8,
            backgroundBlur = true,
        )
        val parsed = GuiLookSettings.fromJson(GuiLookSettings.toJson(look))
        assertEquals(look, parsed)
        assertEquals(0x1F5EFF, parsed.resolvedAccent())
    }

    @Test
    fun emptyAccentUsesThemeColor() {
        assertNull(GuiLookSettings().resolvedAccent())
        val midnight = GuiThemeId.MIDNIGHT.palette(null)
        val overridden = GuiThemeId.MIDNIGHT.palette(0xFF5C8A)
        assertEquals(0xFFFAFAFA.toInt(), midnight.accent)
        assertEquals(0xFFFF5C8A.toInt(), overridden.accent)
        assertTrue(overridden.accent != midnight.accent)
    }

    @Test
    fun threeLayoutsChangeChrome() {
        val rail = ConfigMetrics.compute(800, 480, GuiLookSettings(layout = GuiLayoutId.RAIL))
        val ribbon = ConfigMetrics.compute(800, 480, GuiLookSettings(layout = GuiLayoutId.RIBBON))
        val compact = ConfigMetrics.compute(800, 480, GuiLookSettings(layout = GuiLayoutId.COMPACT))
        assertTrue(rail.sidebar > compact.sidebar)
        assertEquals(0, ribbon.sidebar)
        assertTrue(ribbon.tabBar > 0)
        assertEquals(0, rail.tabBar)
        assertTrue(compact.row < rail.row)
        assertEquals(GuiLookSettings().font.next(), GuiFontId.TAHOMA)
        assertEquals(GuiFontId.NUNITO, GuiFontId.SEGOE.previous())
    }

    @Test
    fun railNavFitsOnDefault1080pGuiScale() {
        val rail = ConfigMetrics.compute(480, 270, GuiLookSettings(layout = GuiLayoutId.RAIL))
        val lastBottom = rail.panelY + rail.header + 4 + rail.railNavPitch() * ConfigTab.entries.size
        assertTrue(lastBottom <= rail.panelY + rail.panelH)
        val compact = ConfigMetrics.compute(480, 270, GuiLookSettings(layout = GuiLayoutId.COMPACT))
        val compactBottom = compact.panelY + 8 + compact.compactNavPitch() * ConfigTab.entries.size
        assertTrue(compactBottom <= compact.panelY + compact.panelH)
    }

    @Test
    fun radiusZeroMakesCardsSquare() {
        val chrome = GuiChrome.of(GuiLookSettings(layout = GuiLayoutId.RIBBON, radius = 8))
        assertEquals(0f, chrome.cardRadius)
        assertEquals(8f, chrome.radius)
        assertEquals(0f, chrome.controlRadius)
        assertEquals(8f, GuiChrome.of(GuiLookSettings(controlRadius = 8)).controlRadius)
        val compact = GuiChrome.of(GuiLookSettings(layout = GuiLayoutId.COMPACT, radius = 8))
        assertEquals(0f, compact.cardRadius)
        assertFalse(GuiLookSettings().backgroundBlur)
    }
}
