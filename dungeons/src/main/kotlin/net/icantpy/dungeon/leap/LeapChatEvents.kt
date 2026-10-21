package net.icantpy.dungeon.leap

/**
 * Chat lines that start leap triggers. Input must already have § codes stripped. Each line is
 * trimmed and the typographic apostrophe is folded to ASCII before matching. Returns one entry per
 * matched event with the player name captured from the line, or null when the line names nobody.
 */
object LeapChatEvents {
    /** [playerGroup] is the regex group holding the player name, or null when the line names nobody. */
    private class Rule(val event: LeapTriggerEvent, val regex: Regex, val playerGroup: Int? = null)

    private fun exact(line: String): Regex = Regex("^" + Regex.escape(line) + "$")

    private val RULES: List<Rule> = listOf(
        // Run lifecycle
        Rule(LeapTriggerEvent.RUN_START, exact("[NPC] Mort: Here, I found this map when I first entered the dungeon.")),
        Rule(LeapTriggerEvent.RUN_END, Regex("""^\s*> EXTRA STATS <$""")),
        Rule(LeapTriggerEvent.BOSS_ENTRY, exact("[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.")),
        Rule(LeapTriggerEvent.BOSS_ENTRY, exact("[BOSS] Scarf: This is where the journey ends for you, Adventurers.")),
        Rule(LeapTriggerEvent.BOSS_ENTRY, exact("[BOSS] The Professor: I was burdened with terrible news recently...")),
        Rule(LeapTriggerEvent.BOSS_ENTRY, exact("[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!")),
        Rule(LeapTriggerEvent.BOSS_ENTRY, exact("[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.")),
        Rule(LeapTriggerEvent.BOSS_ENTRY, exact("[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!")),
        Rule(LeapTriggerEvent.BOSS_ENTRY, exact("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")),
        Rule(LeapTriggerEvent.BLOOD_OPEN, exact("The BLOOD DOOR has been opened!")),
        Rule(LeapTriggerEvent.BLOOD_CAMP_DONE, exact("[BOSS] The Watcher: That will be enough for now.")),
        Rule(LeapTriggerEvent.BLOOD_CLEAR, exact("[BOSS] The Watcher: You have proven yourself. You may pass.")),
        // Clear phase
        Rule(LeapTriggerEvent.WITHER_KEY, exact("A Wither Key was picked up!")),
        Rule(LeapTriggerEvent.WITHER_KEY, Regex("""^(?:\[.+?\] )?(\w+) has obtained Wither Key!$"""), 1),
        Rule(LeapTriggerEvent.BLOOD_KEY, exact("A Blood Key was picked up!")),
        Rule(LeapTriggerEvent.BLOOD_KEY, Regex("""^(?:\[.+?\] )?(\w+) has obtained Blood Key!$"""), 1),
        Rule(LeapTriggerEvent.WITHER_DOOR, Regex("""^(?:\[.+?\] )?(\w+) opened a WITHER door!$"""), 1),
        Rule(LeapTriggerEvent.PRINCE_KILLED, Regex("""^A Prince falls\. \+1 Bonus Score$""")),
        Rule(LeapTriggerEvent.PUZZLE_FAIL, Regex("""^PUZZLE FAIL! (\w{1,16}) .+$"""), 1),
        Rule(LeapTriggerEvent.BAT_KILLED, Regex("""^A Bat has been slain\. \+1 Bonus Score$""")),
        // F7/M7 bosses and objectives
        Rule(LeapTriggerEvent.GATE_DESTROYED, exact("The gate has been destroyed!")),
        Rule(LeapTriggerEvent.P5_START, exact("[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you.")),
        Rule(LeapTriggerEvent.BONZO_DEAD, exact("[BOSS] Bonzo: Oh I'm dead!")),
        Rule(LeapTriggerEvent.MAXOR_STUNNED, Regex("""^\[BOSS\] Maxor: (?:THAT BEAM! IT HURTS! IT HURTS!!|YOU TRICKED ME!)$""")),
        Rule(LeapTriggerEvent.BOSS_ENRAGED, Regex("""^⚠️? (\w+) is enraged! ⚠️?$"""), 1),
        Rule(LeapTriggerEvent.CRYSTAL_PICKUP, Regex("""^(\w{1,16}) picked up an Energy Crystal!$"""), 1),
        Rule(LeapTriggerEvent.STORM_CRUSHED, Regex("""^\[BOSS\] Storm: (?:Oof|Ouch, that hurt!)$""")),
        Rule(LeapTriggerEvent.STORM_PAD_PHASE, Regex("""^\[BOSS\] Storm: (?:ENERGY HEED MY CALL!|THUNDER LET ME BE YOUR CATALYST!)$""")),
        Rule(LeapTriggerEvent.WK_DRAGON_KILLED, Regex("""^\[BOSS\] Wither King: (?:Oh, this one hurts!|I have more of those\.|My soul is disposable\.)$""")),
        // Other floors
        Rule(LeapTriggerEvent.LIVID_START, exact("[BOSS] Livid: I respect you for making it to here, but I'll be your undoing.")),
        Rule(LeapTriggerEvent.SADAN_GIANTS, exact("[BOSS] Sadan: My giants! Unleashed!")),
        Rule(LeapTriggerEvent.SADAN_DONE, exact("[BOSS] Sadan: You did it. I understand now, you have earned my respect.")),
        Rule(LeapTriggerEvent.PROFESSOR_GUARDIAN, exact("[BOSS] The Professor: I see. You have forced me to use my ultimate technique.")),
        // Player state
        Rule(LeapTriggerEvent.ABILITY_COOLDOWN, Regex("""^This ability is on cooldown for \d+s\.$""")),
        Rule(LeapTriggerEvent.SELF_DEATH, Regex("""^☠ You were .+\.$""")),
        Rule(LeapTriggerEvent.TEAMMATE_DEATH, Regex("""^☠ (\w{1,16}) .+?(?: and became a ghost)?\.$"""), 1),
        Rule(LeapTriggerEvent.MASK_PROC, Regex("""^Your (?:.+ )?Bonzo's Mask saved your life!$""")),
        Rule(LeapTriggerEvent.MASK_PROC, Regex("""^Second Wind Activated! Your Spirit Mask saved your life!$""")),
        Rule(LeapTriggerEvent.MASK_PROC, Regex("""^Your Phoenix Pet saved you from certain death!$""")),
        // Lobby. The captured text is the player name, or the class for CLASS_SELECTED.
        Rule(LeapTriggerEvent.PLAYER_READY, Regex("""^(\w{1,16}) is now ready!$"""), 1),
        Rule(LeapTriggerEvent.COUNTDOWN, exact("Starting in 1 second.")),
        Rule(LeapTriggerEvent.CLASS_SELECTED, Regex("""^You have selected the (Healer|Tank|Mage|Berserk|Archer) Dungeon Class!$"""), 1),
        Rule(LeapTriggerEvent.PF_JOINED, Regex("""^Party Finder > (\w{1,16}) joined the dungeon group! \(\w+ Level \d+\)$"""), 1),
        // Puzzles
        Rule(LeapTriggerEvent.PUZZLE_SOLVED, Regex("""^PUZZLE SOLVED! .+$""")),
        Rule(LeapTriggerEvent.MIMIC_DEAD, Regex("""^Party > .*?: .*?(?i:Mimic dead!?|Mimic Killed!)\s*$""")),
        Rule(LeapTriggerEvent.BLESSING, Regex("""^DUNGEON BUFF! .+ found a Blessing of (\w+).*$"""), 1),
        Rule(LeapTriggerEvent.BLESSING, Regex("""^A Blessing of (\w+) .*was picked up!$"""), 1),
        // Kuudra (NPC dialogue)
        Rule(LeapTriggerEvent.KUUDRA_SUPPLIES, exact("[NPC] Elle: ARGH! All of the supplies fell into the lava! You need to retrieve them quickly!")),
        Rule(LeapTriggerEvent.KUUDRA_BUILD_DONE, exact("[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!")),
        Rule(LeapTriggerEvent.KUUDRA_STUN, exact("[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!")),
        Rule(LeapTriggerEvent.KUUDRA_LAIR, exact("[NPC] Elle: What just happened!? Is this Kuudra's real lair?")),
        // Slayer
        Rule(LeapTriggerEvent.SLAYER_STARTED, Regex("""^\s*SLAYER QUEST STARTED!$""")),
        Rule(LeapTriggerEvent.SLAYER_COMPLETE, Regex("""^\s*(?:SLAYER QUEST COMPLETE!|NICE! SLAYER BOSS SLAIN!)$""")),
        Rule(LeapTriggerEvent.SLAYER_FAILED, Regex("""^\s*SLAYER QUEST FAILED!$""")),
    )

