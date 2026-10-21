package net.icantpy.dungeon.dragons

/**
 * Decides which of the spawning dragons to kill first. Ported from Odin's DragonPriority.
 * [playerClass] is the uppercase class name (for example "BERSERK"), or null when unknown.
 */
object DragonPriority {
    private val defaultOrder = listOf(WitherDragon.Red, WitherDragon.Orange, WitherDragon.Blue, WitherDragon.Purple, WitherDragon.Green)
    private val splitOrder = listOf(WitherDragon.Orange, WitherDragon.Green, WitherDragon.Red, WitherDragon.Blue, WitherDragon.Purple)

    private const val PAUL_MULTIPLIER = 1.25
    private const val TIME_BLESSING_BONUS = 2.5

    /** Order used when priority is disabled or no context exists: Red, Orange, Blue, Purple, Green. */
    fun fallbackOrder(spawning: Set<WitherDragon>): WitherDragon =
        spawning.minBy { defaultOrder.indexOf(it) }

    fun totalPower(powerBlessing: Int, paul: Boolean, timeBlessing: Boolean): Double =
        powerBlessing * (if (paul) PAUL_MULTIPLIER else 1.0) + (if (timeBlessing) TIME_BLESSING_BONUS else 0.0)

    fun decide(
        spawning: Set<WitherDragon>,
        playerClass: String?,
        powerBlessing: Int,
        paul: Boolean,
        timeBlessing: Boolean,
        settings: DragonSettings,
    ): WitherDragon {
        require(spawning.isNotEmpty()) { "decide needs at least one spawning dragon" }
        if (!settings.priorityEnabled) return fallbackOrder(spawning)

        val power = totalPower(powerBlessing, paul, timeBlessing)
        val cls = playerClass?.uppercase()
        val hasPurple = WitherDragon.Purple in spawning
        val splitPower = power >= settings.normalPower || (hasPurple && power >= settings.easyPower)
        val order = when {
            !splitPower -> defaultOrder
            cls == "BERSERK" || cls == "MAGE" -> splitOrder
            else -> splitOrder.reversed()
        }
        val sorted = spawning.sortedBy { order.indexOf(it) }

        // The solo-debuff class takes the lowest-ranked dragon instead of the highest.
        val soloApplies = power >= settings.easyPower &&
            (hasPurple || settings.soloDebuffOnAll) &&
            cls == settings.soloDebuff.className
        return if (soloApplies) sorted.last() else sorted.first()
    }
}
