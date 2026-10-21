package net.icantpy.dungeon.dragons

import kotlin.test.Test
import kotlin.test.assertEquals

class DragonPriorityTest {
    private val defaults = DragonSettings()

    private fun decide(
        spawning: Set<WitherDragon>,
        playerClass: String? = "BERSERK",
        power: Int = 0,
        paul: Boolean = false,
        time: Boolean = false,
        settings: DragonSettings = defaults,
    ) = DragonPriority.decide(spawning, playerClass, power, paul, time, settings)

    @Test
    fun disabledPriorityUsesFixedOrder() {
        val settings = defaults.copy(priorityEnabled = false)
        assertEquals(WitherDragon.Blue, decide(setOf(WitherDragon.Green, WitherDragon.Blue), power = 32, time = true, settings = settings))
    }

    @Test
    fun lowPowerUsesDefaultOrder() {
        assertEquals(WitherDragon.Blue, decide(setOf(WitherDragon.Green, WitherDragon.Blue), power = 20))
    }

    @Test
    fun berserkAtNormalPowerUsesSplitOrder() {
        assertEquals(WitherDragon.Green, decide(setOf(WitherDragon.Green, WitherDragon.Blue), power = 22))
    }

    @Test
    fun archerAtNormalPowerUsesReversedSplitOrder() {
        assertEquals(WitherDragon.Blue, decide(setOf(WitherDragon.Green, WitherDragon.Blue), playerClass = "ARCHER", power = 22))
    }

    @Test
    fun paulBuffMultipliesPower() {
        val spawning = setOf(WitherDragon.Green, WitherDragon.Blue)
        assertEquals(WitherDragon.Green, decide(spawning, power = 18, paul = true))
        assertEquals(WitherDragon.Blue, decide(spawning, power = 18, paul = false))
    }

    @Test
    fun timeBlessingAddsBonusPower() {
        assertEquals(WitherDragon.Green, decide(setOf(WitherDragon.Green, WitherDragon.Blue), power = 20, time = true))
    }

    @Test
    fun purpleUsesEasyThreshold() {
        assertEquals(WitherDragon.Green, decide(setOf(WitherDragon.Purple, WitherDragon.Green), power = 19))
        assertEquals(WitherDragon.Blue, decide(setOf(WitherDragon.Green, WitherDragon.Blue), power = 19))
    }

    @Test
    fun soloDebuffClassTakesLowestRankedDragonWhenPurpleSpawns() {
        val spawning = setOf(WitherDragon.Purple, WitherDragon.Orange)
        assertEquals(WitherDragon.Orange, decide(spawning, playerClass = "TANK", power = 20))
        assertEquals(WitherDragon.Purple, decide(spawning, playerClass = "HEALER", power = 20))
    }

    @Test
    fun soloDebuffOnAllAppliesWithoutPurple() {
        val spawning = setOf(WitherDragon.Orange, WitherDragon.Red)
        val all = defaults.copy(soloDebuffOnAll = true)
        assertEquals(WitherDragon.Red, decide(spawning, playerClass = "TANK", power = 25))
        assertEquals(WitherDragon.Orange, decide(spawning, playerClass = "TANK", power = 25, settings = all))
    }

    @Test
    fun fallbackOrderPicksEarliestInDefaultList() {
        assertEquals(WitherDragon.Purple, DragonPriority.fallbackOrder(setOf(WitherDragon.Purple, WitherDragon.Green)))
    }
}
