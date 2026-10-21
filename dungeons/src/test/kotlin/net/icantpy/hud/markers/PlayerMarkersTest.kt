package net.icantpy.hud.markers

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerMarkersTest {
    private val red = 0xFFFF0000.toInt()

    @AfterTest
    fun reset() {
        PlayerMarkers.clearAll()
        PlayerMarkers.classResolver = { null }
    }

    @Test
    fun expiresAfterDuration() {
        PlayerMarkers.mark("a", MarkerSelector.ByName("Alice"), MarkerStyle.BOX, red, 1_000, nowMs = 0)
        assertEquals(1, PlayerMarkers.active(999).size)
        assertEquals(0, PlayerMarkers.active(1_000).size)
    }

    @Test
    fun zeroDurationPersists() {
        PlayerMarkers.mark("a", MarkerSelector.ByName("Alice"), MarkerStyle.ARROW, red, 0, nowMs = 0)
        assertEquals(1, PlayerMarkers.active(Long.MAX_VALUE - 1).size)
    }

    @Test
    fun clearRemovesOnlyThatId() {
        PlayerMarkers.mark("a", MarkerSelector.ByName("Alice"), MarkerStyle.BOX, red, 0, 0)
        PlayerMarkers.mark("b", MarkerSelector.ByName("Bob"), MarkerStyle.BEAM, red, 0, 0)
        PlayerMarkers.clear("a")
        assertEquals(listOf("b"), PlayerMarkers.active(0).map { it.id })
        PlayerMarkers.clearAll()
        assertEquals(0, PlayerMarkers.active(0).size)
    }

    @Test
    fun sameIdReplacesMarker() {
        PlayerMarkers.mark("a", MarkerSelector.ByName("Alice"), MarkerStyle.BOX, red, 1_000, 0)
        PlayerMarkers.mark("a", MarkerSelector.ByName("Bob"), MarkerStyle.BEAM, 0xFF00FF00.toInt(), 0, 500)
        val active = PlayerMarkers.active(10_000)
        assertEquals(1, active.size)
        assertEquals("Bob", active[0].targetName)
        assertEquals(MarkerStyle.BEAM, active[0].style)
        assertEquals(Long.MAX_VALUE, active[0].expiresAtMs)
    }

    @Test
    fun nameSelectorIsResolvedDirectly() {
        assertEquals("Alice", PlayerMarkers.resolveName(MarkerSelector.ByName("Alice")))
        assertNull(PlayerMarkers.resolveName(MarkerSelector.ByName("  ")))
    }

    @Test
    fun classSelectorUsesResolver() {
        var asked: String? = null
        PlayerMarkers.classResolver = { cls -> asked = cls; if (cls == "ARCHER") "Carl" else null }
        PlayerMarkers.mark("c", MarkerSelector.ByClass(MarkerClass.ARCHER), MarkerStyle.ARROW, red, 0, 0)
        val active = PlayerMarkers.active(0)
        assertEquals("ARCHER", asked)
        assertEquals("Carl", active[0].targetName)

        PlayerMarkers.mark("m", MarkerSelector.ByClass(MarkerClass.MAGE), MarkerStyle.ARROW, red, 0, 0)
        assertNull(PlayerMarkers.active(0).first { it.id == "m" }.targetName)
    }

    @Test
    fun classTokensParse() {
        assertEquals(MarkerClass.BERSERK, MarkerClass.fromToken("bers"))
        assertEquals(MarkerClass.TANK, MarkerClass.fromToken(" Tank "))
        assertNull(MarkerClass.fromToken("empty"))
    }

    @Test
    fun labelFormatting() {
        assertEquals("Alice | Healer | 12m", PlayerMarkers.labelText("Alice", MarkerClass.HEALER, 12.7))
        assertEquals("Bob", PlayerMarkers.labelText("Bob", null, null))
    }
}
