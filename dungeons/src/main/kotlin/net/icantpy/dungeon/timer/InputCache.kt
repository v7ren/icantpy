package net.icantpy.dungeon.timer

/**
 * Reuses the last computed value while every input is unchanged. Primitive inputs (Int, Long, Boolean) compare by
 * value; all other inputs compare by identity, which is only correct for immutable data objects that are replaced
 * (not mutated) on change.
 */
class InputCache<V> {
    private var lastInputs: Array<Any?> = emptyArray()
    private var lastValue: V? = null
    private var filled = false

    fun get(vararg inputs: Any?, compute: () -> V): V {
        @Suppress("UNCHECKED_CAST")
        if (filled && sameInputs(lastInputs, inputs)) return lastValue as V
        val value = compute()
        lastInputs = arrayOf(*inputs)
        lastValue = value
        filled = true
        return value
    }

    fun clear() {
        filled = false
        lastValue = null
        lastInputs = emptyArray()
    }

    companion object {
        internal fun sameInputs(previous: Array<Any?>, next: Array<out Any?>): Boolean {
            if (previous.size != next.size) return false
            for (i in next.indices) {
                val a = previous[i]
                val b = next[i]
                val same = when (a) {
                    is Int, is Long, is Boolean -> a == b
                    else -> a === b
                }
                if (!same) return false
            }
            return true
        }
    }
}
