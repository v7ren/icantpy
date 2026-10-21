package net.icantpy.qol.perf

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

/** Ray budget for one frame. [take] consumes one ray and returns false once the frame has none left. */
class RayBudget(private var remaining: Int) {
    fun take(): Boolean {
        if (remaining <= 0) return false
        remaining--
        return true
    }
}

/**
 * Tests one entity for "fully hidden behind blocks". Returns true when every sample ray is blocked, false when
 * any sample is visible or the entity is gone, and null when the budget ran out before the answer was known.
 */
fun interface HiddenTest {
    fun isFullyHidden(entityId: Int, rays: RayBudget): Boolean?
}

/**
 * Round-robin cache of "fully hidden" per entity id, stored without boxing. Entries are refreshed at least every
 * [REFRESH_FRAMES] frames; stale or unknown entities count as visible. Work is limited to [raysPerFrame] rays per
 * frame. Size is capped at [maxEntries]. Render thread only.
 *
 * Each entry packs `checkedFrame << 1 | hiddenBit` into one long, so the map holds no per-entry objects.
 */
class OcclusionCache(
    private val rayTest: HiddenTest,
    private val raysPerFrame: Int = RAYS_PER_FRAME,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val entries = Int2LongOpenHashMap().apply { defaultReturnValue(NONE) }
    private val queue = IntArrayFIFOQueue()
    private val queued = IntOpenHashSet()

    /** Reports whether [entityId] was recently found fully hidden. Unknown or stale entries answer false. */
    fun isHidden(entityId: Int, frame: Long): Boolean {
        val value = entries.get(entityId)
        return value != NONE && isHiddenFresh(value, frame)
    }

    /**
     * One lookup for the render hot path: returns the fresh hidden answer, or queues [entityId] for a test when
     * its entry is missing or stale and answers false until the test runs.
     */
    fun touch(entityId: Int, frame: Long): Boolean {
        val value = entries.get(entityId)
        if (value != NONE && frame - (value shr 1) <= REFRESH_FRAMES) return (value and 1L) != 0L
        if (queued.add(entityId)) queue.enqueue(entityId)
        return false
    }

    /** Queues [entityId] for a test unless its entry is fresh or it is already queued. */
    fun request(entityId: Int, frame: Long) {
        touch(entityId, frame)
    }

    /** Spends this frame's ray budget on queued entities, oldest request first. */
    fun advance(frame: Long) {
        val rays = RayBudget(raysPerFrame)
        var attempts = queue.size()
        while (attempts > 0 && !queue.isEmpty()) {
            attempts--
            val id = queue.firstInt()
            val hidden = rayTest.isFullyHidden(id, rays)
            if (hidden == null) {
                // Out of rays mid-test: move this entity to the back so others get a turn next frame.
                queue.dequeueInt()
                queue.enqueue(id)
                return
            }
            queue.dequeueInt()
            queued.remove(id)
            store(id, frame, hidden)
        }
    }

    /** Drops the entry for an entity that was removed or left the world. */
    fun forget(entityId: Int) {
        entries.remove(entityId)
    }

    fun clear() {
        entries.clear()
        queue.clear()
        queued.clear()
    }

    val size: Int get() = entries.size

    private fun isHiddenFresh(value: Long, frame: Long): Boolean =
        (value and 1L) != 0L && frame - (value shr 1) <= REFRESH_FRAMES

    private fun store(id: Int, frame: Long, hidden: Boolean) {
        if (!entries.containsKey(id) && entries.size >= maxEntries) evictOne()
        entries.put(id, (frame shl 1) or (if (hidden) 1L else 0L))
    }

    private fun evictOne() {
        val iterator = (entries.keys as IntSet).iterator()
        if (iterator.hasNext()) entries.remove(iterator.nextInt())
    }

    companion object {
        const val RAYS_PER_FRAME: Int = 48
        const val REFRESH_FRAMES: Long = 10L
        const val MAX_ENTRIES: Int = 4096
        private const val NONE: Long = Long.MIN_VALUE
    }
}
