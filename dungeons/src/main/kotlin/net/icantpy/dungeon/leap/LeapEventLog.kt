package net.icantpy.dungeon.leap

/** One detected trigger event, kept for the debug page whether or not any card reacted. */
data class LeapEventEntry(
    val timeMs: Long,
    val event: LeapTriggerEvent,
    val value: String,
    val source: String,
    val cardsFired: Int,
)

/** Fixed-size history of detected events. Newest entries are returned first. Not thread-safe by itself. */
class LeapEventRing(private val capacity: Int = CAPACITY) {
    private val entries = ArrayDeque<LeapEventEntry>()

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun record(entry: LeapEventEntry) {
        entries.addFirst(entry)
        while (entries.size > capacity) entries.removeLast()
    }

    /** Newest first. */
    fun newestFirst(): List<LeapEventEntry> = entries.toList()

    fun clear() {
        entries.clear()
    }

    companion object {
        const val CAPACITY: Int = 30
    }
}

/** Shared event history used by the Spirit Leap debug page. Recording is always on; it is cheap. */
object LeapEventLog {
    private val ring = LeapEventRing()

    @Synchronized
    fun record(timeMs: Long, event: LeapTriggerEvent, value: String, source: String, cardsFired: Int) {
        ring.record(LeapEventEntry(timeMs, event, value.take(MAX_VALUE_CHARS), source, cardsFired))
    }

    @Synchronized
    fun newestFirst(): List<LeapEventEntry> = ring.newestFirst()

    @Synchronized
    fun clear() {
        ring.clear()
    }

    private const val MAX_VALUE_CHARS = 60
}
