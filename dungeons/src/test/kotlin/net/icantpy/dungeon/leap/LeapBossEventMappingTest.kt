package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Each F7/M7 boss line maps to exactly one event, and the trigger dropdown labels match the fight order. */
class LeapBossEventMappingTest {
    private fun event(line: String) = LeapOrientEngine.bossEvent(line)

    @Test
    fun maxorLinesMapToStartAndDeathNotStormStart() {
        assertEquals(LeapTriggerEvent.MAXOR_START, event("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"))
        assertEquals(LeapTriggerEvent.MAXOR_DEATH, event("[BOSS] Maxor: I'M TOO YOUNG TO DIE AGAIN!"))
        assertEquals(LeapTriggerEvent.BOSS_ENTRY, LeapChatEvents.detect("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!").single().first)
    }

    @Test
    fun stormGoldorNecronAndWitherKingLines() {
        assertEquals(LeapTriggerEvent.BOSS_STORM_START, event("[BOSS] Storm: Pathetic Maxor, just like expected."))
        assertEquals(LeapTriggerEvent.BOSS_STORM_END, event("[BOSS] Storm: I should have known that I stood no chance."))
        assertEquals(LeapTriggerEvent.BOSS_GOLDOR, event("[BOSS] Goldor: Who dares trespass into my domain?"))
        assertEquals(LeapTriggerEvent.BOSS_GOLDOR_DEATH, event("[BOSS] Goldor: You have done it, you destroyed the factory..."))
        assertEquals(LeapTriggerEvent.BOSS_NECRON, event("[BOSS] Necron: I'm afraid, your journey ends now."))
        assertEquals(LeapTriggerEvent.BOSS_NECRON_DEATH, event("[BOSS] Necron: All this, for nothing..."))
        assertEquals(
            LeapTriggerEvent.WITHER_KING_DEATH,
            event("[BOSS] Wither King: Incredible. You did what I couldn't do myself."),
        )
        assertEquals(
            LeapTriggerEvent.P5_START,
            LeapChatEvents.detect("[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you.").single().first,
        )
    }

    @Test
    fun unrelatedBossLinesMapToNothing() {
        assertNull(event("[BOSS] Maxor: Some other line"))
        assertNull(event("[BOSS] Wither King: Incredible."))
    }

    @Test
    fun dropdownLabelsAreFightOrderedAndUnique() {
        val f7 = LeapTriggerEvent.entries.filter { it.group == "F7/M7 boss" }
        assertEquals(
            listOf(
                "Maxor starts (P1)", "Maxor dies", "Storm starts (P2)", "Storm dies", "Goldor starts (P3)",
                "Core opens", "Goldor dies", "Necron starts (P4)", "Necron dies", "Wither King starts (P5)",
                "P5 relics", "Relic picked up", "Your relic placed", "Any relic placed", "Wither King dies",
                "Bonzo dies", "Maxor stunned (crystal beam)", "Boss enraged", "Energy crystal picked",
                "Storm crushed", "Storm lightning/pad phase", "Wither King dragon killed",
                "Dragon spawning", "Dragon spawned", "Dragon killed", "Priority dragon chosen",
            ),
            f7.map { it.label() },
        )
    }

    @Test
    fun savedBossStormStartIdStillLoads() {
        assertEquals(LeapTriggerEvent.BOSS_STORM_START, LeapTriggerEvent.valueOf("BOSS_STORM_START"))
    }
}
