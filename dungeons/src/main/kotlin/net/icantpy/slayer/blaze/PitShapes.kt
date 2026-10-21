package net.icantpy.slayer.blaze

/**
 * Shape-based pit detection, a safety net next to the tracker's block-change detection. Every Inferno Demonlord
 * fire pit is a plus of five floor blocks, so a small group of touching pit-block columns is a pit even if it was
 * never seen forming (it formed just outside the scan area, or while detection was paused).
 *
 * Uneven or floating terrain can drop arms of the plus (no floor block there), so any 4-connected group of
 * [MIN_GROUP]..[MAX_GROUP] columns counts. Lone or paired blocks and large decorative terracotta areas do not.
 * Columns are compared by x/z only, so arms at different heights still join up.
 */
object PitShapes {
    /** A plus missing two arms still has three blocks. */
    const val MIN_GROUP = 3

    /** Two or three overlapping pluses stay under this; floors and walls of terracotta are bigger. */
    const val MAX_GROUP = 15

    /** Adds to [out] every column of [pitColumns] whose 4-connected group has [MIN_GROUP]..[MAX_GROUP] columns. */
    fun pitGroups(pitColumns: Set<Long>, out: MutableSet<Long>) {
        // Runs every tick during the fight; nearly always nothing to group, so skip the allocations.
        if (pitColumns.size < MIN_GROUP) return
        val seen = HashSet<Long>()
        val group = ArrayList<Long>()
        val stack = ArrayDeque<Long>()
        for (start in pitColumns) {
            if (!seen.add(start)) continue
            group.clear()
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val key = stack.removeLast()
                group += key
                val x = unpackX(key)
                val z = unpackZ(key)
                for (next in neighbours(x, z)) {
                    if (next in pitColumns && seen.add(next)) stack.addLast(next)
                }
            }
            if (group.size in MIN_GROUP..MAX_GROUP) out.addAll(group)
        }
    }

    /**
     * Adds to [out] every column within [radius] blocks (center to center) of a column in [columns], the columns
     * themselves included. Used to keep a wider berth around fire. A radius below 1 adds just the columns.
     */
    fun expand(columns: Set<Long>, radius: Float, out: MutableSet<Long>) {
        if (columns.isEmpty()) return
        val reach = radius.toInt()
        val reachSq = radius * radius
        for (key in columns) {
            val x = unpackX(key)
            val z = unpackZ(key)
            for (dx in -reach..reach) {
                for (dz in -reach..reach) {
                    if (dx * dx + dz * dz <= reachSq) out.add(packXZ(x + dx, z + dz))
                }
            }
            out.add(key)
        }
    }

    private fun neighbours(x: Int, z: Int): LongArray =
        longArrayOf(packXZ(x + 1, z), packXZ(x - 1, z), packXZ(x, z + 1), packXZ(x, z - 1))
}
