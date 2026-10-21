package net.icantpy.dungeon.leap

import net.icantpy.util.Text

data class LeapBossBar(val name: String, val progress: Float)

/**
 * Tracks F7/M7 boss phases from chat and boss bars.
 *
 * Boss-bar deaths are only counted for a boss whose bar was previously seen alive in the current
 * fight. A bar that is first sighted at zero (spawn, reload, hidden bar) never counts as a death.
 * Confirmed dialogue/bar transitions persist after short-lived timer counters expire.
 */
class LeapBossTracker {
    var phase: LeapPhase = LeapPhase.UNKNOWN
        private set
    var necronRaging: Boolean = false
        private set
    private var coreOpened = false
    private var bossEntryAgeTicks = 0
    private var deaths: Set<LeapTriggerEvent> = emptySet()
    private val aliveBosses = mutableSetOf<String>()

    val necronBersActive: Boolean get() = phase == LeapPhase.P4 && !necronRaging

    fun reset() {
        phase = LeapPhase.UNKNOWN
        necronRaging = false
        coreOpened = false
        bossEntryAgeTicks = 0
        deaths = emptySet()
        aliveBosses.clear()
    }

    /** Noamm's Maxor safeguard: death bars only count after 120 boss-entry server ticks. */
    fun onServerTick() { bossEntryAgeTicks++ }

    fun onChat(raw: String): List<LeapTriggerEvent> {
        val line = LeapOrientEngine.normalizeBossLine(raw)
        if (line == "[BOSS] Necron: ARGH!") {
            advance(LeapPhase.P4)
            necronRaging = true
            return emptyList()
        }
        val event = LeapOrientEngine.bossEvent(line) ?: return emptyList()
        if (event == LeapTriggerEvent.MAXOR_START) {
            reset()
            phase = LeapPhase.P1
            return listOf(event)
        }
        return observe(event)
    }

    fun onBossBars(bars: List<LeapBossBar>): List<LeapTriggerEvent> = buildList {
        bars.forEach { bar ->
            val boss = bossKey(bar.name) ?: return@forEach
            if (!bar.progress.isFinite()) return@forEach
            if (bar.progress > ALIVE_PROGRESS) {
                aliveBosses += boss
                if (boss == "maxor") advance(LeapPhase.P1)
                return@forEach
            }
            // Still above zero: a dying bar is not a death yet.
            if (bar.progress > 0f) return@forEach
            // Zero health only counts once this boss's bar was observed alive in this fight.
            if (boss !in aliveBosses) return@forEach
            val event = deathFor(boss) ?: return@forEach
            aliveBosses -= boss
            addAll(observe(event))
        }
    }

    fun confirmP5(): List<LeapTriggerEvent> = observe(LeapTriggerEvent.BOSS_NECRON_DEATH)

    private fun deathFor(boss: String): LeapTriggerEvent? = when (boss) {
        "maxor" -> LeapTriggerEvent.MAXOR_DEATH.takeIf { bossEntryAgeTicks > MAXOR_SAFEGUARD_TICKS }
        "goldor" -> LeapTriggerEvent.BOSS_GOLDOR_DEATH.takeIf { coreOpened }
        "necron" -> LeapTriggerEvent.BOSS_NECRON_DEATH.takeIf { necronRaging }
        else -> null
    }

    private fun observe(event: LeapTriggerEvent): List<LeapTriggerEvent> {
        val destination = when (event) {
            LeapTriggerEvent.BOSS_STORM_START, LeapTriggerEvent.MAXOR_DEATH -> LeapPhase.P2
            LeapTriggerEvent.BOSS_STORM_END, LeapTriggerEvent.BOSS_GOLDOR -> LeapPhase.P3
            LeapTriggerEvent.BOSS_CORE -> {
                coreOpened = true
                LeapPhase.P3
            }
            LeapTriggerEvent.BOSS_GOLDOR_DEATH, LeapTriggerEvent.BOSS_NECRON -> LeapPhase.P4
            LeapTriggerEvent.BOSS_NECRON_DEATH, LeapTriggerEvent.BOSS_P5 -> LeapPhase.P5
            else -> phase
        }
        if (destination.ordinal < phase.ordinal) return emptyList()
        advance(destination)
        if (event in ONCE_PER_FIGHT) {
            if (event in deaths) return emptyList()
            deaths = deaths + event
        }
        return listOf(event)
    }

    private fun advance(next: LeapPhase) {
        if (next.ordinal > phase.ordinal) phase = next
    }

    companion object {
        /** Progress above this counts as the boss being alive; avoids treating a nearly dead bar as alive/dead noise. */
        const val ALIVE_PROGRESS = 0.05f
        /** Boss-entry server ticks before a Maxor zero-health bar may count as a death. */
        const val MAXOR_SAFEGUARD_TICKS = 120

        private val BOSS_NAME = Regex("\\b(Maxor|Storm|Goldor|Necron)\\b", RegexOption.IGNORE_CASE)

        /** Lowercase boss key for a bar name, or null for bars this tracker does not follow. */
        fun bossKey(barName: String): String? =
            BOSS_NAME.find(Text.strip(barName))?.value?.lowercase()

        /** Events that fire once per boss fight, even when both a bar and a dialogue line report them. */
        private val ONCE_PER_FIGHT = setOf(
            LeapTriggerEvent.MAXOR_DEATH, LeapTriggerEvent.BOSS_STORM_START, LeapTriggerEvent.BOSS_STORM_END,
            LeapTriggerEvent.BOSS_GOLDOR_DEATH, LeapTriggerEvent.BOSS_NECRON_DEATH,
            LeapTriggerEvent.WITHER_KING_DEATH,
        )
    }
}
