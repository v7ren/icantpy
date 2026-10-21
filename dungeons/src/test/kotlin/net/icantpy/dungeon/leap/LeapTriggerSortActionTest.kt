package net.icantpy.dungeon.leap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeapTriggerSortActionTest {
    private val sortStep = LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "Dps")
    private val sortTrigger = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START).copy(
        id = "sort-on-maxor",
        steps = listOf(sortStep),
    )

    private fun jsonOf(triggers: List<LeapOrientTrigger>): JsonObject =
        JsonObject().apply { add("triggers", LeapTriggerJson.triggersToJson(triggers)) }

    @Test
    fun sortStepAndPresetNameSurviveJsonRoundTrip() {
        val text = JsonParser.parseString(jsonOf(listOf(sortTrigger)).toString()).asJsonObject
        val loaded = LeapTriggerJson.triggersFromJson(text)!!.single()
        assertEquals(listOf(sortStep), loaded.steps)
        assertEquals(sortTrigger, loaded)
    }

    @Test
    fun savedTriggersWithoutActionFieldsLoadAsLeapTargets() {
        val old = JsonParser.parseString(
            """{"triggers": [{"id": "old-row", "event": "BOSS_STORM_START", "target": "CLASS", "targetValue": "healer"}]}""",
        ).asJsonObject
        val loaded = LeapTriggerJson.triggersFromJson(old)!!.single()
        assertTrue(loaded.armLeap)
        assertEquals(listOf(LeapAutoStep.leapTarget()), loaded.steps)
        assertEquals(LeapCenterTarget.CLASS, loaded.target)
        assertEquals("Leap to Healer", loaded.subtitle())
    }

    @Test
    fun unknownActionNameFallsBackToLeapTarget() {
        val json = JsonParser.parseString("""{"triggers": [{"id": "x", "event": "CLOCK", "action": "NOPE"}]}""").asJsonObject
        val loaded = LeapTriggerJson.triggersFromJson(json)!!.single()
        assertTrue(loaded.armLeap)
        assertEquals(listOf(LeapAutoStep.leapTarget()), loaded.steps)
    }

    @Test
    fun sortStepDescribesPresetInSubtitleAndSummary() {
        assertEquals("Sort: Dps", sortTrigger.subtitle())
        assertEquals("Storm starts (P2) → Sort: Dps", sortTrigger.summary())
        assertEquals("Sort: no preset", sortTrigger.copy(steps = listOf(LeapAutoStep(LeapAutoKind.SORT_INVENTORY))).subtitle())
    }

    @Test
    fun applySharedCopiesStepsToPresetCard() {
        val preset = LeapOrientSettings.DEFAULT_TRIGGERS.first { it.id == "arch-maxor-bers" }
        val settings = LeapOrientSettings(triggers = listOf(preset))
        val card = LeapTriggerCard(listOf(preset))
        val updated = LeapTriggerCards.applyShared(settings, card, sortTrigger)
        val stored = updated.triggers.single { it.id == preset.id }
        assertEquals(listOf(sortStep), stored.steps)
        assertEquals(false, stored.armLeap)
    }

    @Test
    fun maxorChatLineMatchesSortTriggerWithoutLeapTarget() {
        val line = "[BOSS] Storm: Pathetic Maxor, just like expected."
        assertEquals(LeapTriggerEvent.BOSS_STORM_START, LeapOrientEngine.bossEvent(line))
        val ctx = LeapRouteContext(
            floor = LeapFloor.F7,
            phase = LeapPhase.P2,
            section = 0,
            route = LeapStormRoute.PY,
            selfClass = LeapDungeonClass.HEALER,
        )
        val check = LeapOrientEngine.eligibility(sortTrigger, LeapTriggerEvent.BOSS_STORM_START, ctx)
        assertTrue(check.ok, check.rejected ?: "")
        // No prerequisite leap is needed, so the sort runs without deferring behind the lock state.
        assertTrue(LeapTriggerLockState.isSatisfied(sortTrigger))
    }
}
