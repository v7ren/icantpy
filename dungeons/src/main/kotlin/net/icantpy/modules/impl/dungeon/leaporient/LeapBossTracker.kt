package net.icantpy.modules.impl.dungeon.leaporient

import net.icantpy.util.Text

data class LeapBossBar(val name: String, val progress: Float)

/** Confirmed dialogue/bar transitions persist after short-lived timer counters expire. */
class LeapBossTracker {
    var phase: LeapPhase = LeapPhase.UNKNOWN
        private set
    var necronRaging: Boolean = false
        private set
    private var coreOpened = false
    private var bossEntryAgeTicks = 0
    private var deaths: Set<LeapTriggerEvent> = emptySet()

    val necronBersActive: Boolean get() = phase == LeapPhase.P4 && !necronRaging

    fun reset() {
        phase = LeapPhase.UNKNOWN
        necronRaging = false
        coreOpened = false
        bossEntryAgeTicks = 0
        deaths = emptySet()
    }

    /** Noamm's Maxor safeguard: death bars only count after 120 boss-entry server ticks. */
    fun onServerTick() { bossEntryAgeTicks++ }

    fun onChat(raw: String): List<LeapTriggerEvent> {
        val line = LeapOrientEngine.normalizeBossLine(raw)
        if (line == "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!") {
            reset()
            phase = LeapPhase.P1
            return emptyList()
        }
        if (line == "[BOSS] Necron: ARGH!") {
            advance(LeapPhase.P4)
            necronRaging = true
            return emptyList()
        }
        val event = LeapOrientEngine.bossEvent(line) ?: return emptyList()
        return observe(event)
    }

    fun onBossBars(bars: List<LeapBossBar>): List<LeapTriggerEvent> = buildList {
        bars.forEach { bar ->
            val boss = Regex("\\b(Maxor|Storm|Goldor|Necron)\\b", RegexOption.IGNORE_CASE)
                .find(Text.strip(bar.name))?.value?.lowercase() ?: return@forEach
            if (!bar.progress.isFinite()) return@forEach
            if (bar.progress > 0f) {
                if (boss == "maxor") advance(LeapPhase.P1)
                return@forEach
            }
            // Noamm F7Titles: use raw zero health, guarded by boss-entry/Core/ARGH state.
            // Do not require a positive snapshot: reloads and hidden bars can omit it.
            val event = when (boss) {
                "maxor" -> if (bossEntryAgeTicks > 120) LeapTriggerEvent.BOSS_STORM_START else null
                "goldor" -> if (coreOpened) LeapTriggerEvent.BOSS_GOLDOR_DEATH else null
                "necron" -> if (necronRaging) LeapTriggerEvent.BOSS_NECRON_DEATH else null
                else -> null
            } ?: return@forEach
            addAll(observe(event))
        }
    }

    fun confirmP5(): List<LeapTriggerEvent> = observe(LeapTriggerEvent.BOSS_NECRON_DEATH)

    private fun observe(event: LeapTriggerEvent): List<LeapTriggerEvent> {
        val destination = when (event) {
            LeapTriggerEvent.BOSS_STORM_START -> LeapPhase.P2
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
        if (event in DEATH_EVENTS) {
            if (event in deaths) return emptyList()
            deaths = deaths + event
        }
        return listOf(event)
    }

    private fun advance(next: LeapPhase) {
        if (next.ordinal > phase.ordinal) phase = next
    }

    companion object {
        private val DEATH_EVENTS = setOf(
            LeapTriggerEvent.BOSS_STORM_START, LeapTriggerEvent.BOSS_STORM_END,
            LeapTriggerEvent.BOSS_GOLDOR_DEATH, LeapTriggerEvent.BOSS_NECRON_DEATH,
        )
    }
}
