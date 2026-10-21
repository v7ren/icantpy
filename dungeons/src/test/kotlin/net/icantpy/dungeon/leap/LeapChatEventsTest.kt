package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals

class LeapChatEventsTest {
    private fun assertOne(line: String, event: LeapTriggerEvent, player: String? = null) {
        assertEquals(listOf(event to player), LeapChatEvents.detect(line), "line: $line")
    }

    private fun assertNone(line: String) {
        assertEquals(emptyList(), LeapChatEvents.detect(line), "line: $line")
    }

    private val skull = "☠"

    @Test
    fun runLifecycleLines() {
        assertOne("[NPC] Mort: Here, I found this map when I first entered the dungeon.", LeapTriggerEvent.RUN_START)
        assertOne("> EXTRA STATS <", LeapTriggerEvent.RUN_END)
        assertOne("   > EXTRA STATS <  ", LeapTriggerEvent.RUN_END)
        assertNone("EXTRA STATS")
        assertNone("> EXTRA STATS < extra")
        assertNone("[NPC] Mort: Here, I found this map.")
    }

    @Test
    fun bossEntryAcceptsBothApostrophes() {
        assertOne("[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] Bonzo: Gratz for making it this far, but I’m basically unbeatable.", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] Scarf: This is where the journey ends for you, Adventurers.", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] The Professor: I was burdened with terrible news recently...", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] Livid: Welcome, you’ve arrived right on time. I am Livid, the Master of Shadows.", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", LeapTriggerEvent.BOSS_ENTRY)
        assertOne("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO’S HERE!", LeapTriggerEvent.BOSS_ENTRY)
        assertNone("[BOSS] Maxor: WELL! WELL! WELL!")
        assertNone("[BOSS] Bonzo: Gratz for making it this far.")
    }

    @Test
    fun bloodRoomLines() {
        assertOne("The BLOOD DOOR has been opened!", LeapTriggerEvent.BLOOD_OPEN)
        assertOne("[BOSS] The Watcher: That will be enough for now.", LeapTriggerEvent.BLOOD_CAMP_DONE)
        assertOne("[BOSS] The Watcher: You have proven yourself. You may pass.", LeapTriggerEvent.BLOOD_CLEAR)
        assertNone("The BLOOD DOOR has been opened")
        assertNone("[BOSS] The Watcher: That will be enough.")
        assertNone("[BOSS] The Watcher: You may pass.")
    }

    @Test
    fun keyAndDoorLinesCapturePlayer() {
        assertOne("A Wither Key was picked up!", LeapTriggerEvent.WITHER_KEY)
        assertOne("Notch has obtained Wither Key!", LeapTriggerEvent.WITHER_KEY, "Notch")
        assertOne("[MVP+] Notch has obtained Wither Key!", LeapTriggerEvent.WITHER_KEY, "Notch")
        assertOne("A Blood Key was picked up!", LeapTriggerEvent.BLOOD_KEY)
        assertOne("Notch has obtained Blood Key!", LeapTriggerEvent.BLOOD_KEY, "Notch")
        assertOne("Notch opened a WITHER door!", LeapTriggerEvent.WITHER_DOOR, "Notch")
        assertOne("[VIP] Notch opened a WITHER door!", LeapTriggerEvent.WITHER_DOOR, "Notch")
        assertNone("Notch has obtained Wither Key")
        assertNone("Notch opened a BLOOD door!")
        assertNone("A Wither Key was picked up")
        assertNone("You have obtained Wither Key!!")
    }

    @Test
    fun princeAndPuzzleLines() {
        assertOne("A Prince falls. +1 Bonus Score", LeapTriggerEvent.PRINCE_KILLED)
        assertNone("A Prince falls. +2 Bonus Score")
        assertNone("A Prince falls +1 Bonus Score")
        assertOne("PUZZLE FAIL! Notch lost connection", LeapTriggerEvent.PUZZLE_FAIL, "Notch")
        assertOne("PUZZLE FAIL! Notch was not fast enough", LeapTriggerEvent.PUZZLE_FAIL, "Notch")
        assertNone("PUZZLE FAIL!")
        assertOne("PUZZLE SOLVED! Notch", LeapTriggerEvent.PUZZLE_SOLVED)
    }

    @Test
    fun terminalLinesFireSectionDoneOnlyOnTheLastOne() {
        assertOne("Notch activated a terminal! (3/7)", LeapTriggerEvent.TERMINAL_DONE, "Notch")
        assertOne("Notch completed a lever! (2/5)", LeapTriggerEvent.TERMINAL_DONE, "Notch")
        assertOne("Notch activated a device! (1/4)", LeapTriggerEvent.TERMINAL_DONE, "Notch")
        assertEquals(
            listOf(LeapTriggerEvent.TERMINAL_DONE to "Notch", LeapTriggerEvent.SECTION_DONE to "Notch"),
            LeapChatEvents.detect("Notch activated a terminal! (7/7)"),
        )
        assertNone("Notch activated a terminal! 3/7")
        assertNone("Notch activated a chest! (3/7)")
        assertNone("Notch completed a terminal (3/7)")
    }

    @Test
    fun gateAndF7BossLines() {
        assertOne("The gate has been destroyed!", LeapTriggerEvent.GATE_DESTROYED)
        assertOne("[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you.", LeapTriggerEvent.P5_START)
        assertOne("[BOSS] Bonzo: Oh I'm dead!", LeapTriggerEvent.BONZO_DEAD)
        assertNone("The gate has been destroyed")
        assertNone("[BOSS] Bonzo: Oh I'm dead")
    }

    @Test
    fun selfDeathAndMaskProcs() {
        assertOne("$skull You were killed by Bonzo.", LeapTriggerEvent.SELF_DEATH)
        assertOne("$skull You were killed by Bonzo and became a ghost.", LeapTriggerEvent.SELF_DEATH)
        assertOne("  $skull You were killed by Bonzo.  ", LeapTriggerEvent.SELF_DEATH)
        assertNone("You were killed by Bonzo.")
        assertOne("Your Bonzo's Mask saved your life!", LeapTriggerEvent.MASK_PROC)
        assertOne("Your Spirit Bonzo's Mask saved your life!", LeapTriggerEvent.MASK_PROC)
        assertOne("Second Wind Activated! Your Spirit Mask saved your life!", LeapTriggerEvent.MASK_PROC)
        assertOne("Your Phoenix Pet saved you from certain death!", LeapTriggerEvent.MASK_PROC)
        assertNone("Your Bonzo's Mask saved you!")
        assertNone("Second Wind Activated!")
    }

    @Test
    fun teammateDeathIgnoresSelfAndReconnects() {
        assertOne("$skull Notch was killed by Bonzo.", LeapTriggerEvent.TEAMMATE_DEATH, "Notch")
        assertOne("$skull Notch fell into the void and became a ghost.", LeapTriggerEvent.TEAMMATE_DEATH, "Notch")
        assertNone("$skull Notch reconnected.")
        assertNone("$skull Notch was killed by Bonzo")
        assertNone("Notch was killed by Bonzo.")
    }

    @Test
    fun unrelatedChatMatchesNothing() {
        assertNone("")
        assertNone("   ")
        assertNone("Party > Notch: hello")
        assertNone("Notch: Hi everyone")
    }
}

