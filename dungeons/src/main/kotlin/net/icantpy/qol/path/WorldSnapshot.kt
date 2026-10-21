package net.icantpy.qol.path

import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import net.icantpy.location.SkyblockIsland
import net.icantpy.location.SkyblockLocation
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Remembers walkability of chunks the client has loaded, so the pathfinder can plan through chunks that have since
 * unloaded. Ported from senura's WorldCache, which keeps a snapshot of every chunk it saw, and extended with disk
 * persistence so the cache survives payload reloads and game restarts.
 *
 * Capture runs in [tick] on the client thread. Each tick reads sections until about [TIME_BUDGET_NANOS] has passed
 * (always at least [MIN_SECTIONS_PER_TICK] non-empty sections when there is work). Uncached loaded chunks come first,
 * nearest to the player first; afterwards loaded cached chunks are refreshed round-robin. Empty sections are stored as
 * the shared all-air array without reads.
 *
 * Persistence: each world has one file, `<config>/icantpy-worldcache/<key>.bin.gz` (see [worldKey]). When the key
 * changes, the old contents are saved if dirty and the new key's file is loaded in the background. Saves run every
 * [SAVE_INTERVAL_NANOS] while dirty, on a single daemon thread that also performs loads, so file I/O never blocks
 * the client thread. [clear] saves synchronously (bounded wait) before dropping memory.
 *
 * Only the client thread may touch the fields of this object.
 */
object WorldSnapshot {
    /** Non-empty sections read in one tick at minimum, even past the time budget. */
    private const val MIN_SECTIONS_PER_TICK = 2

    /** Chunks finished in one tick at most, so an area of empty chunks cannot keep one tick busy. */
    private const val MAX_CHUNKS_PER_TICK = 64

    /** Time spent capturing per tick, beyond the minimum section count. 1.5 ms is about 3% of a 50 ms tick. */
    private const val TIME_BUDGET_NANOS = 1_500_000L

    /** Capture radius in chunks is the render distance clamped to this, so a capture square stays small. */
    private const val MAX_CAPTURE_RADIUS = 16

    private const val SAVE_INTERVAL_NANOS = 60_000_000_000L
    private const val KEY_CHECK_INTERVAL_TICKS = 20
    private const val SYNC_SAVE_TIMEOUT_SECONDS = 5L

    private val AIR_SECTION = ChunkStoreFile.ALL_AIR

    private var store = ChunkStore(NavigatorConfig.DEFAULT_CACHE_MAX_CHUNKS)
    private var job: CaptureJob? = null
    private var refreshCursor = 0
    private val scratch = BlockPos.MutableBlockPos()

    /** Chunk offsets from the player, nearest first. Stored as dx, dz pairs; rebuilt when the radius changes. */
    private var ringRadius = -1
    private var ringOffsets = IntArray(0)

    private var currentKey: String? = null
    private var keyLevel: ClientLevel? = null
    private var ticksSinceKeyCheck = 0

    /** True while on SkyBlock with the island not known yet; capture waits. */
    private var keyPending = false

    /** Increases whenever the key is opened or cleared; late load results from an older key are dropped. */
    private var generation = 0L
    private var savedRevision = 0L
    private var lastSaveNanos = 0L

    private var executor: ExecutorService? = null
    private val pendingLoad = AtomicReference<PendingLoad?>(null)
    private val saveFailed = AtomicBoolean(false)

    private class PendingLoad(val generation: Long, val chunks: List<ChunkColumn>)

    /** Number of chunk columns currently cached. */
    val chunkCount: Int get() = store.size

    /** Key of the world whose data is cached, or an empty string when none is open. */
    val worldKey: String get() = currentKey.orEmpty()

    /** True when the chunk at ([chunkX], [chunkZ]) is in the snapshot. */
    fun isCached(chunkX: Int, chunkZ: Int): Boolean = store.contains(chunkX, chunkZ)

