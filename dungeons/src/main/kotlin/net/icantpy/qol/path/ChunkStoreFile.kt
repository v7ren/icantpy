package net.icantpy.qol.path

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** One cached chunk column: section Y to flag bytes. The byte arrays are shared and must not be modified. */
data class ChunkColumn(val chunkX: Int, val chunkZ: Int, val sections: Map<Int, ByteArray>)

/**
 * Pure on-disk format for [ChunkStore] contents (java.io and java.util.zip only, no Minecraft types).
 *
 * File layout, inside one gzip stream, all integers big-endian:
 * magic "ICWC", format version (int, currently 1), chunk count (int), then per chunk (least recently used first):
 * chunkX (int), chunkZ (int), section count (int), then per section: sectionY (int), flag (byte), and when the flag is
 * [SECTION_RAW] the 4096 flag bytes. Flag [SECTION_ALL_AIR] stands for [ALL_AIR] and carries no data.
 *
 * Loading never throws for bad content: [LoadResult.Corrupt] is returned and the file is renamed to `.bad`.
 */
object ChunkStoreFile {
    const val DIRECTORY_NAME: String = "icantpy-worldcache"
    const val FORMAT_VERSION: Int = 1
    const val FILE_SUFFIX: String = ".bin.gz"

    /** Shared all-air section. Loaded all-air sections reuse this instance; it is never written to. */
    val ALL_AIR: ByteArray = ByteArray(ChunkStore.SECTION_SIZE) { ChunkStore.PASSABLE.toByte() }

    private val MAGIC_BYTES: ByteArray = "ICWC".toByteArray(Charsets.US_ASCII)
    private val DASH_RUN = Regex("-{2,}")
    private const val SECTION_RAW = 0
    private const val SECTION_ALL_AIR = 1
    private const val MAX_CHUNKS_IN_FILE = 1 shl 16
    private const val MAX_SECTIONS_PER_CHUNK = 64
    private const val MAX_KEY_LENGTH = 96
    private const val BUFFER_BYTES = 1 shl 16

    sealed interface LoadResult {
        data class Loaded(val chunks: List<ChunkColumn>) : LoadResult
        object Missing : LoadResult
        data class Corrupt(val reason: String) : LoadResult
    }

    /**
     * Reduces [raw] to lowercase [a-z0-9_-] so it is safe as a file name. Runs of dashes collapse; empty input gives
     * "unknown".
     */
    fun sanitizeKey(raw: String): String {
        val lowered = raw.lowercase(Locale.ROOT)
        val mapped = buildString(lowered.length) {
            for (c in lowered) append(if (c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-') c else '-')
        }
        val compact = mapped.replace(DASH_RUN, "-").trim('-').take(MAX_KEY_LENGTH).trimEnd('-')
        return compact.ifEmpty { "unknown" }
    }

    /** File for [key] inside [directory]. The key is sanitised again, so it cannot escape the directory. */
    fun fileFor(directory: Path, key: String): Path = directory.resolve(sanitizeKey(key) + FILE_SUFFIX)

    /** Writes [chunks] gzip-compressed to [output]. Closes [output]. */
    fun encode(chunks: List<ChunkColumn>, output: OutputStream) {
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(output, BUFFER_BYTES), BUFFER_BYTES)).use { data ->
            writeBody(data, chunks)
        }
    }

    /**
     * Reads a stream written by [encode], keeping only the most recent [maxChunks] columns (the file lists least
     * recently used first). Never throws for malformed content; returns [LoadResult.Corrupt] instead.
     */
    fun decode(input: InputStream, maxChunks: Int): LoadResult {
        require(maxChunks >= 1) { "maxChunks must be at least 1, was $maxChunks" }
        return try {
            DataInputStream(BufferedInputStream(GZIPInputStream(input, BUFFER_BYTES), BUFFER_BYTES)).use { data ->
                readBody(data, maxChunks)
            }
        } catch (exception: Exception) {
            LoadResult.Corrupt(exception.message ?: exception.javaClass.simpleName)
        }
    }

