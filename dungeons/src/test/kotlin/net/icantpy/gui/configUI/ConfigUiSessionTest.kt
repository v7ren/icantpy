package net.icantpy.gui.configUI

import net.icantpy.gui.clampScroll
import net.icantpy.modules.impl.dungeon.leaporient.LeapDungeonClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigUiSessionTest {
    @Test
    fun presetViewIsRememberedUntilSessionReset() {
        assertEquals(null, ConfigUiSession.leapViewClass)
        ConfigUiSession.leapViewClass = LeapDungeonClass.MAGE
        ConfigUiSession.save(ConfigTab.LEAP, 100)
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
        ConfigUiSession.save(ConfigTab.LEAP, 240)
        assertEquals(ConfigTab.LEAP, ConfigUiSession.tab)
        assertEquals(240, ConfigUiSession.scrollOf(ConfigTab.LEAP))
    }

    @Test
    fun eachTabRemembersItsOwnScroll() {
        ConfigUiSession.save(ConfigTab.LEAP, 180)
        ConfigUiSession.save(ConfigTab.LOOK, 40)
        assertEquals(ConfigTab.LOOK, ConfigUiSession.tab)
        assertEquals(180, ConfigUiSession.scrollOf(ConfigTab.LEAP))
        assertEquals(40, ConfigUiSession.scrollOf(ConfigTab.LOOK))
        assertEquals(0, ConfigUiSession.scrollOf(ConfigTab.TIMERS))
    }

    @Test
    fun negativeScrollIsStoredAsZero() {
        ConfigUiSession.save(ConfigTab.ALERTS, -12)
        assertEquals(0, ConfigUiSession.scrollOf(ConfigTab.ALERTS))
    }

    @Test
    fun emptyContentClampMustNotBeSavedBack() {
        ConfigUiSession.save(ConfigTab.LEAP, 240)
        val wiped = clampScroll(ConfigUiSession.scrollOf(ConfigTab.LEAP), contentHeight = 0, viewHeight = 200)
        assertEquals(0, wiped)
        assertEquals(240, ConfigUiSession.scrollOf(ConfigTab.LEAP))
    }
}
