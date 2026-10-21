package net.icantpy.qol.path

/**
 * Pure (no Minecraft types) store of per-block walkability flags for chunk columns the client has already seen.
 *
 * Each column maps section Y to a [SECTION_SIZE] byte array, one flag byte per block. A flag of 0 means the block
 * is not passable, not a floor and not water, so it is the safe answer for unknown or missing data.
 * Missing data is reported differently by level: a chunk that is not cached gives null from [flags]; a cached chunk
 * with no entry for a section gives [BLOCKED]. Capture stores every section of a chunk (empty sections as shared
 * all-air arrays), so a missing section means it lies outside the captured height range.
 *
 * Columns are evicted least-recently-used once more than [maxChunks] are stored. Reads through [flags] count as use.
 * Not thread-safe; the owner calls it from the client thread. Stored maps and byte arrays are never modified after
 * [put], so [snapshot] results can be written from another thread.
 */
class ChunkStore(maxChunks: Int) {
    init {
        require(maxChunks >= 1) { "maxChunks must be at least 1, was $maxChunks" }
    }

    val maxChunks: Int = maxChunks

    private val columns = object : LinkedHashMap<Long, Map<Int, ByteArray>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Map<Int, ByteArray>>?): Boolean =
            size > this@ChunkStore.maxChunks
    }

    /** Number of cached chunk columns. */
    val size: Int get() = columns.size

    /**
     * Increases on every change to the stored flags ([put] and a [clear] that removed something). Compare with a
     * saved value to tell whether the contents differ from what was last written to disk.
     */
    var revision: Long = 0L
        private set

    /** True when the column is cached. Does not count as a use for eviction. */
    fun contains(chunkX: Int, chunkZ: Int): Boolean = columns.containsKey(pack(chunkX, chunkZ))

    /** Sections of a cached column, or null when it is not cached. Does not count as a use for eviction. */
    fun sectionsOf(chunkX: Int, chunkZ: Int): Map<Int, ByteArray>? = columns[pack(chunkX, chunkZ)]

    /**
     * Stores or replaces a chunk column. The map is copied; the byte arrays are taken over and must not be
     * modified afterwards.
     */
    fun put(chunkX: Int, chunkZ: Int, sections: Map<Int, ByteArray>) {
        for (bytes in sections.values) {
            require(bytes.size == SECTION_SIZE) { "section flags must be $SECTION_SIZE bytes, was ${bytes.size}" }
        }
        columns[pack(chunkX, chunkZ)] = HashMap(sections)
        revision++
    }

    /** Flags of the block at ([x], [y], [z]); null when its chunk is not cached. Works for negative coordinates. */
    fun flags(x: Int, y: Int, z: Int): Int? {
        val column = columns[pack(x shr 4, z shr 4)] ?: return null
        val section = column[y shr 4] ?: return BLOCKED
        return section[index(x, y, z)].toInt() and 0xFF
    }

    fun clear() {
        if (columns.isNotEmpty()) revision++
        columns.clear()
    }

    /**
     * Every cached column, least recently used first, so loading the result in order rebuilds the same eviction order.
     * The maps and byte arrays are shared with the store, not copied.
     */
    fun snapshot(): List<ChunkColumn> = columns.map { (key, sections) ->
        ChunkColumn(unpackChunkX(key), unpackChunkZ(key), sections)
    }

    companion object {
        /** The block may occupy the player's body. */
        const val PASSABLE: Int = 1

        /** The player can stand on the block. */
        const val SOLID_FLOOR: Int = 2

        /** The block is water. */
        const val WATER: Int = 4

        /** Flags for a block with no cached information. */
        const val BLOCKED: Int = 0

        /** Blocks per 16x16x16 section. */
        const val SECTION_SIZE: Int = 4096

        private const val INITIAL_CAPACITY = 64
        private const val LOAD_FACTOR = 0.75f

        /** Index of a block inside its section. Uses the low four bits, so negative coordinates map correctly. */
        fun index(x: Int, y: Int, z: Int): Int = (y and 15) * 256 + (z and 15) * 16 + (x and 15)

        private fun pack(chunkX: Int, chunkZ: Int): Long =
            (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)

        private fun unpackChunkX(key: Long): Int = (key shr 32).toInt()

        private fun unpackChunkZ(key: Long): Int = key.toInt()
    }
}
