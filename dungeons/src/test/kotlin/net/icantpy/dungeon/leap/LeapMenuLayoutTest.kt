package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeapMenuLayoutTest {
    @Test
    fun individualSizesRoundTripAndChangeOnlyThatCard() {
        val defaults = LeapOrientSettings()
        val cfg = defaults.copy(boxSizes = mapOf("0" to "300,100"))
        val loaded = LeapOrientSettings.fromJson(LeapOrientSettings.toJson(cfg))
        assertEquals(cfg.boxSizes, loaded.boxSizes)
        val boxes = LeapMenuLayout.boxes(1000, 800, loaded)
        assertEquals(300f, boxes[0].width)
        assertEquals(100f, boxes[0].height)
        assertEquals(LeapMenuLayout.boxes(1000, 800, defaults)[1], boxes[1])
        assertEquals(4, LeapMenuLayout.hit(1000, 800, 500, 400, loaded, true))
        assertEquals(null, LeapMenuLayout.hit(1000, 800, 5, 5, loaded, true))
    }

    @Test
    fun writtenPixelSizeReloadsForThatCardOnly() {
        val start = LeapMenuLayout.boxes(1000, 800, LeapOrientSettings())
        val resized = LeapMenuEditGeometry.setSize(start, setOf(0), 300f, 100f, 1000, 800)
        val saved = LeapMenuLayout.write(LeapOrientSettings(), resized, 1000, 800, setOf(0), true)
        val loaded = LeapMenuLayout.boxes(1000, 800, saved)
        assertEquals(300f, loaded[0].width, 0.01f)
        assertEquals(100f, loaded[0].height, 0.01f)
        assertEquals(start[1], loaded[1])
        val reset = LeapMenuLayout.boxes(1000, 800, LeapMenuLayout.clear(saved, setOf(0)))
        assertEquals(start[0].width, reset[0].width)
        assertEquals(start[0].height, reset[0].height)
    }

    @Test
    fun invalidSizeFallsBackAndHugeValidSizeStaysOnScreen() {
        val defaults = LeapMenuLayout.boxes(480, 270, LeapOrientSettings())
        val invalid = LeapMenuLayout.boxes(480, 270, LeapOrientSettings(boxSizes = mapOf("0" to "NaN,-1")))
        assertEquals(defaults, invalid)
        val huge = LeapMenuLayout.boxes(480, 270, LeapOrientSettings(boxSizes = mapOf("0" to "800,400")))[0]
        assertTrue(huge.left >= 0 && huge.right <= 480 && huge.top >= 0 && huge.bottom <= 270)
    }
    @Test
    fun settingsRoundTripPreservesLayoutAndIndependentToggles() {
        val settings = LeapOrientSettings(menuGap = 22, boxesOnly = true, boxPositions = mapOf("0" to "0.25,0.3"),
            boxOrder = listOf("3", "1", "0", "2", "4"),
            menuOpacity = 40,
            autoLeapEnabled = true, autoLeapBossOnly = false, autoLeapDelayMs = 150, doorOpenerLeapEnabled = true)
        val loaded = LeapOrientSettings.fromJson(LeapOrientSettings.toJson(settings))
        assertEquals(settings.menuGap, loaded.menuGap)
        assertEquals(settings.boxesOnly, loaded.boxesOnly)
        assertEquals(settings.boxPositions, loaded.boxPositions)
        assertEquals(settings.boxOrder, loaded.boxOrder)
        assertEquals(40, loaded.menuOpacity)
        assertEquals(0.4f, loaded.cardFillAlpha())
        assertEquals(settings.autoLeapEnabled, loaded.autoLeapEnabled)
        assertEquals(settings.autoLeapBossOnly, loaded.autoLeapBossOnly)
        assertEquals(settings.autoLeapDelayMs, loaded.autoLeapDelayMs)
        assertEquals(settings.doorOpenerLeapEnabled, loaded.doorOpenerLeapEnabled)
        val json = LeapOrientSettings.toJson(settings)
        json.addProperty("menuGap", -100)
        json.addProperty("autoLeapDelayMs", 5000)
        json.addProperty("menuOpacity", 140)
        val clamped = LeapOrientSettings.fromJson(json)
        assertEquals(0, clamped.menuGap)
        assertEquals(2000, clamped.autoLeapDelayMs)
        assertEquals(100, clamped.menuOpacity)
        assertEquals(1f, LeapOrientSettings().cardFillAlpha())
        assertEquals(0f, LeapOrientSettings(menuOpacity = 0).cardFillAlpha())
    }
    @Test
    fun deadZonesNeverFallThroughToCorner() {
        val cfg = LeapOrientSettings(menuGap = 12)
        assertEquals(4, LeapMenuLayout.hit(800, 600, 400, 300, cfg, true))
        assertEquals(null, LeapMenuLayout.hit(800, 600, 305, 300, cfg, true))
        assertEquals(null, LeapMenuLayout.hit(800, 600, 400, 100, cfg, true))
        assertEquals(0, LeapMenuLayout.hit(800, 600, 20, 20, cfg, true))
        assertEquals(null, LeapMenuLayout.hit(800, 600, 20, 20, cfg.copy(boxesOnly = true), true))
    }

    @Test
    fun movedCardsUseTheirActualBoundsIncludingCenter() {
        val cfg = LeapOrientSettings(boxPositions = mapOf("4" to "0.75,0.75"))
        assertEquals(4, LeapMenuLayout.hit(800, 600, 600, 450, cfg, true))
        assertEquals(null, LeapMenuLayout.hit(800, 600, 400, 300, cfg, true))
        assertEquals(null, LeapMenuLayout.hit(800, 600, -1, 20, cfg, true))
    }
    @Test
    fun defaultCardsLeaveRoomForCenterAndGap() {
        val boxes = LeapMenuLayout.boxes(800, 600, LeapOrientSettings(menuGap = 12))
        val center = boxes[4]
        assertTrue(boxes[0].right <= center.left - 12)
        assertTrue(boxes[1].left >= center.right + 12)
        assertTrue(boxes[0].bottom <= center.top - 12)
        assertTrue(boxes[2].top >= center.bottom + 12)
    }

    @Test
    fun savedPositionsFollowResolutionAndRejectInvalidNumbers() {
        val cfg = LeapOrientSettings(boxPositions = mapOf("0" to "0.25,0.25", "4" to "NaN,0.5"))
        val boxes = LeapMenuLayout.boxes(800, 600, cfg)
        assertEquals(200f, boxes[0].centerX)
        assertEquals(150f, boxes[0].centerY)
        assertEquals(400f, boxes[4].centerX)
        assertEquals(300f, boxes[4].centerY)
    }

    @Test
    fun increasingScaleKeepsTheConfiguredGap() {
        val boxes = LeapMenuLayout.boxes(1200, 900, LeapOrientSettings(scale = 1.5f, menuGap = 16))
        assertEquals(24f, boxes[4].left - boxes[0].right)
    }

    @Test
    fun defaultLayoutFitsSmallWindowsInsteadOfClippingCards() {
        val cfg = LeapOrientSettings(scale = 2f, menuGap = 64)
        val boxes = LeapMenuLayout.boxes(480, 270, cfg)
        assertTrue(boxes.all { it.left >= 0 && it.top >= 0 && it.right <= 480 && it.bottom <= 270 })
    }

    @Test
    fun overlappingCardsHitTheTopLayer() {
        val stacked = LeapOrientSettings(
            boxesOnly = true,
            boxPositions = mapOf("0" to "0.25,0.25", "1" to "0.25,0.25"),
            boxSizes = mapOf("0" to "120,60", "1" to "120,60"),
            boxOrder = listOf("1", "0"),
        )
        val point = LeapMenuLayout.boxes(800, 600, stacked)[0]
        val x = point.centerX.toInt()
        val y = point.centerY.toInt()
        assertEquals(0, LeapMenuLayout.hit(800, 600, x, y, stacked, true))
        val raised = LeapMenuLayout.bringToFront(stacked, 1)
        assertEquals(listOf(0, 2, 3, 4, 1), LeapMenuLayout.layers(raised))
        assertEquals(1, LeapMenuLayout.hit(800, 600, x, y, raised, true))
        assertEquals(listOf(0, 1, 2, 3, 4), LeapMenuLayout.layers(LeapOrientSettings()))
    }
}