    /** Captures sections of the chunk being read. Called every client tick; cheap when caching is off. */
    fun tick() {
        val config = Navigator.config()
        if (!config.cacheWorld) {
            if (currentKey != null || store.size > 0 || job != null) clear()
            return
        }
        pollAsyncResults()
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (level == null) {
            periodicSave()
            return
        }
        refreshKey(mc, level, config.cacheMaxChunks)
        periodicSave()
        val player = mc.player ?: return
        val radius = mc.options.effectiveRenderDistance.coerceIn(1, MAX_CAPTURE_RADIUS)
        val centerX = player.blockPosition().x shr 4
        val centerZ = player.blockPosition().z shr 4
        advance(level, centerX, centerZ, radius)
    }

    /** Saves the open world if it changed, then drops every cached chunk and closes the key. */
    fun clear() {
        val key = currentKey
        if (key != null) saveNow(key)
        store.clear()
        job = null
        refreshCursor = 0
        currentKey = null
        keyLevel = null
        ticksSinceKeyCheck = 0
        generation++
        pendingLoad.set(null)
        executor?.shutdown()
        executor = null
    }

    /**
     * Flag bits ([ChunkStore] constants) for the block at (x, y, z) from the snapshot; null when caching is off or
     * the chunk was never captured.
     */
    fun cachedFlags(x: Int, y: Int, z: Int): Int? {
        if (!Navigator.config().cacheWorld) return null
        return store.flags(x, y, z)
    }

    /** Opens the key for the current level when it changed, or resizes the store when the limit changed. */
    private fun refreshKey(mc: Minecraft, level: ClientLevel, maxChunks: Int) {
        val due = level !== keyLevel || ticksSinceKeyCheck >= KEY_CHECK_INTERVAL_TICKS
        if (due) {
            keyLevel = level
            ticksSinceKeyCheck = 0
            val key = keyFor(mc, level)
            // On SkyBlock with the island not known yet (e.g. mid lobby switch): hold, so nothing is captured into
            // the previous island's cache or a throwaway server key. Re-checked on the next key check.
            keyPending = key == null
            if (key != null && key != currentKey) {
                openKey(key, maxChunks)
                return
            }
        } else {
            ticksSinceKeyCheck++
        }
        if (keyPending) return
        val key = currentKey ?: return
        if (store.maxChunks != maxChunks) openKey(key, maxChunks)
    }

    /** Saves the previous key if dirty, empties memory, then loads [key]'s file in the background. */
    private fun openKey(key: String, maxChunks: Int) {
        currentKey?.let { if (store.revision != savedRevision) enqueueSnapshotSave(it) }
        if (store.maxChunks != maxChunks) store = ChunkStore(maxChunks)
        store.clear()
        job = null
        refreshCursor = 0
        currentKey = key
        generation++
        savedRevision = store.revision
        lastSaveNanos = System.nanoTime()
        queueLoad(key, generation, maxChunks)
    }

    /**
     * World key: the Skyblock island (shared by every server of that island, so all hub lobbies share one cache)
     * or the server / singleplayer world, then the dimension. Everything is sanitised to [a-z0-9_-].
     */
    private fun keyFor(mc: Minecraft, level: ClientLevel): String? {
        val dimension = ChunkStoreFile.sanitizeKey(level.dimension().identifier().toString())
        val snapshot = SkyblockLocation.snapshot
        if (snapshot.onSkyblock && snapshot.island == SkyblockIsland.UNKNOWN) return null
        val scope = skyblockScope() ?: serverScope(mc)
        return ChunkStoreFile.sanitizeKey("${scope}__$dimension")
    }

    private fun skyblockScope(): String? {
        val snapshot = SkyblockLocation.snapshot
        if (!snapshot.onSkyblock || snapshot.island == SkyblockIsland.UNKNOWN) return null
        return "skyblock-" + ChunkStoreFile.sanitizeKey(snapshot.island.tabName)
    }

    private fun serverScope(mc: Minecraft): String {
        val singleplayer = mc.singleplayerServer
        if (singleplayer != null) {
            val levelName = singleplayer.worldData?.levelName.orEmpty()
            return "singleplayer-" + ChunkStoreFile.sanitizeKey(levelName)
        }
        val ip = mc.currentServer?.ip
        if (!ip.isNullOrBlank()) return "server-" + ChunkStoreFile.sanitizeKey(ip)
        return "unknown"
    }

