package net.icantpy.gui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigTabTest {
    @Test
    fun sidebarGroupsFollowTheInformationArchitecture() {
        assertEquals(
            listOf("Home", "Dungeons", "Cosmetics", "Quality of life", "Slayer", "Interface"),
            ConfigSection.entries.map { it.label },
        )
        assertEquals(listOf("Overview"), pageTitles(ConfigSection.HOME))
        assertEquals(
            listOf("Clocks", "Alerts", "Splits & PBs", "Score", "Wither Dragons", "Spirit Leap"),
            pageTitles(ConfigSection.DUNGEONS),
        )
        assertEquals(listOf("Item Customize", "Morph", "Visuals"), pageTitles(ConfigSection.COSMETICS))
        assertEquals(
            listOf("Freelook", "Waypoints", "Stats & Loadouts", "Attribute Shards", "Inventory Sort", "Drop Ult", "Performance"),
            pageTitles(ConfigSection.QOL),
        )
        assertEquals(listOf("Carry Counter", "Fire Pits", "Boss Aim"), pageTitles(ConfigSection.SLAYER))
        assertEquals(listOf("Appearance", "HUD Layout"), pageTitles(ConfigSection.INTERFACE))
    }

    @Test
    fun pageTitlesStayUniqueAndInDeclarationOrder() {
        val titles = ConfigPage.entries.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
        assertEquals(ConfigPage.entries.sortedBy { it.section.ordinal }, ConfigPage.entries)
    }

    @Test
    fun subtabsSplitTheCrowdedPagesInTabOrder() {
        assertEquals(listOf("Routes", "Layout", "Look", "Quick leap", "Keybinds", "Debug"), ConfigPage.LEAP.subtabs)
        assertEquals(listOf("Display", "Dungeon", "Mechanics", "Debug"), ConfigPage.CLOCKS.subtabs)
        assertEquals(listOf("Customize", "Sharing"), ConfigPage.ITEMS.subtabs)
        assertEquals(listOf("Counter", "Keys", "Players"), ConfigPage.CARRIES.subtabs)
        assertEquals(listOf("Theme", "Text & shape", "Advanced"), ConfigPage.APPEARANCE.subtabs)
        assertEquals(listOf("Cleanup", "World", "Camera", "Viewmodel", "Hand", "Effects", "Model"), ConfigPage.VISUALS.subtabs)
    }

    @Test
    fun onlyTheSplitPagesHaveSubtabsAndOnlyHudLayoutLeavesThePanel() {
        assertEquals(
            setOf(ConfigPage.LEAP, ConfigPage.CLOCKS, ConfigPage.ITEMS, ConfigPage.CARRIES, ConfigPage.APPEARANCE, ConfigPage.VISUALS),
            ConfigPage.entries.filter { it.subtabs.isNotEmpty() }.toSet(),
        )
        assertEquals(listOf(ConfigPage.HUD_LAYOUT), ConfigPage.entries.filter { it.opensHudEditor })
        assertFalse(ConfigPage.OVERVIEW.opensHudEditor)
    }

    private fun pageTitles(section: ConfigSection): List<String> =
        ConfigPage.entries.filter { it.section == section }.map { it.title }

    @Test
    fun sidebarGroupsSkipNothingVisible() {
        val groups = ConfigNav.sidebarGroups()
        assertEquals(ConfigSection.entries.size, groups.size)
        assertTrue(groups.all { (_, pages) -> pages.isNotEmpty() })
    }
}
