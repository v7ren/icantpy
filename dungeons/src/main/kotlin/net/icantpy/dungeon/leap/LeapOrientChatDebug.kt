package net.icantpy.dungeon.leap

object LeapOrientChatDebug {
    data class Entry(
        val sender: String,
        val message: String,
        val keywordHit: Boolean,
        val selfOk: Boolean,
        val senderOk: Boolean,
        val armed: Boolean,
        val detail: String,
        val atMs: Long = System.currentTimeMillis(),
    )

    private val history = ArrayDeque<Entry>(12)
    var showHud: Boolean = true
    var last: Entry? = null

    fun record(entry: Entry) {
        last = entry
        history.addFirst(entry)
        while (history.size > 12) {
            history.removeLast()
        }
    }

    fun history(): List<Entry> = history.toList()

    fun clear() {
        history.clear()
        last = null
    }

    fun hudLines(showSkips: Boolean = true): List<String> {
        if (!showHud) return emptyList()
        val recent = history.asSequence()
            .filter { showSkips || it.armed }
            .take(3)
            .toList()
        if (recent.isEmpty()) return emptyList()
        return recent.map { entry ->
            val result = if (entry.armed) "§aLOCK ${entry.sender}" else "§cSKIP"
            val why = if (entry.armed) "" else " §7(${entry.detail})"
            "§8[chat] §f${entry.sender}§7: §f${entry.message} → $result$why"
        }
    }

    fun formatChat(entry: Entry): String {
        val lines = buildList {
            add("§8§m--------------------")
            add("§dLeap Orient §7chat trigger")
            add("§7Party > §f${entry.sender}§7: §f${entry.message}")
            add("§7keyword: ${if (entry.keywordHit) "§ayes" else "§cno"}")
            add("§7self spot: ${if (entry.selfOk) "§aok" else "§cfail"}")
            add("§7sender spot: ${if (entry.senderOk) "§aok" else "§cfail"}")
            add(if (entry.armed) "§a→ locked ${entry.sender}" else "§c→ ${entry.detail}")
            add("§8§m--------------------")
        }
        return lines.joinToString("\n")
    }
}
