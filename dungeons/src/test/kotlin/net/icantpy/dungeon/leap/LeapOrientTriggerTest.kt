package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeapOrientTriggerTest {
    @Test
    fun announceTemplateFillsClassAndName() {
        val text = LeapChatPatterns.formatAnnounce(
            "[icantpy] leaped to {class}",
            "Ren",
            LeapDungeonClass.HEALER,
        )
        assertEquals("[icantpy] leaped to Healer", text)
        val named = LeapChatPatterns.formatAnnounce(
            "[icantpy] leaped to {name}",
            "Ren",
            LeapDungeonClass.MAGE,
        )
        assertEquals("[icantpy] leaped to Ren", named)
    }

    @Test
    fun parseLeapedClassAndName() {
        val settings = LeapOrientSettings()
        val byClass = LeapOrientEngine.parseLeaped("[icantpy] leaped to Healer", settings)
        assertEquals(LeapDungeonClass.HEALER, byClass?.asClass)
        val byName = LeapOrientEngine.parseLeaped("Leaped to Ren!", settings)
        assertEquals("Ren", byName?.asName)
        assertNull(LeapOrientEngine.parseLeaped("[icantpy] at ee2", settings))
    }

    @Test
    fun locationPingAlsoAcceptsClass() {
        val ping = LeapOrientEngine.parseAtPing("[icantpy] at healer", "[icantpy]")
        assertNotNull(ping)
        assertEquals(LeapDungeonClass.HEALER, ping.second)
        val spot = LeapOrientEngine.parseAtPing("[icantpy] at sss", "[icantpy]")
        assertEquals(OrientSpot.SS, spot?.first)
    }

    @Test
    fun leapedToSelfMatchesOwnClass() {
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.LEAPED_TO, "berserk").copy(match = "self")
        val leaped = LeapedChatMatch("Healer", LeapDungeonClass.HEALER, null)
        assertTrue(
            LeapOrientEngine.leapedMatchHits(
                trigger,
                leaped,
                "Ren",
                LeapDungeonClass.HEALER,
            ) { LeapDungeonClass.EMPTY },
        )
    }

    @Test
    fun stormDeathTriggerAppliesOnP2() {
        val trigger = LeapOrientSettings.DEFAULT_TRIGGERS.first { it.id == "mage-storm-healer" }
        val ctx = LeapOrientEngine.contextFromStateId("m7p3", selfClass = LeapDungeonClass.MAGE)
        assertTrue(LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.BOSS_STORM_END, ctx).ok)
        assertEquals(LeapTriggerEvent.BOSS_STORM_END, LeapOrientEngine.bossEvent("[BOSS] Storm: I should have known that I stood no chance."))
        assertEquals(
            LeapTriggerEvent.BOSS_STORM_START,
            LeapOrientEngine.bossEvent("[BOSS] Storm: Pathetic Maxor, just like expected."),
        )
        assertEquals(
            LeapTriggerEvent.BOSS_GOLDOR_DEATH,
            LeapOrientEngine.bossEvent("[BOSS] Necron: You went further than any human before, congratulations."),
        )
        assertEquals(
            LeapTriggerEvent.BOSS_NECRON,
            LeapOrientEngine.bossEvent("[BOSS] Necron: I'm afraid, your journey ends now."),
        )
        assertEquals(
            LeapTriggerEvent.BOSS_GOLDOR_DEATH,
            LeapOrientEngine.bossEvent("[BOSS]   Goldor: You have done it, you destroyed the factory..."),
        )
        assertEquals(
            LeapTriggerEvent.BOSS_GOLDOR_DEATH,
            LeapOrientEngine.bossEvent("§cDungeon: [BOSS] Necron: You went further than any human before, congratulations."),
        )
        assertEquals(
            LeapTriggerEvent.BOSS_NECRON_DEATH,
            LeapOrientEngine.bossEvent("[BOSS] Necron: All this, for nothing..."),
        )
    }

    @Test
    fun resolveArmCentersClass() {
        val trigger = LeapOrientTrigger.create(LeapTriggerEvent.BOSS_STORM_END, "healer")
        val healer = LeapPlayer("dbgHealer", LeapDungeonClass.HEALER)
        val request = LeapOrientEngine.resolveArm(
            trigger = trigger,
            sender = "boss",
            senderClass = LeapDungeonClass.EMPTY,
            leaped = null,
            classOf = { LeapDungeonClass.EMPTY },
            playerForClass = { clazz -> healer.takeIf { it.clazz == clazz } },
        )
        assertEquals("dbgHealer", request?.targetName)
        assertEquals(LeapDungeonClass.HEALER, request?.targetClass)
    }
}