    private fun cacheDirectory(): Path =
        FabricLoader.getInstance().configDir.resolve(ChunkStoreFile.DIRECTORY_NAME)

    private fun pathFor(key: String): Path = ChunkStoreFile.fileFor(cacheDirectory(), key)

    /** Applies a finished background load to the open key, and records failed saves so they are retried. */
    private fun pollAsyncResults() {
        if (saveFailed.getAndSet(false)) savedRevision = -1L
        val pending = pendingLoad.getAndSet(null) ?: return
        if (pending.generation != generation) return
        val dirtyBeforeLoad = store.revision != savedRevision
        for (column in pending.chunks) {
            if (!store.contains(column.chunkX, column.chunkZ)) {
                store.put(column.chunkX, column.chunkZ, column.sections)
            }
        }
        // Loaded data matches the file, so it is clean unless something was captured before it arrived.
        if (!dirtyBeforeLoad) savedRevision = store.revision
    }

    private fun periodicSave() {
        val key = currentKey ?: return
        if (store.revision == savedRevision) return
        val now = System.nanoTime()
        if (now - lastSaveNanos < SAVE_INTERVAL_NANOS) return
        lastSaveNanos = now
        enqueueSnapshotSave(key)
    }

    /** Takes a shallow snapshot of the store on the client thread and writes it on the background thread. */
    private fun enqueueSnapshotSave(key: String) {
        val chunks = store.snapshot()
        savedRevision = store.revision
        val path = pathFor(key)
        writer().execute {
            try {
                ChunkStoreFile.save(path, chunks)
            } catch (exception: Exception) {
                saveFailed.set(true)
                Icantpy.LOGGER.warn("Failed to save world cache ${path.fileName}", exception)
            }
        }
    }