    /** Writes to `<name>.tmp`, then moves it over [path]. Falls back to a plain replace when atomic move is refused. */
    fun save(path: Path, chunks: List<ChunkColumn>) {
        path.parent?.let { Files.createDirectories(it) }
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        Files.newOutputStream(tmp).use { encode(chunks, it) }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Loads [path]. A missing file gives [LoadResult.Missing]. A file that fails to decode is renamed to
     * `<name>.bad` and gives [LoadResult.Corrupt]. I/O errors opening the file are thrown to the caller.
     */
    fun load(path: Path, maxChunks: Int): LoadResult {
        if (!Files.isRegularFile(path)) return LoadResult.Missing
        val result = Files.newInputStream(path).use { decode(it, maxChunks) }
        if (result is LoadResult.Corrupt) quarantine(path)
        return result
    }

    private fun quarantine(path: Path) {
        try {
            Files.move(path, path.resolveSibling("${path.fileName}.bad"), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            // Leaving the bad file in place is acceptable; the caller already reports the corruption.
        }
    }

    private fun writeBody(data: DataOutputStream, chunks: List<ChunkColumn>) {
        data.write(MAGIC_BYTES)
        data.writeInt(FORMAT_VERSION)
        data.writeInt(chunks.size)
        for (column in chunks) {
            data.writeInt(column.chunkX)
            data.writeInt(column.chunkZ)
            data.writeInt(column.sections.size)
            for ((sectionY, bytes) in column.sections) {
                require(bytes.size == ChunkStore.SECTION_SIZE) { "section must be ${ChunkStore.SECTION_SIZE} bytes" }
                data.writeInt(sectionY)
                if (bytes === ALL_AIR || bytes.contentEquals(ALL_AIR)) {
                    data.writeByte(SECTION_ALL_AIR)
                } else {
                    data.writeByte(SECTION_RAW)
                    data.write(bytes)
                }
            }
        }
    }

    private fun readBody(data: DataInputStream, maxChunks: Int): LoadResult {
        val magic = ByteArray(MAGIC_BYTES.size)
        data.readFully(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) throw IOException("not an icantpy world cache file")
        val version = data.readInt()
        if (version != FORMAT_VERSION) throw IOException("unsupported world cache version $version")
        val count = data.readInt()
        if (count !in 0..MAX_CHUNKS_IN_FILE) throw IOException("bad chunk count $count")
        val kept = ArrayDeque<ChunkColumn>()
        repeat(count) {
            val column = readColumn(data)
            if (kept.size == maxChunks) kept.removeFirst()
            kept.addLast(column)
        }
        if (data.read() != -1) throw IOException("trailing data after the last chunk")
        return LoadResult.Loaded(kept.toList())
    }

    private fun readColumn(data: DataInputStream): ChunkColumn {
        val chunkX = data.readInt()
        val chunkZ = data.readInt()
        val sectionCount = data.readInt()
        if (sectionCount !in 0..MAX_SECTIONS_PER_CHUNK) throw IOException("bad section count $sectionCount")
        val sections = HashMap<Int, ByteArray>(sectionCount * 2)
        repeat(sectionCount) {
            val sectionY = data.readInt()
            if (sections.containsKey(sectionY)) throw IOException("duplicate section $sectionY")
            sections[sectionY] = readSection(data)
        }
        return ChunkColumn(chunkX, chunkZ, sections)
    }

    private fun readSection(data: DataInputStream): ByteArray =
        when (val flag = data.readUnsignedByte()) {
            SECTION_ALL_AIR -> ALL_AIR
            SECTION_RAW -> ByteArray(ChunkStore.SECTION_SIZE).also { data.readFully(it) }
            else -> throw IOException("bad section flag $flag")
        }
}
