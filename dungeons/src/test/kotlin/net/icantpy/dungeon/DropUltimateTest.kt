package net.icantpy.dungeon

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DropUltimateTest {
    @Test
    fun enabledOnlySwapsInsideDungeons() {
        assertTrue(swap(enabled = true, inDungeon = true))
        assertFalse(swap(enabled = true, inDungeon = false))
    }

    @Test
    fun disabledNeverSwaps() {
        assertFalse(swap(enabled = false, inDungeon = true))
    }

    @Test
    fun openScreenOrSpectatorBlocksSwap() {
        assertFalse(swap(enabled = true, inDungeon = true, screenOpen = true))
        assertFalse(swap(enabled = true, inDungeon = true, spectator = true))
    }

    @Test
    fun alreadyOnTargetSlotLeavesVanillaDropAlone() {
        assertFalse(swap(enabled = true, inDungeon = true, previousSlot = 8, targetSlot = 8))
        assertTrue(swap(enabled = true, inDungeon = true, previousSlot = 0, targetSlot = 8))
    }

    @Test
    fun customTargetSlotIsRespected() {
        assertTrue(swap(enabled = true, inDungeon = true, previousSlot = 0, targetSlot = 2))
        assertFalse(swap(enabled = true, inDungeon = true, previousSlot = 2, targetSlot = 2))
    }

    @Test
    fun skyblockMenuTitleIsMatchedCaseInsensitively() {
        assertTrue(DropUltimate.isSkyblockMenu("SkyBlock Menu"))
        assertTrue(DropUltimate.isSkyblockMenu("  skyblock menu  "))
        assertFalse(DropUltimate.isSkyblockMenu("Spirit Leap"))
        assertFalse(DropUltimate.isSkyblockMenu(""))
    }

    @Test
    fun settingsDefaultToDisabledSlotNineWithMenuClose() {
        val defaults = DropUltimateSettings.fromJson(JsonObject())
        assertFalse(defaults.enabled)
        assertEquals(9, defaults.slot)
        assertEquals(9, defaults.stackSlot)
        assertEquals(DropUltimateSettings.UNBOUND, defaults.stackKey)
        assertTrue(defaults.closeSkyblockMenu)
    }

    @Test
    fun settingsSlotIsClampedAndMapsToZeroBasedIndex() {
        assertEquals(0, DropUltimateSettings(slot = 1).targetIndex())
        assertEquals(8, DropUltimateSettings(slot = 9).targetIndex())
        assertEquals(8, DropUltimateSettings(slot = 42).targetIndex())
        assertEquals(0, DropUltimateSettings(slot = -3).targetIndex())
        assertEquals(8, DropUltimateSettings.fromJson(JsonObject().apply { addProperty("slot", 99) }).targetIndex())
    }

    @Test
    fun stackSlotIsIndependentAndClamped() {
        val settings = DropUltimateSettings(slot = 3, stackSlot = 7)
        assertEquals(2, settings.targetIndex())
        assertEquals(6, settings.stackTargetIndex())
        assertEquals(8, DropUltimateSettings(stackSlot = 99).stackTargetIndex())
        assertEquals(0, DropUltimateSettings(stackSlot = 0).stackTargetIndex())
    }

    @Test
    fun settingsRoundTripThroughJson() {
        val custom = DropUltimateSettings(
            enabled = true,
            slot = 4,
            stackSlot = 6,
            stackKey = 86,
            closeSkyblockMenu = false,
        )
        assertEquals(custom, DropUltimateSettings.fromJson(custom.toJson()))
    }

    private fun swap(
        enabled: Boolean,
        inDungeon: Boolean,
        screenOpen: Boolean = false,
        spectator: Boolean = false,
        previousSlot: Int = 0,
        targetSlot: Int = 8,
    ): Boolean = DropUltimate.shouldSwap(
        enabled = enabled,
        inDungeon = inDungeon,
        screenOpen = screenOpen,
        spectator = spectator,
        previousSlot = previousSlot,
        targetSlot = targetSlot,
    )
}
