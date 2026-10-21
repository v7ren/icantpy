package net.icantpy.dungeon.score

import kotlin.math.ceil

/** Pure text for the score HUD and alert. Uses Minecraft § formatting codes. */
object ScoreHudLines {
    private const val ESTIMATE_TAG = " §8(est.)"

    fun lines(breakdown: ScoreBreakdown, showBreakdown: Boolean): List<String> = buildList {
        val estimate = if (breakdown.estimate) ESTIMATE_TAG else ""
        add("§${breakdown.rank.colorCode}§lScore ${breakdown.total} (${breakdown.rank.label})§r$estimate")
        if (showBreakdown) {
            add("§7Skill ${breakdown.skill} · Explore ${breakdown.explore} · Speed ${breakdown.speed} · Bonus ${breakdown.bonus}")
        }
        hint(breakdown)?.let { add("§7$it") }
    }

    /**
     * Shortfall hint below 300: secret percent still needed for full secret points, and crypts still missing.
     * Null when the score is already 300 or more, or nothing is short.
     */
    fun hint(breakdown: ScoreBreakdown): String? {
        if (breakdown.total >= ScoreRank.S_PLUS.minTotal) return null
        val parts = buildList {
            breakdown.secretsPercentShort?.takeIf { it > 0.0 }?.let { add("${ceil(it).toInt()}% secrets") }
            if (breakdown.cryptsShort > 0) add("${breakdown.cryptsShort} crypts")
        }
        return if (parts.isEmpty()) null else "Need: " + parts.joinToString(" + ")
    }

    fun alertText(threshold: Int): String = "§6Dungeon score reached $threshold"
}
