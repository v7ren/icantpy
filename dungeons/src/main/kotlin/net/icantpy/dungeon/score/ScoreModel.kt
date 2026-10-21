package net.icantpy.dungeon.score

import net.icantpy.dungeon.DungeonFloor
import kotlin.math.ceil

/**
 * Catacombs score rank. [minTotal] is the inclusive lower bound; entries are ordered from highest to lowest.
 * [colorCode] is the Minecraft formatting code used for the HUD text.
 */
enum class ScoreRank(val label: String, val minTotal: Int, val colorCode: Char) {
    S_PLUS("S+", 300, 'd'),
    S("S", 270, '6'),
    A("A", 230, 'e'),
    B("B", 160, 'a'),
    C("C", 100, 'b'),
    D("D", 0, '7'),
    ;

    companion object {
        fun forTotal(total: Int): ScoreRank = entries.first { total >= it.minTotal }
    }
}

/**
 * Everything the calculator needs. Nullable fields are unknown values; the calculator uses a neutral
 * fallback for them and lists them in [ScoreBreakdown.missingInputs].
 */
data class ScoreInputs(
    val floor: DungeonFloor,
    val clearedPercent: Int? = null,
    val secretsFoundPercent: Double? = null,
    val failedPuzzles: Int = 0,
    val deaths: Int = 0,
    val spiritPetFirstDeath: Boolean = false,
    val elapsedSeconds: Int? = null,
    val crypts: Int = 0,
    val mimicKilled: Boolean = false,
    val princeKilled: Boolean = false,
    val paulBonus: Boolean = false,
)

/**
 * Result of [ScoreCalculator.compute].
 * [secretsPercentShort] is null when the secret percentage is unknown; 0.0 when the secret points are full.
 * [estimate] is always true for now: the score is built from tab/sidebar values, not the dungeon map.
 */
data class ScoreBreakdown(
    val skill: Int,
    val explore: Int,
    val speed: Int,
    val bonus: Int,
    val total: Int,
    val rank: ScoreRank,
    val secretsPercentShort: Double?,
    val cryptsShort: Int,
    val missingInputs: List<String>,
    val estimate: Boolean = true,
)

/**
 * Pure Catacombs score model. Constants are the simplified rules documented in the feature spec;
 * they are approximations of the in-game score, not an official reimplementation.
 */
object ScoreCalculator {
    // Skill: 20 base, plus up to 80 from clear, minus puzzle and death penalties. Capped at 100.
    const val SKILL_BASE = 20
    const val SKILL_CLEAR_POINTS = 80
    const val MAX_SKILL = 100
    const val FAILED_PUZZLE_PENALTY = 10
    const val DEATH_PENALTY = 2
    const val SPIRIT_PET_FIRST_DEATH_SAVE = 1

    // Explore: 60 for the clear percentage, 40 for secrets relative to the floor requirement.
    const val EXPLORE_CLEAR_POINTS = 60
    const val EXPLORE_SECRET_POINTS = 40

    // Speed: 100 within the time limit, then a penalty that grows with the percentage over the limit.
    const val SPEED_FULL = 100
    const val SPEED_GENTLE_OVERTIME_PERCENT = 20.0
    const val SPEED_GENTLE_PENALTY_PER_PERCENT = 0.5
    const val SPEED_STEEP_PENALTY_PER_PERCENT = 1.0
    const val SPEED_GENTLE_PENALTY_CAP = 10.0

    // Bonus: crypts (max 5), mimic (F6/F7/M6/M7), prince, optional Paul EZPZ bonus.
    const val MAX_CRYPT_BONUS = 5
    const val MIMIC_BONUS = 2
    const val PRINCE_BONUS = 1
    const val PAUL_BONUS = 10
    const val CRYPTS_FOR_FULL_BONUS = MAX_CRYPT_BONUS

    private val MIMIC_FLOORS = setOf(
        DungeonFloor.F6,
        DungeonFloor.F7,
        DungeonFloor.M6,
        DungeonFloor.M7,
    )

    /** Inclusive secret percentage required for full explore secret points on [floor]. */
    fun requiredSecretPercent(floor: DungeonFloor): Int = when (floor) {
        DungeonFloor.F1 -> 30
        DungeonFloor.F2 -> 40
        DungeonFloor.F3 -> 50
        DungeonFloor.F4 -> 60
        DungeonFloor.F5 -> 70
        DungeonFloor.F6 -> 85
        else -> 100
    }

    /** Approximate time limit in seconds. F1-F5 600 s, F6 720 s, F7 840 s, M1-M7 480 s. */
    fun timeLimitSeconds(floor: DungeonFloor): Int = when (floor) {
        DungeonFloor.F6 -> 720
        DungeonFloor.F7 -> 840
        DungeonFloor.M1, DungeonFloor.M2, DungeonFloor.M3, DungeonFloor.M4,
        DungeonFloor.M5, DungeonFloor.M6, DungeonFloor.M7 -> 480
        else -> 600
    }

