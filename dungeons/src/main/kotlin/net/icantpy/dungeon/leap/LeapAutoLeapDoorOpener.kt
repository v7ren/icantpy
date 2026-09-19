package net.icantpy.dungeon.leap

/** Only named server announcements can identify a leap destination. */
object LeapAutoLeapDoorOpener {
    private val formatting = Regex("§[0-9a-fk-orx]", RegexOption.IGNORE_CASE)
    private val openedDoor = Regex(
        "^(?:\\[[^]\\r\\n]+]\\s+)*(\\w{1,16}) (?:has )?opened (?:a|the) (?:WITHER|BLOOD) door!$",
        RegexOption.IGNORE_CASE,
    )
    private var latest: String? = null

    fun parse(raw: String): String? {
        val line = formatting.replace(raw, "").trim()
        if (line.contains("Party >", ignoreCase = true)) return null
        return openedDoor.find(line)?.groupValues?.get(1)
    }

    fun update(previous: String?, raw: String): String? = parse(raw) ?: previous

    fun onChat(raw: String) {
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
    }
}

object LeapAutoLeapPolicy {
    fun doorOpenerClick(cfg: LeapOrientSettings, inDungeon: Boolean, inBossRoom: Boolean): Boolean =
        cfg.enabled && cfg.doorOpenerLeapEnabled && inDungeon && !inBossRoom
}
