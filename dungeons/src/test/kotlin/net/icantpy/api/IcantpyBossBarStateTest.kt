package net.icantpy.api

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IcantpyBossBarStateTest {
    @Test
    fun zeroHealthIsObservedBeforeRemovalWithoutAnimationOrPolling() {
        val state = IcantpyBossBarState()
        val id = UUID.randomUUID()
        assertEquals(IcantpyBossBar("Maxor", 1f), state.add(id, "Maxor", 1f))
        assertEquals(IcantpyBossBar("Maxor", 0f), state.progress(id, 0f))
        state.remove(id)
        assertNull(state.progress(id, 0f))
    }

    @Test
    fun renamedBarKeepsRawHealthAndUsesLatestBossName() {
        val state = IcantpyBossBarState()
        val id = UUID.randomUUID()
        state.add(id, "Goldor", 0.8f)
        assertEquals(IcantpyBossBar("Necron", 0.8f), state.name(id, "Necron"))
        assertEquals(IcantpyBossBar("Necron", 0f), state.progress(id, 0f))
    }

    @Test
    fun unknownAndNonFiniteUpdatesDoNotCreateDeaths() {
        val state = IcantpyBossBarState()
        val id = UUID.randomUUID()
        assertNull(state.progress(id, 0f))
        assertNull(state.name(id, "Maxor"))
        assertNull(state.add(id, "Maxor", Float.NaN))
        assertNull(state.progress(id, 0f))
        state.add(id, "Necron", 1f)
        assertNull(state.progress(id, Float.NEGATIVE_INFINITY))
        assertEquals(IcantpyBossBar("Necron", 0f), state.progress(id, 0f))
    }

    @Test
    fun disconnectClearsPreviousRunsBars() {
        val state = IcantpyBossBarState()
        val id = UUID.randomUUID()
        state.add(id, "Necron", 1f)
        state.clear()
        assertNull(state.progress(id, 0f))
    }
}
