package net.icantpy.dungeon.leap

/** Only named server announcements can identify a leap destination. */
object LeapAutoLeapDoorOpener {
    private val formatting = Regex("§[0-9a-fk-orx]", RegexOption.IGNORE_CASE)

    // Rank and head tags may sit right against the name ("[MVP+] [Ren head]Ren"), so no space is required after them.
    private const val TAGS = "(?:\\[[^]\\r\\n]+]\\s*)*"
    private val openedDoor = Regex(
        "^$TAGS(\\w{1,16}) (?:has )?opened (?:a|the) (?:WITHER|BLOOD) door!$",
        RegexOption.IGNORE_CASE,
    )
    private val bloodKey = Regex("^$TAGS(\\w{1,16}) has obtained Blood Key!$", RegexOption.IGNORE_CASE)
    private val bloodDoorOpened = Regex("^The BLOOD DOOR has been opened!$", RegexOption.IGNORE_CASE)

    /** The blood door announcement names nobody; the player who just picked up the Blood Key is the one who opened it. */
    private const val KEY_HOLDER_WINDOW_MS = 60_000L

    private var latest: String? = null
    private var bloodKeyHolder: String? = null
    private var bloodKeyAtMs = 0L

    private fun clean(raw: String): String = formatting.replace(raw, "").trim()

    fun parse(raw: String): String? {
        val line = clean(raw)
        if (line.contains("Party >", ignoreCase = true)) return null
        return openedDoor.find(line)?.groupValues?.get(1)
    }

    fun update(previous: String?, raw: String): String? = parse(raw) ?: previous

    /** The Blood Key holder named in [raw], or null for any other line. */
    fun parseBloodKeyHolder(raw: String): String? {
        val line = clean(raw)
        if (line.contains("Party >", ignoreCase = true)) return null
        return bloodKey.find(line)?.groupValues?.get(1)
    }

    fun isBloodDoorOpened(raw: String): Boolean = bloodDoorOpened.matches(clean(raw))

    fun onChat(raw: String, nowMs: Long = System.currentTimeMillis()) {
        parseBloodKeyHolder(raw)?.let {
            bloodKeyHolder = it
            bloodKeyAtMs = nowMs
        }
        if (isBloodDoorOpened(raw)) {
            val holder = bloodKeyHolder
            if (holder != null && nowMs - bloodKeyAtMs in 0..KEY_HOLDER_WINDOW_MS) {
                latest = holder
                return
            }
        }
        latest = update(latest, raw)
    }

    fun latestName(): String? = latest

    fun hudLines(cfg: LeapOrientSettings, name: String? = latest): List<String> {
        if (!cfg.showLockHud || !cfg.enabled || !cfg.doorOpenerLeapEnabled) return emptyList()
        val who = name?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return listOf("§d$who", "§7Door opener")
    }

    fun clear() {
        latest = null
        bloodKeyHolder = null
        bloodKeyAtMs = 0L
    }
}

object LeapAutoLeapPolicy {
    fun doorOpenerClick(cfg: LeapOrientSettings, inDungeon: Boolean, inBossRoom: Boolean): Boolean =
        cfg.enabled && cfg.doorOpenerLeapEnabled && inDungeon && !inBossRoom
}
