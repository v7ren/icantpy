package net.icantpy.hud.markers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.icantpy.dungeon.leap.LeapAutoKind
import net.icantpy.dungeon.leap.LeapAutoStep
import net.icantpy.dungeon.leap.LeapOrientTrigger
import net.icantpy.dungeon.leap.LeapTriggerEvent
import net.icantpy.dungeon.leap.LeapTriggerJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkerLookTest {
    private val base = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START).copy(id = "card-1")

    private fun roundTrip(step: LeapAutoStep): LeapAutoStep {
        val json = LeapTriggerJson.triggersToJson(listOf(base.copy(steps = listOf(step))))
        return LeapTriggerJson.triggersFromJson(JsonObject().apply { add("triggers", json) })!!.single().steps.single()
    }

    @Test
    fun newLookFieldsSurviveJsonRoundTrip() {
        val mark = LeapAutoStep(
            LeapAutoKind.MARK_PLAYER,
            markOpacity = 40,
            markSize = 200,
            markHeight = 3,
            markPulse = false,
            markPlayerGlow = true,
            markMarkerGlow = true,
            markArrowShape = MarkerShapes.DIAMOND,
            markSpin = true,
            markBob = false,
            markThroughWalls = true,
        )
        assertEquals(mark, roundTrip(mark))
    }

    @Test
    fun bareMarkStepUsesLookDefaults() {
        val item = JsonObject().apply {
            addProperty("id", "card-1")
            add("steps", JsonArray().apply { add(JsonParser.parseString("""{"kind":"MARK_PLAYER"}""")) })
        }
        val step = LeapTriggerJson.triggersFromJson(JsonObject().apply { add("triggers", JsonArray().apply { add(item) }) })!!
            .single().steps.single()
        assertEquals(80, step.markOpacity)
        assertEquals(100, step.markSize)
        assertEquals(0, step.markHeight)
        assertTrue(step.markPulse)
        assertFalse(step.markPlayerGlow)
        assertFalse(step.markMarkerGlow)
        assertEquals(MarkerShapes.CLASSIC, step.markArrowShape)
        assertFalse(step.markSpin)
        assertTrue(step.markBob)
        assertFalse(step.markThroughWalls)
    }

    @Test
    fun unknownShapeFallsBackToClassic() {
        val item = JsonObject().apply {
            addProperty("id", "card-1")
            add("steps", JsonArray().apply { add(JsonParser.parseString("""{"kind":"MARK_PLAYER","markArrowShape":"WAT"}""")) })
        }
        val step = LeapTriggerJson.triggersFromJson(JsonObject().apply { add("triggers", JsonArray().apply { add(item) }) })!!
            .single().steps.single()
        assertEquals(MarkerShapes.CLASSIC, step.markArrowShape)
    }

    @Test
    fun classicShapeScalesWithSizePercent() {
        // Shaft is the last box; its half width and length scale with size.
        val small = MarkerShapes.parts(MarkerShapes.CLASSIC, 50, 0.0, 0.0, 0.0).last() as MarkerPart.Box
        assertEquals(0.06, small.maxX, 1e-9)
        assertEquals(0.75, small.maxY - (small.minY), 1e-9)
        val large = MarkerShapes.parts(MarkerShapes.CLASSIC, 200, 0.0, 0.0, 0.0).last() as MarkerPart.Box
        assertEquals(0.24, large.maxX, 1e-9)
        assertEquals(3.0, large.maxY - large.minY, 1e-9)
        // Widest head slab at 200 percent is 1.6 blocks half width.
        val heads = MarkerShapes.parts(MarkerShapes.CLASSIC, 200, 0.0, 0.0, 0.0).filterIsInstance<MarkerPart.Box>().dropLast(1)
        assertEquals(1.6, heads.last().maxX, 1e-9)
    }

    @Test
    fun segmentShapesHaveExpectedCountsAndSpinKeepsRadius() {
        val diamond = MarkerShapes.parts(MarkerShapes.DIAMOND, 100, 0.0, 0.0, 0.0)
        assertEquals(12, diamond.size)
        val ring = MarkerShapes.parts(MarkerShapes.RING, 100, 0.0, 0.0, 0.0)
        assertEquals(32, ring.size)
        assertTrue(ring.all { it is MarkerPart.Seg })
        // Spin rotates the outer ring (first 16 segments) and keeps every vertex on its radius around the base.
        val spun = MarkerShapes.parts(MarkerShapes.RING, 100, 0.0, 0.0, 0.0, spinRad = 1.1)
        spun.take(16).forEach { part ->
            val seg = part as MarkerPart.Seg
            assertEquals(0.7, Math.hypot(seg.x1, seg.z1), 1e-9)
        }
    }

    @Test
    fun everyShapeProducesGeometryAboveTheTip() {
        for (shape in MarkerShapes.SHAPES) {
            val parts = MarkerShapes.parts(shape, 100, 0.0, 5.0, 0.0)
            assertTrue(parts.isNotEmpty(), shape)
            val minY = parts.minOf { p ->
                when (p) {
                    is MarkerPart.Box -> p.minY
                    is MarkerPart.Seg -> minOf(p.y1, p.y2)
                }
            }
            assertTrue(minY >= 5.0 - 1e-9, "$shape dips below its tip: $minY")
        }
    }

    @Test
    fun shapeCycleWraps() {
        assertEquals(MarkerShapes.CHEVRON, MarkerShapes.next(MarkerShapes.CLASSIC))
        assertEquals(MarkerShapes.CLASSIC, MarkerShapes.next(MarkerShapes.POINTER))
        assertEquals(MarkerShapes.CLASSIC, MarkerShapes.next("nope"))
    }

    @Test
    fun alphaHonorsOpacityAndPulseBounds() {
        assertEquals(255, MarkerLook.alpha(255, 100, pulseOn = false, pulseFactor = 0.7f))
        assertEquals(51, MarkerLook.alpha(255, 20, pulseOn = false, pulseFactor = 0.2f))
        assertEquals(178, MarkerLook.alpha(255, 100, pulseOn = true, pulseFactor = 0.7f))
        assertEquals(255, MarkerLook.alpha(255, 100, pulseOn = true, pulseFactor = 1.0f))
        for (t in 0..100) {
            val pulse = PlayerMarkers.pulse(t * 100L)
            val a = MarkerLook.alpha(255, 80, pulseOn = true, pulseFactor = pulse)
            assertTrue(a in 142..204, "alpha $a out of band at $t")
        }
    }

    @Test
    fun choicesCycleAndWrap() {
        assertEquals(30, MarkerLook.cycle(MarkerLook.OPACITY_CHOICES, 20))
        assertEquals(20, MarkerLook.cycle(MarkerLook.OPACITY_CHOICES, 100))
        assertEquals(50, MarkerLook.cycle(MarkerLook.SIZE_CHOICES, 300))
        assertEquals("Off", MarkerLook.heightLabel(0))
    }

    @Test
    fun glowLedgerAppliesReappliesAndRestoresOriginalFlag() {
        val ledger = MarkerGlowLedger()
        val calls = mutableListOf<Pair<Int, Boolean>>()
        val apply = { id: Int, glowing: Boolean -> calls += id to glowing }

        // Entity 7 was already glowing from the game; entity 9 was not.
        ledger.reconcile(mapOf(7 to true, 9 to false), apply)
        assertEquals(setOf(7, 9), ledger.glowingIds)
        assertEquals(listOf(7 to true, 9 to true), calls)

        calls.clear()
        ledger.reconcile(mapOf(9 to true), apply)
        // 7 is restored to its original glowing state (true), 9 stays wanted.
        assertEquals(listOf(9 to true, 7 to true), calls)
        assertEquals(setOf(9), ledger.glowingIds)

        calls.clear()
        ledger.releaseAll(apply)
        assertEquals(listOf(9 to false), calls)
        assertTrue(ledger.glowingIds.isEmpty())
    }

    @Test
    fun glowLedgerTouchesNothingWhenNeverWanted() {
        val ledger = MarkerGlowLedger()
        var touched = 0
        ledger.releaseAll { _, _ -> touched++ }
        assertEquals(0, touched)
    }
}
