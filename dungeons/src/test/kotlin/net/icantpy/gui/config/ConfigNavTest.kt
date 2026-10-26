package net.icantpy.gui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigNavTest {
    @Test
    fun everyFeatureBelongsToExactlyOnePage() {
        val owned = ConfigPage.entries.flatMap { it.moduleIds }
        assertEquals(owned.size, owned.toSet().size, "a feature is listed on more than one page")
        assertEquals(AetherModuleId.entries.toSet(), owned.toSet(), "a feature is on no page")
    }

    @Test
    fun everyFeatureHasADisplayName() {
        AetherModuleId.entries.forEach { id ->
            val module = AetherModules.byId(id)
            assertTrue(module != null && module.name.isNotBlank(), "missing name for $id")
        }
    }

    @Test
    fun leapSearchFindsSpiritLeap() {
        assertTrue(ConfigPage.LEAP in ConfigNav.search("leap"))
    }

    @Test
    fun searchMatchesSubtabNamesToo() {
        assertTrue(ConfigPage.LEAP in ConfigNav.search("keybinds"))
        assertTrue(ConfigPage.APPEARANCE in ConfigNav.search("text & shape"))
    }

    @Test
    fun sortSearchFindsInventorySort() {
        assertTrue(ConfigPage.INVENTORY_SORT in ConfigNav.search("sort"))
    }

    @Test
    fun unknownSearchIsEmpty() {
        assertEquals(emptyList(), ConfigNav.search("zzz"))
    }

    @Test
    fun blankSearchMatchesNothing() {
        assertEquals(emptyList(), ConfigNav.search("   "))
        assertEquals(emptyList(), ConfigNav.search(""))
    }

    @Test
    fun multiWordSearchRequiresEveryWord() {
        val results = ConfigNav.search("stats masks")
        assertTrue(ConfigPage.STATS in results)
        assertFalse(ConfigPage.FREELOOK in results)
        assertEquals(emptyList(), ConfigNav.search("leap zzz"))
    }

    @Test
    fun searchMatchesSectionAndFeatureNames() {
        assertTrue(ConfigPage.FREELOOK in ConfigNav.search("utility"))
        assertTrue(ConfigPage.CLOCKS in ConfigNav.search("mechanic"))
        assertTrue(ConfigPage.STATS in ConfigNav.search("Stats"))
    }

    @Test
    fun searchIsCaseInsensitive() {
        assertEquals(ConfigNav.search("leap"), ConfigNav.search("LEAP"))
    }

    @Test
    fun overviewCardsCoverEveryFeaturePageExceptOverviewAndHudLayout() {
        val cards = ConfigNav.overviewPages()
        assertFalse(ConfigPage.OVERVIEW in cards)
        assertFalse(ConfigPage.HUD_LAYOUT in cards)
        assertTrue(cards.all { it.primaryModuleId != null })
        assertEquals(
            ConfigPage.entries.filter { it.moduleIds.isNotEmpty() && !it.opensHudEditor }.size,
            cards.size,
        )
    }
}
