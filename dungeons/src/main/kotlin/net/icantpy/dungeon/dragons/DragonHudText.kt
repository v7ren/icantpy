package net.icantpy.dungeon.dragons

import java.util.Locale

data class DragonHudLine(val text: String, val colorArgb: Int)

/** Pure HUD line building, kept free of Minecraft types so it can be unit tested. */
object DragonHudText {
    private val countdownRed = 0xFFFF5555.toInt()
    private val countdownYellow = 0xFFFFFF55.toInt()
    private val countdownGreen = 0xFF55FF55.toInt()
    private const val PRIORITY_MARK = "★ "
    private const val RED_MAX_TICKS = 20L
    private const val YELLOW_MAX_TICKS = 60L

    /** One line per spawning or alive dragon, in dragon order. The priority dragon is marked. */
    fun lines(
        states: List<DragonState>,
        priority: WitherDragon?,
        nowTick: Long,
        style: DragonTimerStyle,
    ): List<DragonHudLine> =
        states
            .filter { it.phase != DragonPhase.DEAD }
            .sortedBy { it.dragon.ordinal }
            .map { lineFor(it, isPriority = it.dragon == priority, nowTick = nowTick, style = style) }

    fun previewLines(style: DragonTimerStyle): List<DragonHudLine> = listOf(
        DragonHudLine("$PRIORITY_MARK${WitherDragon.Red.displayName} ${formatTicks(70L, style)}", countdownGreen),
        DragonHudLine("${WitherDragon.Orange.displayName} ALIVE ${formatTicks(240L, style)}", WitherDragon.Orange.colorArgb),
    )

    fun formatTicks(ticks: Long, style: DragonTimerStyle): String {
        val safe = ticks.coerceAtLeast(0L)
        return when (style) {
            DragonTimerStyle.MILLISECONDS -> "${safe * DragonTracker.MS_PER_TICK}ms"
            DragonTimerStyle.SECONDS -> String.format(Locale.ROOT, "%.1fs", safe / 20.0)
            DragonTimerStyle.TICKS -> "${safe}t"
        }
    }

    /** Countdown colour: 20 ticks or less red, 60 or less yellow, otherwise green. */
    fun countdownArgb(ticksLeft: Long): Int = when {
        ticksLeft <= RED_MAX_TICKS -> countdownRed
        ticksLeft <= YELLOW_MAX_TICKS -> countdownYellow
        else -> countdownGreen
    }

    private fun lineFor(state: DragonState, isPriority: Boolean, nowTick: Long, style: DragonTimerStyle): DragonHudLine {
        val mark = if (isPriority) PRIORITY_MARK else ""
        val name = state.dragon.displayName
        return when (state.phase) {
            DragonPhase.SPAWNING -> {
                val left = state.ticksToSpawn.toLong()
                DragonHudLine("$mark$name ${formatTicks(left, style)}", countdownArgb(left))
            }
            DragonPhase.ALIVE -> {
                val elapsed = nowTick - state.spawnedAtTick
                DragonHudLine("$mark$name ALIVE ${formatTicks(elapsed, style)}", state.dragon.colorArgb)
            }
            DragonPhase.DEAD -> DragonHudLine("$mark$name DEAD", countdownRed)
        }
    }
}
