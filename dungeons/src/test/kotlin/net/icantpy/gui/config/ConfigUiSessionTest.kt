package net.icantpy.gui.config

import net.icantpy.gui.clampScroll
import net.icantpy.dungeon.leap.LeapDungeonClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigUiSessionTest {
    @AfterTest
    fun resetSession() {
        ConfigUiSession.reset()
    }

    @Test
    fun shardShortcutOpensShardsAtTopWithoutResettingOtherPages() {
        ConfigUiSession.select(ConfigPage.LEAP)
        ConfigUiSession.save(ConfigPage.LEAP, 0, 120)
        ConfigUiSession.save(ConfigPage.ALERTS, 0, 180)
        ConfigUiSession.selectShards()
        assertEquals(ConfigPage.SHARDS, ConfigUiSession.page)
        assertEquals(0, ConfigUiSession.scrollOf(ConfigPage.SHARDS))
        assertEquals(120, ConfigUiSession.scrollOf(ConfigPage.LEAP, 0))
        assertEquals(180, ConfigUiSession.scrollOf(ConfigPage.ALERTS, 0))
    }

    @Test
    fun presetViewIsRememberedUntilSessionReset() {
        assertEquals(null, ConfigUiSession.leapViewClass)
        ConfigUiSession.leapViewClass = LeapDungeonClass.MAGE
        ConfigUiSession.save(ConfigPage.CLOCKS, 0, 100)
        assertEquals(LeapDungeonClass.MAGE, ConfigUiSession.leapViewClass)
        ConfigUiSession.reset()
        assertEquals(null, ConfigUiSession.leapViewClass)
    }

    @Test
    fun reopenKeepsTheLastPageAndScroll() {
        ConfigUiSession.save(ConfigPage.CLOCKS, 0, 240)
        assertEquals(ConfigPage.CLOCKS, ConfigUiSession.page)
        assertEquals(240, ConfigUiSession.scrollOf(ConfigPage.CLOCKS))
    }

    @Test
    fun resetReturnsToOverviewAndClearsScroll() {
        ConfigUiSession.save(ConfigPage.CLOCKS, 0, 240)
        ConfigUiSession.reset()
        assertEquals(ConfigPage.OVERVIEW, ConfigUiSession.page)
        assertEquals(0, ConfigUiSession.scrollOf(ConfigPage.CLOCKS))
        assertEquals(0, ConfigUiSession.subtabOf(ConfigPage.LEAP))
    }

    @Test
    fun eachPageRemembersItsOwnScroll() {
        ConfigUiSession.save(ConfigPage.CLOCKS, 0, 180)
        ConfigUiSession.save(ConfigPage.APPEARANCE, 0, 40)
        assertEquals(ConfigPage.APPEARANCE, ConfigUiSession.page)
        assertEquals(180, ConfigUiSession.scrollOf(ConfigPage.CLOCKS))
        assertEquals(40, ConfigUiSession.scrollOf(ConfigPage.APPEARANCE))
        assertEquals(0, ConfigUiSession.scrollOf(ConfigPage.ITEMS))
    }

    @Test
    fun eachSubtabRemembersItsOwnScrollWithinThePage() {
        ConfigUiSession.select(ConfigPage.LEAP)
        ConfigUiSession.save(ConfigPage.LEAP, 0, 50)
        ConfigUiSession.selectSubtab(1)
        assertEquals(1, ConfigUiSession.subtabOf(ConfigPage.LEAP))
        assertEquals(0, ConfigUiSession.scrollOf(ConfigPage.LEAP))
        ConfigUiSession.save(ConfigPage.LEAP, 1, 70)
        ConfigUiSession.selectSubtab(0)
        assertEquals(50, ConfigUiSession.scrollOf(ConfigPage.LEAP))
        assertEquals(70, ConfigUiSession.scrollOf(ConfigPage.LEAP, 1))
    }

    @Test
    fun subtabSelectionIsClampedToThePageTabs() {
        ConfigUiSession.select(ConfigPage.LEAP)
        ConfigUiSession.selectSubtab(9)
        assertEquals(ConfigPage.LEAP.subtabs.lastIndex, ConfigUiSession.subtabOf(ConfigPage.LEAP))
        ConfigUiSession.selectSubtab(-4)
        assertEquals(0, ConfigUiSession.subtabOf(ConfigPage.LEAP))
    }

    @Test
    fun pagesWithoutSubtabsIgnoreSubtabSelection() {
        ConfigUiSession.select(ConfigPage.ALERTS)
        ConfigUiSession.selectSubtab(1)
        assertEquals(0, ConfigUiSession.subtabOf(ConfigPage.ALERTS))
    }

    @Test
    fun negativeScrollIsStoredAsZero() {
        ConfigUiSession.save(ConfigPage.FREELOOK, 0, -12)
        assertEquals(0, ConfigUiSession.scrollOf(ConfigPage.FREELOOK))
    }

    @Test
    fun emptyContentClampMustNotBeSavedBack() {
        ConfigUiSession.save(ConfigPage.CLOCKS, 0, 240)
        val wiped = clampScroll(ConfigUiSession.scrollOf(ConfigPage.CLOCKS), contentHeight = 0, viewHeight = 200)
        assertEquals(0, wiped)
        assertEquals(240, ConfigUiSession.scrollOf(ConfigPage.CLOCKS))
    }

    @Test
    fun cosmeticsShortcutOpensItemsPage() {
        ConfigUiSession.select(ConfigPage.STATS)
        ConfigUiSession.selectCosmeticsItems()
        assertEquals(ConfigPage.ITEMS, ConfigUiSession.page)
    }

    @Test
    fun selectingPagesOnlyChangesTheOpenPage() {
        ConfigUiSession.select(ConfigPage.LEAP)
        ConfigUiSession.select(ConfigPage.WAYPOINTS)
        assertEquals(ConfigPage.WAYPOINTS, ConfigUiSession.page)
        assertEquals(0, ConfigUiSession.subtabOf(ConfigPage.LEAP))
    }
}
