package net.icantpy.qol.lava

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LavaEspTest {
    @Test
    fun configDefaultsToTheUsersHeightAndClamps() {
        assertEquals(64, LavaEspConfig().minY)
        val parsed = LavaEspConfig.parse("""{"enabled":true,"minY":9999,"radiusChunks":0,"rgb":16711680}""")
        assertTrue(parsed.enabled)
        assertEquals(LavaEspConfig.MAX_Y_LIMIT, parsed.minY)
        assertEquals(LavaEspConfig.MIN_RADIUS, parsed.radiusChunks)
        assertEquals(0xFF0000, parsed.rgb)
        assertEquals(LavaEspConfig(), LavaEspConfig.parse("[]"))
    }

    @Test
    fun chunksAreListedNearestFirstInsideTheRadius() {
        val list = LavaEsp.chunksNear(0, 0, 2)
        assertEquals(LavaEsp.pack(0, 0), list.first())
        assertTrue(LavaEsp.pack(2, 2) !in list, "corner is outside a round radius")
        assertTrue(LavaEsp.pack(-2, 0) in list)
    }

    @Test
    fun nearestKeepsTheClosestBlocks() {
        val close = BlockPos.asLong(1, 70, 0)
        val far = BlockPos.asLong(40, 70, 0)
        val mid = BlockPos.asLong(10, 70, 0)
        val kept = LavaEsp.nearest(listOf(longArrayOf(far, close), longArrayOf(mid)), 0.0, 70.0, 0.0, 2)
        assertEquals(listOf(close, mid), kept.toList())
    }
}
