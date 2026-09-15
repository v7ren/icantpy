package net.icantpy.modules.impl.dungeon.leaporient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeapOrientStateTest {
    @Test
    fun latestAutomaticTankOverridesOldHealerPreference() {
        LeapOrientTargets.clear()
        val settings = LeapOrientSettings().withPriority("m7p2", LeapPreferKind.CLASS, "healer")
        LeapOrientTargets.arm("healer", OrientSpot.ANY, LeapDungeonClass.HEALER, 10000, nowMs = 1000,
            priority = 40, sourceKind = LeapSourceKind.CLOCK)
        LeapOrientTargets.arm("tank", OrientSpot.ANY, LeapDungeonClass.TANK, 10000, nowMs = 1100,
            priority = 40, sourceKind = LeapSourceKind.CLOCK)
        assertEquals("tank", LeapOrientTargets.pick("m7p2", settings, nowMs = 1200)?.targetName)
        LeapOrientTargets.clear()
    }

    @Test
    fun cursorQuadrantMatchesOdinFormula() {
        assertEquals(0, LeapOrientSpots.cursorQuadrant(800, 600, 100, 100))
        assertEquals(1, LeapOrientSpots.cursorQuadrant(800, 600, 500, 100))
        assertEquals(2, LeapOrientSpots.cursorQuadrant(800, 600, 100, 400))
        assertEquals(3, LeapOrientSpots.cursorQuadrant(800, 600, 500, 400))
    }

    @Test
    fun centerHitUsesMiddleGap() {
        assertTrue(LeapOrientSpots.centerHit(800, 600, 400, 300, 1f, 180, 64))
        assertEquals(false, LeapOrientSpots.centerHit(800, 600, 40, 40, 1f, 180, 64))
    }

    @Test
    fun pickPrefersConfiguredLocationForGameState() {
        LeapOrientTargets.clear()
        val settings = LeapOrientSettings().withGoldorPriority(2, "ee2")
        LeapOrientTargets.arm("healer", OrientSpot.EE3, LeapDungeonClass.HEALER, 10_000, nowMs = 1_000)
        LeapOrientTargets.arm("tank", OrientSpot.GOLDOR_S4, LeapDungeonClass.TANK, 10_000, nowMs = 1_100)
        LeapOrientTargets.arm("archer", OrientSpot.EE2, LeapDungeonClass.ARCHER, 10_000, nowMs = 1_200)
        val pick = LeapOrientTargets.pick("m7p3s2", settings, nowMs = 1_300)
        assertEquals("archer", pick?.sender)
    }

    @Test
    fun pickFallsBackToMostRecentWhenPriorityMissing() {
        LeapOrientTargets.clear()
        val settings = LeapOrientSettings().withGoldorPriority(2, "ee2")
        LeapOrientTargets.arm("healer", OrientSpot.EE3, LeapDungeonClass.HEALER, 10_000, nowMs = 1_000)
        LeapOrientTargets.arm("tank", OrientSpot.GOLDOR_S4, LeapDungeonClass.TANK, 10_000, nowMs = 1_400)
        val pick = LeapOrientTargets.pick("m7p3s2", settings, nowMs = 1_500)
        assertEquals("tank", pick?.sender)
    }

    @Test
    fun pendingExpires() {
        LeapOrientTargets.clear()
        LeapOrientTargets.arm("archer", OrientSpot.EE2, LeapDungeonClass.ARCHER, 5, nowMs = 1_000)
        assertTrue(LeapOrientTargets.all(1_002).isNotEmpty())
        assertNull(LeapOrientTargets.pick("m7p3s2", LeapOrientSettings(), nowMs = 1_006))
    }

    @Test
    fun odinSortingPlacesArcherTopLeft() {
        val sorted = LeapMenuSort.odinSorting(
            listOf(
                LeapPlayer("heal", LeapDungeonClass.HEALER),
                LeapPlayer("arch", LeapDungeonClass.ARCHER),
                LeapPlayer("tank", LeapDungeonClass.TANK),
                LeapPlayer("bers", LeapDungeonClass.BERSERK),
            ),
        )
        assertEquals("arch", sorted[0].name)
        assertEquals("bers", sorted[1].name)
        assertEquals("heal", sorted[2].name)
        assertEquals("tank", sorted[3].name)
    }

    @Test
    fun debugPartyOmitsSimulatedSelfClass() {
        val players = DebugParty.leapPlayers("Ren", LeapDungeonClass.BERSERK)
        assertTrue(players.none { it.clazz == LeapDungeonClass.BERSERK })
        assertTrue(players.any { it.clazz == LeapDungeonClass.MAGE })
        assertTrue(players.any { it.clazz == LeapDungeonClass.ARCHER })
    }

    @Test
    fun preferredLocationReadsGoldorSection() {
        val settings = LeapOrientSettings().withGoldorPriority(3, "ee3")
        assertEquals("ee3", settings.preferredLocation("m7p3s3"))
        assertEquals("ee3", settings.preferredLocation("f7p3s3"))
    }

    @Test
    fun sssAndCoreAreDistinctFromEachOther() {
        assertEquals(OrientSpot.SS, OrientSpot.fromToken("sss"))
        assertEquals(OrientSpot.SS, OrientSpot.fromToken("ss"))
        assertTrue(OrientSpot.sameToken("ss", "sss"))
        assertEquals(OrientSpot.CORE, OrientSpot.fromToken("core"))
        assertEquals(false, OrientSpot.matches(OrientSpot.CORE, OrientSpot.GOLDOR_S4))
    }

    @Test
    fun pickPrefersClassWhenConfigured() {
        LeapOrientTargets.clear()
        val settings = LeapOrientSettings().withPriority("m7p2", LeapPreferKind.CLASS, "healer")
        LeapOrientTargets.arm("mage", OrientSpot.ANY, LeapDungeonClass.MAGE, 10_000, nowMs = 1_000)
        LeapOrientTargets.arm(
            "boss",
            OrientSpot.ANY,
            LeapDungeonClass.HEALER,
            10_000,
            nowMs = 900,
            targetClass = LeapDungeonClass.HEALER,
            label = "heal",
        )
        val pick = LeapOrientTargets.pick("m7p2", settings, nowMs = 1_100)
        assertEquals(LeapDungeonClass.HEALER, pick?.targetClass ?: pick?.clazz)
    }
}
