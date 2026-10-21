package net.icantpy.qol.perf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FramePacingTest {
    private val still = ViewAngles(yawDegrees = 0f, pitchDegrees = 0f, fovDegrees = 70f)

    private fun state(
        generatedPerReal: Int = 1,
        generatedSinceReal: Int = 0,
        hasCapture: Boolean = true,
        inLevel: Boolean = true,
        screenOpen: Boolean = false,
        hudHidden: Boolean = false,
        lastGeneratedUnpainted: Boolean = false,
        rotationDeltaDegrees: Float = 5f,
        translationBlocks: Double = 0.1,
        fovChanged: Boolean = false,
        captureAgeMillis: Long = 10L,
        adaptive: Boolean = false,
        adaptiveGenerating: Boolean = true,
    ) = FramePacingState(
        generatedPerReal = generatedPerReal,
        generatedSinceReal = generatedSinceReal,
        hasCapture = hasCapture,
        inLevel = inLevel,
        screenOpen = screenOpen,
        hudHidden = hudHidden,
        lastGeneratedUnpainted = lastGeneratedUnpainted,
        rotationDeltaDegrees = rotationDeltaDegrees,
        translationBlocks = translationBlocks,
        fovChanged = fovChanged,
        captureAgeMillis = captureAgeMillis,
        adaptive = adaptive,
        adaptiveGenerating = adaptiveGenerating,
    )

    @Test
    fun `two times mode alternates generated and real frames`() {
        assertEquals(
            listOf(FrameKind.GENERATED, FrameKind.REAL, FrameKind.GENERATED, FrameKind.REAL),
            simulateCadence(generatedPerReal = 1, frames = 4),
        )
    }

    @Test
    fun `three times mode shows two generated frames per real frame`() {
        assertEquals(
            listOf(
                FrameKind.GENERATED, FrameKind.GENERATED, FrameKind.REAL,
                FrameKind.GENERATED, FrameKind.GENERATED, FrameKind.REAL,
            ),
            simulateCadence(generatedPerReal = 2, frames = 6),
        )
    }

    /** Real frames reset the generated counter, as afterLevelRender does in FrameGen. */
    private fun simulateCadence(generatedPerReal: Int, frames: Int): List<FrameKind> {
        var generatedSinceReal = 0
        return (0 until frames).map {
            val kind = FramePacing.decide(state(generatedPerReal = generatedPerReal, generatedSinceReal = generatedSinceReal))
            generatedSinceReal = if (kind == FrameKind.REAL) 0 else generatedSinceReal + 1
            kind
        }
    }

    @Test
    fun `off mode always renders real`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(generatedPerReal = 0)))
    }

    @Test
    fun `missing capture forces a real frame`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(hasCapture = false)))
    }

    @Test
    fun `open screen forces a real frame`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(screenOpen = true)))
    }

    @Test
    fun `hidden hud forces a real frame`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(hudHidden = true)))
    }

    @Test
    fun `generated frame that never reached the hud forces a real frame`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(lastGeneratedUnpainted = true)))
    }

    @Test
    fun `missing level forces a real frame`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(inLevel = false)))
    }

    @Test
    fun `large rotation forces a real frame and the limit itself does not`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(rotationDeltaDegrees = 30.5f)))
        assertEquals(FrameKind.GENERATED, FramePacing.decide(state(rotationDeltaDegrees = 30f)))
    }

    @Test
    fun `large translation forces a real frame and the limit itself does not`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(translationBlocks = 0.8)))
        assertEquals(FrameKind.GENERATED, FramePacing.decide(state(translationBlocks = 0.75)))
    }

    @Test
    fun `fov change forces a real frame`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(fovChanged = true)))
    }

    @Test
    fun `stale capture older than 100 milliseconds forces a real frame`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(captureAgeMillis = 101L)))
        assertEquals(FrameKind.GENERATED, FramePacing.decide(state(captureAgeMillis = 100L)))
    }

    @Test
    fun `nan measurements force a real frame`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(rotationDeltaDegrees = Float.NaN)))
        assertEquals(FrameKind.REAL, FramePacing.decide(state(translationBlocks = Double.NaN)))
    }

    @Test
    fun `non adaptive mode generates even above refresh`() {
        assertEquals(
            FrameKind.GENERATED,
            FramePacing.decide(state(adaptive = false, adaptiveGenerating = false)),
        )
    }

    @Test
    fun `adaptive mode renders real while the gate is off`() {
        assertEquals(FrameKind.REAL, FramePacing.decide(state(adaptive = true, adaptiveGenerating = false)))
        assertEquals(FrameKind.GENERATED, FramePacing.decide(state(adaptive = true, adaptiveGenerating = true)))
    }

    @Test
    fun `gate is ignored when adaptive is off`() {
        assertEquals(FrameKind.GENERATED, FramePacing.decide(state(adaptive = false, adaptiveGenerating = false)))
    }

    /** At refresh 60 the gate turns on below 52 fps and off above 57 fps; 52..57 is the hold band. */
    private fun gateAt(gate: AdaptiveGate, fps: Float, millis: Long, refresh: Int = 60) =
        FramePacing.advanceAdaptiveGate(gate, fps, refresh, millis)

    @Test
    fun `gate stays on inside the band`() {
        var gate = AdaptiveGate(generating = true)
        for (t in 0L..2000L step 100L) gate = gateAt(gate, 55f, t)
        assertTrue(gate.generating)
        assertNull(gate.pendingSinceMillis)
    }

    @Test
    fun `gate stays off inside the band`() {
        var gate = AdaptiveGate(generating = false)
        for (t in 0L..2000L step 100L) gate = gateAt(gate, 55f, t)
        assertFalse(gate.generating)
        assertNull(gate.pendingSinceMillis)
    }

    @Test
    fun `gate switches off only after the hold time above refresh minus 3`() {
        var gate = AdaptiveGate(generating = true)
        gate = gateAt(gate, 58f, 1000L)
        assertTrue(gate.generating)
        gate = gateAt(gate, 58f, 1499L)
        assertTrue(gate.generating, "still inside the 0.5 s hold")
        gate = gateAt(gate, 58f, 1500L)
        assertFalse(gate.generating)
    }

    @Test
    fun `gate switches on only after the hold time below refresh minus 8`() {
        var gate = AdaptiveGate(generating = false)
        gate = gateAt(gate, 50f, 0L)
        gate = gateAt(gate, 50f, 499L)
        assertFalse(gate.generating)
        gate = gateAt(gate, 50f, 500L)
        assertTrue(gate.generating)
    }

    @Test
    fun `a wanted switch that reverts before the hold time is cancelled`() {
        var gate = AdaptiveGate(generating = true)
        gate = gateAt(gate, 58f, 0L)
        gate = gateAt(gate, 55f, 300L)
        assertNull(gate.pendingSinceMillis)
        gate = gateAt(gate, 58f, 400L)
        assertTrue(gate.generating, "hold restarts from 400 ms, so no switch at 600 ms")
        gate = gateAt(gate, 58f, 600L)
        assertTrue(gate.generating)
        gate = gateAt(gate, 58f, 900L)
        assertFalse(gate.generating)
    }

    @Test
    fun `unmeasured zero rate switches on from off after the hold`() {
        var gate = AdaptiveGate(generating = false)
        gate = gateAt(gate, 0f, 0L)
        gate = gateAt(gate, 0f, 500L)
        assertTrue(gate.generating)
    }

    @Test
    fun `nan never changes an existing state`() {
        val on = gateAt(AdaptiveGate(generating = true), Float.NaN, 0L)
        assertTrue(on.generating)
        val off = gateAt(AdaptiveGate(generating = false), Float.NaN, 0L)
        assertFalse(off.generating)
        assertNull(off.pendingSinceMillis)
    }

    @Test
    fun `implausible refresh rate falls back to 60`() {
        assertEquals(60, FramePacing.refreshRateOrFallback(0))
        assertEquals(60, FramePacing.refreshRateOrFallback(-1))
        assertEquals(60, FramePacing.refreshRateOrFallback(5000))
        assertEquals(144, FramePacing.refreshRateOrFallback(144))
    }

    @Test
    fun `natural rate scales real rate back up only after generated frames`() {
        assertEquals(30f, FramePacing.naturalFps(measuredRealFps = 30f, generatedPerReal = 1, generatedInLastCycle = 0))
        assertEquals(60f, FramePacing.naturalFps(measuredRealFps = 30f, generatedPerReal = 1, generatedInLastCycle = 1))
        assertEquals(60f, FramePacing.naturalFps(measuredRealFps = 20f, generatedPerReal = 2, generatedInLastCycle = 2))
    }

    @Test
    fun `fps smoothing moves toward the instant rate and converges`() {
        var fps = 0f
        repeat(200) { fps = FramePacing.smoothFps(fps, frameIntervalSeconds = 1.0 / 50.0) }
        assertEquals(50f, fps, 0.01f)
    }

    @Test
    fun `fps smoothing ignores invalid intervals`() {
        assertEquals(42f, FramePacing.smoothFps(42f, 0.0))
        assertEquals(42f, FramePacing.smoothFps(42f, Double.NaN))
        assertEquals(42f, FramePacing.smoothFps(42f, -1.0))
    }

    @Test
    fun `fov movement needs a previous value and more than half a degree`() {
        assertFalse(FramePacing.fovMoving(previousFovDegrees = null, currentFovDegrees = 90f))
        assertFalse(FramePacing.fovMoving(previousFovDegrees = 70f, currentFovDegrees = 70.5f))
        assertTrue(FramePacing.fovMoving(previousFovDegrees = 70f, currentFovDegrees = 70.6f))
        assertTrue(FramePacing.fovMoving(previousFovDegrees = 80f, currentFovDegrees = 70f))
    }

    @Test
    fun `zero rotation warp is the identity`() {
        for (u in listOf(0f, 0.25f, 0.5f, 0.9f, 1f)) {
            for (v in listOf(0f, 0.3f, 1f)) {
                val point = assertNotNull(FramePacing.warp(u, v, still, still, aspect = 16f / 9f))
                assertTrue(abs(point[0] - u) < 1e-4f, "u $u -> ${point[0]}")
                assertTrue(abs(point[1] - v) < 1e-4f, "v $v -> ${point[1]}")
            }
        }
    }

    @Test
    fun `turning yaw right shifts the captured center to the left`() {
        val turned = still.copy(yawDegrees = 10f)
        val point = assertNotNull(FramePacing.warp(0.5f, 0.5f, still, turned, aspect = 16f / 9f))
        assertTrue(point[0] < 0.5f, "expected leftward shift, got ${point[0]}")
        assertTrue(abs(point[1] - 0.5f) < 1e-4f)
    }

    @Test
    fun `turning yaw left shifts the captured center to the right`() {
        val turned = still.copy(yawDegrees = -10f)
        val point = assertNotNull(FramePacing.warp(0.5f, 0.5f, still, turned, aspect = 16f / 9f))
        assertTrue(point[0] > 0.5f, "expected rightward shift, got ${point[0]}")
    }

    @Test
    fun `pitch down moves captured center upward on screen`() {
        val lookedDown = still.copy(pitchDegrees = 10f)
        val point = assertNotNull(FramePacing.warp(0.5f, 0.5f, still, lookedDown, aspect = 16f / 9f))
        assertTrue(point[1] < 0.5f, "expected upward shift, got ${point[1]}")
    }

    @Test
    fun `point behind the current camera returns null`() {
        val turnedAround = still.copy(yawDegrees = 180f)
        assertNull(FramePacing.warp(0.5f, 0.5f, still, turnedAround, aspect = 1f))
    }

    @Test
    fun `rotation delta measures the angle between view directions`() {
        assertEquals(0f, FramePacing.rotationDeltaDegrees(still, still), 1e-3f)
        assertEquals(90f, FramePacing.rotationDeltaDegrees(still, still.copy(yawDegrees = 90f)), 1e-2f)
        assertEquals(20f, FramePacing.rotationDeltaDegrees(still, still.copy(pitchDegrees = 20f)), 1e-2f)
    }
}
