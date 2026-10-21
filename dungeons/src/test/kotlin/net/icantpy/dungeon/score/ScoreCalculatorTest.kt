package net.icantpy.dungeon.score

import net.icantpy.dungeon.DungeonFloor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreCalculatorTest {
    private fun f7Perfect(
        crypts: Int = 5,
        mimic: Boolean = true,
        prince: Boolean = true,
        deaths: Int = 0,
        spirit: Boolean = false,
        failed: Int = 0,
        elapsed: Int? = 800,
        paul: Boolean = false,
    ) = ScoreInputs(
        floor = DungeonFloor.F7,
        clearedPercent = 100,
        secretsFoundPercent = 100.0,
        failedPuzzles = failed,
        deaths = deaths,
        spiritPetFirstDeath = spirit,
        elapsedSeconds = elapsed,
        crypts = crypts,
        mimicKilled = mimic,
        princeKilled = prince,
        paulBonus = paul,
    )

    @Test
    fun perfectF7WithFiveCryptsIsAboveThreeHundred() {
        val result = ScoreCalculator.compute(f7Perfect())
        assertEquals(100, result.skill)
        assertEquals(100, result.explore)
        assertEquals(100, result.speed)
        assertEquals(8, result.bonus)
        assertEquals(308, result.total)
        assertEquals(ScoreRank.S_PLUS, result.rank)
        assertTrue(result.estimate)
    }

    @Test
    fun deathsReduceSkillAndSpiritPetSavesOnePointOnFirstDeath() {
        assertEquals(100, ScoreCalculator.compute(f7Perfect(deaths = 0)).skill)
        assertEquals(96, ScoreCalculator.compute(f7Perfect(deaths = 2)).skill)
        assertEquals(97, ScoreCalculator.compute(f7Perfect(deaths = 2, spirit = true)).skill)
    }

    @Test
    fun failedPuzzleCostsTenSkillPoints() {
        assertEquals(90, ScoreCalculator.compute(f7Perfect(failed = 1)).skill)
        assertEquals(80, ScoreCalculator.compute(f7Perfect(failed = 2)).skill)
    }

    @Test
    fun speedIsFullWithinLimitAndDropsAfterward() {
        assertEquals(100, ScoreCalculator.speedScore(840, DungeonFloor.F7))
        assertEquals(90, ScoreCalculator.speedScore(1008, DungeonFloor.F7)) // 20% over
        assertEquals(70, ScoreCalculator.speedScore(1176, DungeonFloor.F7)) // 40% over
        assertEquals(95, ScoreCalculator.speedScore(660, DungeonFloor.F1)) // 10% over the 600 s F1 limit
    }

    @Test
    fun speedIsClampedToZero() {
        assertEquals(0, ScoreCalculator.speedScore(100_000, DungeonFloor.F1))
    }

    @Test
    fun cryptBonusCapsAtFive() {
        assertEquals(5, ScoreCalculator.bonusScore(f7Perfect(crypts = 9, mimic = false, prince = false)))
    }

    @Test
    fun mimicBonusOnlyOnF6F7AndMasterFloors() {
        val onF1 = ScoreInputs(DungeonFloor.F1, mimicKilled = true)
        val onM7 = ScoreInputs(DungeonFloor.M7, mimicKilled = true)
        assertEquals(0, ScoreCalculator.bonusScore(onF1))
        assertEquals(2, ScoreCalculator.bonusScore(onM7))
    }

    @Test
    fun paulBonusAddsTen() {
        assertEquals(18, ScoreCalculator.bonusScore(f7Perfect(paul = true)))
    }

    @Test
    fun missingClearAndSecretsAreReportedAndEstimated() {
        val result = ScoreCalculator.compute(ScoreInputs(DungeonFloor.F7))
        assertEquals(listOf("cleared %", "secrets %", "time"), result.missingInputs)
        assertNull(result.secretsPercentShort)
    }

    @Test
    fun secretPointsAreFlooredAndRelativeToFloorRequirement() {
        // F1 requires 30%. 15% is half the requirement, so 20 of 40 points.
        assertEquals(20, ScoreCalculator.exploreScore(ScoreInputs(DungeonFloor.F1, clearedPercent = 0, secretsFoundPercent = 15.0)))
        // F7 requires 100%. 80% gives floor(40 * 0.8) = 32 points.
        assertEquals(32, ScoreCalculator.secretPoints(DungeonFloor.F7, 80.0))
    }

    @Test
    fun secretsShortReportsPercentToFullPoints() {
        val result = ScoreCalculator.compute(f7Perfect().copy(secretsFoundPercent = 80.0, crypts = 3))
        assertEquals(20.0, result.secretsPercentShort)
        assertEquals(2, result.cryptsShort)
    }

    @Test
    fun rankThresholds() {
        assertEquals(ScoreRank.S_PLUS, ScoreRank.forTotal(300))
        assertEquals(ScoreRank.S, ScoreRank.forTotal(299))
        assertEquals(ScoreRank.S, ScoreRank.forTotal(270))
        assertEquals(ScoreRank.A, ScoreRank.forTotal(269))
        assertEquals(ScoreRank.A, ScoreRank.forTotal(230))
        assertEquals(ScoreRank.B, ScoreRank.forTotal(229))
        assertEquals(ScoreRank.B, ScoreRank.forTotal(160))
        assertEquals(ScoreRank.C, ScoreRank.forTotal(159))
        assertEquals(ScoreRank.C, ScoreRank.forTotal(100))
        assertEquals(ScoreRank.D, ScoreRank.forTotal(99))
    }
}
