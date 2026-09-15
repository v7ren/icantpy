package net.icantpy.modules.impl.timer

import net.icantpy.modules.impl.dungeon.BossRooms
import net.icantpy.modules.impl.dungeon.ChatClocks
import net.icantpy.modules.impl.dungeon.DungeonFloor
import net.icantpy.modules.impl.terra.TerraPots
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TickClocksTest {
    @Test
    fun stormPadArmsPadLightningAndStormTick() {
        val next = TickClocks().onChat("[BOSS] Storm: Pathetic Maxor, just like expected.")
        assertEquals(TickClocks.PAD_TICKS, next.pad)
        assertEquals(TickClocks.LIGHTNING_TICKS, next.lightning)
        assertEquals(0, next.stormTick)
    }

    @Test
    fun padLoopsFromZeroToNineteen() {
        assertEquals(0, TickClocks(pad = 1).tick().pad)
        assertEquals(TickClocks.PAD_TICKS - 1, TickClocks(pad = 0).tick().pad)
    }

    @Test
    fun goldorTickLoopsAndStormDeathStartsGoldor() {
        var clocks = TickClocks().onChat("[BOSS] Storm: I should have known that I stood no chance.")
        assertEquals(TickClocks.GOLDOR_START_TICKS, clocks.goldorStart)
        assertEquals(104, clocks.goldorStart)
        assertEquals(TickClocks.INACTIVE, clocks.pad)
        assertEquals(TickClocks.INACTIVE, clocks.stormTick)
        clocks = clocks.onChat("[BOSS] Goldor: Who dares trespass into my domain?")
        assertEquals(TickClocks.GOLDOR_TICKS, clocks.goldorTick)
        assertEquals(0, clocks.goldorElapsed)
        clocks = TickClocks(goldorTick = 1).tick()
        assertEquals(0, clocks.goldorTick)
        clocks = clocks.tick()
        assertEquals(TickClocks.GOLDOR_TICKS - 1, clocks.goldorTick)
        clocks = TickClocks(goldorTick = 0, goldorStart = 5).tick()
        assertEquals(TickClocks.INACTIVE, clocks.goldorTick)
        assertEquals(4, clocks.goldorStart)
    }

    @Test
    fun padTipsFollowTheCrushWindow() {
        assertEquals(PadTip.NONE, PadTip.fromPadTicks(-1))
        assertEquals(PadTip.STEP_ON, PadTip.fromPadTicks(20))
        assertEquals(PadTip.STEP_ON, PadTip.fromPadTicks(15))
        assertEquals(PadTip.GYRO, PadTip.fromPadTicks(14))
        assertEquals(PadTip.GYRO, PadTip.fromPadTicks(8))
        assertEquals(PadTip.GET_OFF, PadTip.fromPadTicks(7))
        assertEquals(PadTip.GET_OFF, PadTip.fromPadTicks(0))
    }

    @Test
    fun coreOpeningClearsGoldor() {
        val next = TickClocks(goldorTick = 40, goldorStart = 10)
            .onChat("The Core entrance is opening!")
        assertEquals(TickClocks.INACTIVE, next.goldorTick)
        assertEquals(TickClocks.INACTIVE, next.goldorStart)
    }

    @Test
    fun necronDropIsSixtyTicks() {
        val next = TickClocks().onChat("[BOSS] Necron: I'm afraid, your journey ends now.")
        assertEquals(TickClocks.NECRON_TICKS, next.necron)
        assertEquals(TickClocks.INACTIVE, next.tickUntilInactive(TickClocks.NECRON_TICKS + 1).necron)
    }

    @Test
    fun stormPyFiresOnceAtNinetyFive() {
        val first = TickClocks().onChat("[BOSS] Storm: ENERGY HEED MY CALL!")
        assertEquals(TickClocks.PY_TICKS, first.py)
        assertTrue(first.pyTriggered)
        val second = first.onChat("[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!")
        assertEquals(TickClocks.PY_TICKS, second.py)
        val done = TickClocks(py = 0, pyTriggered = true).tick()
        assertEquals(TickClocks.INACTIVE, done.py)
        assertEquals(TickClocks.INACTIVE, done.onChat("[BOSS] Storm: ENERGY HEED MY CALL!").py)
    }

    @Test
    fun stormTickCountsUpAndSecretsUseCounter() {
        val started = TickClocks().onChat("[BOSS] Storm: Pathetic Maxor, just like expected.")
        val ticked = started.tick()
        assertEquals(1, ticked.stormTick)
        assertEquals(19, ticked.secretRemaining())
        val mort = ticked.onChat("[NPC] Mort: Here, I found this map when I first entered the dungeon.")
        assertEquals(0, mort.secretsCounter)
        assertEquals(20, mort.secretRemaining())
    }

    @Test
    fun scoreboardParsesMasterFloorAndCleared() {
        assertEquals(
            DungeonFloor.M7,
            ChatClocks.floorFromLines(listOf("Master Mode Catacombs (M7)")),
        )
        assertEquals(
            DungeonFloor.F6,
            ChatClocks.floorFromLines(listOf("The Catacombs (F6)")),
        )
        assertTrue(ChatClocks.inDungeon(listOf("The Catacombs (F7)")))
        assertTrue(ChatClocks.inDungeon(listOf("Cleared: 100%", "Time Elapsed: 01m 02s")))
        assertEquals(0, ChatClocks.percentCleared(listOf("Cleared: 0%")))
        assertEquals(61, ChatClocks.percentCleared(listOf("Cleared: 61% (136)")))
        assertTrue(ChatClocks.inDungeon(listOf("Crypts: 5")))
        assertFalse(ChatClocks.inDungeon(listOf("SkyBlock Hub")))
        assertTrue(ChatClocks.elsewhere(listOf("SkyBlock Hub")))
        assertTrue(ChatClocks.elsewhere(listOf("Area: Hub")))
        assertFalse(ChatClocks.elsewhere(listOf("Area: Storm")))
        assertFalse(ChatClocks.elsewhere(listOf("Time Elapsed: 01m 02s")))
        assertFalse(ChatClocks.elsewhere(emptyList()))
    }

    @Test
    fun f7BossBoxIncludesStormPads() {
        assertTrue(BossRooms.contains(DungeonFloor.F7, 0.0, 0.0))
        assertTrue(BossRooms.contains(DungeonFloor.M7, -6.9, -6.9))
        assertTrue(BossRooms.contains(DungeonFloor.F7, -8.0, 0.0))
        assertTrue(BossRooms.contains(DungeonFloor.F7, -20.0, 35.0))
        assertFalse(BossRooms.contains(DungeonFloor.F7, -41.0, 0.0))
        assertFalse(BossRooms.contains(DungeonFloor.F6, 0.0, -10.0))
        assertTrue(BossRooms.contains(DungeonFloor.F6, 0.0, 0.0))
        assertFalse(BossRooms.contains(DungeonFloor.NONE, 0.0, 0.0))
    }

    @Test
    fun necronFormatMatchesOdinPrefix() {
        val text = TickClocks.format("§4Necron dropping in", 35, 60, showTicks = true, TimerSettings())
        assertTrue(text.contains("Necron dropping in"))
        assertTrue(text.contains("35t"))
    }

    @Test
    fun settingsToggleIsImmutable() {
        val next = TimerSettings().toggle("padHud")
        assertFalse(next.padHud)
        assertTrue(TimerSettings().padHud)
    }

    @Test
    fun hudPositionClampsToScreen() {
        assertEquals(0 to 0, TimerSettings.clamp(-10, -4, 40, 20, 200, 100))
        assertEquals(160 to 80, TimerSettings.clamp(999, 999, 40, 20, 200, 100))
        assertEquals(8 to 8, TimerSettings.clamp(8, 8, 40, 20, 200, 100))
    }

    @Test
    fun notifyDefaultsToCenterAndTriggersRoundTrip() {
        val settings = TimerSettings()
        assertEquals(-1 to -1, settings.position(TimerHud.NOTIFICATION))
        assertEquals(80 to 26, settings.resolveNotifyPosition(40, 12, 200, 100))
        val trigger = TimerTrigger.create(TimerClock.PAD, seconds = 1.0, message = "hi", durationSeconds = 3.0)
        val next = settings.upsertTrigger(trigger)
        assertEquals(1, next.triggers.size)
        assertEquals(20, next.triggers[0].ticks)
        assertEquals(60, next.triggers[0].durationTicks)
        assertTrue(next.removeTrigger(trigger.id).triggers.isEmpty())
    }

    private fun TickClocks.tickUntilInactive(times: Int): TickClocks {
        var next = this
        repeat(times) { next = next.tick() }
        return next
    }
}

