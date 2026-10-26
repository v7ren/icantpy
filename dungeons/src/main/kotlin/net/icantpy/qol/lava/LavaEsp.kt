package net.icantpy.qol.lava

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB

/**
 * Highlights lava above a chosen height in the chunks the server has already sent. It reads the client's own
 * loaded chunks a few per tick (nothing is requested or sent) and draws outlines with the world gizmos.
 */
object LavaEsp {
    private const val CHUNKS_PER_TICK = 3
    private const val PASS_INTERVAL_TICKS = 40
    private const val REBUILD_INTERVAL_TICKS = 10
    private const val MAX_DRAWN = 1_500
    private const val STROKE_WIDTH = 1.6f
    private const val FILL_ALPHA = 0x30000000
    private const val OUTLINE_ALPHA = 0xFF000000.toInt()

    @Volatile
    var config: LavaEspConfig = LavaEspConfig()
        private set

    /** Lava block positions per chunk, packed with [ChunkPos.asLong]. */
    private val byChunk = HashMap<Long, LongArray>()
    private val queue = ArrayDeque<Long>()
    private var ticks = 0L
    private var nextPassTick = 0L
    private var lastRebuildTick = 0L
    private var dirty = false

    @Volatile
    private var drawn: LongArray = LongArray(0)

    fun load() {
        config = LavaEspStore.load()
        clear()
    }

    fun reset() = clear()

    fun edit(change: (LavaEspConfig) -> LavaEspConfig) {
        val next = change(config)
        if (next == config) return
        val rescan = next.minY != config.minY || next.radiusChunks != config.radiusChunks || next.surfaceOnly != config.surfaceOnly
        config = next
        LavaEspStore.save(next)
        if (!next.enabled || rescan) clear()
    }

    fun tick() {
        val c = config
        if (!c.enabled) {
            if (byChunk.isNotEmpty() || drawn.isNotEmpty()) clear()
            return
        }
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null) {
            clear()
            return
        }
        ticks++
        val centerX = player.blockPosition().x shr 4
        val centerZ = player.blockPosition().z shr 4
        if (ticks >= nextPassTick && queue.isEmpty()) {
            nextPassTick = ticks + PASS_INTERVAL_TICKS
            queue.addAll(chunksNear(centerX, centerZ, c.radiusChunks))
            val keep = HashSet<Long>(queue)
            if (byChunk.keys.retainAll(keep)) dirty = true
        }
        val source = level.chunkSource
        repeat(CHUNKS_PER_TICK) {
            val packed = queue.removeFirstOrNull() ?: return@repeat
            val cx = unpackX(packed)
            val cz = unpackZ(packed)
            val chunk = source.getChunk(cx, cz, ChunkStatus.FULL, false)
            val found = if (chunk == null) LongArray(0) else scan(chunk, c.minY, c.surfaceOnly)
            val before = byChunk[packed]
            if (found.isEmpty()) {
                if (before != null) {
                    byChunk.remove(packed)
                    dirty = true
                }
            } else if (before == null || !before.contentEquals(found)) {
                byChunk[packed] = found
                dirty = true
            }
        }
        if (dirty && ticks - lastRebuildTick >= REBUILD_INTERVAL_TICKS) {
            lastRebuildTick = ticks
            dirty = false
            drawn = nearest(byChunk.values, player.x, player.y, player.z, MAX_DRAWN)
        }
    }

    fun renderWorld() {
        val c = config
        if (!c.enabled) return
        val list = drawn
        if (list.isEmpty()) return
        val onTop = c.throughWalls
        val outline = OUTLINE_ALPHA or c.rgb
        val style = GizmoStyle.strokeAndFill(outline, STROKE_WIDTH, FILL_ALPHA or c.rgb)
        for (packed in list) {
            val x = BlockPos.getX(packed).toDouble()
            val y = BlockPos.getY(packed).toDouble()
            val z = BlockPos.getZ(packed).toDouble()
            val gizmo = Gizmos.cuboid(AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0), style)
            if (onTop) gizmo.setAlwaysOnTop()
        }
    }

    /** Number of highlighted blocks, for the settings page. */
    fun count(): Int = drawn.size

    /** Lava blocks above [minY] in [chunk], as packed block positions. */
    private fun scan(chunk: ChunkAccess, minY: Int, surfaceOnly: Boolean): LongArray {
        val out = ArrayList<Long>()
        val origin = chunk.pos
        val sections = chunk.sections
        val cursor = BlockPos.MutableBlockPos()
        for (index in sections.indices) {
            val baseY = chunk.minY + index * SECTION_SIZE
            if (baseY + SECTION_SIZE - 1 <= minY) continue
            val section = sections[index]
            if (section.hasOnlyAir() || !section.maybeHas { it.`is`(Blocks.LAVA) }) continue
            for (ly in 0 until SECTION_SIZE) {
                val y = baseY + ly
                if (y <= minY) continue
                for (lz in 0 until SECTION_SIZE) {
                    for (lx in 0 until SECTION_SIZE) {
                        if (!section.getBlockState(lx, ly, lz).`is`(Blocks.LAVA)) continue
                        val x = origin.minBlockX + lx
                        val z = origin.minBlockZ + lz
                        if (surfaceOnly && chunk.getBlockState(cursor.set(x, y + 1, z)).`is`(Blocks.LAVA)) continue
                        out += BlockPos.asLong(x, y, z)
                    }
                }
            }
        }
        return out.toLongArray()
    }

    private fun clear() {
        byChunk.clear()
        queue.clear()
        drawn = LongArray(0)
        dirty = false
        nextPassTick = 0L
    }

    private const val SECTION_SIZE = 16

    private fun unpackX(packed: Long): Int = (packed and 0xFFFFFFFFL).toInt()

    private fun unpackZ(packed: Long): Int = (packed ushr 32).toInt()

    /** Chunk coordinates within [radius] of the centre, nearest first, packed x low and z high. */
    internal fun chunksNear(centerX: Int, centerZ: Int, radius: Int): List<Long> {
        val result = ArrayList<Pair<Int, Long>>()
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                if (dx * dx + dz * dz > radius * radius) continue
                result += (dx * dx + dz * dz) to pack(centerX + dx, centerZ + dz)
            }
        }
        return result.sortedBy { it.first }.map { it.second }
    }

    internal fun pack(chunkX: Int, chunkZ: Int): Long = (chunkX.toLong() and 0xFFFFFFFFL) or (chunkZ.toLong() shl 32)

    /** The [limit] packed block positions closest to the point, nearest first. */
    internal fun nearest(arrays: Collection<LongArray>, x: Double, y: Double, z: Double, limit: Int): LongArray {
        var total = 0
        for (array in arrays) total += array.size
        if (total == 0) return LongArray(0)
        val all = LongArray(total)
        var at = 0
        for (array in arrays) {
            array.copyInto(all, at)
            at += array.size
        }
        if (total <= limit) return all
        return all.sortedBy { packed ->
            val dx = BlockPos.getX(packed) + 0.5 - x
            val dy = BlockPos.getY(packed) + 0.5 - y
            val dz = BlockPos.getZ(packed) + 0.5 - z
            dx * dx + dy * dy + dz * dz
        }.take(limit).toLongArray()
    }
}
