package net.icantpy.hud.markers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.icantpy.dungeon.leap.LeapAutoKind
import net.icantpy.dungeon.leap.LeapAutoStep
import net.icantpy.dungeon.leap.LeapOrientTrigger
import net.icantpy.dungeon.leap.LeapTriggerEvent
import net.icantpy.dungeon.leap.LeapTriggerJson
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerGlowTest {
    private val red = 0xFFFF0000.toInt()
    private val byName = MarkerSelector.ByName("Alice")

    @AfterTest
    fun reset() {
        PlayerMarkers.clearAll()
        PlayerMarkers.classResolver = { null }
    }

    private fun markGlow(id: String, name: String, glow: Boolean, durationMs: Long = 0, now: Long = 0, color: Int = red) {
        PlayerMarkers.mark(
            id, MarkerSelector.ByName(name), MarkerStyle.BOX, color, durationMs, now,
            playerGlow = glow,
        )
    }

    private val entityIds = mapOf("Alice" to 7, "Bob" to 9)
    private fun resolve(marker: ActiveMarker): Int? = marker.targetName?.let { entityIds[it] }

    @Test
    fun markedPlayerGetsColorAndUnmarkedGetsNull() {
        markGlow("a", "Alice", glow = true)
        PlayerMarkers.refreshGlow(0, ::resolve)
        assertEquals(red and 0xFFFFFF, PlayerMarkers.glowColorFor(7))
        assertNull(PlayerMarkers.glowColorFor(9))
        assertNull(PlayerMarkers.glowColorFor(12345))
    }

    @Test
    fun glowOffMarkerPublishesNothing() {
        markGlow("a", "Alice", glow = false)
        PlayerMarkers.refreshGlow(0, ::resolve)
        assertNull(PlayerMarkers.glowColorFor(7))
    }

    @Test
    fun expiredMarkerDropsOutOnNextRefresh() {
        markGlow("a", "Alice", glow = true, durationMs = 1_000, now = 0)
        PlayerMarkers.refreshGlow(999, ::resolve)
        assertEquals(red and 0xFFFFFF, PlayerMarkers.glowColorFor(7))
        PlayerMarkers.refreshGlow(1_000, ::resolve)
        assertNull(PlayerMarkers.glowColorFor(7))
    }

    @Test
    fun unresolvedTargetPublishesNothing() {
        PlayerMarkers.mark("c", MarkerSelector.ByClass(MarkerClass.MAGE), MarkerStyle.BOX, red, 0, 0, playerGlow = true)
        PlayerMarkers.refreshGlow(0, ::resolve)
        assertNull(PlayerMarkers.glowColorFor(7))
    }

    @Test
    fun firstMarkerWinsWhenTwoTargetOneEntity() {
        markGlow("a", "Alice", glow = true, color = 0xFF0000FF.toInt())
        markGlow("b", "Alice", glow = true, color = 0xFF00FF00.toInt())
        PlayerMarkers.refreshGlow(0, ::resolve)
        assertEquals(0x0000FF, PlayerMarkers.glowColorFor(7))
        assertEquals(0x0000FF, PlayerMarkers.glowColorFor(7))
    }

    @Test
    fun glowColorUsesBrightness() {
        PlayerMarkers.mark(
            "a", byName, MarkerStyle.BOX, 0xFF808080.toInt(), 0, 0,
            playerGlow = true, brightnessPercent = 50,
        )
        PlayerMarkers.refreshGlow(0, ::resolve)
        assertEquals(0x404040, PlayerMarkers.glowColorFor(7))
    }

    @Test
    fun buildGlowColorsIgnoresMarkersWithoutGlow() {
        markGlow("a", "Alice", glow = false)
        markGlow("b", "Bob", glow = true)
        val map = PlayerMarkers.buildGlowColors(PlayerMarkers.active(0), ::resolve)
        assertEquals(mapOf(9 to (red and 0xFFFFFF)), map)
    }

    @Test
    fun brightnessBoundsAndIdentity() {
        for (p in MarkerLook.BRIGHTNESS_CHOICES) {
            for (c in listOf(0x000000, 0xFFFFFF, 0x123456, 0xFF8800)) {
                val out = MarkerLook.brightenRgb(c, p)
                assertTrue(out in 0..0xFFFFFF, "rgb $out out of range for $p")
            }
        }
        assertEquals(0x123456, MarkerLook.brightenRgb(0xFF123456.toInt(), 100))
        assertEquals(0x808080, MarkerLook.brightenRgb(0xFF808080.toInt(), 100))
        assertEquals(0x404040, MarkerLook.brightenRgb(0x808080, 50))
        // White stays white however bright, and above 100 channels never decrease.
        assertEquals(0xFFFFFF, MarkerLook.brightenRgb(0xFFFFFF, 300))
        assertTrue(MarkerLook.brightenRgb(0x204060, 150) > 0x204060)
        // Out of range percents clamp to the nearest bound.
        assertEquals(MarkerLook.brightenRgb(0x204060, 50), MarkerLook.brightenRgb(0x204060, 1))
        assertEquals(MarkerLook.brightenRgb(0x204060, 300), MarkerLook.brightenRgb(0x204060, 9_999))
    }

    @Test
    fun boostAlphaBounds() {
        assertEquals(120, MarkerLook.boostAlpha(120, 100))
        assertEquals(120, MarkerLook.boostAlpha(120, 50))
        assertEquals(240, MarkerLook.boostAlpha(120, 200))
        assertEquals(255, MarkerLook.boostAlpha(200, 300))
        assertEquals(0, MarkerLook.boostAlpha(-5, 300))
    }

    @Test
    fun brightnessCyclesThroughChoices() {
        assertEquals(125, MarkerLook.cycle(MarkerLook.BRIGHTNESS_CHOICES, 100))
        assertEquals(50, MarkerLook.cycle(MarkerLook.BRIGHTNESS_CHOICES, 300))
    }

    @Test
    fun hookActiveAfterCallWithinWindowOnly() {
        // Use far-future times so other tests cannot leave a live timestamp in range.
        PlayerMarkers.glowColorFor(1)
        PlayerMarkers.noteGlowHookTick(100_000)
        assertTrue(PlayerMarkers.glowHookActive(100_000))
        assertTrue(PlayerMarkers.glowHookActive(100_000 + PlayerMarkers.HOOK_WINDOW_MS))
        assertFalse(PlayerMarkers.glowHookActive(100_000 + PlayerMarkers.HOOK_WINDOW_MS + 1))
    }

    @Test
    fun hookInactiveWithoutRecentCall() {
        // Other tests may leave a pending call flag; consume it at a time well after the window of any earlier check.
        PlayerMarkers.noteGlowHookTick(400_000)
        assertFalse(PlayerMarkers.glowHookActive(400_000 + PlayerMarkers.HOOK_WINDOW_MS + 1))
        PlayerMarkers.noteGlowHookTick(500_000)
        assertFalse(PlayerMarkers.glowHookActive(500_000 + PlayerMarkers.HOOK_WINDOW_MS + 1))
    }

    @Test
    fun ledgerRestoresOriginalStateNotBlindlyFalse() {
        // An entity that already had the glowing effect must keep it after the marker ends.
        val ledger = MarkerGlowLedger()
        val calls = mutableListOf<Pair<Int, Boolean>>()
        ledger.reconcile(mapOf(7 to true, 9 to false)) { id, g -> calls += id to g }
        calls.clear()
        ledger.releaseAll { id, g -> calls += id to g }
        assertEquals(listOf(7 to true, 9 to false), calls.sortedBy { it.first })
        assertTrue(ledger.glowingIds.isEmpty())
    }

    @Test
    fun markBrightnessSurvivesJsonRoundTrip() {
        val base = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START).copy(id = "card-1")
        val mark = LeapAutoStep(LeapAutoKind.MARK_PLAYER, markBrightness = 300)
        val json = LeapTriggerJson.triggersToJson(listOf(base.copy(steps = listOf(mark))))
        val back = LeapTriggerJson.triggersFromJson(JsonObject().apply { add("triggers", json) })!!.single().steps.single()
        assertEquals(300, back.markBrightness)
        assertEquals(mark, back)
    }

    @Test
    fun badBrightnessFallsBackToDefault() {
        val item = JsonObject().apply {
            addProperty("id", "card-1")
            add("steps", JsonArray().apply { add(JsonParser.parseString("""{"kind":"MARK_PLAYER","markBrightness":77}""")) })
        }
        val step = LeapTriggerJson.triggersFromJson(JsonObject().apply { add("triggers", JsonArray().apply { add(item) }) })!!
            .single().steps.single()
        assertEquals(MarkerLook.DEFAULT_BRIGHTNESS, step.markBrightness)
    }
}
