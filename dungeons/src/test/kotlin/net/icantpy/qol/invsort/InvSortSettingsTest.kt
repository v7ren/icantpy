package net.icantpy.qol.invsort

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvSortSettingsTest {
    private val named = listOf(
        InvSortPreset("Combat", hotkey = 70),
        InvSortPreset("Mining", hotkey = InvSortLimits.NO_HOTKEY),
    )
    private val settings = InvSortSettings(presets = named)

    @Test fun hotkeyOwnerFindsBoundPresetOnly() {
        assertEquals(0, settings.hotkeyOwner(70))
        assertNull(settings.hotkeyOwner(71))
        assertNull(settings.hotkeyOwner(InvSortLimits.NO_HOTKEY))
    }

    @Test fun hotkeyOwnerIgnoresTheEditedPreset() {
        assertNull(settings.hotkeyOwner(70, exceptIndex = 0))
    }

    @Test fun setHotkeyBindsOnlyTheTargetPreset() {
        val updated = settings.setHotkey(1, 80)
        assertEquals(listOf(70, 80), updated.presets.map { it.hotkey })
    }

    @Test fun setHotkeyOnMissingPresetIsIgnored() {
        assertEquals(settings, settings.setHotkey(5, 80))
    }

    @Test fun uniqueHotkeysKeepFirstOwner() {
        val presets = listOf(
            InvSortPreset("A", hotkey = 1),
            InvSortPreset("B", hotkey = 1),
            InvSortPreset("C", hotkey = -1),
        )
        assertEquals(listOf(1, -1, -1), InvSortSettings.withUniqueHotkeys(presets).map { it.hotkey })
    }

    private val ordered = InvSortSettings(
        presets = listOf(
            InvSortPreset("A", slots = mapOf(9 to InvSortSlot("k-a", "minecraft:stone", "Stone")), hotkey = 65),
            InvSortPreset("B", hotkey = InvSortLimits.NO_HOTKEY),
            InvSortPreset("C", hotkey = 67),
        ),
    )

    @Test fun movePresetDownShiftsPresetsBetween() {
        assertEquals(listOf("B", "C", "A"), ordered.movePreset(0, 2).presets.map { it.name })
    }

    @Test fun movePresetUpShiftsPresetsBetween() {
        assertEquals(listOf("C", "A", "B"), ordered.movePreset(2, 0).presets.map { it.name })
    }

    @Test fun movePresetAdjacentSwapsTwo() {
        assertEquals(listOf("B", "A", "C"), ordered.movePreset(1, 0).presets.map { it.name })
    }

    @Test fun movedPresetKeepsSlotsAndHotkey() {
        val moved = ordered.movePreset(0, 2).presets[2]
        assertEquals(ordered.presets[0], moved)
        assertEquals(65, moved.hotkey)
    }

    @Test fun movePresetLeavesOriginalSettingsUnchanged() {
        ordered.movePreset(0, 2)
        assertEquals(listOf("A", "B", "C"), ordered.presets.map { it.name })
    }

    @Test fun movePresetOutOfRangeOrSameIndexIsIgnored() {
        assertEquals(ordered, ordered.movePreset(-1, 0))
        assertEquals(ordered, ordered.movePreset(0, 3))
        assertEquals(ordered, ordered.movePreset(7, 1))
        assertEquals(ordered, ordered.movePreset(1, 1))
    }

    @Test fun delayRangeNormalizesBoundsAndOrder() {
        assertEquals(0 to 1000, InvSortSettings.normalizeDelayRange(-20, 5_000))
        assertEquals(0 to 0, InvSortSettings.normalizeDelayRange(0, 0))
        assertEquals(100 to 300, InvSortSettings.normalizeDelayRange(300, 100))
    }
}
