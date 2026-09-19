package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapBossTrackerTest {
    @Test
    fun observedMaxorDeathDeduplicatesLateStormIntro() {
        val tracker = LeapBossTracker()
        repeat(121) { tracker.onServerTick() }

        assertTrue(tracker.onBossBars(listOf(LeapBossBar("§cMaxor", 1f))).isEmpty())
        assertEquals(LeapPhase.P1, tracker.phase)
        assertEquals(
            listOf(LeapTriggerEvent.BOSS_STORM_START),
            tracker.onBossBars(listOf(LeapBossBar("§cMaxor", 0f))),
        )
        assertEquals(LeapPhase.P2, tracker.phase)
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("§cMaxor", 0f))).isEmpty())
        assertTrue(tracker.onChat("[BOSS] Storm: Pathetic Maxor, just like expected.").isEmpty())
        assertEquals(LeapPhase.P2, tracker.phase)
    }

    @Test
    fun goldorBarDeathRequiresCoreOpening() {
        val tracker = LeapBossTracker()
        assertEquals(
            listOf(LeapTriggerEvent.BOSS_GOLDOR),
            tracker.onChat("[BOSS] Goldor: Who dares trespass into my domain?"),
        )
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Goldor", 0.5f))).isEmpty())
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Goldor", 0f))).isEmpty())
        assertEquals(LeapPhase.P3, tracker.phase)

        assertEquals(listOf(LeapTriggerEvent.BOSS_CORE), tracker.onChat("The Core entrance is opening!"))
        assertEquals(
            listOf(LeapTriggerEvent.BOSS_GOLDOR_DEATH),
            tracker.onBossBars(listOf(LeapBossBar("Goldor", 0f))),
        )
        assertEquals(LeapPhase.P4, tracker.phase)
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Goldor", 0f))).isEmpty())
    }

    @Test
    fun necronPhasePersistsWhenBarsDisappearAndEarlierDialogueArrives() {
        val tracker = LeapBossTracker()
        assertEquals(
            listOf(LeapTriggerEvent.BOSS_NECRON),
            tracker.onChat("[BOSS] Necron: I'm afraid, your journey ends now."),
        )
        assertEquals(LeapPhase.P4, tracker.phase)
        assertTrue(tracker.necronBersActive)

        assertTrue(tracker.onBossBars(emptyList()).isEmpty())
        assertTrue(tracker.onChat("An unrelated chat message").isEmpty())
        tracker.onChat("[BOSS] Storm: I should have known that I stood no chance.")
        assertEquals(LeapPhase.P4, tracker.phase)
        assertTrue(tracker.necronBersActive)
    }

    @Test
    fun necronRageDisablesBersUntilDeathMovesToP5() {
        val tracker = LeapBossTracker()
        tracker.onChat("[BOSS] Necron: I'm afraid, your journey ends now.")
        assertTrue(tracker.necronBersActive)

        assertTrue(tracker.onChat("[BOSS] Necron: ARGH!").isEmpty())
        assertEquals(LeapPhase.P4, tracker.phase)
        assertTrue(tracker.necronRaging)
        assertFalse(tracker.necronBersActive)

        assertEquals(
            listOf(LeapTriggerEvent.BOSS_NECRON_DEATH),
            tracker.onChat("[BOSS] Necron: All this, for nothing..."),
        )
        assertEquals(LeapPhase.P5, tracker.phase)
        assertFalse(tracker.necronBersActive)
        assertTrue(tracker.onChat("[BOSS] Necron: All this, for nothing...").isEmpty())
        assertTrue(tracker.onBossBars(emptyList()).isEmpty())
        assertEquals(LeapPhase.P5, tracker.phase)
    }

    @Test
    fun zeroBarsRequireNoammEntryCoreAndArghGuards() {
        val tracker = LeapBossTracker()
        val zeroBars = listOf("Maxor", "Storm", "Goldor", "Necron").map { LeapBossBar(it, 0f) }

        assertTrue(tracker.onBossBars(zeroBars).isEmpty())
        assertEquals(LeapPhase.UNKNOWN, tracker.phase)
        assertFalse(tracker.necronBersActive)

        tracker.onChat("The Core entrance is opening!")
        assertEquals(listOf(LeapTriggerEvent.BOSS_GOLDOR_DEATH),
            tracker.onBossBars(listOf(LeapBossBar("Goldor", 0f))))
        assertEquals(LeapPhase.P4, tracker.phase)
        tracker.onChat("[BOSS] Necron: I'm afraid, your journey ends now.")
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Necron", 0f))).isEmpty())
        assertEquals(LeapPhase.P4, tracker.phase)
        assertTrue(tracker.necronBersActive)
    }
}