class TimerAlertsTest {
    @Test
    fun necronFiresWhenRemainingCrossesThreshold() {
        val trigger = TimerTrigger("necron", TimerClock.NECRON, ticks = 20)
        val arm = TriggerArm()
        assertTrue(arm.evaluate(snap(TimerClock.NECRON to 40), listOf(trigger)).isEmpty())
        assertTrue(arm.evaluate(snap(TimerClock.NECRON to 21), listOf(trigger)).isEmpty())
        assertEquals(1, arm.evaluate(snap(TimerClock.NECRON to 20), listOf(trigger)).size)
        assertTrue(arm.evaluate(snap(TimerClock.NECRON to 19), listOf(trigger)).isEmpty())
    }

    @Test
    fun countdownFiresWhenFinished() {
        val trigger = TimerTrigger("pad-done", TimerClock.PAD, ticks = 0)
        val arm = TriggerArm()
        arm.evaluate(snap(TimerClock.PAD to 2), listOf(trigger))
        assertTrue(arm.evaluate(snap(TimerClock.PAD to 1), listOf(trigger)).isEmpty())
        assertEquals(1, arm.evaluate(snap(TimerClock.PAD to 0), listOf(trigger)).size)
    }

    @Test
    fun padRearmsAfterLoop() {
        val trigger = TimerTrigger("pad", TimerClock.PAD, ticks = 10)
        val arm = TriggerArm()
        arm.evaluate(snap(TimerClock.PAD to 20), listOf(trigger))
        assertEquals(1, arm.evaluate(snap(TimerClock.PAD to 10), listOf(trigger)).size)
        arm.evaluate(snap(TimerClock.PAD to 0), listOf(trigger))
        arm.evaluate(snap(TimerClock.PAD to 19), listOf(trigger))
        assertEquals(1, arm.evaluate(snap(TimerClock.PAD to 10), listOf(trigger)).size)
    }

