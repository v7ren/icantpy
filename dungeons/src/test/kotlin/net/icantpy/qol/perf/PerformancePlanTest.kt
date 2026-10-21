package net.icantpy.qol.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformancePlanTest {
    private val vanillaHigh = VideoSnapshot(
        renderDistance = 16,
        simulationDistance = 12,
        graphicsPreset = "FABULOUS",
        ambientOcclusion = true,
        particles = "ALL",
        cloudStatus = "FANCY",
        biomeBlendRadius = 5,
        entityShadows = true,
        entityDistanceScaling = 1.0,
        mipmapLevels = 4,
        enableVsync = true,
        framerateLimit = 120,
    )

    @Test
    fun `target applies fixed preset values`() {
        val target = PerformancePlan.target(vanillaHigh)

        assertEquals("FAST", target.graphicsPreset)
        assertEquals(false, target.ambientOcclusion)
        assertEquals("DECREASED", target.particles)
        assertEquals("OFF", target.cloudStatus)
        assertEquals(0, target.biomeBlendRadius)
        assertFalse(target.entityShadows)
        assertEquals(0.75, target.entityDistanceScaling)
        assertFalse(target.enableVsync)
        assertEquals(PerformancePlan.UNLIMITED_FRAMERATE, target.framerateLimit)
    }

    @Test
    fun `render distance and simulation distance are only lowered`() {
        val target = PerformancePlan.target(vanillaHigh)
        assertEquals(8, target.renderDistance)
        assertEquals(6, target.simulationDistance)
    }

    @Test
    fun `values already below the limits are left untouched`() {
        val low = vanillaHigh.copy(renderDistance = 4, simulationDistance = 3, mipmapLevels = 1)
        val target = PerformancePlan.target(low)

        assertEquals(4, target.renderDistance)
        assertEquals(3, target.simulationDistance)
        assertEquals(1, target.mipmapLevels)
    }

    @Test
    fun `mipmap levels are only lowered to two`() {
        assertEquals(2, PerformancePlan.target(vanillaHigh.copy(mipmapLevels = 4)).mipmapLevels)
        assertEquals(2, PerformancePlan.target(vanillaHigh.copy(mipmapLevels = 2)).mipmapLevels)
        assertEquals(0, PerformancePlan.target(vanillaHigh.copy(mipmapLevels = 0)).mipmapLevels)
    }

    @Test
    fun `target is idempotent`() {
        val once = PerformancePlan.target(vanillaHigh)
        assertEquals(once, PerformancePlan.target(once))
    }

    @Test
    fun `snapshot survives a JSON round trip`() {
        val json = VideoSnapshotJson.toJson(vanillaHigh)
        assertEquals(vanillaHigh, VideoSnapshotJson.parse(json))
    }

    @Test
    fun `malformed JSON falls back to defaults`() {
        assertNull(VideoSnapshotJson.parseOrNull("{not json"))
        assertNull(VideoSnapshotJson.parseOrNull("[1, 2, 3]"))
        assertEquals(VideoSnapshot(), VideoSnapshotJson.parse("{not json"))
        assertEquals(VideoSnapshot(), VideoSnapshotJson.parse("\"text\""))
    }

    @Test
    fun `wrong-typed and unknown fields fall back per field`() {
        val json = """
            {
              "renderDistance": "lots",
              "simulationDistance": 7.5,
              "graphicsPreset": "ULTRA",
              "particles": "MINIMAL",
              "cloudStatus": 3,
              "enableVsync": "yes",
              "entityDistanceScaling": 0.5,
              "unknownField": true
            }
        """.trimIndent()
        val parsed = VideoSnapshotJson.parse(json)
        val defaults = VideoSnapshot()

        assertEquals(defaults.renderDistance, parsed.renderDistance)
        assertEquals(defaults.simulationDistance, parsed.simulationDistance)
        assertEquals(defaults.graphicsPreset, parsed.graphicsPreset)
        assertEquals("MINIMAL", parsed.particles)
        assertEquals(defaults.cloudStatus, parsed.cloudStatus)
        assertEquals(defaults.enableVsync, parsed.enableVsync)
        assertEquals(0.5, parsed.entityDistanceScaling)
    }

    @Test
    fun `parseOrNull returns a snapshot for a valid object`() {
        assertNotNull(VideoSnapshotJson.parseOrNull("{}"))
        assertTrue(VideoSnapshotJson.parseOrNull("{}") == VideoSnapshot())
    }
}
