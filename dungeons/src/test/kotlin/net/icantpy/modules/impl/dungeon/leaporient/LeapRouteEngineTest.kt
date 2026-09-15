package net.icantpy.modules.impl.dungeon.leaporient

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.icantpy.modules.impl.timer.ClockSnapshot
import net.icantpy.modules.impl.timer.TimerClock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeapRouteEngineTest {
    private val party = listOf(
        LeapPlayer("Arch", LeapDungeonClass.ARCHER),
        LeapPlayer("Bers", LeapDungeonClass.BERSERK),
        LeapPlayer("Heal", LeapDungeonClass.HEALER),
        LeapPlayer("Mage", LeapDungeonClass.MAGE),
        LeapPlayer("Tank", LeapDungeonClass.TANK),
    )

    @AfterTest
    fun clearLocks() {
        LeapOrientTargets.clear()
    }

    @Test
    fun magePyLightningTargetsTankNotHealer() {
        val trigger = row("mage-py-lightning-tank")
        val ctx = ctx(LeapDungeonClass.MAGE, LeapPhase.P2)
        assertTrue(LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.CLOCK, ctx).ok)
        val request = arm(trigger, ctx)
        assertEquals(LeapDungeonClass.TANK, request.targetClass)
        assertEquals("Purple checkpoint", trigger.zone)
        assertFalse(row("mage-py-35s-healer").id == trigger.id)
    }

    @Test
    fun berserkAndHealerDoNotReceiveMageStormClock() {
        val trigger = row("mage-py-35s-healer")
        val bers = LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.CLOCK, ctx(LeapDungeonClass.BERSERK, LeapPhase.P2))
        val heal = LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.CLOCK, ctx(LeapDungeonClass.HEALER, LeapPhase.P2))
        val unknown = LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.CLOCK, ctx(LeapDungeonClass.EMPTY, LeapPhase.P2))
        assertEquals("wrong class", bers.rejected)
        assertEquals("wrong class", heal.rejected)
        assertEquals("class unknown", unknown.rejected)
    }

    @Test
    fun magePyHasTwoDistinctHealerLocks() {
        val yellow = row("mage-py-35s-healer")
        val ss = row("mage-storm-healer")
        val ctx = ctx(LeapDungeonClass.MAGE, LeapPhase.P2)
        assertTrue(LeapOrientEngine.eligibility(yellow, LeapTriggerEvent.CLOCK, ctx).ok)
        assertTrue(LeapOrientEngine.eligibility(ss, LeapTriggerEvent.BOSS_STORM_END, ctx).ok)
        assertEquals("Yellow", yellow.zone)
        assertEquals("SS", ss.zone)
        assertNotEquals(yellow.reason, ss.reason)
        assertEquals("Healer @ Yellow", arm(yellow, ctx).label)
        assertEquals("Healer @ SS", arm(ss, ctx).label)
    }

    @Test
    fun healerPredevFollowsRoute() {
        val gy = LeapRoutePreset.rows(LeapStormRoute.GY).first { it.id == "heal-predev" }
        val py = LeapRoutePreset.rows(LeapStormRoute.PY).first { it.id == "heal-predev" }
        assertEquals("mage", gy.targetValue)
        assertEquals("berserk", py.targetValue)
        assertEquals("GY predev", gy.reason)
        assertEquals("PY predev", py.reason)
        val gyCtx = ctx(LeapDungeonClass.HEALER, LeapPhase.P2, LeapStormRoute.GY)
        assertEquals(LeapDungeonClass.MAGE, arm(gy, gyCtx).targetClass)
        val pyCtx = ctx(LeapDungeonClass.HEALER, LeapPhase.P2, LeapStormRoute.PY)
        assertEquals(LeapDungeonClass.BERSERK, arm(py, pyCtx).targetClass)
    }

    @Test
    fun padAfterZeroFiresLeapClockTrigger() {
        val spec = ClockSpec.forSeconds(TimerClock.PAD, -1.0)
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.CLOCK, "healer").copy(
            id = "pad-after-healer",
            clock = spec,
            forClass = LeapDungeonClass.MAGE,
            phase = LeapPhase.P2,
        )
        val watcher = LeapClockWatcher()
        watcher.evaluate(snap(TimerClock.PAD, 1), listOf(trigger))
        assertTrue(watcher.evaluate(snap(TimerClock.PAD, 0), listOf(trigger)).isEmpty())
        for (value in 19 downTo 1) {
            assertTrue(watcher.evaluate(snap(TimerClock.PAD, value), listOf(trigger)).isEmpty())
        }
        assertEquals(1, watcher.evaluate(snap(TimerClock.PAD, 0), listOf(trigger)).size)
    }

    @Test
    fun stormElapsedFiresOnceOnRisingCrossing() {
        val trigger = row("mage-py-35s-healer")
        val watcher = LeapClockWatcher()
        val spec = trigger.clock!!
        assertTrue(watcher.evaluate(snap(spec.clock, 680), listOf(trigger)).isEmpty())
        assertEquals(1, watcher.evaluate(snap(spec.clock, 700), listOf(trigger)).size)
        assertTrue(watcher.evaluate(snap(spec.clock, 700), listOf(trigger)).isEmpty())
    }

    @Test
    fun stormResetAllowsTheSameTriggerNextCycle() {
        val trigger = row("mage-py-35s-healer")
        val watcher = LeapClockWatcher()
        val spec = trigger.clock!!
        watcher.evaluate(snap(spec.clock, 680), listOf(trigger))
        assertEquals(1, watcher.evaluate(snap(spec.clock, 700), listOf(trigger)).size)
        watcher.evaluate(snap(spec.clock, -1), listOf(trigger))
        watcher.evaluate(snap(spec.clock, 0), listOf(trigger))
        watcher.evaluate(snap(spec.clock, 680), listOf(trigger))
        assertEquals(1, watcher.evaluate(snap(spec.clock, 700), listOf(trigger)).size)
    }

    @Test
    fun partyPingOverridesBossLock() {
        LeapOrientTargets.arm(
            sender = "boss",
            location = OrientSpot.ANY,
            clazz = LeapDungeonClass.HEALER,
            durationMs = 10_000,
            nowMs = 1_000,
            targetName = "Heal",
            targetClass = LeapDungeonClass.HEALER,
            priority = LeapSourceKind.BOSS.rank(),
            zone = "SS",
            reason = "Storm defeated · P3 entry",
            sourceKind = LeapSourceKind.BOSS,
        )
        val ping = LeapOrientTargets.arm(
            sender = "Arch",
            location = OrientSpot.EE2,
            clazz = LeapDungeonClass.ARCHER,
            durationMs = 10_000,
            nowMs = 1_100,
            targetName = "Arch",
            targetClass = LeapDungeonClass.ARCHER,
            priority = LeapSourceKind.PARTY_PING.rank(),
            zone = "ee2",
            reason = "Party ping",
            sourceKind = LeapSourceKind.PARTY_PING,
        )
        assertNotNull(ping)
        val pick = LeapOrientTargets.pick("m7p2", LeapOrientSettings(), nowMs = 1_200)
        assertEquals("Arch", pick?.targetName)
        val dropped = LeapOrientTargets.arm(
            sender = "clock",
            location = OrientSpot.ANY,
            clazz = LeapDungeonClass.HEALER,
            durationMs = 10_000,
            nowMs = 1_300,
            targetName = "Heal",
            targetClass = LeapDungeonClass.HEALER,
            priority = LeapSourceKind.CLOCK.rank(),
            zone = "Yellow",
            reason = "PY upcrush",
            sourceKind = LeapSourceKind.CLOCK,
        )
        assertNull(dropped)
        assertEquals("Arch", LeapOrientTargets.pick("m7p2", LeapOrientSettings(), nowMs = 1_400)?.targetName)
    }

    @Test
    fun missingTargetClearsWhenRosterIsKnown() {
        LeapOrientTargets.arm(
            sender = "boss",
            location = OrientSpot.ANY,
            clazz = LeapDungeonClass.HEALER,
            durationMs = 10_000,
            nowMs = 1_000,
            targetName = "Heal",
            targetClass = LeapDungeonClass.HEALER,
        )
        LeapOrientTargets.dropIfTargetMissing(setOf("Arch", "Mage"), nowMs = 1_100)
        assertTrue(LeapOrientTargets.all(1_100).isEmpty())
    }

    @Test
    fun oldJsonWithoutClassOrClockStillLoads() {
        val item = JsonObject()
        item.addProperty("id", "legacy-storm")
        item.addProperty("event", "BOSS_STORM_END")
        item.addProperty("targetValue", "healer")
        val root = JsonObject()
        val array = JsonArray()
        array.add(item)
        root.add("triggers", array)
        val parsed = LeapTriggerJson.triggersFromJson(root) ?: error("expected triggers")
        val trigger = parsed.first()
        assertEquals("legacy-storm", trigger.id)
        assertEquals(LeapDungeonClass.EMPTY, trigger.forClass)
        assertNull(trigger.clock)
        assertEquals(LeapFloor.ANY, trigger.floor)
    }

    @Test
    fun applyPresetTwiceDoesNotDuplicateRows() {
        val custom = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_CORE, "tank").copy(id = "custom-core")
        val once = LeapOrientSettings(triggers = listOf(custom)).applyPreset()
        val twice = once.applyPreset()
        assertEquals(once.triggers.size, twice.triggers.size)
        assertEquals(1, twice.triggers.count { it.id == "custom-core" })
        assertEquals(1, twice.triggers.count { it.id == "mage-storm-healer" })
    }

    @Test
    fun f7NeverArmsP5Triggers() {
        val trigger = row("mage-necron-healer").copy(phase = LeapPhase.P5, group = "P5")
        val ctx = ctx(LeapDungeonClass.MAGE, LeapPhase.P5, floor = LeapFloor.F7)
        assertEquals("F7 no P5", LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.BOSS_NECRON_DEATH, ctx).rejected)
    }

    @Test
    fun necronDeathUsesP5EntryLine() {
        assertEquals(
            LeapTriggerEvent.BOSS_NECRON_DEATH,
            LeapOrientEngine.bossEvent("[BOSS] Necron: All this, for nothing..."),
        )
        val trigger = LeapOrientSettings.DEFAULT_TRIGGERS.first { it.id == "mage-necron-healer" }
        assertEquals(LeapTriggerEvent.BOSS_NECRON_DEATH, trigger.event)
        val ctx = ctx(LeapDungeonClass.MAGE, LeapPhase.P4, floor = LeapFloor.M7)
        assertTrue(LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.BOSS_NECRON_DEATH, ctx).ok)
    }

    @Test
    fun necronCongratulationsConfirmsGoldorDeathTransition() {
        assertEquals(
            LeapTriggerEvent.BOSS_GOLDOR_DEATH,
            LeapOrientEngine.bossEvent("[BOSS] Necron: You went further than any human before, congratulations."),
        )
        assertEquals("healer", row("mage-goldor-healer").targetValue)
    }

    @Test
    fun necronIntroMapsToNecronStart() {
        assertEquals(
            LeapTriggerEvent.BOSS_NECRON,
            LeapOrientEngine.bossEvent("§cDungeon: [BOSS] Necron: I'm afraid, your journey ends now."),
        )
    }

    @Test
    fun goldorDeathLineMapsToEvent() {
        assertEquals(
            LeapTriggerEvent.BOSS_GOLDOR_DEATH,
            LeapOrientEngine.bossEvent("[BOSS] Goldor: You have done it, you destroyed the factory…"),
        )
    }

    @Test
    fun duplicateClassRequiresExplicitPlayer() {
        val dupes = party + LeapPlayer("Heal2", LeapDungeonClass.HEALER)
        val ctx = ctx(LeapDungeonClass.MAGE, LeapPhase.P2).copy(knownPlayers = dupes)
        val trigger = row("mage-storm-healer")
        assertNull(armOrNull(trigger, ctx))
        val assigned = ctx.copy(classPlayers = mapOf(LeapDungeonClass.HEALER to "Heal"))
        assertEquals("Heal", arm(trigger, assigned).targetName)
    }

    @Test
    fun stormDeathLockSurvivesP3Entry() {
        val ctx = ctx(LeapDungeonClass.MAGE, LeapPhase.P2)
        val request = arm(row("mage-storm-healer"), ctx)
        assertEquals(LeapPhase.P3, request.phase)
        LeapOrientTargets.arm(
            sender = request.sender,
            location = request.location,
            clazz = request.clazz,
            durationMs = 10_000,
            nowMs = 1_000,
            targetName = request.targetName,
            targetClass = request.targetClass,
            priority = request.priority,
            zone = request.zone,
            reason = request.reason,
            phase = request.phase,
        )
        LeapOrientTargets.dropIfPhaseChanged(LeapPhase.P3, nowMs = 1_100)
        assertEquals("Heal", LeapOrientTargets.pick("m7p3s1", LeapOrientSettings(), nowMs = 1_200)?.targetName)
    }

    @Test
    fun resolveTargetPrefersAssignedName() {
        val pending = PendingLeap(
            sender = "boss",
            location = OrientSpot.ANY,
            clazz = LeapDungeonClass.HEALER,
            expiresAtMs = 10_000,
            atMs = 1_000,
            targetName = "Heal2",
            targetClass = LeapDungeonClass.HEALER,
        )
        val teammates = listOf(
            LeapPlayer("Heal", LeapDungeonClass.HEALER),
            LeapPlayer("Heal2", LeapDungeonClass.HEALER),
        )
        assertEquals("Heal2", LeapRoster.resolveTarget(pending, teammates, LeapOrientSettings())?.name)
    }

    @Test
    fun resolveTargetFallsBackToClassInChest() {
        val pending = PendingLeap(
            sender = "dbgBers",
            location = OrientSpot.GOLDOR_S4,
            clazz = LeapDungeonClass.BERSERK,
            expiresAtMs = 10_000,
            atMs = 1_000,
            targetName = "dbgBers",
            targetClass = LeapDungeonClass.BERSERK,
        )
        val teammates = listOf(
            LeapPlayer("oTuesday", LeapDungeonClass.BERSERK),
            LeapPlayer("FireKnight573", LeapDungeonClass.MAGE),
        )
        assertEquals("oTuesday", LeapRoster.resolveTarget(pending, teammates, LeapOrientSettings())?.name)
    }

    @Test
    fun leapedChainDuringClearDoesNotArm() {
        val trigger = row("heal-py-landed-bers")
        assertEquals(LeapTriggerEvent.LEAP_CHAIN, trigger.event)
        val clear = ctx(LeapDungeonClass.HEALER, LeapPhase.CLEAR)
        assertEquals("wrong phase", LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.LEAP_CHAIN, clear).rejected)
        val p2 = ctx(LeapDungeonClass.HEALER, LeapPhase.P2)
        assertTrue(LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.LEAP_CHAIN, p2).ok)
    }

    @Test
    fun leapChainSchedulerArmsAfterDelay() {
        val trigger = row("heal-py-landed-bers")
        val ctx = ctx(LeapDungeonClass.HEALER, LeapPhase.P2)
        val request = LeapOrientEngine.resolveArm(
            trigger = trigger,
            sender = "you",
            senderClass = LeapDungeonClass.HEALER,
            leaped = LeapedChatMatch("Mage", LeapDungeonClass.MAGE, "mage"),
            classOf = { name -> party.firstOrNull { it.name.equals(name, ignoreCase = true) }?.clazz ?: LeapDungeonClass.EMPTY },
            playerForClass = { clazz -> party.firstOrNull { it.clazz == clazz } },
            context = ctx,
        ) ?: error("expected arm")
        assertEquals(LeapDungeonClass.BERSERK, request.targetClass)
        LeapChainScheduler.schedule(trigger, request, delayMs = 500, durationMs = 8_000, nowMs = 1_000)
        assertTrue(LeapChainScheduler.due(nowMs = 1_400).isEmpty())
        val ready = LeapChainScheduler.due(nowMs = 1_600)
        assertEquals(1, ready.size)
        assertEquals(LeapDungeonClass.BERSERK, ready.first().request.targetClass)
    }

    @Test
    fun p5RelicsLineMapsToEvent() {
        assertEquals(
            LeapTriggerEvent.BOSS_NECRON_DEATH,
            LeapOrientEngine.bossEvent("[BOSS] Necron: All this, for nothing..."),
        )
        val trigger = row("heal-p5-relic-bers")
        assertEquals(LeapTriggerEvent.RELIC_PICKUP, trigger.event)
        val ctx = ctx(LeapDungeonClass.HEALER, LeapPhase.P5)
        assertTrue(LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.RELIC_PICKUP, ctx).ok)
    }

    @Test
    fun f7BlocksP5RelicTriggers() {
        val trigger = row("heal-p5-relic-bers")
        val ctx = ctx(LeapDungeonClass.HEALER, LeapPhase.P5, floor = LeapFloor.F7)
        assertEquals("F7 no P5", LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.RELIC_PICKUP, ctx).rejected)
    }

    @Test
    fun relicItemDetectionDoesNotAssumeAColor() {
        assertTrue(LeapRelicState.isKingRelicId("PURPLE_KING_RELIC"))
        assertTrue(LeapRelicState.isKingRelicId("CYAN_KING_RELIC"))
        assertFalse(LeapRelicState.isKingRelicId("KING_RELIC"))
        assertFalse(LeapRelicState.isKingRelicId("PURPLE_RELIC"))
    }

    @Test
    fun everyClassGetsTheSameGenericRelicPreset() {
        val triggers = LeapRoutePreset.rows().filter { it.event == LeapTriggerEvent.RELIC_PICKUP }
        assertEquals(LeapDungeonClass.entries.filter(LeapDungeonClass::isReal).toSet(), triggers.map { it.forClass }.toSet())
        assertTrue(triggers.all { it.targetValue == "berserk" })
        assertTrue(triggers.all { trigger ->
            listOf(trigger.zone, trigger.reason).none { label ->
                listOf("purple", "blue", "green", "orange", "red").any { it in label.lowercase() }
            }
        })
    }

    @Test
    fun legacyColorPresetMigratesToGenericRelicPreset() {
        val oldId = "tank-p5-green-arch"
        val legacy = LeapOrientTrigger(
            id = oldId,
            enabled = false,
            event = LeapTriggerEvent.RELIC_PICKUP,
            targetValue = "archer",
            forClass = LeapDungeonClass.TANK,
            presetId = oldId,
        )
        val migrated = LeapRoutePreset.migrate(
            LeapOrientSettings(triggers = listOf(legacy), disabledPresetIds = setOf(oldId)),
        )
        val generic = migrated.triggers.first { it.id == "tank-p5-relic-bers" }
        assertFalse(generic.enabled)
        assertEquals("berserk", generic.targetValue)
        assertTrue(migrated.triggers.none { it.id == oldId })
    }

    @Test
    fun bossDeathPresetTargetsMatchDefaultRoute() {
        assertEquals("berserk", row("mage-maxor-bers").targetValue)
        assertEquals("healer", row("mage-storm-healer").targetValue)
        assertEquals("healer", row("bers-storm-healer").targetValue)
        assertEquals("healer", row("mage-goldor-healer").targetValue)
        assertEquals("healer", row("mage-necron-healer").targetValue)
    }

    private fun row(id: String): LeapOrientTrigger =
        LeapRoutePreset.rows().first { it.id == id }

    private fun ctx(
        self: LeapDungeonClass,
        phase: LeapPhase,
        route: LeapStormRoute = LeapStormRoute.PY,
        floor: LeapFloor = LeapFloor.M7,
    ): LeapRouteContext = LeapRouteContext(
        floor = floor,
        phase = phase,
        section = if (phase == LeapPhase.P3) 1 else 0,
        route = route,
        selfClass = self,
        ee2Owner = LeapDungeonClass.MAGE,
        knownPlayers = party,
    )

    private fun arm(trigger: LeapOrientTrigger, ctx: LeapRouteContext): LeapArmRequest =
        armOrNull(trigger, ctx) ?: error("expected arm for ${trigger.id}")

    private fun armOrNull(trigger: LeapOrientTrigger, ctx: LeapRouteContext): LeapArmRequest? =
        LeapOrientEngine.resolveArm(
            trigger = trigger,
            sender = "boss",
            senderClass = LeapDungeonClass.EMPTY,
            leaped = null,
            classOf = { name -> party.firstOrNull { it.name.equals(name, ignoreCase = true) }?.clazz ?: LeapDungeonClass.EMPTY },
            playerForClass = { clazz -> party.firstOrNull { it.clazz == clazz } },
            context = ctx,
        )

    private fun snap(clock: TimerClock, value: Int): ClockSnapshot =
        ClockSnapshot(mapOf(clock to value))
}