    @Test
    fun stormElapsedFiresOnCountUp() {
        val trigger = TimerTrigger("storm", TimerClock.STORM_ELAPSED, ticks = 40)
        val arm = TriggerArm()
        arm.evaluate(snap(TimerClock.STORM_ELAPSED to 0), listOf(trigger))
        assertTrue(arm.evaluate(snap(TimerClock.STORM_ELAPSED to 39), listOf(trigger)).isEmpty())
        assertEquals(1, arm.evaluate(snap(TimerClock.STORM_ELAPSED to 40), listOf(trigger)).size)
    }

    @Test
    fun disabledTriggerDoesNotFire() {
        val trigger = TimerTrigger("off", TimerClock.PY, ticks = 40, enabled = false)
        val arm = TriggerArm()
        arm.evaluate(snap(TimerClock.PY to 50), listOf(trigger))
        assertTrue(arm.evaluate(snap(TimerClock.PY to 40), listOf(trigger)).isEmpty())
    }

    @Test
    fun thirteenPointFourSecondsIsTwoHundredSixtyEightTicks() {
        assertEquals(268, TimerTrigger.secondsToTicks(13.4))
        assertEquals("13.40s", TimerTrigger.formatSeconds(268))
        assertEquals(0.0, TimerTrigger.parseSeconds(""))
        assertEquals(2.0, TimerTrigger.parseSeconds("2"))
        assertEquals(-2.0, TimerTrigger.parseSeconds("-2"))
        assertEquals(-40, TimerTrigger.secondsToTicks(-2.0))
        assertEquals("-2", TimerTrigger.ticksToInput(-40))
        assertEquals(null, TimerTrigger.parseSeconds("-"))
    }

