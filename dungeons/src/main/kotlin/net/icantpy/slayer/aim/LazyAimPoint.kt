package net.icantpy.slayer.aim

import java.util.SplittableRandom
import kotlin.math.exp

/**
 * A wandering offset inside a target's hitbox, so lazy aim never sits on the exact center. Each axis is a fraction
 * in -1..1 of the hitbox half-extent. A new random goal is picked every [minHoldMs]..[maxHoldMs] and the current
 * offset eases toward it, so the aim point drifts instead of jumping. Pure: time and randomness are inputs.
 */
class LazyAimPoint(
    private val minHoldMs: Long = 500L,
    private val maxHoldMs: Long = 1400L,
    /** Time constant of the drift toward the goal, in milliseconds. */
    private val driftMs: Float = 350f,
    seed: Long = 0L,
) {
    private val random = SplittableRandom(seed)
    private var x = 0f
    private var y = 0f
    private var z = 0f
    private var goalX = 0f
    private var goalY = 0f
    private var goalZ = 0f
    private var nextGoalMs = Long.MIN_VALUE
    private var lastMs = Long.MIN_VALUE

    /**
     * Advances to [nowMs] and returns the offset as fractions of the half-extents, each scaled by [strength]
     * (0 = always the center, 1 = anywhere up to the hitbox edge). Vertical spread is kept smaller so the aim
     * stays on the body rather than the feet or the top of the head.
     */
    fun sample(nowMs: Long, strength: Float, out: FloatArray) {
        if (nowMs >= nextGoalMs) pickGoal(nowMs)
        val dt = if (lastMs == Long.MIN_VALUE) 0f else (nowMs - lastMs).coerceIn(0L, 250L).toFloat()
        lastMs = nowMs
        val alpha = 1f - exp(-dt / driftMs)
        x += (goalX - x) * alpha
        y += (goalY - y) * alpha
        z += (goalZ - z) * alpha
        val s = strength.coerceIn(0f, 1f)
        // "+ 0f" turns -0.0 (negative offset times zero strength) into a plain 0.
        out[0] = x * s + 0f
        out[1] = y * s * VERTICAL_SHARE + 0f
        out[2] = z * s + 0f
    }

    fun reset() {
        x = 0f; y = 0f; z = 0f
        goalX = 0f; goalY = 0f; goalZ = 0f
        nextGoalMs = Long.MIN_VALUE
        lastMs = Long.MIN_VALUE
    }

    private fun pickGoal(nowMs: Long) {
        goalX = random.nextDouble(-1.0, 1.0).toFloat()
        goalY = random.nextDouble(-1.0, 1.0).toFloat()
        goalZ = random.nextDouble(-1.0, 1.0).toFloat()
        nextGoalMs = nowMs + random.nextLong(minHoldMs, maxHoldMs + 1)
    }

    private companion object {
        const val VERTICAL_SHARE = 0.6f
    }
}
