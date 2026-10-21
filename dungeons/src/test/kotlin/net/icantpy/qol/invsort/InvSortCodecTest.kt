package net.icantpy.qol.invsort

import com.google.gson.GsonBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvSortCodecTest {
    private val sword = InvSortSlot(key = "HYPERION", itemId = "minecraft:diamond_sword", label = "Hyperion")
    private val potion = InvSortSlot(key = "minecraft:potion", itemId = "minecraft:potion", label = "Potion")
    private val source = InvSortSettings(
        enabled = false,
        presets = listOf(InvSortPreset("Combat", mapOf(9 to sword, 35 to potion), hotkey = 70)),
        clickDelayMinMs = 120,
        clickDelayMaxMs = 260,
        openDelayMs = 300,
        closeWhenDone = false,
        moveCursor = false,
        instant = true,
        buttonOffsetX = -3,
        buttonOffsetY = 40,
    )

    private fun parse(json: String) = InvSortCodec.parseJson(json)

    @Test fun roundTripPreservesSettingsAndHotkeys() {
        val json = GsonBuilder().create().toJson(InvSortCodec.toJson(source))
        val result = parse(json)
        assertNull(result.error)
        assertEquals(source, result.settings)
    }

    @Test fun missingHotkeyLoadsAsNone() {
        val preset = parse("""{"presets": [{"name": "A", "slots": {}}]}""").settings.presets.single()
        assertEquals(InvSortLimits.NO_HOTKEY, preset.hotkey)
    }

    @Test fun negativeHotkeyLoadsAsNone() {
        val preset = parse("""{"presets": [{"name": "A", "hotkey": -7, "slots": {}}]}""").settings.presets.single()
        assertEquals(InvSortLimits.NO_HOTKEY, preset.hotkey)
    }

    @Test fun duplicateHotkeysKeepFirstPresetOnly() {
        val json = """
            {"presets": [
              {"name": "First", "hotkey": 66, "slots": {}},
              {"name": "Second", "hotkey": 66, "slots": {}},
              {"name": "Third", "hotkey": 67, "slots": {}}
            ]}
        """.trimIndent()
        val presets = parse(json).settings.presets
        assertEquals(listOf(66, -1, 67), presets.map { it.hotkey })
        assertEquals(listOf("First", "Second", "Third"), presets.map { it.name })
    }

    @Test fun legacyClickDelayTicksMigratesToMillisecondRangeAndKeepsPresets() {
        val json = """
            {"enabled": true, "clickDelayTicks": 2, "buttonOffsetX": 4, "buttonOffsetY": 60,
             "presets": [{"name": "Preset 1", "slots": {"9": {"key": "HYPERION", "itemId": "minecraft:diamond_sword", "label": "Hyperion"}}}]}
        """.trimIndent()
        val settings = parse(json).settings
        assertEquals(100, settings.clickDelayMinMs)
        assertEquals(180, settings.clickDelayMaxMs)
        assertEquals(listOf("Preset 1"), settings.presets.map { it.name })
        assertEquals(setOf(9), settings.presets.single().slots.keys)
        assertEquals(InvSortLimits.NO_HOTKEY, settings.presets.single().hotkey)
    }

    @Test fun hotbarSwapAndSwapOnePerPressRoundTrip() {
        val settings = InvSortSettings(
            swapOnePerPress = true,
            presets = listOf(InvSortPreset("Hot", mapOf(36 to sword), hotbarSwap = true), InvSortPreset("Full", emptyMap())),
        )
        val json = GsonBuilder().create().toJson(InvSortCodec.toJson(settings))
        val parsed = parse(json).settings
        assertEquals(settings, parsed)
        assertTrue(parsed.swapOnePerPress)
        assertTrue(parsed.presets[0].hotbarSwap)
        assertFalse(parsed.presets[1].hotbarSwap)
    }

    @Test fun missingHotbarSwapFieldsLoadAsFalse() {
        val settings = parse("""{"presets": [{"name": "A", "slots": {}}]}""").settings
        assertFalse(settings.swapOnePerPress)
        assertFalse(settings.presets.single().hotbarSwap)
    }

    @Test fun oldFileLoadsUnchangedAndNewFieldsAreWritten() {
        val json = """{"enabled": true, "presets": [{"name": "Combat", "hotkey": 70, "showButton": false, "slots": {"36": {"key": "k", "itemId": "minecraft:stone", "label": "Stone"}}}]}"""
        val result = parse(json)
        assertNull(result.error)
        val preset = result.settings.presets.single()
        assertEquals("Combat", preset.name)
        assertEquals(70, preset.hotkey)
        assertFalse(preset.showButton)
        assertFalse(preset.hotbarSwap)
        assertEquals(setOf(36), preset.slots.keys)
        val written = InvSortCodec.toJson(result.settings)
        assertFalse(written.get("swapOnePerPress").asBoolean)
        assertFalse(written.getAsJsonArray("presets")[0].asJsonObject.get("hotbarSwap").asBoolean)
    }

    @Test fun legacyFieldIsNotWrittenBack() {
        val json = GsonBuilder().create().toJson(InvSortCodec.toJson(InvSortSettings()))
        assertTrue("clickDelayTicks" !in json)
        assertTrue("clickDelayMinMs" in json)
    }

    @Test fun clampsDelaysAndOffsets() {
        val result = parse("""{"clickDelayMinMs": -1, "clickDelayMaxMs": 99999, "openDelayMs": 999999, "buttonOffsetX": 50000, "buttonOffsetY": -50000}""")
        assertEquals(InvSortLimits.MIN_CLICK_DELAY_MS, result.settings.clickDelayMinMs)
        assertEquals(InvSortLimits.MAX_CLICK_DELAY_MS, result.settings.clickDelayMaxMs)
        assertEquals(InvSortLimits.MAX_OPEN_DELAY_MS, result.settings.openDelayMs)
        assertEquals(InvSortLimits.MAX_BUTTON_OFFSET, result.settings.buttonOffsetX)
        assertEquals(-InvSortLimits.MAX_BUTTON_OFFSET, result.settings.buttonOffsetY)
    }

    @Test fun oldFileWithoutInstantLoadsAsOff() {
        val json = """{"enabled": true, "clickDelayMinMs": 90, "clickDelayMaxMs": 170, "moveCursor": true}"""
        val settings = parse(json).settings
        assertNull(parse(json).error)
        assertEquals(false, settings.instant)
        assertEquals(90, settings.clickDelayMinMs)
    }

    @Test fun zeroDelaysLoadWithoutClampingToFifty() {
        val settings = parse("""{"clickDelayMinMs": 0, "clickDelayMaxMs": 0}""").settings
        assertEquals(0, settings.clickDelayMinMs)
        assertEquals(0, settings.clickDelayMaxMs)
    }

    @Test fun reversedDelayRangeIsOrdered() {
        val settings = parse("""{"clickDelayMinMs": 400, "clickDelayMaxMs": 200}""").settings
        assertEquals(200, settings.clickDelayMinMs)
        assertEquals(400, settings.clickDelayMaxMs)
    }

    @Test fun dropsInvalidPresetsAndSlots() {
        val json = """
            {"presets": [
              {"name": "   ", "slots": {"9": {"key": "a", "itemId": "minecraft:stone", "label": "A"}}},
              {"name": "Good", "slots": {
                "44": {"key": "a", "itemId": "minecraft:stone", "label": "A"},
                "45": {"key": "a", "itemId": "minecraft:stone", "label": "A"},
                "0": {"key": "a", "itemId": "minecraft:stone", "label": "A"},
                "10": {"key": "", "itemId": "minecraft:stone", "label": "A"},
                "11": {"key": "ok", "itemId": "minecraft:stone", "label": "Ok"}
              }}
            ]}
        """.trimIndent()
        val settings = parse(json).settings
        assertEquals(listOf("Good"), settings.presets.map { it.name })
        assertEquals(setOf(11), settings.presets.single().slots.keys)
    }

    @Test fun rejectsLongPresetNames() {
        val longName = "x".repeat(InvSortLimits.MAX_NAME_LENGTH + 1)
        val json = """{"presets": [{"name": "$longName", "slots": {}}]}"""
        assertTrue(parse(json).settings.presets.isEmpty())
    }

    @Test fun malformedJsonReturnsDefaultsWithError() {
        val result = parse("{not json")
        assertNotNull(result.error)
        assertEquals(InvSortSettings(), result.settings)
    }

    @Test fun nonObjectRootReturnsDefaultsWithError() {
        val result = parse("[1, 2]")
        assertNotNull(result.error)
        assertEquals(InvSortSettings(), result.settings)
    }

    @Test fun wrongTypesFallBackToDefaults() {
        val settings = parse("""{"enabled": "yes", "clickDelayMinMs": "fast", "moveCursor": 1}""").settings
        assertEquals(InvSortSettings(), settings)
    }

    @Test fun lockInputRoundTripsWhenTurnedOff() {
        val json = GsonBuilder().create().toJson(InvSortCodec.toJson(source.copy(lockInput = false)))
        assertFalse(parse(json).settings.lockInput)
    }

    @Test fun missingLockInputLoadsAsOn() {
        assertTrue(parse("""{"presets": []}""").settings.lockInput)
    }

    @Test fun safetyLevelRoundTripsAndDefaultsToBalanced() {
        val json = GsonBuilder().create().toJson(InvSortCodec.toJson(source.copy(safetyLevel = net.icantpy.qol.auto.CursorGuard.SafetyLevel.STRICT)))
        assertEquals(net.icantpy.qol.auto.CursorGuard.SafetyLevel.STRICT, parse(json).settings.safetyLevel)
        assertEquals(net.icantpy.qol.auto.CursorGuard.SafetyLevel.BALANCED, parse("""{"presets": []}""").settings.safetyLevel)
        assertEquals(net.icantpy.qol.auto.CursorGuard.SafetyLevel.BALANCED, parse("""{"safetyLevel": "nope"}""").settings.safetyLevel)
    }

    @Test fun lockInputDefaultsOnWhenWrittenByDefault() {
        assertTrue(InvSortCodec.toJson(InvSortSettings()).get("lockInput").asBoolean)
    }
}
