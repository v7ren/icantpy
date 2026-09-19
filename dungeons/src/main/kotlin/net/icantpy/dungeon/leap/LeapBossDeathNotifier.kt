package net.icantpy.dungeon.leap

object LeapBossDeathNotifier {
    private const val HUD_DURATION_MS = 5_000L

    data class Detection(
        val boss: String,
        val line: String,
        val atMs: Long,
    )

    private var last: Detection? = null

    fun record(event: LeapTriggerEvent, line: String, nowMs: Long = System.currentTimeMillis()): Detection? {
        val boss = bossName(event) ?: return null
        return Detection(boss, line, nowMs).also { last = it }
    }

    fun status(nowMs: Long = System.currentTimeMillis()): String {
        val detection = last ?: return "None detected"
        val secondsAgo = (nowMs - detection.atMs).coerceAtLeast(0L) / 1_000L
        return "${detection.boss} died · ${secondsAgo}s ago"
    }

    fun hudLine(nowMs: Long = System.currentTimeMillis()): String? {
        val detection = last ?: return null
        if (nowMs - detection.atMs !in 0..HUD_DURATION_MS) return null
        return "§aBoss detected §f${detection.boss} died"
    }

    fun clear() {
        last = null
    }

    private fun bossName(event: LeapTriggerEvent): String? = when (event) {
        LeapTriggerEvent.BOSS_STORM_START -> "Maxor"
        LeapTriggerEvent.BOSS_STORM_END -> "Storm"
        LeapTriggerEvent.BOSS_GOLDOR_DEATH -> "Goldor"
        LeapTriggerEvent.BOSS_NECRON_DEATH -> "Necron"
        else -> null
    }
}
