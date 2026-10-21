package net.icantpy.dungeon.splits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunSplitsCodecTest {
    @Test
    fun roundTripPreservesSettingsAndFloorRecords() {
        val document = RunSplitsDocument(
            settings = RunSplitsSettings(
                enabled = false,
                showPbDelta = false,
                showSumOfBest = true,
                compact = true,
                hudX = 10,
                hudY = 20,
                hudScale = 0.75f,
            ),
            floors = mapOf(
                "F7" to FloorRecord(
                    bestTotalMs = 700_000L,
                    pbSplits = mapOf("maxor" to 300_000L),
                    bestSegments = mapOf("maxor" to 200_000L),
                    history = listOf(RunRecord(1_700_000_000_000L, 700_000L, isPb = true)),
                ),
            ),
        )

        val decoded = RunSplitsCodec.decode(RunSplitsCodec.encode(document))

        assertFalse(decoded.malformed)
        assertEquals(document, decoded.document)
    }

    @Test
    fun malformedRootReturnsDefaultsAndFlagsFile() {
        val notJson = RunSplitsCodec.decode("{not json")
        assertTrue(notJson.malformed)
        assertEquals(RunSplitsDocument(), notJson.document)

        val wrongRoot = RunSplitsCodec.decode("[1, 2, 3]")
        assertTrue(wrongRoot.malformed)
        assertEquals(RunSplitsDocument(), wrongRoot.document)
    }

    @Test
    fun badFieldsFallBackPerField() {
        val text = """{"settings":{"hudX":"abc","enabled":"yes","hudScale":99,"compact":true}}"""

        val decoded = RunSplitsCodec.decode(text)

        assertFalse(decoded.malformed)
        val settings = decoded.document.settings
        assertEquals(4, settings.hudX)
        assertTrue(settings.enabled)
        assertEquals(3.0f, settings.hudScale)
        assertTrue(settings.compact)
    }

    @Test
    fun negativeTimesAreDropped() {
        val text = """{"floors":{"F1":{"bestTotalMs":-5,"pbSplits":{"bonzo":-1,"livid":10}}}}"""

        val record = RunSplitsCodec.decode(text).document.floors["F1"]!!

        assertEquals(null, record.bestTotalMs)
        assertEquals(mapOf("livid" to 10L), record.pbSplits)
    }

    @Test
    fun decodedHistoryIsBounded() {
        val runs = (0 until 25).joinToString(",") { """{"endedAtEpochMs":$it,"totalMs":1000,"isPb":false}""" }
        val text = """{"floors":{"F2":{"history":[$runs]}}}"""

        val history = RunSplitsCodec.decode(text).document.floors["F2"]!!.history

        assertEquals(PbLedger.HISTORY_LIMIT, history.size)
    }
}
