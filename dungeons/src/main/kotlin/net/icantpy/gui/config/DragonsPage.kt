package net.icantpy.gui.config

import net.icantpy.dungeon.dragons.DragonController
import net.icantpy.dungeon.dragons.DragonSettings
import net.icantpy.dungeon.dragons.DragonTimerStyle
import net.icantpy.dungeon.dragons.SoloDebuffClass
import net.icantpy.dungeon.dragons.WitherDragon
import java.util.Locale

/**
 * Config body for "Wither Dragons": priority choices, spawn timer and message toggles, per-dragon personal bests
 * and the HUD editor entry.
 */
internal class DragonsPage {
    fun draw(draw: MenuDraw, m: ConfigMetrics, startY: Int, toast: (String) -> Unit, openHudEditor: () -> Unit): Int {
        val settings = DragonController.settings()
        var y = RunPageRows.help(
            draw, m, startY,
            "Spawn priority and timers for the Wither King phase 5 dragons. Everything stays local: nothing is sent to the server.",
        )
        y = RunPageRows.help(
            draw, m, y - RunPageRows.GROUP_GAP,
            "Spawn timers need loader 1.0.3.34 (restart required); without it dragons are detected when they appear.",
        )
        y = RunPageRows.heading(draw, m, y, "Priority")
        y = RunPageRows.toggle(draw, m, y, "Priority", "Pick which spawning dragon to kill first, from your class and blessings.", settings.priorityEnabled) {
            DragonController.updateSettings { it.copy(priorityEnabled = !it.priorityEnabled) }
        }
        y = RunPageRows.toggle(draw, m, y, "Paul buff", "Count the Paul buff as a 25% power boost in priority.", settings.paulBuff) {
            DragonController.updateSettings { it.copy(paulBuff = !it.paulBuff) }
        }
        y = RunPageRows.toggle(draw, m, y, "Solo debuff on all", "Apply the solo debuff even when no purple dragon spawns.", settings.soloDebuffOnAll) {
            DragonController.updateSettings { it.copy(soloDebuffOnAll = !it.soloDebuffOnAll) }
        }
        y = RunPageRows.button(
            draw, m, y, "Solo debuff class", "Class that takes the lowest-ranked dragon at high power. Click to switch.",
            settings.soloDebuff.name.lowercase().replaceFirstChar { it.uppercase() }, ButtonStyle.GHOST, 96,
        ) { DragonController.updateSettings { it.copy(soloDebuff = nextSolo(it.soloDebuff)) } }
        y = RunPageRows.button(
            draw, m, y, "Normal power", "Blessing power at which the normal split order applies. Click to add 1, wrapping at 40.",
            settings.normalPower.toString(), ButtonStyle.GHOST, 64,
        ) { DragonController.updateSettings { it.copy(normalPower = nextPower(it.normalPower)) } }
        y = RunPageRows.button(
            draw, m, y, "Easy power", "Blessing power at which the easy split order applies. Click to add 1, wrapping at 40.",
            settings.easyPower.toString(), ButtonStyle.GHOST, 64,
        ) { DragonController.updateSettings { it.copy(easyPower = nextPower(it.easyPower)) } }
        y = RunPageRows.heading(draw, m, y + RunPageRows.GROUP_GAP, "Spawn timers and messages")
        y = RunPageRows.toggle(draw, m, y, "Spawn title", "Show a title when the priority dragon is chosen.", settings.showSpawnTitle) {
            DragonController.updateSettings { it.copy(showSpawnTitle = !it.showSpawnTitle) }
        }
        y = RunPageRows.toggle(draw, m, y, "Timer HUD", "Show the countdown and alive timers for each dragon.", settings.showTimerHud) {
            DragonController.updateSettings { it.copy(showTimerHud = !it.showTimerHud) }
        }
        y = RunPageRows.button(
            draw, m, y, "Timer style", "How the timer HUD shows time. Click to switch.",
            timerStyleLabel(settings.timerStyle), ButtonStyle.GHOST, 96,
        ) { DragonController.updateSettings { it.copy(timerStyle = nextStyle(it.timerStyle)) } }
        y = RunPageRows.toggle(draw, m, y, "Kill messages", "Show a local message with the time each dragon was killed.", settings.showKillMessages) {
            DragonController.updateSettings { it.copy(showKillMessages = !it.showKillMessages) }
        }
        y = RunPageRows.toggle(draw, m, y, "Dragon boxes", "Outline living and spawning dragons in the world.", settings.showBoxes) {
            DragonController.updateSettings { it.copy(showBoxes = !it.showBoxes) }
        }
        y = RunPageRows.heading(draw, m, y + RunPageRows.GROUP_GAP, "Personal bests")
        WitherDragon.entries.forEach { dragon ->
            y = personalBestRow(draw, m, y, dragon, toast)
        }
        y = RunPageRows.button(
            draw, m, y + RunPageRows.GROUP_GAP, "HUD position", "Drag the dragon timer box to move it and use + or - to scale it.",
            "Edit HUD position", ButtonStyle.PRIMARY, 150, openHudEditor,
        )
        return y + RunPageRows.GROUP_GAP
    }

    private fun personalBestRow(draw: MenuDraw, m: ConfigMetrics, y: Int, dragon: WitherDragon, toast: (String) -> Unit): Int {
        val best = DragonController.personalBestMs(dragon)
        val subtitle = best?.let { "Best kill: ${String.format(Locale.ROOT, "%.2f", it / 1000.0)} s. Click Reset PB to clear it." }
            ?: "No kill recorded yet."
        return RunPageRows.button(draw, m, y, "${dragon.displayName} dragon", subtitle, "Reset PB", ButtonStyle.GHOST, 96) {
            if (DragonController.personalBestMs(dragon) == null) {
                toast("No ${dragon.displayName} PB to reset")
            } else {
                DragonController.resetPersonalBest(dragon)
                toast("Reset the ${dragon.displayName} PB")
            }
        }
    }

    private fun timerStyleLabel(style: DragonTimerStyle): String = when (style) {
        DragonTimerStyle.MILLISECONDS -> "Milliseconds"
        DragonTimerStyle.SECONDS -> "Seconds"
        DragonTimerStyle.TICKS -> "Ticks"
    }

    companion object {
        /** Next value in 0..[DragonSettings.MAX_POWER]; wraps to the minimum after the maximum. */
        fun nextPower(current: Int): Int =
            if (current >= DragonSettings.MAX_POWER) DragonSettings.MIN_POWER else current + 1

        fun nextSolo(current: SoloDebuffClass): SoloDebuffClass =
            SoloDebuffClass.entries[(current.ordinal + 1) % SoloDebuffClass.entries.size]

        fun nextStyle(current: DragonTimerStyle): DragonTimerStyle =
            DragonTimerStyle.entries[(current.ordinal + 1) % DragonTimerStyle.entries.size]
    }
}
