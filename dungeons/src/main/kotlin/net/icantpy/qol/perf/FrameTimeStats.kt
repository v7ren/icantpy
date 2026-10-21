package net.icantpy.qol.perf

import kotlin.math.ceil

/**
 * Fixed-size ring buffer of recent frame times in nanoseconds. Pure Kotlin with no Minecraft types and no
 * allocation per sample: the sort scratch buffer is allocated once in the constructor.
 */
class FrameTimeStats(val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val samples = LongArray(capacity)
    private val scratch = LongArray(capacity)
    private var head = 0

    /** Number of valid samples; valid entries are always `samples[0 until size]`. */
    var size: Int = 0
        private set

    fun record(frameNanos: Long) {
        if (frameNanos <= 0L) return
        samples[head] = frameNanos
        head = if (head + 1 == capacity) 0 else head + 1
        if (size < capacity) size++
    }

    fun clear() {
        head = 0
        size = 0
    }

    fun averageFrameMs(): Double {
        if (size == 0) return 0.0
        var total = 0L
        for (i in 0 until size) total += samples[i]
        return total.toDouble() / size / NANOS_PER_MS
    }

    fun fps(): Double {
        val avg = averageFrameMs()
        return if (avg <= 0.0) 0.0 else 1000.0 / avg
    }

    /** Average FPS over the worst [fraction] of frames in the window (0.01 = 1% low). */
    fun lowFps(fraction: Double): Double {
        val n = size
        if (n == 0) return 0.0
        System.arraycopy(samples, 0, scratch, 0, n)
        java.util.Arrays.sort(scratch, 0, n)
        val worstCount = ceil(n * fraction).toInt().coerceIn(1, n)
        var total = 0L
        for (i in n - worstCount until n) total += scratch[i]
        val avgMs = total.toDouble() / worstCount / NANOS_PER_MS
        return if (avgMs <= 0.0) 0.0 else 1000.0 / avgMs
    }

    fun onePercentLowFps(): Double = lowFps(0.01)

    fun pointOnePercentLowFps(): Double = lowFps(0.001)

    fun maxFrameMs(): Double {
        var max = 0L
        for (i in 0 until size) if (samples[i] > max) max = samples[i]
        return max.toDouble() / NANOS_PER_MS
    }

    companion object {
        const val DEFAULT_CAPACITY = 1000
        private const val NANOS_PER_MS = 1_000_000.0
    }
}
