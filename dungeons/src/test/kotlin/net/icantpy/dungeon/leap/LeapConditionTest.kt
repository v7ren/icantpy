package net.icantpy.dungeon.leap

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapConditionTest {
    private fun term(fact: LeapFact, value: String, negate: Boolean = false, join: LeapJoin = LeapJoin.AND) =
        LeapTerm(fact, value, negate, join)

    private val facts = LeapFacts(
        loadout = 3,
        selfClass = LeapDungeonClass.MAGE,
        partyClasses = setOf(LeapDungeonClass.MAGE, LeapDungeonClass.TANK),
        leapedTo = setOf(LeapDungeonClass.TANK),
        phase = LeapPhase.P2,
        floor = LeapFloor.M7,
        healthPercent = 40,
    )

    @Test
    fun termsReadTheFacts() {
        assertTrue(LeapConditions.holds(term(LeapFact.LOADOUT, "3"), facts))
        assertFalse(LeapConditions.holds(term(LeapFact.LOADOUT, "4"), facts))
        assertTrue(LeapConditions.holds(term(LeapFact.MY_CLASS, "MAGE"), facts))
        assertTrue(LeapConditions.holds(term(LeapFact.PARTY_HAS, "TANK"), facts))
        assertFalse(LeapConditions.holds(term(LeapFact.PARTY_HAS, "HEALER"), facts))
        assertTrue(LeapConditions.holds(term(LeapFact.LEAPED_TO, "TANK"), facts))
        assertTrue(LeapConditions.holds(term(LeapFact.PHASE, "P2"), facts))
        assertTrue(LeapConditions.holds(term(LeapFact.FLOOR, "M7"), facts))
        assertTrue(LeapConditions.holds(term(LeapFact.HEALTH_BELOW, "50"), facts))
        assertFalse(LeapConditions.holds(term(LeapFact.HEALTH_BELOW, "40"), facts))
    }

    @Test
    fun unknownFactsAreFalseAndNotFlipsThem() {
        val unknown = LeapFacts()
        assertFalse(LeapConditions.holds(term(LeapFact.LOADOUT, "3"), unknown))
        assertTrue(LeapConditions.holds(term(LeapFact.LOADOUT, "3", negate = true), unknown))
        assertFalse(LeapConditions.holds(term(LeapFact.HEALTH_BELOW, "50"), unknown))
    }

    @Test
    fun andBindsTighterThanOr() {
        val loadout3 = term(LeapFact.LOADOUT, "3")
        val loadout9 = term(LeapFact.LOADOUT, "9", join = LeapJoin.OR)
        val healer = term(LeapFact.PARTY_HAS, "HEALER", join = LeapJoin.AND)
        // 3 OR (9 AND healer): the first group holds.
        assertTrue(LeapConditions.evaluate(listOf(loadout3, loadout9, healer), facts))
        // 9 OR (3 AND healer): healer is missing, 9 is wrong.
        val swapped = listOf(term(LeapFact.LOADOUT, "9"), term(LeapFact.LOADOUT, "3", join = LeapJoin.OR), healer)
        assertFalse(LeapConditions.evaluate(swapped, facts))
        assertTrue(LeapConditions.evaluate(emptyList(), facts))
    }

    private fun step(branch: LeapBranch, vararg terms: LeapTerm) =
        LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 1, branch = branch, conditions = terms.toList())

    @Test
    fun ifElseIfElseRunsExactlyOneLink() {
        val isThree = term(LeapFact.LOADOUT, "3")
        val isFour = term(LeapFact.LOADOUT, "4")
        val steps = listOf(
            step(LeapBranch.IF, isFour),
            step(LeapBranch.ELSE_IF, isThree),
            step(LeapBranch.ELSE),
            step(LeapBranch.ALWAYS),
        )
        assertEquals(listOf(false, true, false, true), LeapConditions.plan(steps, facts))
        assertEquals(listOf(false, false, true, true), LeapConditions.plan(steps, facts.copy(loadout = 7)))
        assertEquals(listOf(true, false, false, true), LeapConditions.plan(steps, facts.copy(loadout = 4)))
    }

    @Test
    fun anAlwaysRowEndsTheChain() {
        val steps = listOf(step(LeapBranch.IF, term(LeapFact.LOADOUT, "3")), step(LeapBranch.ALWAYS), step(LeapBranch.ELSE))
        // The ELSE has no chain above it any more, so it acts as Always.
        assertEquals(listOf(true, true, true), LeapConditions.plan(steps, facts))
    }

    @Test
    fun repairedTurnsOrphanedElseRowsIntoStarters() {
        val steps = listOf(step(LeapBranch.ELSE_IF, term(LeapFact.LOADOUT, "3")), step(LeapBranch.ELSE), step(LeapBranch.ELSE))
        assertEquals(
            listOf(LeapBranch.IF, LeapBranch.ELSE, LeapBranch.ALWAYS),
            LeapConditions.repaired(steps).map { it.branch },
        )
    }

    @Test
    fun movingAndRemovingRowsKeepsChainsValid() {
        val steps = listOf(step(LeapBranch.IF, term(LeapFact.LOADOUT, "3")), step(LeapBranch.ELSE), step(LeapBranch.ALWAYS))
        val removedHead = LeapAutoStepList.removed(steps, 0)
        assertEquals(LeapBranch.ALWAYS, removedHead.first().branch)
        val movedElseUp = LeapAutoStepList.moved(steps, 1, 0)
        assertEquals(LeapBranch.IF, movedElseUp[1].branch)
        assertEquals(LeapBranch.ALWAYS, movedElseUp[0].branch)
    }

    @Test
    fun allowedBranchesNeedAChainAbove() {
        val steps = listOf(step(LeapBranch.ALWAYS), step(LeapBranch.IF), step(LeapBranch.ELSE_IF))
        assertEquals(listOf(LeapBranch.ALWAYS, LeapBranch.IF), LeapConditions.allowedBranches(steps, 0))
        assertEquals(listOf(LeapBranch.ALWAYS, LeapBranch.IF), LeapConditions.allowedBranches(steps, 1))
        assertEquals(LeapBranch.entries, LeapConditions.allowedBranches(steps, 2))
    }

    @Test
    fun labelsShowTheCondition() {
        val label = step(LeapBranch.IF, term(LeapFact.LOADOUT, "3"), term(LeapFact.PARTY_HAS, "HEALER", negate = true))
            .copy(requires = LeapDungeonClass.TANK).label()
        assertEquals("If loadout is 3 and not party has Healer: Loadout 1 · after Tank", label)
    }

    @Test
    fun stepsRoundTripThroughJson() {
        val original = listOf(
            step(LeapBranch.IF, term(LeapFact.LOADOUT, "3"), term(LeapFact.MY_CLASS, "MAGE", join = LeapJoin.OR))
                .copy(requires = LeapDungeonClass.HEALER),
            step(LeapBranch.ELSE),
        )
        val obj = JsonObject()
        obj.add("steps", LeapTriggerJson.stepsToJson(original))
        val trigger = LeapOrientTrigger(id = "t", event = LeapTriggerEvent.BOSS_STORM_END, steps = original)
        val json = JsonObject().also { it.add("triggers", LeapTriggerJson.triggersToJson(listOf(trigger))) }
        val back = LeapTriggerJson.triggersFromJson(json)!!.single().steps
        assertEquals(original, back)
    }

    @Test
    fun oldSavesLoadAsAlwaysRows() {
        val json = JsonObject()
        val steps = com.google.gson.JsonArray()
        steps.add(JsonObject().also { it.addProperty("kind", "SWITCH_LOADOUT"); it.addProperty("loadout", 2) })
        val trigger = JsonObject().also {
            it.addProperty("id", "old")
            it.addProperty("event", "BOSS_STORM_END")
            it.add("steps", steps)
        }
        json.add("triggers", com.google.gson.JsonArray().also { a -> a.add(trigger) })
        val step = LeapTriggerJson.triggersFromJson(json)!!.single().steps.single()
        assertEquals(LeapBranch.ALWAYS, step.branch)
        assertTrue(step.conditions.isEmpty())
        assertFalse(step.requires.isReal())
    }

    @Test
    fun parkedStepsWaitForTheirLeap() {
        LeapTriggerLockState.reset()
        val waiting = step(LeapBranch.ALWAYS).copy(requires = LeapDungeonClass.TANK)
        val trigger = LeapOrientTrigger(id = "t", event = LeapTriggerEvent.BOSS_STORM_END, steps = listOf(waiting))
        assertFalse(LeapTriggerLockState.isSatisfied(waiting))
        LeapTriggerLockState.deferStep(DeferredLeapStep(trigger, waiting, "me", "msg"))
        LeapTriggerLockState.markCompleted(LeapDungeonClass.MAGE)
        assertTrue(LeapTriggerLockState.takeSteps(LeapDungeonClass.MAGE).isEmpty())
        LeapTriggerLockState.markCompleted(LeapDungeonClass.TANK)
        assertTrue(LeapTriggerLockState.isSatisfied(waiting))
        assertEquals(1, LeapTriggerLockState.takeSteps(LeapDungeonClass.TANK).size)
        LeapTriggerLockState.reset()
    }
}
