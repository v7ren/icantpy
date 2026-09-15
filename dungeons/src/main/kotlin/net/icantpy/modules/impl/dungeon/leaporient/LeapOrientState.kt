package net.icantpy.modules.impl.dungeon.leaporient

import kotlin.jvm.JvmOverloads

data class LeapOrientLock(
    val target: String,
    val expiresAtMs: Long,
    val sourceId: Long = System.nanoTime(),
)

object LeapOrientState {
    var lock: LeapOrientLock? = null
        private set

    var debugSelfSpot: OrientSpot? = null
    var debugSelfClass: LeapDungeonClass? = null
    val debugSenderSpots: MutableMap<String, OrientSpot> = mutableMapOf()

    fun arm(target: String, durationMs: Long): LeapOrientLock {
        val next = LeapOrientLock(target, System.currentTimeMillis() + durationMs)
        lock = next
        return next
    }

    fun clear() {
        lock = null
    }

    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long {
        val active = lock ?: return 0
        return (active.expiresAtMs - nowMs).coerceAtLeast(0)
    }

    @JvmOverloads
    fun isActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        lock != null && remainingMs(nowMs) > 0

    fun tick(nowMs: Long = System.currentTimeMillis()) {
        if (lock != null && remainingMs(nowMs) <= 0) {
            clear()
        }
    }

    fun <T> orient(
        list: List<T>,
        nameOf: (T) -> String,
        targetName: String,
        quadrant: Int,
    ): List<T> {
        if (list.isEmpty() || quadrant !in 0..3) return list
        val padded = ArrayList<T>(4)
        for (index in 0 until 4) {
            padded += list.getOrNull(index) ?: return list
        }
        val from = padded.indexOfFirst { nameOf(it).equals(targetName, ignoreCase = true) }
        if (from == -1 || from == quadrant) return padded
        val displaced = padded[quadrant]
        padded[quadrant] = padded[from]
        padded[from] = displaced
        return padded
    }
}
