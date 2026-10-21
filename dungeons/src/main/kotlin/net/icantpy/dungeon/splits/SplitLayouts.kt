package net.icantpy.dungeon.splits

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.leap.LeapTriggerEvent

/** One split: a stable [id] (used as a persistence key) bound to the trigger event that completes it. */
data class SplitDef(val id: String, val name: String, val event: LeapTriggerEvent)

/** Per-floor split lists. The last entry of a list is the run end when it is bound to RUN_END. */
object SplitLayouts {
    private val BLOOD_OPEN = SplitDef("blood_open", "Blood open", LeapTriggerEvent.BLOOD_OPEN)
    private val WATCHER_DONE = SplitDef("watcher_done", "Watcher done", LeapTriggerEvent.BLOOD_CAMP_DONE)
    private val BOSS_ENTRY = SplitDef("boss_entry", "Boss entry", LeapTriggerEvent.BOSS_ENTRY)
    private val BOSS_END = SplitDef("boss_end", "Boss end", LeapTriggerEvent.RUN_END)

    private val BONZO = SplitDef("bonzo", "Bonzo", LeapTriggerEvent.BONZO_DEAD)
    private val LIVID = SplitDef("livid", "Livid", LeapTriggerEvent.LIVID_START)
    private val GIANTS = SplitDef("giants", "Giants", LeapTriggerEvent.SADAN_GIANTS)
    private val SADAN = SplitDef("sadan", "Sadan", LeapTriggerEvent.SADAN_DONE)
    private val MAXOR = SplitDef("maxor", "Maxor", LeapTriggerEvent.MAXOR_DEATH)
    private val STORM = SplitDef("storm", "Storm", LeapTriggerEvent.BOSS_STORM_END)
    private val TERMINALS = SplitDef("terminals", "Terminals", LeapTriggerEvent.BOSS_CORE)
    private val GOLDOR = SplitDef("goldor", "Goldor", LeapTriggerEvent.BOSS_GOLDOR_DEATH)
    private val NECRON = SplitDef("necron", "Necron", LeapTriggerEvent.BOSS_NECRON_DEATH)
    private val DRAGONS = SplitDef("dragons", "Dragons", LeapTriggerEvent.WITHER_KING_DEATH)

    private val COMMON: List<SplitDef> = listOf(BLOOD_OPEN, WATCHER_DONE, BOSS_ENTRY)
    private val F1_LIST: List<SplitDef> = COMMON + listOf(BONZO, BOSS_END)
    private val SIMPLE_BOSS_LIST: List<SplitDef> = COMMON + listOf(BOSS_END)
    private val F5_LIST: List<SplitDef> = COMMON + listOf(LIVID, BOSS_END)
    private val F6_LIST: List<SplitDef> = COMMON + listOf(GIANTS, SADAN, BOSS_END)
    private val F7_LIST: List<SplitDef> = COMMON + listOf(MAXOR, STORM, TERMINALS, GOLDOR, NECRON)
    private val M7_LIST: List<SplitDef> = F7_LIST + DRAGONS

    /** Splits for [floor]. Null, NONE and unrecognised floors get the common splits only (run end still finishes the run). */
    fun forFloor(floor: DungeonFloor?): List<SplitDef> = when (floor) {
        DungeonFloor.F1, DungeonFloor.M1 -> F1_LIST
        DungeonFloor.F2, DungeonFloor.F3, DungeonFloor.F4,
        DungeonFloor.M2, DungeonFloor.M3, DungeonFloor.M4 -> SIMPLE_BOSS_LIST
        DungeonFloor.F5, DungeonFloor.M5 -> F5_LIST
        DungeonFloor.F6, DungeonFloor.M6 -> F6_LIST
        DungeonFloor.F7 -> F7_LIST
        DungeonFloor.M7 -> M7_LIST
        DungeonFloor.NONE, null -> COMMON
    }

    /** Every event any layout can use, plus RUN_END which always finishes a run. */
    val TRACKED_EVENTS: Set<LeapTriggerEvent> =
        (F1_LIST + SIMPLE_BOSS_LIST + F5_LIST + F6_LIST + M7_LIST).map { it.event }.toSet() + LeapTriggerEvent.RUN_END
}
