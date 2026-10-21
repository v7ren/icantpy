package net.icantpy.qol.visual

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisualsConfigTest {
    @Test
    fun defaultsAreVanilla() {
        val c = VisualsConfig()
        assertFalse(c.anyActive())
        assertEquals(6000L, c.dayTime)
        assertEquals(VisualsConfig.UNBOUND, c.zoomKey)
        assertEquals(SwingMode.VANILLA, c.swingMode)
        assertEquals(VisualsConfig(), VisualsConfig.parse("{}"))
    }

    @Test
    fun malformedInputYieldsDefaults() {
        assertEquals(VisualsConfig(), VisualsConfig.parse("{not json"))
        assertEquals(VisualsConfig(), VisualsConfig.parse("[1,2,3]"))
        assertEquals(VisualsConfig(), VisualsConfig.parse(""))
    }

    @Test
    fun wrongJsonTypesFallBackToDefaults() {
        val c = VisualsConfig.parse("""{"fullbright":"yes","cameraDistance":"far","swingMode":7}""")
        assertFalse(c.fullbright)
        assertEquals(VisualsConfig.DEFAULT_CAMERA_DISTANCE, c.cameraDistance)
        assertEquals(SwingMode.VANILLA, c.swingMode)
    }

    @Test
    fun numbersAreClampedToRange() {
        val high = VisualsConfig.parse(
            """{"dayTime":99999,"fogDistance":50,"cameraDistance":100,"zoomFactor":0.5,
               "aspectRatio":9,"offsetX":5,"rotYaw":900,"scale":3,"swingSpeed":0.01}""",
        )
        assertEquals(23999L, high.dayTime)
        assertEquals(4f, high.fogDistance)
        assertEquals(20f, high.cameraDistance)
        assertEquals(1.5f, high.zoomFactor)
        assertEquals(3f, high.aspectRatio)
        assertEquals(2f, high.offsetX)
        assertEquals(180f, high.rotYaw)
        assertEquals(2f, high.scale)
        assertEquals(0.25f, high.swingSpeed)

        val low = VisualsConfig.parse("""{"dayTime":-5,"fogDistance":0,"offsetZ":-9,"scale":0}""")
        assertEquals(0L, low.dayTime)
        assertEquals(0.25f, low.fogDistance)
        assertEquals(-2f, low.offsetZ)
        assertEquals(0.1f, low.scale)
    }

    @Test
    fun unknownSwingModeFallsBackToVanilla() {
        assertEquals(SwingMode.VANILLA, VisualsConfig.parse("""{"swingMode":"moonwalk"}""").swingMode)
        assertEquals(SwingMode.SWING_DOWN, VisualsConfig.parse("""{"swingMode":"swing_down"}""").swingMode)
        assertEquals(SwingMode.PUNCH, VisualsConfig.parse("""{"swingMode":"  PUNCH "}""").swingMode)
    }

    @Test
    fun zeroZoomKeyMeansUnbound() {
        assertEquals(VisualsConfig.UNBOUND, VisualsConfig.parse("""{"zoomKey":0}""").zoomKey)
        assertEquals(67, VisualsConfig.parse("""{"zoomKey":67}""").zoomKey)
        assertFalse(VisualsConfig.parse("""{"zoomKey":0}""").anyActive())
    }

    @Test
    fun anyActiveFollowsEverySettingGroup() {
        assertTrue(VisualsConfig(noLightning = true).anyActive())
        assertTrue(VisualsConfig(fogDistance = 2f).anyActive())
        assertTrue(VisualsConfig(zoomKey = 67).anyActive())
        assertTrue(VisualsConfig(viewmodelEnabled = true).anyActive())
        assertFalse(VisualsConfig(cameraDistance = 9f, swingSpeed = 2f).anyActive())
    }

    @Test
    fun jsonRoundTripKeepsEveryField() {
        val original = VisualsConfig(
            noVignette = true,
            timeChanger = true,
            dayTime = 13000L,
            fogDistance = 0.5f,
            customCameraDistance = true,
            cameraDistance = 7.5f,
            zoomKey = 67,
            zoomFactor = 6f,
            zoomSmooth = false,
            customAspect = true,
            aspectRatio = 1.25f,
            viewmodelEnabled = true,
            offsetX = -0.3f,
            rotRoll = 45f,
            scale = 0.75f,
            swingMode = SwingMode.STAB,
            swingSpeed = 2f,
            noEquipReset = true,
        )
        assertEquals(original, VisualsConfig.parse(original.toJson()))
    }

    @Test
    fun loadingMalformedFileDoesNotOverwriteIt() {
        val dir = Files.createTempDirectory("visuals-test")
        val path = dir.resolve("visuals.json")
        Files.writeString(path, "{broken")
        assertEquals(VisualsConfig(), VisualsStore.loadFrom(path))
        assertEquals("{broken", Files.readString(path))
    }

    @Test
    fun savedFileLoadsBack() {
        val dir = Files.createTempDirectory("visuals-test")
        val path = dir.resolve("visuals.json")
        val saved = VisualsConfig(fullbright = true, dayTime = 18000L, timeChanger = true)
        VisualsStore.saveTo(path, saved)
        assertEquals(saved, VisualsStore.loadFrom(path))
        assertFalse(Files.exists(dir.resolve("visuals.json.tmp")))
    }
}
