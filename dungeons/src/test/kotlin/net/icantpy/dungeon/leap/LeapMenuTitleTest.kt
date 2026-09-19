package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapMenuTitleTest {
    @Test
    fun odinExactTitlesMatch() {
        assertTrue(LeapMenu.isLeapTitle("Spirit Leap"))
        assertTrue(LeapMenu.isLeapTitle("Teleport to Player"))
    }

    @Test
    fun skyblockerContainsSpiritLeap() {
        assertTrue(LeapMenu.isLeapTitle("spirit leap"))
        assertTrue(LeapMenu.isLeapTitle("§5Spirit Leap"))
    }

    @Test
    fun skyblockMenusDoNotMatch() {
        assertFalse(LeapMenu.isLeapTitle("SkyBlock Menu"))
        assertFalse(LeapMenu.isLeapTitle("Chest"))
        assertFalse(LeapMenu.isLeapTitle("Bags"))
        assertFalse(LeapMenu.isLeapTitle("Auctions Browser"))
        assertFalse(LeapMenu.isLeapTitle("InfiniLeap"))
        assertFalse(LeapMenu.isLeapTitle("Skyblocker Leap Overlay"))
    }
}
