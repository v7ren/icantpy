package net.icantpy.cosmetics.items

/**
 * Shared animation clock for chroma rendering. NEU keeps a single process-wide start time so all
 * animated colours stay in phase; evaluation takes an explicit time so pure logic stays testable.
 */
object ItemCustomizeClock {
    private var startMillis: Long = -1L

    fun reset() {
        startMillis = -1L
    }

    fun elapsedSeconds(nowMillis: Long = System.currentTimeMillis()): Float =
        ((nowMillis - ensureStart(nowMillis)) / 1000f)

    fun elapsedMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        nowMillis - ensureStart(nowMillis)

    private fun ensureStart(nowMillis: Long): Long {
        if (startMillis < 0L) startMillis = nowMillis
        return startMillis
    }
}
