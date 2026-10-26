package net.icantpy.qol.fishing

import kotlin.random.Random

enum class FishingAction { NONE, CAST, REEL }

/** Why the cycle stopped itself; the macro shows it in chat. */
enum class FishingStop(val message: String) {
    NONE(""),
    CAST_FAILED("the rod never cast (is it in your hand?)"),
    NO_BITES("no bites for too long (is the hook in water?)"),
}

/** [quiet] marks a reel made only because the no-bite timeout ran out, so the caller can say what it saw. */
data class FishingStep(
    val action: FishingAction = FishingAction.NONE,
    val stop: FishingStop = FishingStop.NONE,
    val quiet: Boolean = false,
)

/** Delay ranges and limits for one cycle, all in milliseconds. */
data class FishingTiming(
    val reelMinMs: Int,
    val reelMaxMs: Int,
    val castMinMs: Int,
    val castMaxMs: Int,
    val noBiteMs: Long,
)

/**
 * The cast / wait / reel loop as a pure state machine. The caller reports what it sees each tick (whether
 * the player's hook is out, whether a bite marker is next to it) and does what the returned step says.
 */
class FishingCycle(private val random: Random = Random.Default) {
    private enum class Phase { IDLE, CAST_SENT, FISHING, REEL_SENT }

    private var phase = Phase.IDLE
    private var dueMs = 0L
    private var sentMs = 0L
    private var hookSinceMs = 0L
    private var failedCasts = 0
    private var quietTimeouts = 0

    fun reset() {
        phase = Phase.IDLE
        dueMs = 0L
        sentMs = 0L
        hookSinceMs = 0L
        failedCasts = 0
        quietTimeouts = 0
    }

    fun step(hookOut: Boolean, bite: Boolean, nowMs: Long, timing: FishingTiming): FishingStep = when (phase) {
        Phase.IDLE -> idle(hookOut, nowMs, timing)
        Phase.CAST_SENT -> castSent(hookOut, nowMs)
        Phase.FISHING -> fishing(hookOut, bite, nowMs, timing)
        Phase.REEL_SENT -> reelSent(hookOut, nowMs)
    }

    private fun idle(hookOut: Boolean, nowMs: Long, timing: FishingTiming): FishingStep {
        if (hookOut) return enterFishing(nowMs)
        if (dueMs == 0L) dueMs = nowMs + sample(timing.castMinMs, timing.castMaxMs)
        if (nowMs < dueMs) return FishingStep()
        dueMs = 0L
        sentMs = nowMs
        phase = Phase.CAST_SENT
        return FishingStep(FishingAction.CAST)
    }

    private fun castSent(hookOut: Boolean, nowMs: Long): FishingStep {
        if (hookOut) {
            failedCasts = 0
            return enterFishing(nowMs)
        }
        if (nowMs - sentMs < RESPONSE_MS) return FishingStep()
        phase = Phase.IDLE
        failedCasts++
        return if (failedCasts >= MAX_FAILED_CASTS) FishingStep(stop = FishingStop.CAST_FAILED) else FishingStep()
    }

    private fun fishing(hookOut: Boolean, bite: Boolean, nowMs: Long, timing: FishingTiming): FishingStep {
        if (!hookOut) {
            phase = Phase.IDLE
            dueMs = 0L
            return FishingStep()
        }
        if (bite && dueMs == 0L) dueMs = nowMs + sample(timing.reelMinMs, timing.reelMaxMs)
        if (dueMs != 0L && nowMs >= dueMs) return reel(nowMs, bitten = true)
        if (dueMs == 0L && nowMs - hookSinceMs > timing.noBiteMs) {
            quietTimeouts++
            if (quietTimeouts >= MAX_QUIET_TIMEOUTS) return FishingStep(stop = FishingStop.NO_BITES)
            return reel(nowMs, bitten = false)
        }
        return FishingStep()
    }

    private fun reelSent(hookOut: Boolean, nowMs: Long): FishingStep {
        if (!hookOut) {
            phase = Phase.IDLE
            dueMs = 0L
            return FishingStep()
        }
        if (nowMs - sentMs < RESPONSE_MS) return FishingStep()
        // The reel did not take: look for the bite again from now.
        phase = Phase.FISHING
        dueMs = 0L
        hookSinceMs = nowMs
        return FishingStep()
    }

    private fun enterFishing(nowMs: Long): FishingStep {
        phase = Phase.FISHING
        dueMs = 0L
        hookSinceMs = nowMs
        return FishingStep()
    }

    private fun reel(nowMs: Long, bitten: Boolean): FishingStep {
        if (bitten) quietTimeouts = 0
        dueMs = 0L
        sentMs = nowMs
        phase = Phase.REEL_SENT
        return FishingStep(FishingAction.REEL, quiet = !bitten)
    }

    private fun sample(minMs: Int, maxMs: Int): Long {
        val low = minOf(minMs, maxMs).coerceAtLeast(0)
        val high = maxOf(minMs, maxMs).coerceAtLeast(low)
        return (low + random.nextInt(high - low + 1)).toLong().coerceAtLeast(1L)
    }

    companion object {
        /** How long a cast or reel may take to show on the hook before it counts as lost. */
        const val RESPONSE_MS = 2_500L
        const val MAX_FAILED_CASTS = 3
        const val MAX_QUIET_TIMEOUTS = 3
    }
}