    private val TERMINAL = Regex("""^(\w+) (?:activated|completed) a (?:terminal|lever|device)! \((\d+)/(\d+)\)$""")

    fun detect(line: String): List<Pair<LeapTriggerEvent, String?>> {
        val text = line.trim().replace('’', '\'')
        if (text.isEmpty()) return emptyList()
        val hits = mutableListOf<Pair<LeapTriggerEvent, String?>>()
        for (rule in RULES) {
            val match = rule.regex.find(text) ?: continue
            val player = rule.playerGroup?.let { match.groupValues[it] }?.takeIf { it.isNotBlank() }
            if (rule.event == LeapTriggerEvent.TEAMMATE_DEATH) {
                if (player == null || player == "You" || text.contains("reconnected.")) continue
            }
            hits += rule.event to player
        }
        TERMINAL.find(text)?.let { match ->
            val player = match.groupValues[1]
            val done = match.groupValues[2].toIntOrNull()
            val total = match.groupValues[3].toIntOrNull()
            hits += LeapTriggerEvent.TERMINAL_DONE to player
            if (done != null && total != null && total > 0 && done == total) {
                hits += LeapTriggerEvent.SECTION_DONE to player
            }
        }
        return hits
    }
}

/**
 * Edge-triggered health threshold with hysteresis. Fires once when the ratio falls below the
 * threshold, re-arms only after it rises above threshold + [REARM_MARGIN] percentage points, and
 * never fires twice within [cooldownMs]. Pure: callers pass the ratio and the clock.
 */
class LeapHealthHysteresis(private val cooldownMs: Long = COOLDOWN_MS) {
    private var armed = true
    private var lastFireMs: Long? = null

    /** Feeds one sample. Returns true when the trigger should fire now. */
    fun update(ratio: Double, thresholdPercent: Int, nowMs: Long): Boolean {
        val percent = thresholdPercent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val threshold = percent / 100.0
        val rearm = (percent + REARM_MARGIN) / 100.0
        if (!armed) {
            if (ratio > rearm) armed = true
            return false
        }
        if (ratio >= threshold) return false
        val last = lastFireMs
        if (last != null && nowMs - last < cooldownMs) return false
        armed = false
        lastFireMs = nowMs
        return true
    }

    /** Re-arms without clearing the cooldown, e.g. while the trigger is not eligible. */
    fun rearm() {
        armed = true
    }

    companion object {
        const val COOLDOWN_MS: Long = 5_000L
        const val REARM_MARGIN: Int = 10
        const val MIN_PERCENT: Int = 10
        const val MAX_PERCENT: Int = 90
    }
}
