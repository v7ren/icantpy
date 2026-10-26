package net.icantpy.slayer.blaze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvoidMobsTest {
    @Test
    fun parseSplitsTrimsAndLowercases() {
        assertEquals(listOf("typhoeus", "quazii"), AvoidMobs.parse(" Typhoeus, QUAZII ,, "))
        assertTrue(AvoidMobs.parse("  ").isEmpty())
    }

    @Test
    fun matchesIgnoresColourCodesAndCase() {
        val names = AvoidMobs.parse("typhoeus")
        assertTrue(AvoidMobs.matches("§c☠ §eTyphoeus §a1.2M§c❤", names))
        assertFalse(AvoidMobs.matches("Inferno Demonlord", names))
    }

    @Test
    fun circledLettersMatchPlainNames() {
        assertTrue(AvoidMobs.matches("ⓉⓎⓅⒽⓄⒺⓊⓈ §c35M§c❤", AvoidMobs.parse("typhoeus")))
        assertTrue(AvoidMobs.matches("Typhoeus 3", AvoidMobs.parse("ⓉⓎⓅⒽⓄⒺⓊⓈ")))
        assertTrue(AvoidMobs.matches("ⓆⓊⒶⓏⒾⒾ", AvoidMobs.parse("quazii")))
    }

    @Test
    fun configRoundTripsTheAvoidMobFields() {
        val c = FirePitConfig(avoidMobs = true, avoidMobNames = "a, b", avoidMobRadius = 4f)
        val back = FirePitConfig.fromJsonObject(c.toJsonObject())
        assertEquals(c.avoidMobs, back.avoidMobs)
        assertEquals("a, b", back.avoidMobNames)
        assertEquals(4f, back.avoidMobRadius)
    }

    @Test
    fun firePillarCountdownStandIsRecognised() {
        assertTrue(AvoidMobs.isPillarName("§6§l2s §c§l8 hits"))
        assertTrue(AvoidMobs.isPillarName("§6§l10s §c§l8 hits"))
        assertTrue(AvoidMobs.isPillarName("§6§l1.5s §c§l1 hit"))
        assertFalse(AvoidMobs.isPillarName("Inferno Typhoeus 3"))
        assertFalse(AvoidMobs.isPillarName("Spawned by: Player"))
    }
}
