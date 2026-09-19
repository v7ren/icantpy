package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OdinLeapBridgeTest {
    @Test
    fun classLookupIncludesSelfEvenThoughLeapMenuOmitsSelf() {
        val self = LeapPlayer("localHealer", LeapDungeonClass.HEALER)
        val bers = LeapPlayer("bers", LeapDungeonClass.BERSERK)
        val lookup = OdinLeapBridge.playersForClassLookup(listOf(self, bers), listOf(bers))
        assertEquals(LeapDungeonClass.HEALER, lookup.first { it.name == self.name }.clazz)
        val trigger = LeapRoutePreset.rows().first { it.id == "heal-predev" }
        assertTrue(LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.BOSS_STORM_START,
            LeapRouteContext(floor = LeapFloor.M7, phase = LeapPhase.P2,
                selfClass = lookup.first { it.name == self.name }.clazz)).ok)
    }

    @Test
    fun fullRosterClassWinsOverStaleLeapRosterAndNamesDeduplicate() {
        val all = listOf(LeapPlayer("Ren", LeapDungeonClass.MAGE))
        val leaps = listOf(LeapPlayer("ren", LeapDungeonClass.HEALER), LeapPlayer("tank", LeapDungeonClass.TANK))
        assertEquals(listOf(LeapPlayer("Ren", LeapDungeonClass.MAGE), LeapPlayer("tank", LeapDungeonClass.TANK)),
            OdinLeapBridge.playersForClassLookup(all, leaps))
    }
}
