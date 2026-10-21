package net.icantpy.dungeon.leap

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.icantpy.hud.markers.MarkerClass
import net.icantpy.hud.markers.MarkerSelector
import net.icantpy.hud.markers.MarkerStyle
import net.icantpy.hud.markers.PlayerMarkers
import net.icantpy.qol.auto.AutoTarget
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapMarkStepTest {
    private val base = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START).copy(id = "card-1")
    private val classMark = LeapAutoStep(LeapAutoKind.MARK_PLAYER, markClass = "ARCHER")
    private val playerMark = LeapAutoStep(LeapAutoKind.MARK_PLAYER, markBy = LeapMarkRouter.BY_PLAYER, markPlayer = "Alice")
    private val sort = LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "Combat")

    @AfterTest
    fun reset() {
        PlayerMarkers.clearAll()
        PlayerMarkers.classResolver = { null }
    }

    private fun trigger(steps: List<LeapAutoStep>) = base.copy(steps = steps)

    private fun roundTrip(steps: List<LeapAutoStep>): List<LeapAutoStep> {
        val json = LeapTriggerJson.triggersToJson(listOf(trigger(steps)))
        return LeapTriggerJson.triggersFromJson(JsonObject().apply { add("triggers", json) })!!.single().steps
    }

    @Test
    fun markFieldsSurviveJsonRoundTrip() {
        val mark = LeapAutoStep(
            LeapAutoKind.MARK_PLAYER,
            markBy = LeapMarkRouter.BY_PLAYER,
            markClass = "TANK",
            markPlayer = "Alice",
            markStyle = "BEAM",
            markColor = 0xFF55FF55.toInt(),
            markSeconds = 0,
            markShowName = false,
        )
        assertEquals(listOf(mark, sort), roundTrip(listOf(mark, sort)))
    }

    @Test
    fun markStepWithOnlyKindUsesDefaults() {
        val item = JsonObject().apply {
            addProperty("id", "card-1")
            add("steps", JsonArray().apply { add(JsonParser.parseString("""{"kind":"MARK_PLAYER"}""")) })
        }
        val step = LeapTriggerJson.triggersFromJson(JsonObject().apply { add("triggers", JsonArray().apply { add(item) }) })!!
            .single().steps.single()
        assertEquals(LeapAutoKind.MARK_PLAYER, step.kind)
        assertEquals("CLASS", step.markBy)
        assertEquals("HEALER", step.markClass)
        assertEquals("", step.markPlayer)
        assertEquals("ARROW", step.markStyle)
        assertEquals(0xFFFF5555.toInt(), step.markColor)
        assertEquals(10, step.markSeconds)
        assertTrue(step.markShowName)
    }

    @Test
    fun markRowsAreNotQueuedAsGameActions() {
        assertTrue(trigger(listOf(classMark)).queueableSteps().isEmpty())
        val route = LeapAutoRouter.route(listOf(classMark, sort, playerMark))
        assertEquals(listOf<AutoTarget>(AutoTarget.Sort("Combat")), route.targets)
        assertTrue(route.skipped.isEmpty())
        assertTrue(route.armedSorts.isEmpty())
    }

    @Test
    fun markRouteResolvesSelectorsAndReportsIncompleteRows() {
        val blankPlayer = LeapAutoStep(LeapAutoKind.MARK_PLAYER, markBy = LeapMarkRouter.BY_PLAYER, markPlayer = "  ")
        val route = LeapMarkRouter.route(listOf(classMark, blankPlayer, playerMark))
        assertEquals(listOf(0, 2), route.marks.map { it.stepIndex })
        assertEquals(MarkerSelector.ByClass(MarkerClass.ARCHER), route.marks[0].selector)
        assertEquals(MarkerSelector.ByName("Alice"), route.marks[1].selector)
        assertEquals(listOf(blankPlayer), route.skipped)
    }

    @Test
    fun durationAndStyleFollowTheStep() {
        val untilReset = LeapMarkRouter.route(listOf(classMark.copy(markSeconds = 0))).marks.single()
        assertEquals(0L, untilReset.durationMs)
        val timed = LeapMarkRouter.route(listOf(classMark.copy(markSeconds = 15, markStyle = "BOX"))).marks.single()
        assertEquals(15_000L, timed.durationMs)
        assertEquals(MarkerStyle.BOX, timed.style)
        assertEquals(MarkerStyle.ARROW, LeapMarkRouter.styleOf(classMark.copy(markStyle = "nonsense")))
    }

    @Test
    fun applyPutsMarkersKeyedByCardAndStep() {
        val route = LeapMarkRouter.route(listOf(sort, classMark.copy(markSeconds = 0), playerMark.copy(markSeconds = 5)))
        LeapMarkRouter.apply(route.marks, "card-1", nowMs = 1_000)
        val active = PlayerMarkers.active(1_000).associateBy { it.id }
        assertEquals(setOf("card-1#1", "card-1#2"), active.keys)
        assertEquals(Long.MAX_VALUE, active.getValue("card-1#1").expiresAtMs)
        assertEquals(6_000L, active.getValue("card-1#2").expiresAtMs)
        assertTrue(PlayerMarkers.active(6_000).none { it.id == "card-1#2" })
    }

    @Test
    fun classMarkResolvesThroughResolverAtDrawTime() {
        PlayerMarkers.classResolver = { token -> if (token == "ARCHER") "Carl" else null }
        LeapMarkRouter.apply(LeapMarkRouter.route(listOf(classMark)).marks, "card-1", nowMs = 0)
        assertEquals("Carl", PlayerMarkers.active(0).single().targetName)
    }

    @Test
    fun testPlanCarriesMarksAndReportsIncompleteOnes() {
        val blankPlayer = LeapAutoStep(LeapAutoKind.MARK_PLAYER, markBy = LeapMarkRouter.BY_PLAYER)
        val plan = LeapTestPlanner.plan(trigger(listOf(classMark, blankPlayer)))
        assertEquals(1, plan.marks.size)
        assertEquals(listOf(blankPlayer), plan.skipped)
        assertFalse(plan.armLeap)
        assertFalse(plan.nothingToTest)
    }

    @Test
    fun labelsNameTheTargetStyleAndClass() {
        assertEquals("Mark: Healer (arrow)", LeapAutoStep(LeapAutoKind.MARK_PLAYER, markClass = "HEALER").label())
        assertEquals("Mark: Alice (beam)", playerMark.copy(markStyle = "BEAM").label())
        assertEquals("Mark: no player (arrow)", LeapAutoStep(LeapAutoKind.MARK_PLAYER, markBy = "PLAYER").label())
    }

    @Test
    fun colorNamesCoverThePalette() {
        assertEquals("Red", LeapMarkRouter.colorName(0xFFFF5555.toInt()))
        assertEquals("Custom", LeapMarkRouter.colorName(0xFF123456.toInt()))
    }
}
