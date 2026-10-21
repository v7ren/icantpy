package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals

class LeapChatEventsNewTest {
    private fun assertOne(line: String, event: LeapTriggerEvent, player: String? = null) {
        assertEquals(listOf(event to player), LeapChatEvents.detect(line), "line: $line")
    }

    private fun assertNone(line: String) {
        assertEquals(emptyList(), LeapChatEvents.detect(line), "line: $line")
    }

    @Test
    fun maxorStunned() {
        assertOne("[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!", LeapTriggerEvent.MAXOR_STUNNED)
        assertOne("[BOSS] Maxor: YOU TRICKED ME!", LeapTriggerEvent.MAXOR_STUNNED)
        assertNone("[BOSS] Maxor: YOU TRICKED ME")
        assertNone("[BOSS] Maxor: THAT BEAM! IT HURTS!")
    }

    @Test
    fun bossEnragedCapturesName() {
        assertOne("⚠ Maxor is enraged! ⚠", LeapTriggerEvent.BOSS_ENRAGED, "Maxor")
        assertOne("⚠️ Storm is enraged! ⚠️", LeapTriggerEvent.BOSS_ENRAGED, "Storm")
        assertNone("Maxor is enraged! ⚠")
        assertNone("⚠ Maxor is enraged!")
        assertNone("⚠ Maxor is calm! ⚠")
    }

    @Test
    fun energyCrystalCapturesPlayer() {
        assertOne("Notch picked up an Energy Crystal!", LeapTriggerEvent.CRYSTAL_PICKUP, "Notch")
        assertNone("Notch picked up an Energy Crystal")
        assertNone("Notch picked up a Energy Crystal!")
    }

    @Test
    fun stormLines() {
        assertOne("[BOSS] Storm: Oof", LeapTriggerEvent.STORM_CRUSHED)
        assertOne("[BOSS] Storm: Ouch, that hurt!", LeapTriggerEvent.STORM_CRUSHED)
        assertOne("[BOSS] Storm: ENERGY HEED MY CALL!", LeapTriggerEvent.STORM_PAD_PHASE)
        assertOne("[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!", LeapTriggerEvent.STORM_PAD_PHASE)
        assertNone("[BOSS] Storm: Oof!")
        assertNone("[BOSS] Storm: ENERGY HEED MY CALL")
    }

    @Test
    fun witherKingDragonLines() {
        assertOne("[BOSS] Wither King: Oh, this one hurts!", LeapTriggerEvent.WK_DRAGON_KILLED)
        assertOne("[BOSS] Wither King: I have more of those.", LeapTriggerEvent.WK_DRAGON_KILLED)
        assertOne("[BOSS] Wither King: My soul is disposable.", LeapTriggerEvent.WK_DRAGON_KILLED)
        assertNone("[BOSS] Wither King: I have more of those")
        assertNone("[BOSS] Wither King: Oh, this one hurts")
    }

    @Test
    fun batKilled() {
        assertOne("A Bat has been slain. +1 Bonus Score", LeapTriggerEvent.BAT_KILLED)
        assertNone("A Bat has been slain. +2 Bonus Score")
        assertNone("A Bat has been slain.")
    }

    @Test
    fun otherFloorBossLinesAcceptBothApostrophes() {
        assertOne("[BOSS] Livid: I respect you for making it to here, but I'll be your undoing.", LeapTriggerEvent.LIVID_START)
        assertOne("[BOSS] Livid: I respect you for making it to here, but I’ll be your undoing.", LeapTriggerEvent.LIVID_START)
        assertNone("[BOSS] Livid: I respect you for making it to here.")
        assertOne("[BOSS] Sadan: My giants! Unleashed!", LeapTriggerEvent.SADAN_GIANTS)
        assertNone("[BOSS] Sadan: My giants! Unleashed")
        assertOne("[BOSS] Sadan: You did it. I understand now, you have earned my respect.", LeapTriggerEvent.SADAN_DONE)
        assertOne("[BOSS] Sadan: You did it. I understand now, you have earned my respect.", LeapTriggerEvent.SADAN_DONE)
        assertNone("[BOSS] Sadan: You did it.")
        assertNone("[BOSS] Sadan: I understand now, you have earned my respect.")
        assertOne("[BOSS] The Professor: I see. You have forced me to use my ultimate technique.", LeapTriggerEvent.PROFESSOR_GUARDIAN)
        assertNone("[BOSS] The Professor: I see.")
    }

    @Test
    fun abilityCooldown() {
        assertOne("This ability is on cooldown for 3s.", LeapTriggerEvent.ABILITY_COOLDOWN)
        assertOne("This ability is on cooldown for 12s.", LeapTriggerEvent.ABILITY_COOLDOWN)
        assertNone("This ability is on cooldown for s.")
        assertNone("This ability is on cooldown for 3 s.")
        assertNone("Your ability is on cooldown for 3s.")
    }
}
