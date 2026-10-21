package net.icantpy.dungeon.leap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.icantpy.qol.auto.AutoTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapSortArmedTest {
    private val armedSort = LeapAutoStep(
        LeapAutoKind.SORT_INVENTORY,
        sortPreset = "P1",
        sortArmed = true,
        sortLockUntilDone = false,
        sortReopenBlockMs = 2000,
        sortArmTimeoutMs = 0,
    )
    private val base = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_START).copy(id = "armed-card")

    private fun jsonOf(triggers: List<LeapOrientTrigger>): JsonObject =
        JsonObject().apply { add("triggers", LeapTriggerJson.triggersToJson(triggers)) }

    private fun parse(text: String): List<LeapOrientTrigger> =
        LeapTriggerJson.triggersFromJson(JsonParser.parseString(text).asJsonObject)!!

    private class FakeRequester(private val accept: Boolean = true) : ArmedSortRequester {
        val calls = mutableListOf<List<Any>>()
        override fun requestArmedSort(
            presetName: String,
            cardKey: String,
            armTimeoutMs: Int,
            reopenBlockMs: Int,
            lockUntilDone: Boolean,
        ): Boolean {
            calls += listOf(presetName, cardKey, armTimeoutMs, reopenBlockMs, lockUntilDone)
            return accept
        }
    }

    @Test
    fun defaultsAreUnarmedWithLockOnAndOneSecondBlock() {
        val step = LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "P1")
        assertFalse(step.sortArmed)
        assertTrue(step.sortLockUntilDone)
        assertEquals(1000, step.sortReopenBlockMs)
        assertEquals(30_000, step.sortArmTimeoutMs)
    }

    @Test
    fun armedFieldsSurviveJsonRoundTrip() {
        val trigger = base.copy(steps = listOf(armedSort))
        val loaded = parse(jsonOf(listOf(trigger)).toString()).single()
        assertEquals(listOf(armedSort), loaded.steps)
    }

    @Test
    fun oldSortStepJsonLoadsWithDefaults() {
        val loaded = parse(
            """{"triggers": [{"id": "old", "event": "BOSS_STORM_START", "steps": [{"kind": "SORT_INVENTORY", "sortPreset": "Dps"}]}]}""",
        ).single()
        assertEquals(
            listOf(LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "Dps", sortLockUntilDone = true, sortReopenBlockMs = 1000, sortArmTimeoutMs = 30_000)),
            loaded.steps,
        )
        assertFalse(loaded.steps.single().sortArmed)
    }

    @Test
    fun armedLabelShowsSuffix() {
        assertEquals("Sort: P1 (armed)", armedSort.label())
        assertEquals("Sort: P1", armedSort.copy(sortArmed = false).label())
        assertEquals(listOf("Sort: P1 (armed)"), base.copy(steps = listOf(armedSort)).stepLabels())
    }

    @Test
    fun armedSortIsRoutedToArmAndFollowingStepsStayQueued() {
        val stats = LeapAutoStep(LeapAutoKind.EQUIP_STATS, statsPreset = "helm")
        val loadout = LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 3)
        val route = LeapAutoRouter.route(listOf(armedSort, stats, loadout))
        assertEquals(listOf(armedSort), route.armedSorts)
        assertEquals(listOf(AutoTarget.Stats("helm"), AutoTarget.Loadout(3)), route.targets)
        assertTrue(route.skipped.isEmpty())

        val requester = FakeRequester()
        assertEquals(1, LeapAutoRouter.arm(route, "card-key", requester))
        assertEquals(listOf(listOf<Any>("P1", "card-key", 0, 2000, false)), requester.calls)
    }

    @Test
    fun armedSortWithoutPresetIsSkippedNotArmed() {
        val route = LeapAutoRouter.route(listOf(armedSort.copy(sortPreset = "")))
        assertTrue(route.armedSorts.isEmpty())
        assertEquals(1, route.skipped.size)
    }

    @Test
    fun nonArmedSortStaysQueued() {
        val route = LeapAutoRouter.route(listOf(armedSort.copy(sortArmed = false)))
        assertEquals(listOf<AutoTarget>(AutoTarget.Sort("P1")), route.targets)
        assertTrue(route.armedSorts.isEmpty())
    }

    @Test
    fun testPlanArmsSortStepsToo() {
        val trigger = base.copy(steps = listOf(LeapAutoStep.leapTarget(), armedSort))
        val plan = LeapTestPlanner.plan(trigger)
        assertEquals(listOf(armedSort), plan.armedSorts)
        assertTrue(plan.targets.isEmpty())
        assertFalse(plan.nothingToTest)
    }

    @Test
    fun reopenAndTimeoutChoicesHaveLabels() {
        assertEquals("Off", LeapAutoStep.reopenBlockLabel(0))
        assertEquals("0.5s", LeapAutoStep.reopenBlockLabel(500))
        assertEquals("5s", LeapAutoStep.reopenBlockLabel(5000))
        assertEquals("Never", LeapAutoStep.armTimeoutLabel(0))
        assertEquals("2 min", LeapAutoStep.armTimeoutLabel(120_000))
        assertEquals("10s", LeapAutoStep.armTimeoutLabel(10_000))
    }
}
