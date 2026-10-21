package net.icantpy.qol.path

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChunkStoreFileTest {
    /** Seeded pseudo-random flags: they do not compress, so size comparisons stay meaningful. */
    private fun raw(seed: Int): ByteArray = ByteArray(ChunkStore.SECTION_SIZE).also { Random(seed.toLong()).nextBytes(it) }

    private fun encodeToBytes(chunks: List<ChunkColumn>): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkStoreFile.encode(chunks, out)
        return out.toByteArray()
    }

    private fun decodeBytes(bytes: ByteArray, maxChunks: Int = 64): ChunkStoreFile.LoadResult =
        ChunkStoreFile.decode(ByteArrayInputStream(bytes), maxChunks)

    /** Gzip of arbitrary body bytes, for building malformed files. */
    private fun gzip(body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(body) }
        return out.toByteArray()
    }

    private fun header(version: Int, count: Int): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write("ICWC".toByteArray(Charsets.US_ASCII))
            data.writeInt(version)
            data.writeInt(count)
        }
        return out.toByteArray()
    }

    @Test
    fun roundTripKeepsContentsIncludingNegativeCoordinatesAndAirSections() {
        val floor = raw(3)
        val chunks = listOf(
            ChunkColumn(-7, -1, mapOf(-4 to floor, 0 to ChunkStoreFile.ALL_AIR)),
            ChunkColumn(0, 12, mapOf(3 to ChunkStoreFile.ALL_AIR, 4 to raw(9)))
        )

        val loaded = assertIs<ChunkStoreFile.LoadResult.Loaded>(decodeBytes(encodeToBytes(chunks)))

        assertEquals(listOf(-7 to -1, 0 to 12), loaded.chunks.map { it.chunkX to it.chunkZ })
        assertEquals(setOf(-4, 0), loaded.chunks[0].sections.keys)
        assertTrue(loaded.chunks[0].sections.getValue(-4).contentEquals(floor))
        assertTrue(loaded.chunks[1].sections.getValue(4).contentEquals(raw(9)))
    }

    @Test
    fun allAirSectionsLoadAsTheSharedArrayAndCostOneByte() {
        val airOnly = listOf(ChunkColumn(1, 1, mapOf(0 to ChunkStoreFile.ALL_AIR)))
        val rawOnly = listOf(ChunkColumn(1, 1, mapOf(0 to raw(1))))

        val loaded = assertIs<ChunkStoreFile.LoadResult.Loaded>(decodeBytes(encodeToBytes(airOnly)))
        assertSame(ChunkStoreFile.ALL_AIR, loaded.chunks.single().sections.getValue(0))
        assertTrue(encodeToBytes(rawOnly).size - encodeToBytes(airOnly).size > ChunkStore.SECTION_SIZE / 2)
    }

    @Test
    fun emptyFileRoundTripsToNoChunks() {
        val loaded = assertIs<ChunkStoreFile.LoadResult.Loaded>(decodeBytes(encodeToBytes(emptyList())))
        assertTrue(loaded.chunks.isEmpty())
    }

    @Test
    fun corruptOrUnknownContentGivesCorruptWithoutThrowing() {
        val valid = encodeToBytes(listOf(ChunkColumn(0, 0, mapOf(0 to raw(1)))))

        assertIs<ChunkStoreFile.LoadResult.Corrupt>(decodeBytes(ByteArray(0)))
        assertIs<ChunkStoreFile.LoadResult.Corrupt>(decodeBytes("plain text".toByteArray()))
        assertIs<ChunkStoreFile.LoadResult.Corrupt>(decodeBytes(valid.copyOf(valid.size - 20)))
        assertIs<ChunkStoreFile.LoadResult.Corrupt>(decodeBytes(gzip(header(version = 2, count = 0))))
        assertIs<ChunkStoreFile.LoadResult.Corrupt>(decodeBytes(gzip(header(version = 1, count = -1))))
        assertIs<ChunkStoreFile.LoadResult.Corrupt>(decodeBytes(gzip(header(version = 1, count = 1))))
    }

    @Test
    fun badSectionFlagGivesCorrupt() {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write("ICWC".toByteArray(Charsets.US_ASCII))
            data.writeInt(1)
            data.writeInt(1)
            data.writeInt(0)
            data.writeInt(0)
            data.writeInt(1)
            data.writeInt(0)
            data.writeByte(7)
        }

        assertIs<ChunkStoreFile.LoadResult.Corrupt>(decodeBytes(gzip(out.toByteArray())))
    }

    @Test
    fun loadKeepsOnlyTheMostRecentChunksWhenLimitIsSmaller() {
        val chunks = (0 until 5).map { ChunkColumn(it, -it, mapOf(0 to ChunkStoreFile.ALL_AIR)) }

        val loaded = assertIs<ChunkStoreFile.LoadResult.Loaded>(decodeBytes(encodeToBytes(chunks), maxChunks = 2))

        assertEquals(listOf(3 to -3, 4 to -4), loaded.chunks.map { it.chunkX to it.chunkZ })
    }

    @Test
    fun saveThenLoadThroughFileRoundTrips() {
        val directory = Files.createTempDirectory("icwc-test")
        try {
            val path = ChunkStoreFile.fileFor(directory, "skyblock-hub__minecraft-overworld")
            val chunks = listOf(ChunkColumn(-2, 5, mapOf(-1 to raw(4), 2 to ChunkStoreFile.ALL_AIR)))

            ChunkStoreFile.save(path, chunks)
            val loaded = assertIs<ChunkStoreFile.LoadResult.Loaded>(ChunkStoreFile.load(path, maxChunks = 16))

            assertEquals(-2 to 5, loaded.chunks.single().let { it.chunkX to it.chunkZ })
            assertTrue(loaded.chunks.single().sections.getValue(-1).contentEquals(raw(4)))
            assertFalse(Files.exists(path.resolveSibling("${path.fileName}.tmp")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingFileIsMissing() {
        val directory = Files.createTempDirectory("icwc-test")
        try {
            assertIs<ChunkStoreFile.LoadResult.Missing>(
                ChunkStoreFile.load(directory.resolve("nothing${ChunkStoreFile.FILE_SUFFIX}"), maxChunks = 4)
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun corruptFileIsQuarantinedAsBadAndNotRethrown() {
        val directory = Files.createTempDirectory("icwc-test")
        try {
            val path: Path = directory.resolve("island${ChunkStoreFile.FILE_SUFFIX}")
            Files.write(path, "garbage".toByteArray())

            assertIs<ChunkStoreFile.LoadResult.Corrupt>(ChunkStoreFile.load(path, maxChunks = 4))

            assertFalse(Files.exists(path))
            assertTrue(Files.exists(path.resolveSibling("${path.fileName}.bad")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun sanitizeKeyKeepsOnlySafeFileCharacters() {
        assertEquals("mod-v7ren-com-25565", ChunkStoreFile.sanitizeKey("mod.v7ren.com:25565"))
        assertEquals("hub-lobby-x", ChunkStoreFile.sanitizeKey("Hub Lobby/../x"))
        assertEquals("minecraft-overworld", ChunkStoreFile.sanitizeKey("minecraft:overworld"))
        assertEquals("unknown", ChunkStoreFile.sanitizeKey("...///"))
    }

    @Test
    fun fileForStaysInsideDirectory() {
        val directory = Path.of("cache-dir")
        val path = ChunkStoreFile.fileFor(directory, "../../escape")

        assertEquals(directory, path.parent)
        assertEquals("escape${ChunkStoreFile.FILE_SUFFIX}", path.fileName.toString())
    }
}
