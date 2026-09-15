package net.icantpy.modules.impl.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatsArmorTest {
    @Test
    fun presetsAreGroupedByArmorSlotAndCanBeRemovedImmutably() {
        val helmet = ArmorPreset(
            id = "helmet",
            inventorySlot = 9,
            itemId = "BONZO_MASK",
            itemName = "Bonzo's Mask",
            itemUuid = "uuid-1",
        )
        val chest = ArmorPreset(
            id = "chest",
            inventorySlot = 10,
            itemId = "GOLDOR_CHESTPLATE",
            itemName = "Goldor's Chestplate",
            itemUuid = "uuid-2",
        )
        val settings = StatsArmorSettings().add(StatsArmorSlot.HEAD, helmet).add(StatsArmorSlot.CHEST, chest)

        assertEquals(listOf(helmet), settings.presets(StatsArmorSlot.HEAD))
        assertEquals(listOf(chest.copy(armorSlot = StatsArmorSlot.CHEST)), settings.presets(StatsArmorSlot.CHEST))
        assertEquals(2, settings.presets().size)
        assertEquals(2, settings.remove("missing").presets().size)
        assertEquals(1, settings.remove("helmet").presets().size)
        assertEquals(2, settings.presets().size)
    }

    @Test
    fun itemMatchingPrefersUuidButSupportsConfiguredIdAndName() {
        val configured = ItemFingerprint("BONZO_MASK", "Bonzo's Mask", "uuid-1")
        assertTrue(configured.matches(ItemFingerprint("BONZO_MASK", "Bonzo's Mask", "uuid-1")))
        assertFalse(configured.matches(ItemFingerprint("BONZO_MASK", "Bonzo's Mask", "uuid-2")))
        assertTrue(
            ItemFingerprint("BONZO_MASK", "Bonzo's Mask", null)
                .matches(ItemFingerprint("BONZO_MASK", "Bonzo's Mask", "uuid-2")),
        )
        assertTrue(
            ItemFingerprint(null, "Bonzo's Mask", null)
                .matches(ItemFingerprint(null, "Bonzo's Mask", "uuid-2")),
        )
    }

    @Test
    fun settingsRoundTripPreservesCapturedItems() {
        val original = StatsArmorSettings(
            enabled = false,
            closeAfterSwap = true,
            showMaskTimers = false,
        ).add(
            StatsArmorSlot.FEET,
            ArmorPreset(
                id = "boots",
                inventorySlot = 36,
                itemId = "SPIRIT_BOOTS",
                itemName = "Spirit Boots",
                itemUuid = "uuid-boots",
            ),
        )

        val loaded = StatsArmorSettings.fromJson(original.toJson())
        assertEquals(original, loaded)
    }

    @Test
    fun favoritesCanBeReorderedWithinTheirArmorSlotWithoutMutatingOriginalSettings() {
        val first = ArmorPreset(id = "first", inventorySlot = 9, itemName = "First")
        val second = ArmorPreset(id = "second", inventorySlot = 10, itemName = "Second")
        val boots = ArmorPreset(id = "boots", inventorySlot = 36, itemName = "Boots")
        val original = StatsArmorSettings()
            .add(StatsArmorSlot.HEAD, first)
            .add(StatsArmorSlot.HEAD, second)
            .add(StatsArmorSlot.FEET, boots)

        val reordered = original.move("second", -1)

        assertEquals(listOf("first", "second"), original.presets(StatsArmorSlot.HEAD).map { it.id })
        assertEquals(listOf("second", "first"), reordered.presets(StatsArmorSlot.HEAD).map { it.id })
        assertEquals(listOf("boots"), reordered.presets(StatsArmorSlot.FEET).map { it.id })
    }

    @Test
    fun addingTheSameFavoriteTwiceKeepsOnlyOneEntryForThatArmorSlot() {
        val favorite = ArmorPreset(
            id = "first-capture",
            inventorySlot = 9,
            itemId = "BONZO_MASK",
            itemName = "Bonzo's Mask",
            itemUuid = "uuid-1",
        )
        val duplicateCapture = favorite.copy(id = "second-capture", inventorySlot = 10)

        val settings = StatsArmorSettings()
            .add(StatsArmorSlot.HEAD, favorite)
            .add(StatsArmorSlot.HEAD, duplicateCapture)

        assertEquals(1, settings.presets(StatsArmorSlot.HEAD).size)
        assertEquals("first-capture", settings.presets(StatsArmorSlot.HEAD).single().id)
    }

    @Test
    fun maskTimersUseOdinDurationsAndTickWithoutMutation() {
        val state = MaskTimerState()
        state.proc(MaskType.SPIRIT)
        assertEquals(30 * 20, state.cooldown(MaskType.SPIRIT))
        assertEquals(3 * 20, state.invulnerability(MaskType.SPIRIT))

        repeat(20) { state.tick() }
        assertEquals(29 * 20, state.cooldown(MaskType.SPIRIT))
        assertEquals(2 * 20, state.invulnerability(MaskType.SPIRIT))
        assertEquals("29.0s", state.display(MaskType.SPIRIT))
    }
}
