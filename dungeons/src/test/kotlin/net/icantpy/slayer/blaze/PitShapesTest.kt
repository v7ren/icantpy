package net.icantpy.slayer.blaze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitShapesTest {
    private fun cols(vararg xz: Pair<Int, Int>): Set<Long> = xz.map { (x, z) -> packXZ(x, z) }.toSet()

    private fun plus(cx: Int, cz: Int) = arrayOf(cx to cz, cx + 1 to cz, cx - 1 to cz, cx to cz + 1, cx to cz - 1)

    private fun groups(columns: Set<Long>): Set<Long> = HashSet<Long>().also { PitShapes.pitGroups(columns, it) }

    @Test
    fun fullPlusIsDetected() {
        assertEquals(cols(*plus(-287, -991)), groups(cols(*plus(-287, -991))))
    }

    @Test
    fun plusMissingArmsOnUnevenGroundIsDetected() {
        // Center with only two arms left (the others had no floor block).
        val partial = cols(0 to 0, 1 to 0, 0 to 1)
        assertEquals(partial, groups(partial))
    }

    @Test
    fun loneAndPairedBlocksAreIgnored() {
        assertTrue(groups(cols(10 to 10, 20 to 0, 21 to 0)).isEmpty())
    }

    @Test
    fun twoTouchingPlusesCountAsOneGroup() {
        val both = cols(*plus(0, 0), *plus(2, 1))
        assertEquals(both, groups(both))
    }

    @Test
    fun largeTerracottaAreaIsDecorationNotAPit() {
        val floor = HashSet<Long>()
        for (x in 0 until 5) for (z in 0 until 5) floor += packXZ(x, z)
        assertTrue(groups(floor).isEmpty())
    }

    @Test
    fun expandZeroRadiusKeepsOnlyTheColumns() {
        val out = HashSet<Long>()
        PitShapes.expand(cols(3 to 4), 0f, out)
        assertEquals(cols(3 to 4), out)
    }

    @Test
    fun expandRadiusOneAddsTheFourNeighbours() {
        val out = HashSet<Long>()
        PitShapes.expand(cols(0 to 0), 1f, out)
        assertEquals(cols(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1), out)
    }

    @Test
    fun expandRadiusTwoIsARoundDisk() {
        val out = HashSet<Long>()
        PitShapes.expand(cols(0 to 0), 2f, out)
        assertEquals(13, out.size)
        assertTrue(packXZ(2, 0) in out)
        assertTrue(packXZ(1, 1) in out)
        assertTrue(packXZ(2, 2) !in out)
    }

    @Test
    fun separateGroupsAreJudgedIndependently() {
        val floor = HashSet<Long>()
        for (x in 0 until 5) for (z in 0 until 5) floor += packXZ(x, z)
        val pit = cols(*plus(20, 20))
        assertEquals(pit, groups(floor + pit))
    }
}
