package net.icantpy.modules.impl.timer

import net.icantpy.gui.configUI.GuiLookSettings
import net.icantpy.gui.configUI.HudOverlaySettings
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrientSettings
import net.icantpy.modules.impl.stats.StatsArmorSettings
import net.icantpy.modules.impl.waypoint.CommandWaypoint

data class TimerSettings(
    val look: GuiLookSettings = GuiLookSettings(),
    val hud: HudOverlaySettings = HudOverlaySettings(),
    val displayInTicks: Boolean = false,
    val showPrefix: Boolean = true,
    val showSuffix: Boolean = true,
    val debugMode: Boolean = false,
    val necronHud: Boolean = false,
    val goldorHud: Boolean = true,
    val startTimer: Boolean = false,
    val padHud: Boolean = true,
    val lightningHud: Boolean = true,
    val pyHud: Boolean = true,
    val stormTickHud: Boolean = true,
    val secretsHud: Boolean = false,
    val necronX: Int = 8,
    val necronY: Int = 8,
    val goldorX: Int = 8,
    val goldorY: Int = 22,
    val padX: Int = 8,
    val padY: Int = 36,
    val lightningX: Int = 8,
    val lightningY: Int = 50,
    val pyX: Int = 8,
    val pyY: Int = 64,
    val stormTickX: Int = 8,
    val stormTickY: Int = 78,
    val secretsX: Int = 8,
    val secretsY: Int = 92,
    val notifyX: Int = DEFAULT_NOTIFY_X,
    val notifyY: Int = DEFAULT_NOTIFY_Y,
    val triggers: List<TimerTrigger> = emptyList(),
    val waypointsEnabled: Boolean = true,
    val showWaypoints: Boolean = true,
    val waypoints: List<CommandWaypoint> = emptyList(),
    val leap: LeapOrientSettings = LeapOrientSettings(),
    val statsArmor: StatsArmorSettings = StatsArmorSettings(),
) {
    fun enabled(hud: TimerHud): Boolean = when (hud) {
        TimerHud.NECRON -> necronHud
        TimerHud.GOLDOR -> goldorHud
        TimerHud.PAD -> padHud
        TimerHud.LIGHTNING -> lightningHud
        TimerHud.PY -> pyHud
        TimerHud.STORM_TICK -> stormTickHud
        TimerHud.SECRETS -> secretsHud
        TimerHud.NOTIFICATION -> true
    }

    fun position(hud: TimerHud): Pair<Int, Int> = when (hud) {
        TimerHud.NECRON -> necronX to necronY
        TimerHud.GOLDOR -> goldorX to goldorY
        TimerHud.PAD -> padX to padY
        TimerHud.LIGHTNING -> lightningX to lightningY
        TimerHud.PY -> pyX to pyY
        TimerHud.STORM_TICK -> stormTickX to stormTickY
        TimerHud.SECRETS -> secretsX to secretsY
        TimerHud.NOTIFICATION -> notifyX to notifyY
    }

    fun toggle(key: String): TimerSettings = when (key) {
        "displayInTicks" -> copy(displayInTicks = !displayInTicks)
        "showPrefix" -> copy(showPrefix = !showPrefix)
        "showSuffix" -> copy(showSuffix = !showSuffix)
        "debugMode" -> copy(debugMode = !debugMode)
        "necronHud" -> copy(necronHud = !necronHud)
        "goldorHud" -> copy(goldorHud = !goldorHud)
        "startTimer" -> copy(startTimer = !startTimer)
        "padHud" -> copy(padHud = !padHud)
        "lightningHud" -> copy(lightningHud = !lightningHud)
        "pyHud" -> copy(pyHud = !pyHud)
        "stormTickHud" -> copy(stormTickHud = !stormTickHud)
        "secretsHud" -> copy(secretsHud = !secretsHud)
        "waypointsEnabled" -> copy(waypointsEnabled = !waypointsEnabled)
        "showWaypoints" -> copy(showWaypoints = !showWaypoints)
        else -> this
    }

    fun withHudPosition(hud: TimerHud, x: Int, y: Int): TimerSettings = when (hud) {
        TimerHud.NECRON -> copy(necronX = x, necronY = y)
        TimerHud.GOLDOR -> copy(goldorX = x, goldorY = y)
        TimerHud.PAD -> copy(padX = x, padY = y)
        TimerHud.LIGHTNING -> copy(lightningX = x, lightningY = y)
        TimerHud.PY -> copy(pyX = x, pyY = y)
        TimerHud.STORM_TICK -> copy(stormTickX = x, stormTickY = y)
        TimerHud.SECRETS -> copy(secretsX = x, secretsY = y)
        TimerHud.NOTIFICATION -> copy(notifyX = x, notifyY = y)
    }

    fun resetHudPosition(hud: TimerHud): TimerSettings =
        if (hud == TimerHud.NOTIFICATION) {
            copy(notifyX = DEFAULT_NOTIFY_X, notifyY = DEFAULT_NOTIFY_Y)
        } else {
            withHudPosition(hud, defaultX(hud), defaultY(hud))
        }

    fun upsertTrigger(trigger: TimerTrigger): TimerSettings {
        val index = triggers.indexOfFirst { it.id == trigger.id }
        val next = if (index < 0) {
            triggers + trigger
        } else {
            triggers.toMutableList().also { it[index] = trigger }
        }
        return copy(triggers = next)
    }

    fun removeTrigger(id: String): TimerSettings =
        copy(triggers = triggers.filter { it.id != id })

    fun upsertWaypoint(waypoint: CommandWaypoint): TimerSettings {
        val index = waypoints.indexOfFirst { it.id == waypoint.id }
        val next = if (index < 0) {
            waypoints + waypoint
        } else {
            waypoints.toMutableList().also { it[index] = waypoint }
        }
        return copy(waypoints = next)
    }

    fun removeWaypoint(id: String): TimerSettings =
        copy(waypoints = waypoints.filter { it.id != id })

    fun resolveNotifyPosition(boxW: Int, boxH: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
        val x = if (notifyX < 0) (screenW - boxW) / 2 else notifyX
        val y = if (notifyY < 0) (screenH / 2 - 24).coerceAtLeast(0) else notifyY
        return clamp(x, y, boxW, boxH, screenW, screenH)
    }

    companion object {
        const val DEFAULT_NOTIFY_X: Int = -1
        const val DEFAULT_NOTIFY_Y: Int = -1

        fun defaultX(hud: TimerHud): Int = 8

        fun defaultY(hud: TimerHud): Int = 8 + hud.ordinal * 14

        fun clamp(x: Int, y: Int, boxW: Int, boxH: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
            val maxX = (screenW - boxW).coerceAtLeast(0)
            val maxY = (screenH - boxH).coerceAtLeast(0)
            return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
        }
    }
}
