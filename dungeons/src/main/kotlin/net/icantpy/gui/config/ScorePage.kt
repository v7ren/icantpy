package net.icantpy.gui.config

import net.icantpy.dungeon.score.ScoreSettings
import net.icantpy.dungeon.score.ScoreTracker

/** Config body for "Score": display and alert toggles, threshold cycling, estimate assumptions and the HUD editor entry. */
internal class ScorePage {
    fun draw(draw: MenuDraw, m: ConfigMetrics, startY: Int, openHudEditor: () -> Unit): Int {
        val settings = ScoreTracker.settings
        var y = RunPageRows.help(
            draw, m, startY,
            "Estimate from sidebar/tab — no dungeon map. The score is a local estimate and is not verified by the game.",
        )
        y = RunPageRows.heading(draw, m, y, "Display")
        y = RunPageRows.toggle(draw, m, y, "Show breakdown", "Show each score component under the total.", settings.showBreakdown) {
            ScoreTracker.updateSettings { it.copy(showBreakdown = !it.showBreakdown) }
        }
        y = RunPageRows.heading(draw, m, y + RunPageRows.GROUP_GAP, "Alerts")
        y = RunPageRows.toggle(draw, m, y, "Alert at threshold 1", "Play a sound and show a notice when the estimate crosses threshold 1.", settings.alert270) {
            ScoreTracker.updateSettings { it.copy(alert270 = !it.alert270) }
        }
        y = RunPageRows.button(
            draw, m, y, "Threshold 1", "Score that triggers the first alert. Click to cycle 200 to 320 in steps of 10.",
            settings.threshold1.toString(), ButtonStyle.GHOST, 64,
        ) { ScoreTracker.updateSettings { it.copy(threshold1 = cycleThreshold(it.threshold1)) } }
        y = RunPageRows.toggle(draw, m, y, "Alert at threshold 2", "Play a sound and show a notice when the estimate crosses threshold 2.", settings.alert300) {
            ScoreTracker.updateSettings { it.copy(alert300 = !it.alert300) }
        }
        y = RunPageRows.button(
            draw, m, y, "Threshold 2", "Score that triggers the second alert. Click to cycle 200 to 320 in steps of 10.",
            settings.threshold2.toString(), ButtonStyle.GHOST, 64,
        ) { ScoreTracker.updateSettings { it.copy(threshold2 = cycleThreshold(it.threshold2)) } }
        y = RunPageRows.heading(draw, m, y + RunPageRows.GROUP_GAP, "Estimate assumptions")
        y = RunPageRows.toggle(draw, m, y, "Assume spirit pet", "Add the spirit pet bonus to the estimate when you have not confirmed one.", settings.assumeSpiritPet) {
            ScoreTracker.updateSettings { it.copy(assumeSpiritPet = !it.assumeSpiritPet) }
        }
        y = RunPageRows.toggle(draw, m, y, "Paul EZPZ bonus", "Add the Paul EZPZ bonus to the estimate.", settings.paulBonus) {
            ScoreTracker.updateSettings { it.copy(paulBonus = !it.paulBonus) }
        }
        y = RunPageRows.button(
            draw, m, y + RunPageRows.GROUP_GAP, "HUD position", "Drag the score box to move it and use + or - to scale it.",
            "Edit HUD position", ButtonStyle.PRIMARY, 150, openHudEditor,
        )
        return y + RunPageRows.GROUP_GAP
    }

    companion object {
        private const val THRESHOLD_MIN = 200
        private const val THRESHOLD_MAX = 320
        private const val THRESHOLD_STEP = 10

        /** Next value in 200..320 step 10. Values outside the range, or off-step, snap to the next step up. */
        fun cycleThreshold(current: Int): Int {
            if (current < THRESHOLD_MIN || current >= THRESHOLD_MAX) return THRESHOLD_MIN
            val steps = (current - THRESHOLD_MIN + THRESHOLD_STEP) / THRESHOLD_STEP
            return (THRESHOLD_MIN + steps * THRESHOLD_STEP).coerceAtMost(THRESHOLD_MAX)
        }
    }
}
