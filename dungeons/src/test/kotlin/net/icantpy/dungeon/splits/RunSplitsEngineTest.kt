package net.icantpy.dungeon.splits

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.leap.LeapTriggerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunSplitsEngineTest {
    private fun feed(
        engine: RunSplitsEngine,
        vararg events: Pair<LeapTriggerEvent, Long>,
    ): CompletedRun? {
        var completed: CompletedRun? = null
        for ((event, at) in events) {
            completed = engine.onEvent(event, at, wallMs = 1_700_000_000_000L) ?: completed
        }
        return completed
    }

    private fun f7Run(endAt: Long = 730_000L) = arrayOf(
        LeapTriggerEvent.BLOOD_OPEN to 10_000L,
        LeapTriggerEvent.BLOOD_CAMP_DONE to 60_000L,
        LeapTriggerEvent.BOSS_ENTRY to 100_000L,
        LeapTriggerEvent.MAXOR_DEATH to 300_000L,
        LeapTriggerEvent.BOSS_STORM_END to 400_000L,
        LeapTriggerEvent.BOSS_CORE to 500_000L,
        LeapTriggerEvent.BOSS_GOLDOR_DEATH to 650_000L,
        LeapTriggerEvent.BOSS_NECRON_DEATH to 700_000L,
        LeapTriggerEvent.RUN_END to endAt,
    )

    @Test
    fun floorLayoutsMatchDesign() {
        assertEquals(
            listOf("blood_open", "watcher_done", "boss_entry", "bonzo", "boss_end"),
            SplitLayouts.forFloor(DungeonFloor.F1).map { it.id },
        )
        assertEquals(
            listOf("blood_open", "watcher_done", "boss_entry", "livid", "boss_end"),
            SplitLayouts.forFloor(DungeonFloor.M5).map { it.id },
        )
        assertEquals(
            listOf("blood_open", "watcher_done", "boss_entry", "giants", "sadan", "boss_end"),
            SplitLayouts.forFloor(DungeonFloor.F6).map { it.id },
        )
        assertEquals(
            listOf("blood_open", "watcher_done", "boss_entry", "maxor", "storm", "terminals", "goldor", "necron"),
            SplitLayouts.forFloor(DungeonFloor.F7).map { it.id },
        )
        assertEquals("dragons", SplitLayouts.forFloor(DungeonFloor.M7).last().id)
        assertEquals(
            listOf("blood_open", "watcher_done", "boss_entry"),
            SplitLayouts.forFloor(null).map { it.id },
        )
        assertEquals(3, SplitLayouts.forFloor(DungeonFloor.NONE).size)
    }

    @Test
    fun f7FullRunRecordsTimingsAndFinishesAtRunEnd() {
        val engine = RunSplitsEngine()
        engine.setFloor(DungeonFloor.F7)
        val completed = feed(engine, LeapTriggerEvent.RUN_START to 0L, *f7Run())

        assertNotNull(completed)
        assertEquals(730_000L, completed.totalMs)
        assertEquals(300_000L, completed.cumulativeMs["maxor"])
        assertEquals(200_000L, completed.segmentMs["maxor"])
        assertEquals(10_000L, completed.segmentMs["blood_open"])
        assertEquals(50_000L, completed.segmentMs["watcher_done"])
        assertEquals(150_000L, completed.segmentMs["goldor"])
        assertEquals(10_000L, completed.cumulativeMs["blood_open"])
        assertEquals(RunSplitsEngine.Phase.FINISHED, engine.phase)
    }

    @Test
    fun outOfOrderAndDuplicateEventsAreIgnored() {
        val engine = RunSplitsEngine()
        engine.setFloor(DungeonFloor.F2)
        val completed = feed(
            engine,
            LeapTriggerEvent.RUN_START to 0L,
            LeapTriggerEvent.BLOOD_OPEN to 10_000L,
            LeapTriggerEvent.BOSS_ENTRY to 100_000L,
            LeapTriggerEvent.BLOOD_CAMP_DONE to 120_000L,
            LeapTriggerEvent.BLOOD_OPEN to 130_000L,
            LeapTriggerEvent.RUN_END to 200_000L,
        )

        assertNotNull(completed)
        assertEquals(10_000L, completed.cumulativeMs["blood_open"])
        assertNull(completed.cumulativeMs["watcher_done"])
        assertEquals(100_000L, completed.cumulativeMs["boss_entry"])
        assertEquals(200_000L, completed.cumulativeMs["boss_end"])
    }

    @Test
    fun abandonedRunIsNotCompleted() {
        val engine = RunSplitsEngine()
        engine.setFloor(DungeonFloor.F1)
        val completed = feed(
            engine,
            LeapTriggerEvent.RUN_START to 0L,
            LeapTriggerEvent.BLOOD_OPEN to 10_000L,
            LeapTriggerEvent.DUNGEON_LEFT to 50_000L,
            LeapTriggerEvent.RUN_END to 60_000L,
        )

        assertNull(completed)
        assertEquals(RunSplitsEngine.Phase.IDLE, engine.phase)
        assertFalse(engine.snapshot(70_000L).active)
    }

    @Test
    fun floorLearnedAfterStartRemapsSeenEvents() {
        val engine = RunSplitsEngine()
        val completed = feed(
            engine,
            LeapTriggerEvent.RUN_START to 0L,
            LeapTriggerEvent.BOSS_ENTRY to 100_000L,
            LeapTriggerEvent.BONZO_DEAD to 150_000L,
        ).also { assertNull(it) }

        engine.setFloor(DungeonFloor.F1)
        val finished = feed(engine, LeapTriggerEvent.RUN_END to 200_000L)

        assertNull(completed)
        assertNotNull(finished)
        assertEquals(150_000L, finished.cumulativeMs["bonzo"])
        assertEquals(100_000L, finished.cumulativeMs["boss_entry"])
    }

    @Test
    fun runningRowUsesLiveTime() {
        val engine = RunSplitsEngine()
        engine.setFloor(DungeonFloor.F7)
        feed(engine, LeapTriggerEvent.RUN_START to 0L, LeapTriggerEvent.BLOOD_OPEN to 10_000L)

        val view = engine.snapshot(30_000L)
        assertTrue(view.active)
        assertEquals(SplitState.DONE, view.rows[0].state)
        assertEquals(10_000L, view.rows[0].segmentMs)
        assertEquals(SplitState.RUNNING, view.rows[1].state)
        assertEquals(30_000L, view.rows[1].cumulativeMs)
        assertEquals(20_000L, view.rows[1].segmentMs)
        assertEquals(SplitState.PENDING, view.rows[2].state)
        assertEquals(30_000L, view.totalMs)
    }

    @Test
    fun bloodOpenStartsRunWhenRunStartNeverArrives() {
        val engine = RunSplitsEngine()
        engine.setFloor(DungeonFloor.F2)
        feed(engine, LeapTriggerEvent.BLOOD_OPEN to 5_000L, LeapTriggerEvent.BOSS_ENTRY to 65_000L)
        val completed = feed(engine, LeapTriggerEvent.RUN_END to 100_000L)

        assertNotNull(completed)
        assertEquals(95_000L, completed.totalMs)
        assertEquals(0L, completed.cumulativeMs["blood_open"])
        assertEquals(60_000L, completed.cumulativeMs["boss_entry"])
    }

    @Test
    fun pbReplacesOnlyWhenFaster() {
        val f7 = DungeonFloor.F7
        fun run(total: Long) = CompletedRun(f7, total, mapOf("maxor" to total / 2), mapOf("maxor" to total / 2), 0L)

        var floors: Map<String, FloorRecord> = emptyMap()
        val first = PbLedger.record(floors, run(700_000L))
        assertTrue(first.isPb)
        floors = first.floors

        val slower = PbLedger.record(floors, run(800_000L))
        assertFalse(slower.isPb)
        floors = slower.floors
        assertEquals(700_000L, floors["F7"]?.bestTotalMs)

        val faster = PbLedger.record(floors, run(650_000L))
        assertTrue(faster.isPb)
        assertEquals(650_000L, faster.floors["F7"]?.bestTotalMs)
        assertEquals(325_000L, faster.floors["F7"]?.pbSplits?.get("maxor"))
    }

    @Test
    fun bestSegmentsUpdateIndependently() {
        val first = CompletedRun(
            DungeonFloor.F7, 500_000L,
            cumulativeMs = mapOf("maxor" to 200_000L, "storm" to 300_000L),
            segmentMs = mapOf("maxor" to 200_000L, "storm" to 100_000L),
            endedAtEpochMs = 1L,
        )
        val second = CompletedRun(
            DungeonFloor.F7, 600_000L,
            cumulativeMs = mapOf("maxor" to 150_000L, "storm" to 270_000L),
            segmentMs = mapOf("maxor" to 150_000L, "storm" to 120_000L),
            endedAtEpochMs = 2L,
        )
        val a = PbLedger.record(emptyMap(), first)
        val b = PbLedger.record(a.floors, second)

        val record = b.floors["F7"]!!
        assertEquals(150_000L, record.bestSegments["maxor"])
        assertEquals(100_000L, record.bestSegments["storm"])
        assertEquals(500_000L, record.bestTotalMs)
        assertEquals(setOf("maxor"), b.newBestSegmentIds)
    }

    @Test
    fun sumOfBestCombinesBestSegmentsOfEveryRow() {
        val a = CompletedRun(
            DungeonFloor.F2, 300_000L,
            cumulativeMs = mapOf("blood_open" to 10_000L, "watcher_done" to 60_000L, "boss_entry" to 100_000L, "boss_end" to 300_000L),
            segmentMs = mapOf("blood_open" to 10_000L, "watcher_done" to 50_000L, "boss_entry" to 40_000L, "boss_end" to 200_000L),
            endedAtEpochMs = 1L,
        )
        val b = CompletedRun(
            DungeonFloor.F2, 282_000L,
            cumulativeMs = mapOf("blood_open" to 12_000L, "watcher_done" to 57_000L, "boss_entry" to 102_000L, "boss_end" to 282_000L),
            segmentMs = mapOf("blood_open" to 12_000L, "watcher_done" to 45_000L, "boss_entry" to 45_000L, "boss_end" to 180_000L),
            endedAtEpochMs = 2L,
        )
        val floors = PbLedger.record(PbLedger.record(emptyMap(), a).floors, b).floors

        val engine = RunSplitsEngine()
        engine.setFloor(DungeonFloor.F2)
        val view = engine.snapshot(0L, floors["F2"])
        assertEquals(10_000L + 45_000L + 40_000L + 180_000L, view.sumOfBestMs)
        assertEquals(282_000L, view.pbTotalMs)
    }

    @Test
    fun historyIsBoundedToTwentyNewestFirst() {
        var floors: Map<String, FloorRecord> = emptyMap()
        for (i in 0 until 25) {
            val run = CompletedRun(DungeonFloor.F3, 400_000L + i, emptyMap(), emptyMap(), endedAtEpochMs = i.toLong())
            floors = PbLedger.record(floors, run).floors
        }
        val history = floors["F3"]!!.history
        assertEquals(20, history.size)
        assertEquals(24L, history.first().endedAtEpochMs)
        assertEquals(5L, history.last().endedAtEpochMs)
    }

    @Test
    fun finishedRunOnlyCompletesOnce() {
        val engine = RunSplitsEngine()
        engine.setFloor(DungeonFloor.F1)
        val first = feed(
            engine,
            LeapTriggerEvent.RUN_START to 0L,
            LeapTriggerEvent.BLOOD_OPEN to 10_000L,
            LeapTriggerEvent.RUN_END to 900_000L,
        )
        assertNotNull(first)
        assertNull(engine.onEvent(LeapTriggerEvent.RUN_END, 950_000L))
    }
}
