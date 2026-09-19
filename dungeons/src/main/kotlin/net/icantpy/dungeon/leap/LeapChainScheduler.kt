package net.icantpy.dungeon.leap

data class PendingLeapChain(
    val triggerId: String,
    val request: LeapArmRequest,
    val armAtMs: Long,
    val durationMs: Long,
)

object LeapChainScheduler {
    private val pending = ArrayList<PendingLeapChain>()

    fun schedule(
        trigger: LeapOrientTrigger,
        request: LeapArmRequest,
        delayMs: Long,
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        pending += PendingLeapChain(
            triggerId = trigger.id,
            request = request,
            armAtMs = nowMs + delayMs.coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(1_000),
        )
    }

    fun due(nowMs: Long = System.currentTimeMillis()): List<PendingLeapChain> {
        if (pending.isEmpty()) return emptyList()
        val ready = pending.filter { it.armAtMs <= nowMs }
        if (ready.isEmpty()) return emptyList()
        pending.removeAll(ready.toSet())
        return ready
    }

    fun clear() {
        pending.clear()
    }

    fun pendingCount(): Int = pending.size
}