class LeapHealthHysteresisTest {
    @Test
    fun firesOnceWhenDroppingBelowThreshold() {
        val watch = LeapHealthHysteresis()
        assertEquals(false, watch.update(0.9, 50, 0L))
        assertEquals(true, watch.update(0.49, 50, 1_000L))
        assertEquals(false, watch.update(0.40, 50, 2_000L))
        assertEquals(false, watch.update(0.20, 50, 3_000L))
    }

    @Test
    fun exactlyAtThresholdDoesNotFire() {
        val watch = LeapHealthHysteresis()
        assertEquals(false, watch.update(0.50, 50, 0L))
    }

    @Test
    fun rearmsOnlyAboveThresholdPlusMargin() {
        val watch = LeapHealthHysteresis(cooldownMs = 0L)
        assertEquals(true, watch.update(0.40, 50, 0L))
        assertEquals(false, watch.update(0.55, 50, 1L), "55% is not above 60%, still disarmed")
        assertEquals(false, watch.update(0.45, 50, 2L))
        assertEquals(false, watch.update(0.61, 50, 3L), "above 60% re-arms")
        assertEquals(true, watch.update(0.45, 50, 4L))
    }

    @Test
    fun cooldownBlocksRepeatFiresForFiveSeconds() {
        val watch = LeapHealthHysteresis()
        assertEquals(true, watch.update(0.40, 50, 0L))
        assertEquals(false, watch.update(0.70, 50, 1_000L))
        assertEquals(false, watch.update(0.30, 50, 4_999L), "inside cooldown")
        assertEquals(true, watch.update(0.30, 50, 5_000L), "cooldown elapsed and still low")
    }

    @Test
    fun rearmKeepsCooldown() {
        val watch = LeapHealthHysteresis()
        assertEquals(true, watch.update(0.40, 50, 0L))
        watch.rearm()
        assertEquals(false, watch.update(0.40, 50, 1_000L))
        assertEquals(true, watch.update(0.40, 50, 5_000L))
    }

    @Test
    fun thresholdIsClampedToSupportedRange() {
        val low = LeapHealthHysteresis()
        assertEquals(true, low.update(0.05, 0, 0L), "0 clamps to 10 percent")
        assertEquals(false, low.update(0.05, 0, 0L), "disarmed after firing")
        val high = LeapHealthHysteresis()
        assertEquals(true, high.update(0.85, 100, 0L), "100 clamps to 90 percent")
    }
}
