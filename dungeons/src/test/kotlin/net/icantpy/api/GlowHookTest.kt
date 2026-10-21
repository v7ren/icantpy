package net.icantpy.api

import java.util.function.IntUnaryOperator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GlowHookTest {
    @AfterTest
    fun reset() {
        GlowHook.install(null)
    }

    @Test
    fun inactiveHookReturnsNoGlowWithoutCallingProvider() {
        var calls = 0
        GlowHook.provider = IntUnaryOperator { calls++; 0x112233 }
        GlowHook.enabled = false
        assertEquals(GlowHook.NO_GLOW, GlowHook.colorFor(7))
        assertEquals(0, calls)
    }

    @Test
    fun installEnablesProviderAndNullDisablesAndClears() {
        GlowHook.install(IntUnaryOperator { id -> if (id == 7) 0xABCDEF else GlowHook.NO_GLOW })
        assertEquals(true, GlowHook.enabled)
        assertEquals(0xABCDEF, GlowHook.colorFor(7))
        assertEquals(GlowHook.NO_GLOW, GlowHook.colorFor(8))

        GlowHook.install(null)
        assertEquals(false, GlowHook.enabled)
        assertEquals(null, GlowHook.provider)
        assertEquals(GlowHook.NO_GLOW, GlowHook.colorFor(7))
    }

    @Test
    fun throwingProviderIsTreatedAsNoGlow() {
        GlowHook.install(IntUnaryOperator { throw IllegalStateException("stale payload") })
        assertEquals(GlowHook.NO_GLOW, GlowHook.colorFor(1))
    }

    @Test
    fun reinstallReplacesPreviousProvider() {
        GlowHook.install(IntUnaryOperator { 0x000001 })
        GlowHook.install(IntUnaryOperator { 0x000002 })
        assertEquals(0x000002, GlowHook.colorFor(3))
    }
}