    fun compute(inputs: ScoreInputs): ScoreBreakdown {
        val skill = skillScore(inputs)
        val explore = exploreScore(inputs)
        val speed = speedScore(inputs.elapsedSeconds, inputs.floor)
        val bonus = bonusScore(inputs)
        val total = skill + explore + speed + bonus
        return ScoreBreakdown(
            skill = skill,
            explore = explore,
            speed = speed,
            bonus = bonus,
            total = total,
            rank = ScoreRank.forTotal(total),
            secretsPercentShort = secretsPercentShort(inputs),
            cryptsShort = (CRYPTS_FOR_FULL_BONUS - inputs.crypts).coerceAtLeast(0),
            missingInputs = missingInputs(inputs),
        )
    }

    internal fun skillScore(inputs: ScoreInputs): Int {
        val cleared = clampedCleared(inputs)
        val clearPoints = SKILL_CLEAR_POINTS * cleared / 100
        val penalty = deathPenalty(inputs.deaths, inputs.spiritPetFirstDeath)
        val raw = clearPoints - FAILED_PUZZLE_PENALTY * inputs.failedPuzzles - penalty
        return (SKILL_BASE + raw.coerceIn(0, SKILL_CLEAR_POINTS)).coerceAtMost(MAX_SKILL)
    }

    /** 2 points per death; a spirit pet saves one point on the first death only. */
    internal fun deathPenalty(deaths: Int, spiritPetFirstDeath: Boolean): Int {
        if (deaths <= 0) return 0
        val save = if (spiritPetFirstDeath) SPIRIT_PET_FIRST_DEATH_SAVE else 0
        return DEATH_PENALTY * deaths - save
    }

    internal fun exploreScore(inputs: ScoreInputs): Int {
        val clearPart = EXPLORE_CLEAR_POINTS * clampedCleared(inputs) / 100
        return clearPart + secretPoints(inputs.floor, inputs.secretsFoundPercent)
    }

    /** Floored so the secret part never exceeds [EXPLORE_SECRET_POINTS]. */
    internal fun secretPoints(floor: DungeonFloor, secretsPercent: Double?): Int {
        if (secretsPercent == null) return 0
        val fraction = (secretsPercent / requiredSecretPercent(floor)).coerceIn(0.0, 1.0)
        return kotlin.math.floor(EXPLORE_SECRET_POINTS * fraction).toInt()
    }

    internal fun speedScore(elapsedSeconds: Int?, floor: DungeonFloor): Int {
        if (elapsedSeconds == null) return SPEED_FULL
        val limit = timeLimitSeconds(floor)
        if (elapsedSeconds <= limit) return SPEED_FULL
        val overPercent = (elapsedSeconds - limit) * 100.0 / limit
        val penalty = if (overPercent <= SPEED_GENTLE_OVERTIME_PERCENT) {
            ceil(overPercent * SPEED_GENTLE_PENALTY_PER_PERCENT)
        } else {
            SPEED_GENTLE_PENALTY_CAP +
                ceil((overPercent - SPEED_GENTLE_OVERTIME_PERCENT) * SPEED_STEEP_PENALTY_PER_PERCENT)
        }
        return (SPEED_FULL - penalty.toInt()).coerceIn(0, SPEED_FULL)
    }

    internal fun bonusScore(inputs: ScoreInputs): Int {
        val crypts = inputs.crypts.coerceIn(0, MAX_CRYPT_BONUS)
        val mimic = if (inputs.mimicKilled && inputs.floor in MIMIC_FLOORS) MIMIC_BONUS else 0
        val prince = if (inputs.princeKilled) PRINCE_BONUS else 0
        val paul = if (inputs.paulBonus) PAUL_BONUS else 0
        return crypts + mimic + prince + paul
    }

    /** Null when the secret percentage is unknown; never negative. */
    internal fun secretsPercentShort(inputs: ScoreInputs): Double? {
        val found = inputs.secretsFoundPercent ?: return null
        return (requiredSecretPercent(inputs.floor) - found).coerceAtLeast(0.0)
    }

    internal fun missingInputs(inputs: ScoreInputs): List<String> = buildList {
        if (inputs.clearedPercent == null) add("cleared %")
        if (inputs.secretsFoundPercent == null) add("secrets %")
        if (inputs.elapsedSeconds == null) add("time")
    }

    private fun clampedCleared(inputs: ScoreInputs): Int = (inputs.clearedPercent ?: 0).coerceIn(0, 100)
}
