package net.icantpy.qol.path

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChunkStoreTest {
    private fun section(fill: Int = 0): ByteArray = ByteArray(ChunkStore.SECTION_SIZE) { fill.toByte() }

    @Test
    fun storesFlagsPerBlockAndReadsThemBack() {
        val store = ChunkStore(8)
        val floor = section()
        floor[ChunkStore.index(3, 5, 7)] = (ChunkStore.PASSABLE or ChunkStore.SOLID_FLOOR).toByte()
        store.put(2, -1, mapOf(0 to floor))

        assertEquals(ChunkStore.PASSABLE or ChunkStore.SOLID_FLOOR, store.flags(2 * 16 + 3, 5, -16 + 7))
        assertEquals(ChunkStore.BLOCKED, store.flags(2 * 16 + 4, 5, -16 + 7))
    }

    @Test
    fun negativeCoordinatesMapToTheirOwnChunk() {
        val store = ChunkStore(8)
        val bytes = section(ChunkStore.WATER)
        store.put(-1, -1, mapOf(-1 to bytes))

        // x = -1 is local column 15 of chunk -1; y = -1 is local row 15 of section -1.
        assertEquals(ChunkStore.WATER, store.flags(-1, -1, -1))
        assertEquals(ChunkStore.WATER, store.flags(-16, -16, -16))
        assertNull(store.flags(0, -1, -1))
    }

    @Test
    fun indexUsesLowBitsForAllCoordinates() {
        assertEquals(0, ChunkStore.index(0, 0, 0))
        assertEquals(15, ChunkStore.index(15, 0, 0))
        assertEquals(16, ChunkStore.index(0, 0, 1))
        assertEquals(256, ChunkStore.index(0, 1, 0))
        assertEquals(ChunkStore.index(3, 5, 7), ChunkStore.index(-13, 21, -9))
    }

    @Test
    fun unknownChunkReturnsNull() {
        val store = ChunkStore(8)
        store.put(0, 0, mapOf(0 to section(ChunkStore.PASSABLE)))

        assertNull(store.flags(16, 0, 0))
        assertFalse(store.contains(1, 0))
        assertTrue(store.contains(0, 0))
    }

    @Test
    fun missingSectionInCachedChunkIsBlocked() {
        val store = ChunkStore(8)
        store.put(0, 0, mapOf(0 to section(ChunkStore.PASSABLE)))

        assertEquals(ChunkStore.BLOCKED, store.flags(0, 16, 0))
        assertEquals(ChunkStore.BLOCKED, store.flags(0, -1, 0))
    }

    @Test
    fun putReplacesAnExistingChunkWithoutGrowingSize() {
        val store = ChunkStore(8)
        store.put(4, 4, mapOf(0 to section(ChunkStore.PASSABLE)))
        store.put(4, 4, mapOf(0 to section(ChunkStore.WATER)))

        assertEquals(1, store.size)
        assertEquals(ChunkStore.WATER, store.flags(64, 0, 64))
    }

    @Test
    fun rejectsSectionsOfWrongSize() {
        val store = ChunkStore(8)
        assertFailsWith<IllegalArgumentException> {
            store.put(0, 0, mapOf(0 to ByteArray(10)))
        }
    }

    @Test
    fun evictsLeastRecentlyUsedChunkBeyondCapacity() {
        val store = ChunkStore(2)
        store.put(0, 0, mapOf(0 to section(ChunkStore.PASSABLE)))
        store.put(1, 0, mapOf(0 to section(ChunkStore.PASSABLE)))
        // Reading chunk (0, 0) makes chunk (1, 0) the least recently used one.
        assertEquals(ChunkStore.PASSABLE, store.flags(0, 0, 0))

        store.put(2, 0, mapOf(0 to section(ChunkStore.PASSABLE)))

        assertEquals(2, store.size)
        assertTrue(store.contains(0, 0))
        assertFalse(store.contains(1, 0))
        assertTrue(store.contains(2, 0))
    }

    @Test
    fun containsDoesNotCountAsUse() {
        val store = ChunkStore(2)
        store.put(0, 0, mapOf(0 to section()))
        store.put(1, 0, mapOf(0 to section()))
        assertTrue(store.contains(0, 0))

        store.put(2, 0, mapOf(0 to section()))

        assertFalse(store.contains(0, 0))
        assertTrue(store.contains(1, 0))
    }

    @Test
    fun clearRemovesEverything() {
        val store = ChunkStore(8)
        store.put(0, 0, mapOf(0 to section(ChunkStore.PASSABLE)))
        store.put(-3, 9, mapOf(2 to section(ChunkStore.WATER)))

        store.clear()

        assertEquals(0, store.size)
        assertNull(store.flags(0, 0, 0))
        assertNull(store.flags(-48, 40, 144))
    }

    @Test
    fun rejectsNonPositiveCapacity() {
        assertFailsWith<IllegalArgumentException> { ChunkStore(0) }
    }

    @Test
    fun revisionChangesOnlyWhenContentsChange() {
        val store = ChunkStore(8)
        assertEquals(0L, store.revision)

        store.put(0, 0, mapOf(0 to section()))
        val afterPut = store.revision
        assertTrue(afterPut > 0L)

        store.flags(0, 0, 0)
        store.contains(0, 0)
        store.sectionsOf(0, 0)
        assertEquals(afterPut, store.revision)

        store.clear()
        assertTrue(store.revision > afterPut)
        val afterClear = store.revision
        store.clear()
        assertEquals(afterClear, store.revision)
    }

    @Test
    fun snapshotListsLeastRecentlyUsedFirstAndSharesArrays() {
        val store = ChunkStore(8)
        val first = section(ChunkStore.PASSABLE)
        store.put(0, 0, mapOf(0 to first))
        store.put(5, -5, mapOf(1 to section(ChunkStore.WATER)))
        // Reading chunk (0, 0) makes it the most recently used one.
        store.flags(0, 0, 0)

        val snapshot = store.snapshot()

        assertEquals(listOf(5 to -5, 0 to 0), snapshot.map { it.chunkX to it.chunkZ })
        assertTrue(snapshot.last().sections[0] === first)
    }

    @Test
    fun sectionsOfReturnsNullForUncachedColumn() {
        val store = ChunkStore(8)
        store.put(2, 3, mapOf(4 to section(ChunkStore.SOLID_FLOOR)))

        assertNull(store.sectionsOf(0, 0))
        assertEquals(setOf(4), store.sectionsOf(2, 3)?.keys)
    }
}
