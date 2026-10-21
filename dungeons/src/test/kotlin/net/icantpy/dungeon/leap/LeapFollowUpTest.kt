package net.icantpy.dungeon.leap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.icantpy.qol.auto.AutoTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LeapFollowUpTest {
    private val leap = LeapAutoStep.leapTarget()

    private fun followUp(value: String, within: Int = 20, hold: LeapHoldMode = LeapHoldMode.TIMED) = LeapAutoStep(
        kind = LeapAutoKind.FOLLOWUP_LEAP,
        followTarget = LeapCenterTarget.CLASS,
        followTargetValue = value,
        followHoldMode = hold,
        followWithinSeconds = within,
    )

    private fun card(vararg steps: LeapAutoStep) = LeapOrientTrigger(
        id = "card-1",
        event = LeapTriggerEvent.SELF_LEAP,
        target = LeapCenterTarget.CLASS,
        targetValue = "healer",
        steps = steps.toList(),
    )

    /** A live target armed at [atMs] for [name] (or only [clazz] when [name] is blank). */
    private fun armed(name: String, clazz: LeapDungeonClass?, atMs: Long = 0L, expiresAtMs: Long = 60_000L) = PendingLeap(
        sender = "me",
        location = OrientSpot.ANY,
        clazz = clazz ?: LeapDungeonClass.EMPTY,
        expiresAtMs = expiresAtMs,
        atMs = atMs,
        targetName = name,
        targetClass = clazz,
    )

    @Test
    fun followUpArmsAfterLeapingToExpectedTarget() {
        LeapFollowUps.clear()
        val c = card(leap, followUp("tank"))
        LeapFollowUps.register(c, armed("Healer1", LeapDungeonClass.HEALER), "Healer1", LeapDungeonClass.HEALER)

        val outcomes = LeapFollowUps.onLeap("Healer1", LeapDungeonClass.HEALER)

        assertEquals(1, outcomes.size)
        val hit = assertIs<LeapFollowUpOutcome.Hit>(outcomes[0])
        assertEquals("tank", hit.pending.next.followTargetValue)
        assertEquals(0, LeapFollowUps.pendingCount())
    }

    @Test
    fun leapToSomeoneElseDiscardsTheChain() {
        LeapFollowUps.clear()
        LeapFollowUps.register(card(leap, followUp("tank")), armed("Healer1", LeapDungeonClass.HEALER), "Healer1", LeapDungeonClass.HEALER)

        val outcome = assertIs<LeapFollowUpOutcome.Discard>(LeapFollowUps.onLeap("Mage2", LeapDungeonClass.MAGE).single())

        assertEquals("leaped to someone else", outcome.reason)
        assertEquals(0, LeapFollowUps.pendingCount())
    }

    @Test
    fun classOnlyExpectationMatchesByClass() {
        LeapFollowUps.clear()
        LeapFollowUps.register(card(leap, followUp("tank")), armed("", LeapDungeonClass.HEALER), "", LeapDungeonClass.HEALER)

        assertTrue(LeapFollowUps.onLeap("Anyone", LeapDungeonClass.TANK).single() is LeapFollowUpOutcome.Discard)

        LeapFollowUps.register(card(leap, followUp("tank")), armed("", LeapDungeonClass.HEALER), "", LeapDungeonClass.HEALER)
        assertIs<LeapFollowUpOutcome.Hit>(LeapFollowUps.onLeap("Anyone", LeapDungeonClass.HEALER).single())
    }

    @Test
    fun windowExpiryDiscardsTheChain() {
        LeapFollowUps.clear()
        LeapFollowUps.register(card(leap, followUp("tank", within = 5)), armed("Healer1", LeapDungeonClass.HEALER, atMs = 0L), "Healer1", LeapDungeonClass.HEALER)
        val live = listOf(armed("Healer1", LeapDungeonClass.HEALER, atMs = 0L))

        assertTrue(LeapFollowUps.tick(4_999L, live).isEmpty())
        val discards = LeapFollowUps.tick(5_000L, live)

        assertEquals(listOf("wait window passed"), discards.map { it.reason })
        assertEquals(0, LeapFollowUps.pendingCount())
    }

    @Test
    fun zeroWindowWaitsUntilThePreviousTargetEnds() {
        LeapFollowUps.clear()
        LeapFollowUps.register(card(leap, followUp("tank", within = 0)), armed("Healer1", LeapDungeonClass.HEALER, expiresAtMs = 30_000L), "Healer1", LeapDungeonClass.HEALER)
        val live = listOf(armed("Healer1", LeapDungeonClass.HEALER, expiresAtMs = 30_000L))

        assertTrue(LeapFollowUps.tick(29_000L, live).isEmpty())
        assertEquals(listOf("previous target ended"), LeapFollowUps.tick(30_000L, emptyList()).map { it.reason })
    }

    @Test
    fun replacedMainTargetDiscardsTheChain() {
        LeapFollowUps.clear()
        LeapFollowUps.register(card(leap, followUp("tank")), armed("Healer1", LeapDungeonClass.HEALER), "Healer1", LeapDungeonClass.HEALER)
        val replaced = listOf(armed("Mage2", LeapDungeonClass.MAGE, atMs = 1_000L))

        assertEquals(listOf("main target replaced"), LeapFollowUps.tick(1_000L, replaced).map { it.reason })
    }

    @Test
    fun fallbackThatIsStillLiveKeepsTheChain() {
        LeapFollowUps.clear()
        LeapFollowUps.register(card(leap, followUp("tank")), armed("Healer1", LeapDungeonClass.HEALER), "Healer1", LeapDungeonClass.HEALER)

        assertTrue(LeapFollowUps.tick(1_000L, listOf(armed("Healer1", LeapDungeonClass.HEALER))).isEmpty())
        assertEquals(1, LeapFollowUps.pendingCount())
    }

    @Test
    fun chainOfTwoFollowUpsArmsInOrder() {
        LeapFollowUps.clear()
        LeapFollowUps.register(card(leap, followUp("tank"), followUp("mage")), armed("Healer1", LeapDungeonClass.HEALER), "Healer1", LeapDungeonClass.HEALER)

        val first = assertIs<LeapFollowUpOutcome.Hit>(LeapFollowUps.onLeap("Healer1", LeapDungeonClass.HEALER).single()).pending
        assertEquals("tank", first.next.followTargetValue)
        LeapFollowUps.continueWith(first, armed("Tank3", LeapDungeonClass.TANK, atMs = 2_000L), "Tank3", LeapDungeonClass.TANK)
        assertEquals(1, LeapFollowUps.pendingCount())

        val second = assertIs<LeapFollowUpOutcome.Hit>(LeapFollowUps.onLeap("Tank3", LeapDungeonClass.TANK).single()).pending
        assertEquals("mage", second.next.followTargetValue)
        LeapFollowUps.continueWith(second, armed("Mage4", LeapDungeonClass.MAGE, atMs = 3_000L), "Mage4", LeapDungeonClass.MAGE)
        assertEquals(0, LeapFollowUps.pendingCount())
    }

    @Test
    fun followUpsAreCappedAtThreePerCard() {
        val c = card(leap, followUp("tank"), followUp("mage"), followUp("archer"), followUp("berserk"))

        assertEquals(3, LeapFollowUp.steps(c).size)
        assertTrue(LeapFollowUp.steps(card(followUp("tank"))).isEmpty(), "a follow-up without a leap target never runs")
    }

    @Test
    fun followUpIsNotQueuedAsAnAutoAction() {
        val route = LeapAutoRouter.route(listOf(followUp("tank"), LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 2)))

        assertEquals(listOf<AutoTarget>(AutoTarget.Loadout(2)), route.targets)
        assertTrue(route.skipped.isEmpty())
    }

    @Test
    fun kindSelectorOffersFollowUpOnlyWithALeapTarget() {
        val sort = LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "P1")

        // A sort row cycling forward reaches Follow-up when another row holds the leap target.
        // Loadout -> Leap target is skipped (leap sits in row 0), so the picker lands on Follow-up.
        assertEquals(LeapAutoKind.FOLLOWUP_LEAP, LeapAutoStep.nextKind(LeapAutoKind.SWITCH_LOADOUT, 1, listOf(leap, sort)))
        assertTrue(LeapAutoStep.canBeFollowUp(listOf(leap, sort), 1))
        // Without a leap target on the card, Follow-up is skipped and the picker moves on to Mark player.
        assertEquals(LeapAutoKind.MARK_PLAYER, LeapAutoStep.nextKind(LeapAutoKind.LEAP_TARGET, 0, listOf(sort)))
        assertTrue(!LeapAutoStep.canBeFollowUp(listOf(sort), 0))
        // The leap row itself cannot become a follow-up: it is the only leap target.
        assertTrue(!LeapAutoStep.canBeFollowUp(listOf(leap), 0))
        // The cap of three other follow-ups also removes the option.
        val full = listOf(leap, followUp("a"), followUp("b"), followUp("c"), sort)
        // Loadout -> Leap (skipped) -> Follow-up (capped) -> Mark player.
        assertEquals(LeapAutoKind.MARK_PLAYER, LeapAutoStep.nextKind(LeapAutoKind.SWITCH_LOADOUT, 4, full))
        assertTrue(!LeapAutoStep.canBeFollowUp(full, 4))
    }

    @Test
    fun addingAFollowUpWithoutALeapTargetIsRefused() {
        val sort = LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = "P1")

        assertEquals(listOf(sort), LeapAutoStepList.added(listOf(sort), followUp("tank")))
        assertEquals(2, LeapAutoStepList.added(listOf(leap), followUp("tank")).size)
    }

    @Test
    fun followUpFieldsRoundTripThroughJson() {
        val step = followUp("mage", within = 60, hold = LeapHoldMode.UNTIL_REPLACED).copy(
            followTarget = LeapCenterTarget.NAME,
            followTargetPlayer = "Alice",
            followHoldLocked = true,
            followHoldSeconds = 15,
        )
        val trigger = card(leap, step)
        val obj = JsonObject().apply { add("triggers", LeapTriggerJson.triggersToJson(listOf(trigger))) }

        val parsed = LeapTriggerJson.triggersFromJson(obj)!!.single()

        assertEquals(trigger.steps, parsed.steps)
    }

    @Test
    fun oldFollowUpJsonUsesDefaults() {
        val text = """{"triggers":[{"id":"x","event":"SELF_LEAP","steps":[{"kind":"LEAP_TARGET"},{"kind":"FOLLOWUP_LEAP"}]}]}"""

        val parsed = LeapTriggerJson.triggersFromJson(JsonParser.parseString(text).asJsonObject)!!.single()
        val follow = parsed.steps[1]

        assertEquals(LeapAutoKind.FOLLOWUP_LEAP, follow.kind)
        assertEquals(LeapCenterTarget.CLASS, follow.followTarget)
        assertEquals("healer", follow.followTargetValue)
        assertEquals(LeapHoldMode.TIMED, follow.followHoldMode)
        assertEquals(20, follow.followWithinSeconds)
    }

    @Test
    fun subtitleShowsLeapChain() {
        val trigger = card(leap, followUp("tank"))

        assertEquals("Leap: Healer → then Tank", trigger.subtitle())

        val withQueued = card(leap, followUp("tank"), LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = 2))
        assertEquals("Leap: Healer → then Tank · Loadout 2", withQueued.subtitle())
    }

    @Test
    fun nextLabelShowsPendingFollowUp() {
        LeapFollowUps.clear()
        assertEquals(null, LeapFollowUps.nextLabel())

        LeapFollowUps.register(card(leap, followUp("tank")), armed("Healer1", LeapDungeonClass.HEALER), "Healer1", LeapDungeonClass.HEALER)

        assertEquals("next: Tank", LeapFollowUps.nextLabel())
        LeapFollowUps.clear()
    }
}
