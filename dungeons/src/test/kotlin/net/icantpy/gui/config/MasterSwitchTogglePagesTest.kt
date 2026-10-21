package net.icantpy.gui.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MasterSwitchTogglePagesTest {
    @Test
    fun itemsMorphAndShardsPagesShowAMasterSwitch() {
        val pages = ConfigNav.togglePages()
        assertTrue(ConfigPage.ITEMS in pages, "Item Customize page has no toggle")
        assertTrue(ConfigPage.MORPH in pages, "Morph page has no toggle")
        assertTrue(ConfigPage.SHARDS in pages, "Attribute Shards page has no toggle")
        assertTrue(ConfigPage.FIRE_PITS in pages, "Fire Pits page has no toggle")
        assertTrue(ConfigPage.BOSS_AIM in pages, "Boss Aim page has no toggle")
    }

    @Test
    fun overrideOnlyFeaturesStillHaveNoMasterSwitch() {
        assertFalse(AetherModules.hasMasterSwitch(AetherModuleId.SHARE))
        assertFalse(AetherModules.hasMasterSwitch(AetherModuleId.LOADOUT))
        assertFalse(AetherModules.hasMasterSwitch(AetherModuleId.CLICK_GUI))
        assertFalse(AetherModules.hasMasterSwitch(AetherModuleId.HUD_EDIT))
        assertFalse(AetherModules.hasMasterSwitch(AetherModuleId.PERFORMANCE))
        assertFalse(AetherModules.hasMasterSwitch(AetherModuleId.VISUALS))
    }

    @Test
    fun formerOverrideFeaturesNowHaveRealSwitches() {
        assertTrue(AetherModules.hasMasterSwitch(AetherModuleId.RENAME))
        assertTrue(AetherModules.hasMasterSwitch(AetherModuleId.MORPH))
        assertTrue(AetherModules.hasMasterSwitch(AetherModuleId.SHARDS))
    }

    @Test
    fun togglePagesMatchPagesWithAPrimarySwitch() {
        val expected = ConfigPage.entries.filter { page ->
            page.primaryModuleId?.let(AetherModules::hasMasterSwitch) == true
        }
        assertTrue(ConfigNav.togglePages() == expected)
    }
}