    /** Saves the store for [key] and waits (bounded) for the write, so nothing is lost on unload. */
    private fun saveNow(key: String) {
        if (store.revision == savedRevision) return
        val chunks = store.snapshot()
        savedRevision = store.revision
        val path = pathFor(key)
        try {
            writer().submit(Callable { ChunkStoreFile.save(path, chunks) })
                .get(SYNC_SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to save world cache ${path.fileName}", exception)
        }
    }

    private fun queueLoad(key: String, loadGeneration: Long, maxChunks: Int) {
        val path = pathFor(key)
        writer().execute {
            try {
                when (val result = ChunkStoreFile.load(path, maxChunks)) {
                    is ChunkStoreFile.LoadResult.Loaded ->
                        pendingLoad.set(PendingLoad(loadGeneration, result.chunks))
                    is ChunkStoreFile.LoadResult.Corrupt ->
                        Icantpy.LOGGER.warn("Ignored corrupt world cache ${path.fileName}: ${result.reason}")
                    ChunkStoreFile.LoadResult.Missing -> Unit
                }
            } catch (exception: Exception) {
                Icantpy.LOGGER.warn("Failed to read world cache ${path.fileName}", exception)
            }
        }
    }

    /** Single daemon thread for all file I/O, so saves and loads of one file never overlap. */
    private fun writer(): ExecutorService {
        executor?.let { return it }
        val created = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "icantpy-worldcache").apply { isDaemon = true }
        }
        executor = created
        return created
    }

    private fun advance(level: ClientLevel, centerX: Int, centerZ: Int, radius: Int) {
        val deadline = System.nanoTime() + TIME_BUDGET_NANOS
        var reads = 0
        var finished = 0
        while (true) {
            val active = currentJob(level, centerX, centerZ, radius) ?: return
            val sections = active.chunk.sections
            while (active.nextIndex < sections.size) {
                if (budgetSpent(reads, finished, deadline)) return
                val index = active.nextIndex
                val section = sections[index]
                val sectionY = active.chunk.getSectionYFromSectionIndex(index)
                if (section == null || section.hasOnlyAir()) {
                    active.sections[sectionY] = AIR_SECTION
                } else {
                    active.sections[sectionY] = readSection(level, active.cx, sectionY, active.cz, section)
                    reads++
                }
                active.nextIndex++
            }
            finishJob(active)
            finished++
        }
    }

    private fun budgetSpent(reads: Int, finished: Int, deadline: Long): Boolean =
        finished >= MAX_CHUNKS_PER_TICK || (reads >= MIN_SECTIONS_PER_TICK && System.nanoTime() >= deadline)

    /** Stores a finished chunk. A refresh that reads identical flags leaves the store clean. */
    private fun finishJob(active: CaptureJob) {
        job = null
        if (sameSections(store.sectionsOf(active.cx, active.cz), active.sections)) return
        store.put(active.cx, active.cz, active.sections)
    }

    private fun sameSections(old: Map<Int, ByteArray>?, new: Map<Int, ByteArray>): Boolean {
        if (old == null || old.size != new.size) return false
        return new.all { (sectionY, bytes) ->
            val previous = old[sectionY]
            previous != null && (previous === bytes || previous.contentEquals(bytes))
        }
    }

    /** The job in progress, if its chunk is still the loaded one; otherwise the next chunk to capture. */
    private fun currentJob(level: ClientLevel, centerX: Int, centerZ: Int, radius: Int): CaptureJob? {
        val existing = job
        if (existing != null) {
            if (level.chunkSource.getChunkNow(existing.cx, existing.cz) === existing.chunk) return existing
            job = null
        }
        return findTarget(level, centerX, centerZ, radius)?.also { job = it }
    }

    /**
     * Pass 0 takes the nearest loaded chunk that is not cached. Pass 1 takes the next loaded cached chunk after
     * [refreshCursor] in distance order, so refreshes rotate through the square.
     */
    private fun findTarget(level: ClientLevel, centerX: Int, centerZ: Int, radius: Int): CaptureJob? {
        val offsets = ringOffsets(radius)
        val total = offsets.size / 2
        for (slot in 0 until total) {
            val cx = centerX + offsets[2 * slot]
            val cz = centerZ + offsets[2 * slot + 1]
            if (store.contains(cx, cz)) continue
            val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
            return CaptureJob(cx, cz, chunk)
        }
        for (step in 0 until total) {
            val slot = (refreshCursor + step) % total
            val cx = centerX + offsets[2 * slot]
            val cz = centerZ + offsets[2 * slot + 1]
            if (!store.contains(cx, cz)) continue
            val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
            refreshCursor = (slot + 1) % total
            return CaptureJob(cx, cz, chunk)
        }
        return null
    }

    /** Chunk offsets inside [radius], sorted by squared distance from the centre. Rebuilt only when the radius changes. */
    private fun ringOffsets(radius: Int): IntArray {
        if (radius == ringRadius) return ringOffsets
        val pairs = ArrayList<IntArray>()
        for (dz in -radius..radius) {
            for (dx in -radius..radius) pairs.add(intArrayOf(dx, dz))
        }
        pairs.sortWith(compareBy<IntArray>({ it[0] * it[0] + it[1] * it[1] }, { it[1] }, { it[0] }))
        val flat = IntArray(pairs.size * 2)
        for ((i, pair) in pairs.withIndex()) {
            flat[2 * i] = pair[0]
            flat[2 * i + 1] = pair[1]
        }
        ringRadius = radius
        ringOffsets = flat
        return flat
    }

    /** Reads one non-empty section into flag bytes using the same rules as [WorldBlockGrid]. */
    private fun readSection(level: ClientLevel, chunkX: Int, sectionY: Int, chunkZ: Int, section: LevelChunkSection): ByteArray {
        val flags = ByteArray(ChunkStore.SECTION_SIZE)
        val baseX = chunkX shl 4
        val baseY = sectionY shl 4
        val baseZ = chunkZ shl 4
        for (y in 0 until 16) {
            for (z in 0 until 16) {
                for (x in 0 until 16) {
                    val state = section.getBlockState(x, y, z)
                    scratch.set(baseX + x, baseY + y, baseZ + z)
                    flags[ChunkStore.index(x, y, z)] = WorldBlockGrid.cellFlags(level, scratch, state).toByte()
                }
            }
        }
        return flags
    }

    /** A chunk being read over several ticks. [nextIndex] is the next section to read. */
    private class CaptureJob(val cx: Int, val cz: Int, val chunk: LevelChunk) {
        val sections = HashMap<Int, ByteArray>()
        var nextIndex = 0
    }
}
