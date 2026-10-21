package net.icantpy.dungeon.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class InputCacheTest {
    private class Box(val label: String)

    @Test
    fun computesOnceWhileInputsAreUnchanged() {
        val cache = InputCache<String>()
        val box = Box("a")
        var calls = 0
        val compute = { calls += 1; "v$calls" }
        assertEquals("v1", cache.get(1, true, box, compute = compute))
        assertEquals("v1", cache.get(1, true, box, compute = compute))
        assertEquals(1, calls)
    }

    @Test
    fun primitiveChangeRecomputes() {
        val cache = InputCache<Int>()
        val box = Box("a")
        var calls = 0
        assertEquals(1, cache.get(1, box) { ++calls })
        assertEquals(2, cache.get(2, box) { ++calls })
        assertEquals(3, cache.get(2, false, box) { ++calls })
    }

    @Test
    fun objectReplacedByEqualInstanceRecomputes() {
        val cache = InputCache<Int>()
        var calls = 0
        assertEquals(1, cache.get(Box("a")) { ++calls })
        // Equal content but a new instance: identity comparison treats it as changed.
        assertEquals(2, cache.get(Box("a")) { ++calls })
    }

    @Test
    fun clearForcesRecompute() {
        val cache = InputCache<Int>()
        val box = Box("a")
        var calls = 0
        cache.get(box) { ++calls }
        cache.clear()
        assertEquals(2, cache.get(box) { ++calls })
    }

    @Test
    fun nullInputsCompareByIdentity() {
        val cache = InputCache<Int>()
        var calls = 0
        assertEquals(1, cache.get(null) { ++calls })
        assertEquals(1, cache.get(null) { ++calls })
        assertEquals(2, cache.get("x") { ++calls })
    }
}
