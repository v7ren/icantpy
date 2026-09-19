package net.icantpy.gui.config

import net.icantpy.gui.clampScroll
import net.icantpy.dungeon.leap.LeapDungeonClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigUiSessionTest {
    @Test
    fun shardsIsReachableFromQolPageBar() {
        ConfigUiSession.select(ConfigTab.QOL)
        ConfigUiSession.selectPage(4)
        assertEquals("Shards", ConfigUiSession.qolPage.label())
        assertEquals(4, ConfigUiSession.pageIndex())
    }

    @Test
    fun shardShortcutOpensQolShardsAtTopWithoutResettingOtherCategories() {
        ConfigUiSession.dungeonPage = DungeonPage.LEAP
        ConfigUiSession.save(ConfigTab.QOL, 180)
        ConfigUiSession.select(ConfigTab.GUI)
        ConfigUiSession.selectShards()
        assertEquals(ConfigTab.QOL, ConfigUiSession.tab)
        assertEquals(QolPage.SHARDS, ConfigUiSession.qolPage)
        assertEquals(0, ConfigUiSession.scrollOf(ConfigTab.QOL))
        assertEquals(DungeonPage.LEAP, ConfigUiSession.dungeonPage)
    }

    @Test
    fun presetViewIsRememberedUntilSessionReset() {
        assertEquals(null, ConfigUiSession.leapViewClass)
        ConfigUiSession.leapViewClass = LeapDungeonClass.MAGE
        ConfigUiSession.save(ConfigTab.DUNGEONS, 100)
        assertEquals(LeapDungeonClass.MAGE, ConfigUiSession.leapViewClass)
        ConfigUiSession.reset()
        assertEquals(null, ConfigUiSession.leapViewClass)
    }

    @AfterTest
    fun resetSession() {
        ConfigUiSession.reset()
    }

    @Test
    fun reopenKeepsTheLastTabAndScroll() {
        ConfigUiSession.save(ConfigTab.DUNGEONS, 240)
        assertEquals(ConfigTab.DUNGEONS, ConfigUiSession.tab)
        assertEquals(240, ConfigUiSession.scrollOf(ConfigTab.DUNGEONS))
    }

    @Test
    fun eachTabRemembersItsOwnScroll() {
        ConfigUiSession.save(ConfigTab.DUNGEONS, 180)
        ConfigUiSession.save(ConfigTab.GUI, 40)
        assertEquals(ConfigTab.GUI, ConfigUiSession.tab)
        assertEquals(180, ConfigUiSession.scrollOf(ConfigTab.DUNGEONS))
        assertEquals(40, ConfigUiSession.scrollOf(ConfigTab.GUI))
        assertEquals(0, ConfigUiSession.scrollOf(ConfigTab.COSMETICS))
    }

    @Test
    fun negativeScrollIsStoredAsZero() {
        ConfigUiSession.save(ConfigTab.QOL, -12)
        assertEquals(0, ConfigUiSession.scrollOf(ConfigTab.QOL))
    }

    @Test
    fun emptyContentClampMustNotBeSavedBack() {
        ConfigUiSession.save(ConfigTab.DUNGEONS, 240)
        val wiped = clampScroll(ConfigUiSession.scrollOf(ConfigTab.DUNGEONS), contentHeight = 0, viewHeight = 200)
        assertEquals(0, wiped)
        assertEquals(240, ConfigUiSession.scrollOf(ConfigTab.DUNGEONS))
    }

    @Test
    fun cosmeticsShortcutOpensItemsPage() {
        ConfigUiSession.select(ConfigTab.QOL)
        ConfigUiSession.selectCosmeticsItems()
        assertEquals(ConfigTab.COSMETICS, ConfigUiSession.tab)
        assertEquals(CosmeticsPage.ITEMS, ConfigUiSession.cosmeticsPage)
    }

    @Test
    fun pageIndexSelectsInsideTheActiveCategory() {
        ConfigUiSession.select(ConfigTab.DUNGEONS)
        ConfigUiSession.selectPage(2)
        assertEquals(DungeonPage.LEAP, ConfigUiSession.dungeonPage)
        ConfigUiSession.select(ConfigTab.QOL)
        ConfigUiSession.selectPage(1)
        assertEquals(QolPage.BOXES, ConfigUiSession.qolPage)
        assertEquals(DungeonPage.LEAP, ConfigUiSession.dungeonPage)
    }
}
