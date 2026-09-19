package net.icantpy.dungeon.leap

import net.icantpy.api.IcantpyBossBarState
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Raw packet state -> boss transition -> eligible preset -> selected menu target. */
class LeapBossFlowTest {
    private val party = listOf(
        LeapPlayer("Mage", LeapDungeonClass.MAGE), LeapPlayer("Heal", LeapDungeonClass.HEALER),
        LeapPlayer("Bers", LeapDungeonClass.BERSERK), LeapPlayer("Tank", LeapDungeonClass.TANK),
    )
    private val settings = LeapOrientSettings()

    @AfterTest
    fun clear() { LeapOrientTargets.clear() }

    @Test
    fun healerMaxorDeathShowsBersBeforeLaterStormDialogue() {
        val bars = IcantpyBossBarState()
        val tracker = LeapBossTracker()
        repeat(121) { tracker.onServerTick() }
        val id = UUID.randomUUID()
        bars.add(id, "Maxor", 1f)!!.let { tracker.onBossBars(listOf(LeapBossBar(it.name, it.progress))) }
        val events = bars.progress(id, 0f)!!.let { tracker.onBossBars(listOf(LeapBossBar(it.name, it.progress))) }
        bars.remove(id)
        assertEquals(listOf(LeapTriggerEvent.BOSS_STORM_START), events)
        arm(events.single(), LeapDungeonClass.HEALER, tracker.phase)
        assertEquals(LeapDungeonClass.BERSERK, pick("m7p2"))
        assertTrue(tracker.onChat("[BOSS] Storm: Pathetic Maxor, just like expected.").isEmpty())
    }

    @Test
    fun necronZeroArmsHealerImmediatelyAndLateDialogueCannotUndoRelicBers() {
        val tracker = LeapBossTracker()
        tracker.onChat("[BOSS] Necron: I'm afraid, your journey ends now.")
        assertTrue(tracker.necronBersActive)
        arm(LeapTriggerEvent.BOSS_NECRON, LeapDungeonClass.MAGE, LeapPhase.P4)
        assertEquals(LeapDungeonClass.BERSERK, pick("m7p4"))
        tracker.onBossBars(listOf(LeapBossBar("Necron", 0.5f)))
        tracker.onChat("[BOSS] Necron: ARGH!")
        LeapOrientTargets.clear()
        val deaths = tracker.onBossBars(listOf(LeapBossBar("Necron", 0f)))
        assertEquals(listOf(LeapTriggerEvent.BOSS_NECRON_DEATH), deaths)
        arm(deaths.single(), LeapDungeonClass.MAGE, tracker.phase)
        assertEquals(LeapDungeonClass.HEALER, pick("m7p5"))
        arm(LeapTriggerEvent.RELIC_PICKUP, LeapDungeonClass.MAGE, LeapPhase.P5)
        assertEquals(LeapDungeonClass.BERSERK, pick("m7p5"))
        assertTrue(tracker.onChat("[BOSS] Necron: All this, for nothing...").isEmpty())
        assertEquals(LeapDungeonClass.BERSERK, pick("m7p5"))
    }

    private fun arm(event: LeapTriggerEvent, self: LeapDungeonClass, phase: LeapPhase) {
        val context = LeapRouteContext(floor = LeapFloor.M7, phase = phase, selfClass = self)
        val trigger = LeapRoutePreset.rows().first { LeapOrientEngine.eligibility(it, event, context).ok }
        val request = LeapOrientEngine.resolveArm(trigger, "boss", LeapDungeonClass.EMPTY, null,
            { name -> party.firstOrNull { it.name == name }?.clazz ?: LeapDungeonClass.EMPTY },
            { clazz -> party.firstOrNull { it.clazz == clazz } }, context)!!
        LeapOrientTargets.arm(request.sender, request.location, request.clazz, 10_000,
            nowMs = 1000, targetName = request.targetName, targetClass = request.targetClass,
            priority = request.priority, phase = request.phase, sourceKind = request.sourceKind)
    }

    private fun pick(state: String) = LeapOrientTargets.pick(state, settings, nowMs = 1001)?.targetClass
}
