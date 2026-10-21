package net.icantpy.qol.invsort

import com.google.gson.GsonBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvSortShowButtonCodecTest {
    private val helmet = InvSortSlot(key = "HELMET", itemId = "minecraft:diamond_helmet", label = "Helmet")

    private fun roundTrip(settings: InvSortSettings): InvSortSettings {
        val json = GsonBuilder().create().toJson(InvSortCodec.toJson(settings))
        return InvSortCodec.parseJson(json).settings
    }

    @Test fun missingShowButtonLoadsAsShown() {
        val preset = InvSortCodec.parseJson("""{"presets": [{"name": "A", "slots": {}}]}""").settings.presets.single()
        assertTrue(preset.showButton)
    }

    @Test fun hiddenPresetRoundTripsWithArmorEntries() {
        val source = InvSortSettings(
            presets = listOf(InvSortPreset("Hidden", mapOf(5 to helmet, 9 to helmet), hotkey = 70, showButton = false)),
        )
        assertEquals(source, roundTrip(source))
        val json = GsonBuilder().create().toJson(InvSortCodec.toJson(source))
        assertTrue("\"showButton\":false" in json)
    }

    @Test fun armorSlotIndicesLoadAndNonArmorOutOfRangeAreDropped() {
        val json = """
            {"presets": [{"name": "Gear", "slots": {
              "5": {"key": "HELMET", "itemId": "minecraft:diamond_helmet", "label": "Helmet"},
              "6": {"key": "CHEST", "itemId": "minecraft:diamond_chestplate", "label": "Chest"},
              "7": {"key": "LEGS", "itemId": "minecraft:diamond_leggings", "label": "Legs"},
              "8": {"key": "BOOTS", "itemId": "minecraft:diamond_boots", "label": "Boots"},
              "4": {"key": "craft", "itemId": "minecraft:stone", "label": "Craft"},
              "45": {"key": "off", "itemId": "minecraft:stone", "label": "Off"}
            }}]}
        """.trimIndent()
        val slots = InvSortCodec.parseJson(json).settings.presets.single().slots
        assertEquals(setOf(5, 6, 7, 8), slots.keys)
    }

    @Test fun oldFileWithoutArmorLoadsUnchanged() {
        val json = """{"presets": [{"name": "Old", "hotkey": 66, "slots": {"9": {"key": "a", "itemId": "minecraft:stone", "label": "A"}}}]}"""
        val preset = InvSortCodec.parseJson(json).settings.presets.single()
        assertEquals(InvSortPreset("Old", mapOf(9 to InvSortSlot("a", "minecraft:stone", "A")), hotkey = 66), preset)
        assertFalse(preset.slots.keys.any { InvSortSlots.isArmor(it) })
    }
}
