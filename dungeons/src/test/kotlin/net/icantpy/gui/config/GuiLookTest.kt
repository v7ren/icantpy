package net.icantpy.gui.config

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
        assertEquals(0xFFC9BBFF.toInt(), midnight.accent)
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
        assertTrue(rail.pageBar > 0)
        assertTrue(rail.viewTop > rail.panelY + rail.header)
        assertTrue(ribbon.pageBar > 0)
    }

    @Test
    fun railSidebarScrollsWhenPagesDoNotFitTheDefaultGuiScale() {
        val rail = ConfigMetrics.compute(480, 270, GuiLookSettings(layout = GuiLayoutId.RAIL))
        val viewport = sidebarBottom(rail) - sidebarTop(rail)
        assertTrue(sidebarContentHeight() > viewport)
        // The brand mark occupies the top 32px of the rail; the page list must start below it.
        assertTrue(sidebarTop(rail) - rail.panelY >= 32)
    }

    @Test
    fun railSidebarIsWideEnoughForPageNamesAndPanelHeightIsCapped() {
        val rail = ConfigMetrics.compute(1920, 1200, GuiLookSettings(layout = GuiLayoutId.RAIL))
        assertTrue(rail.sidebar >= 156)
        assertEquals(460, rail.panelH)
        assertEquals(700, rail.panelW)
        val shortScreen = ConfigMetrics.compute(800, 360, GuiLookSettings(layout = GuiLayoutId.RAIL))
        assertEquals(324, shortScreen.panelH)
    }

    @Test
    fun radiusZeroMakesCardsSquare() {
        val chrome = GuiChrome.of(GuiLookSettings(layout = GuiLayoutId.RIBBON, radius = 8, controlRadius = 0))
        assertEquals(0f, chrome.cardRadius)
        assertEquals(8f, chrome.radius)
        assertEquals(0f, chrome.controlRadius)
        assertEquals(8f, GuiChrome.of(GuiLookSettings(controlRadius = 8)).controlRadius)
        val compact = GuiChrome.of(GuiLookSettings(layout = GuiLayoutId.COMPACT, radius = 8))
        assertEquals(0f, compact.cardRadius)
        assertTrue(GuiLookSettings().backgroundBlur)
    }

    @Test
    fun featureInventoryMatchesReferenceSections() {
        assertEquals(24, AetherModules.all.size)
        fun featureCount(section: ConfigSection) = ConfigPage.entries
            .filter { it.section == section }
            .sumOf { it.moduleIds.size }
        assertEquals(7, featureCount(ConfigSection.DUNGEONS))
        assertEquals(8, featureCount(ConfigSection.QOL))
        assertEquals(2, featureCount(ConfigSection.INTERFACE))
        assertTrue(ConfigPage.LEAP in ConfigNav.search("leap"))
    }

    @Test
    fun fontStylesUseConstructorWeights() {
        assertEquals(400, SkiaFonts.normal.weight)
        assertEquals(700, SkiaFonts.bold.weight)
    }
}
