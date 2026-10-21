package net.icantpy.dungeon.score

import net.icantpy.compat.McCompat
import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.DungeonListener
import net.icantpy.dungeon.leap.LeapTriggerEvent
import net.icantpy.dungeon.timer.AlertSounds
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.location.SkyblockLocation
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory

/**
 * Keeps the live dungeon score estimate and fires the 270/300 alerts. Local only: no chat messages and no packets.
 *
 * Integration: call [tick] every client tick, [onEvent] for each [LeapTriggerEvent] in a run, [applySettings] when the
 * settings change, and [onDisconnect] on disconnect.
 */
object ScoreTracker {
    private const val SAMPLE_TICKS = 10
    private const val ALERT_DURATION_TICKS = 100
    private const val ALERT_SOUND = "pling"
    private val LOGGER = LoggerFactory.getLogger("icantpy")

    private var ticks = 0
    private var runState = ScoreRunState()
    private val alerts = ScoreAlertPolicy()

    var settings: ScoreSettings = ScoreSettings()
        private set

    /** Latest estimate for the current dungeon run. Null outside a dungeon, on an unknown floor, or when disabled. */
    var latest: ScoreBreakdown? = null
        private set

    fun applySettings(next: ScoreSettings) {
        settings = next
        if (!next.enabled) latest = null
    }

    /** Reads config/icantpy-score.json. A failed read keeps defaults rather than blocking the payload load. */
    fun load() {
        applySettings(
            try {
                ScoreSettingsStore.load()
            } catch (error: java.io.IOException) {
                LOGGER.warn("Could not read score settings; using defaults", error)
                ScoreSettings()
            },
        )
    }

    /** Applies and persists a settings change made from the config screen. */
    fun updateSettings(transform: (ScoreSettings) -> ScoreSettings) {
        val next = transform(settings)
        applySettings(next)
        try {
            ScoreSettingsStore.save(ScoreSettingsStore.defaultPath(), next)
        } catch (error: java.io.IOException) {
            LOGGER.warn("Could not save score settings", error)
        }
    }

    fun onEvent(event: LeapTriggerEvent) {
        when (event) {
            LeapTriggerEvent.RUN_START, LeapTriggerEvent.DUNGEON_LEFT -> resetRun()
            else -> runState = runState.withEvent(event)
        }
    }

    fun onDisconnect() {
        ticks = 0
        latest = null
        resetRun()
    }

    /** Samples every [SAMPLE_TICKS] ticks and runs the alert check on each sample. */
    fun tick() {
        ticks += 1
        if (ticks < SAMPLE_TICKS) return
        ticks = 0
        val mc = Minecraft.getInstance()
        if (!settings.enabled || mc.player == null || mc.level == null) {
            latest = null
            return
        }
        val inputs = readInputs(mc)
        if (inputs == null) {
            latest = null
            return
        }
        val breakdown = ScoreCalculator.compute(inputs)
        latest = breakdown
        announce(alerts.evaluate(breakdown.total, thresholdTargets()))
    }

    private fun readInputs(mc: Minecraft): ScoreInputs? {
        val snapshot = SkyblockLocation.snapshot
        if (!(DungeonListener.state.inDungeon || snapshot.inDungeon)) return null
        val floor = snapshot.floor.takeIf { it != DungeonFloor.NONE } ?: DungeonListener.floor
        if (floor == DungeonFloor.NONE) return null
        val lines = McCompat.tabLines(mc) + DungeonListener.sidebarLines()
        val stats = snapshot.stats
        val cleared = stats.cleared ?: DungeonListener.state.percentCleared.takeIf { it >= 0 }
        return ScoreInputs(
            floor = floor,
            clearedPercent = cleared,
            secretsFoundPercent = ScoreParsing.secretsPercent(lines),
            failedPuzzles = runState.failedPuzzles,
            deaths = stats.deaths ?: 0,
            spiritPetFirstDeath = settings.assumeSpiritPet,
            elapsedSeconds = ScoreParsing.elapsedSeconds(lines),
            crypts = stats.crypts ?: 0,
            mimicKilled = runState.mimicKilled,
            princeKilled = runState.princeKilled,
            paulBonus = settings.paulBonus,
        )
    }

    /** Announces only the highest threshold reached in this sample, so two crossings do not stack two titles. */
    private fun announce(crossed: List<Int>) {
        val highest = crossed.maxOrNull() ?: return
        TickTimers.pushNotification(ScoreHudLines.alertText(highest), ALERT_DURATION_TICKS)
        AlertSounds.play(ALERT_SOUND)
    }

    private fun thresholdTargets(): List<Pair<Int, Boolean>> = listOf(
        settings.threshold1 to settings.alert270,
        settings.threshold2 to settings.alert300,
    )

    private fun resetRun() {
        runState = ScoreRunState()
        alerts.reset()
        latest = null
    }
}
