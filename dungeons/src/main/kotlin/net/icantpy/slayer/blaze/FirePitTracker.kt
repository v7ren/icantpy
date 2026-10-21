package net.icantpy.slayer.blaze

/** Packs block column coordinates into one Long key. */
fun packXZ(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

fun unpackX(key: Long): Int = (key shr 32).toInt()

fun unpackZ(key: Long): Int = key.toInt()

/**
 * Remembers which block columns turned into a pit block recently.
 *
 * Only a false -> true transition creates a hazard: pit blocks already present when a column is first seen are
 * arena decoration. A hazard lasts [windowTicks] ticks after the transition, or until the column is observed false.
 */
class FirePitTracker(private val windowTicks: Int = 60) {
    private class Column(var lastSeenTick: Long, var isPit: Boolean, var hazardSinceTick: Long)

    private val columns = HashMap<Long, Column>()
    private val hazardSet = HashSet<Long>()

    fun observe(tick: Long, x: Int, z: Int, isPitBlock: Boolean) {
        val key = packXZ(x, z)
        val column = columns[key]
        if (column == null) {
            columns[key] = Column(tick, isPitBlock, NO_HAZARD)
            return
        }
        column.lastSeenTick = tick
        when {
            !isPitBlock -> column.hazardSinceTick = NO_HAZARD
            !column.isPit -> column.hazardSinceTick = tick
        }
        column.isPit = isPitBlock
    }

    /** Columns that are hazards at [tick]. Returns a reused set; callers must not keep it across ticks. */
    fun hazards(tick: Long): Set<Long> {
        hazardSet.clear()
        for ((key, column) in columns) {
            if (isActive(column, tick)) hazardSet.add(key)
        }
        return hazardSet
    }

    fun hazardCount(tick: Long): Int = columns.values.count { isActive(it, tick) }

    /** Forget columns not observed since [tick] - 200. Call once per scan. */
    fun prune(tick: Long) {
        columns.values.removeAll { it.lastSeenTick < tick - PRUNE_AFTER_TICKS }
    }

    fun clear() {
        columns.clear()
        hazardSet.clear()
    }

    private fun isActive(column: Column, tick: Long): Boolean {
        val since = column.hazardSinceTick
        return since != NO_HAZARD && tick >= since && tick - since < windowTicks
    }

    private companion object {
        const val NO_HAZARD = Long.MIN_VALUE
        const val PRUNE_AFTER_TICKS = 200L
    }
}
