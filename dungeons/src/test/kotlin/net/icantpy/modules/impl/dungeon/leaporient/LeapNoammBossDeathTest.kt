package net.icantpy.modules.impl.dungeon.leaporient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeapNoammBossDeathTest {
    @Test
    fun maxorZeroRequiresMoreThan120BossEntryTicksNotPositiveHealthHistory() {
        val tracker = LeapBossTracker()
        tracker.onChat("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")
        repeat(120) { tracker.onServerTick() }
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Maxor", 0f))).isEmpty())
        tracker.onServerTick()
        assertEquals(listOf(LeapTriggerEvent.BOSS_STORM_START),
            tracker.onBossBars(listOf(LeapBossBar("Maxor", 0f))))
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Maxor", 0f))).isEmpty())
    }

    @Test
    fun goldorZeroNeedsCoreOpeningButNotEarlierPositiveUpdate() {
        val tracker = LeapBossTracker()
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Goldor", 0f))).isEmpty())
        tracker.onChat("The Core entrance is opening!")
        assertEquals(listOf(LeapTriggerEvent.BOSS_GOLDOR_DEATH),
            tracker.onBossBars(listOf(LeapBossBar("Goldor", 0f))))
    }

    @Test
    fun necronZeroNeedsArghButNotEarlierPositiveUpdate() {
        val tracker = LeapBossTracker()
        tracker.onChat("[BOSS] Necron: I'm afraid, your journey ends now.")
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Necron", 0f))).isEmpty())
        tracker.onChat("[BOSS] Necron: ARGH!")
        assertEquals(listOf(LeapTriggerEvent.BOSS_NECRON_DEATH),
            tracker.onBossBars(listOf(LeapBossBar("Necron", 0f))))
        assertTrue(tracker.onChat("[BOSS] Necron: All this, for nothing...").isEmpty())
    }

    @Test
    fun stormUsesExactDeathDialogueNotZeroHealthBar() {
        val tracker = LeapBossTracker()
        tracker.onChat("[BOSS] Storm: Pathetic Maxor, just like expected.")
        tracker.onBossBars(listOf(LeapBossBar("Storm", 1f)))
        assertTrue(tracker.onBossBars(listOf(LeapBossBar("Storm", 0f))).isEmpty())
        assertEquals(listOf(LeapTriggerEvent.BOSS_STORM_END),
            tracker.onChat("[BOSS] Storm: I should have known that I stood no chance."))
    }
}
