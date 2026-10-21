package net.icantpy.dungeon.leap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeapTargetHoldTest {
    private data class Target(
        override val holdTitle: String,
        override val holdKey: String,
        override val holdPriority: Int,
        override val holdMode: LeapHoldMode,
        override val holdLocked: Boolean = false,
        override val holdArmedAtMs: Long = 0,
        override val holdExpiresAtMs: Long = LEAP_HOLD_NEVER_MS,
    ) : LeapHoldable<Target> {
        override fun withExpiry(expiresAtMs: Long): Target = copy(holdExpiresAtMs = expiresAtMs)
    }

    private fun timed(name: String, priority: Int = 5, now: Long = 0, seconds: Int = 5, locked: Boolean = false) =
        Target(name, name, priority, LeapHoldMode.TIMED, locked, now, now + seconds * 1000L)

    private fun until(name: String, priority: Int = 5, now: Long = 0, locked: Boolean = false) =
        Target(name, name, priority, LeapHoldMode.UNTIL_REPLACED, locked, now, now + LEAP_HOLD_NEVER_MS)

    private fun fallback(name: String, priority: Int = 5, now: Long = 0, locked: Boolean = false) =
        Target(name, name, priority, LeapHoldMode.FALLBACK, locked, now, now + LEAP_HOLD_NEVER_MS)

    @Test
    fun timedTargetExpiresWhenItsTimeEnds() {
        val hold = LeapTargetHold<Target>()
        assertTrue(hold.arm(timed("berserk", seconds = 5)))
        hold.tick(4_999)
        assertEquals("berserk", hold.current()?.holdTitle)
        hold.tick(5_000)
        assertNull(hold.current())
        assertTrue(hold.drainEvents().any { it.describe() == "expired: berserk" })
    }

    @Test
    fun untilReplacedTargetPersistsAcrossTime() {
        val hold = LeapTargetHold<Target>()
        hold.arm(until("healer"))
        hold.tick(10 * 60_000L)
        assertEquals("healer", hold.current()?.holdTitle)
    }

    @Test
    fun fallbackComesBackAfterOverrideExpires() {
        val hold = LeapTargetHold<Target>()
        hold.arm(fallback("tank"))
        hold.arm(timed("mage", seconds = 3))
        assertEquals("mage", hold.current()?.holdTitle)
        assertEquals("tank", hold.fallback()?.holdTitle)
        hold.drainEvents()
        hold.tick(3_000)
        assertEquals("tank", hold.current()?.holdTitle)
        assertTrue(hold.drainEvents().any { it.kind == LeapHoldEventKind.FALLBACK_RESTORED && it.title == "tank" })
    }

    @Test
    fun newerFallbackReplacesOlderFallback() {
        val hold = LeapTargetHold<Target>()
        hold.arm(fallback("old"))
        hold.arm(fallback("new"))
        assertEquals("new", hold.fallback()?.holdTitle)
        assertEquals(listOf("new"), hold.all().map { it.holdTitle })
    }

    @Test
    fun fallbackDoesNotInterruptActiveOverride() {
        val hold = LeapTargetHold<Target>()
        hold.arm(timed("mage", seconds = 10))
        hold.arm(fallback("tank"))
        assertEquals("mage", hold.current()?.holdTitle)
    }

    @Test
    fun lockBlocksEqualAndLowerPriorityButAllowsHigher() {
        val hold = LeapTargetHold<Target>()
        hold.arm(timed("first", priority = 5, seconds = 10, locked = true))
        assertFalse(hold.arm(timed("equal", priority = 5)))
        assertFalse(hold.arm(timed("lower", priority = 4)))
        assertEquals("first", hold.current()?.holdTitle)
        val events = hold.drainEvents().map { it.kind }
        assertEquals(listOf(LeapHoldEventKind.ARMED, LeapHoldEventKind.BLOCKED_BY_LOCK, LeapHoldEventKind.BLOCKED_BY_LOCK), events)
        assertTrue(hold.arm(timed("higher", priority = 6)))
        assertEquals("higher", hold.current()?.holdTitle)
    }

    @Test
    fun lockedTimedTargetUnlocksWhenItsTimeEnds() {
        val hold = LeapTargetHold<Target>()
        hold.arm(timed("first", priority = 9, seconds = 2, locked = true))
        assertFalse(hold.arm(timed("blocked", priority = 1)))
        hold.tick(2_000)
        assertTrue(hold.arm(timed("after", priority = 1)))
        assertEquals("after", hold.current()?.holdTitle)
    }

    @Test
    fun lockedUntilReplacedTargetStaysUntilReset() {
        val hold = LeapTargetHold<Target>()
        hold.arm(until("locked", priority = 5, locked = true))
        hold.tick(60 * 60_000L)
        assertFalse(hold.arm(timed("lower", priority = 5)))
        hold.reset()
        assertNull(hold.current())
        assertTrue(hold.arm(timed("free", priority = 1)))
    }

    @Test
    fun lockedFallbackBlocksLowerOverride() {
        val hold = LeapTargetHold<Target>()
        hold.arm(fallback("base", priority = 7, locked = true))
        assertFalse(hold.arm(timed("override", priority = 7)))
        assertTrue(hold.arm(timed("stronger", priority = 8)))
    }

    @Test
    fun resetClearsOverridesAndFallback() {
        val hold = LeapTargetHold<Target>()
        hold.arm(fallback("base"))
        hold.arm(until("override"))
        hold.reset()
        assertNull(hold.current())
        assertNull(hold.fallback())
        assertTrue(hold.all().isEmpty())
    }

    @Test
    fun sameKeyRearmRefreshesInsteadOfStacking() {
        val hold = LeapTargetHold<Target>()
        hold.arm(timed("mage", seconds = 3))
        hold.arm(timed("mage", seconds = 10, now = 1_000))
        assertEquals(1, hold.overrides().size)
        assertEquals(11_000L, hold.current()?.holdExpiresAtMs)
    }

    @Test
    fun lowerPriorityOverrideIsRefusedWithoutLock() {
        val hold = LeapTargetHold<Target>()
        hold.arm(timed("strong", priority = 9))
        assertFalse(hold.arm(timed("weak", priority = 2)))
        assertEquals("strong", hold.current()?.holdTitle)
        assertTrue(hold.drainEvents().any { it.describe() == "lower priority than current: weak" })
    }

    @Test
    fun durationDefaultsFollowTheGlobalSetting() {
        assertEquals(8_000L, LeapHoldSpec().durationMs(8))
        assertEquals(12_000L, LeapHoldSpec(seconds = 12).durationMs(8))
        assertEquals(LEAP_HOLD_NEVER_MS, LeapHoldSpec(LeapHoldMode.UNTIL_REPLACED).durationMs(8))
        assertEquals(LEAP_HOLD_NEVER_MS, LeapHoldSpec(LeapHoldMode.FALLBACK).durationMs(8))
    }

    @Test
    fun statusTextShowsRemainingSecondsAndModes() {
        assertEquals("6s", LeapHoldText.status(timed("a"), remainingMs = 5_200))
        assertEquals("until replaced", LeapHoldText.status(until("b"), remainingMs = 1))
        assertEquals("fallback · locked", LeapHoldText.status(fallback("c", locked = true), remainingMs = 1))
        assertEquals("then: base", LeapHoldText.thenLine(timed("over"), fallback("base")))
        assertNull(LeapHoldText.thenLine(fallback("base"), fallback("base")))
    }

    @Test
    fun oldTriggerJsonKeepsTimedDefaults() {
        val text = """{"triggers":[{"id":"old","event":"BOSS_STORM_END"}]}"""
        val parsed = LeapTriggerJson.triggersFromJson(JsonParser.parseString(text).asJsonObject)!!.single()
        assertEquals(LeapHoldMode.TIMED, parsed.holdMode)
        assertEquals(0, parsed.holdSeconds)
        assertFalse(parsed.holdLocked)
        assertEquals(10_000L, parsed.holdSpec().durationMs(10))
    }

    @Test
    fun oldChainCardKeepsItsChainHoldLength() {
        val text = """{"triggers":[{"id":"chain","event":"LEAP_CHAIN","chainDurationSeconds":8}]}"""
        val parsed = LeapTriggerJson.triggersFromJson(JsonParser.parseString(text).asJsonObject)!!.single()
        assertEquals(8_000L, parsed.holdSpec().durationMs(10))
    }

    @Test
    fun holdSettingsRoundTripThroughJson() {
        val trigger = LeapOrientTrigger.create().copy(holdMode = LeapHoldMode.FALLBACK, holdSeconds = 15, holdLocked = true)
        val obj = JsonObject().apply { add("triggers", LeapTriggerJson.triggersToJson(listOf(trigger))) }
        val parsed = LeapTriggerJson.triggersFromJson(JsonParser.parseString(obj.toString()).asJsonObject)!!.single()
        assertEquals(LeapHoldMode.FALLBACK, parsed.holdMode)
        assertEquals(15, parsed.holdSeconds)
        assertTrue(parsed.holdLocked)
        assertEquals(" · fallback · locked", LeapHoldText.cardSuffix(parsed.holdSpec()))
    }

    @Test
    fun timedPendingTargetsExpireThroughTargetStore() {
        LeapOrientTargets.clear()
        LeapOrientTargets.arm("a", OrientSpot.ANY, LeapDungeonClass.HEALER, 2_000, nowMs = 1_000, priority = 3)
        assertEquals(1, LeapOrientTargets.all(nowMs = 2_999).size)
        assertTrue(LeapOrientTargets.all(nowMs = 3_000).isEmpty())
    }

    @Test
    fun fallbackPendingTargetIsPickedWhenNothingIsOverriding() {
        LeapOrientTargets.clear()
        val settings = LeapOrientSettings()
        LeapOrientTargets.arm("tank", OrientSpot.ANY, LeapDungeonClass.TANK, 0, nowMs = 0, priority = 1,
            holdMode = LeapHoldMode.FALLBACK)
        assertEquals("tank", LeapOrientTargets.pick("p1", settings, nowMs = 1_000_000)?.targetName)
        LeapOrientTargets.arm("mage", OrientSpot.ANY, LeapDungeonClass.MAGE, 2_000, nowMs = 1_000_000, priority = 2)
        assertEquals("mage", LeapOrientTargets.pick("p1", settings, nowMs = 1_000_500)?.targetName)
        assertEquals("tank", LeapOrientTargets.pick("p1", settings, nowMs = 1_002_000)?.targetName)
        LeapOrientTargets.clear()
        assertNull(LeapOrientTargets.pick("p1", settings, nowMs = 1_002_000))
    }
}
