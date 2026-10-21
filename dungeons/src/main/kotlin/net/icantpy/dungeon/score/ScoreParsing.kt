package net.icantpy.dungeon.score

import net.icantpy.util.Text

/** Reads score inputs from tab-list and sidebar text lines. Pure, so it is testable without a client. */
object ScoreParsing {
    private val ELAPSED = Regex("""^Time Elapsed:\s*(.+)$""")
    private val ELAPSED_UNIT = Regex("""(\d+)\s*([hms])""")
    private val SECRETS_PERCENT = Regex("""^Secrets Found: ([\d.]+)%$""")

    /** Parses "Time Elapsed: 12m 34s" or "1h 2m 3s" into seconds. Null when the line is absent or has no units. */
    fun elapsedSeconds(lines: List<String>): Int? {
        val value = lines.map { Text.strip(it) }
            .firstNotNullOfOrNull { ELAPSED.find(it)?.groupValues?.get(1) }
            ?: return null
        val units = ELAPSED_UNIT.findAll(value).toList()
        if (units.isEmpty()) return null
        return units.sumOf { match ->
            val amount = match.groupValues[1].toInt()
            when (match.groupValues[2]) {
                "h" -> amount * SECONDS_PER_HOUR
                "m" -> amount * SECONDS_PER_MINUTE
                else -> amount
            }
        }
    }

    /** Parses the tab line "Secrets Found: 45.2%". Null when only the count line ("Secrets Found: 12") exists. */
    fun secretsPercent(lines: List<String>): Double? =
        lines.map { Text.strip(it) }
            .firstNotNullOfOrNull { SECRETS_PERCENT.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
}