    @Test
    fun countdownNegativeFiresOneSecondAfterTouchingZero() {
        val trigger = TimerTrigger("after", TimerClock.PAD, ticks = -20)
        val arm = TriggerArm()
        arm.evaluate(snap(TimerClock.PAD to 1), listOf(trigger))
        assertTrue(arm.evaluate(snap(TimerClock.PAD to 0), listOf(trigger)).isEmpty())
        for (value in 19 downTo 1) {
            assertTrue(arm.evaluate(snap(TimerClock.PAD to value), listOf(trigger)).isEmpty(), "pad $value")
        }
        assertEquals(1, arm.evaluate(snap(TimerClock.PAD to 0), listOf(trigger)).size)
        for (value in 19 downTo 1) {
            assertTrue(arm.evaluate(snap(TimerClock.PAD to value), listOf(trigger)).isEmpty())
        }
        assertEquals(1, arm.evaluate(snap(TimerClock.PAD to 0), listOf(trigger)).size)
    }

    @Test
    fun countdownNegativeFiresAfterTheClockFinishes() {
        val trigger = TimerTrigger("after", TimerClock.LIGHTNING, ticks = -40)
        val arm = TriggerArm()
        arm.evaluate(snap(TimerClock.LIGHTNING to 2), listOf(trigger))
        arm.evaluate(snap(TimerClock.LIGHTNING to 0), listOf(trigger))
        repeat(39) {
            assertTrue(arm.evaluate(snap(), listOf(trigger)).isEmpty())
        }
        assertEquals(1, arm.evaluate(snap(), listOf(trigger)).size)
    }

    @Test
    fun countUpNegativeFiresAfterTheClockEnds() {
        val trigger = TimerTrigger("after-storm", TimerClock.STORM_ELAPSED, ticks = -20)
        val arm = TriggerArm()
        arm.evaluate(snap(TimerClock.STORM_ELAPSED to 0), listOf(trigger))
        arm.evaluate(snap(TimerClock.STORM_ELAPSED to 40), listOf(trigger))
        assertTrue(arm.evaluate(snap(), listOf(trigger)).isEmpty())
        repeat(19) {
            assertTrue(arm.evaluate(snap(), listOf(trigger)).isEmpty())
        }
        assertEquals(1, arm.evaluate(snap(), listOf(trigger)).size)
    }

    @Test
    fun overlayUsesCustomMessageOrDone() {
        assertEquals("§e§lPAD DONE", TimerTrigger("a", TimerClock.PAD, ticks = 0).overlayText())
        assertEquals("§e§lcrush", TimerTrigger("b", TimerClock.PAD, ticks = 0, message = "crush").overlayText())
        assertEquals("§e§lPAD 1s AFTER", TimerTrigger("c", TimerClock.PAD, ticks = -20).overlayText())
    }

    private fun snap(vararg pairs: Pair<TimerClock, Int>): ClockSnapshot {
        val values = TimerClock.entries.associateWith { TickClocks.INACTIVE }.toMutableMap()
        pairs.forEach { (clock, value) -> values[clock] = value }
        return ClockSnapshot(values)
    }
}

class TerraPotsTest {
    @Test
    fun addsFlowerPotOnceAndExpires() {
        var pots = TerraPots()
        pots = pots.add(1, 70, 2, 2)
        pots = pots.add(1, 70, 2, 2)
        assertEquals(1, pots.entries.size)
        pots = pots.tick()
        assertEquals(1, pots.entries[0].ticksLeft)
        pots = pots.tick()
        assertTrue(pots.entries.isEmpty())
    }

    @Test
    fun enoughClearsPots() {
        val pots = TerraPots().add(0, 0, 0, 40).clear()
        assertTrue(pots.entries.isEmpty())
    }
}
