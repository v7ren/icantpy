package net.icantpy.qol.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.client.CameraType

class FreelookConfigTest {
    @Test
    fun parseKeepsHoldToggleAndKey() {
        val parsed = FreelookConfig.parse(
            """{"enabled":false,"key":65,"mode":"hold"}""",
        )
        assertFalse(parsed.enabled)
        assertEquals(65, parsed.key)
        assertEquals(FreelookMode.HOLD, parsed.mode)
    }

    @Test
    fun missingOrInvalidFieldsUseHoldOrToggleDefault() {
        assertEquals(FreelookConfig(), FreelookConfig.parse("{not json"))
        assertEquals(FreelookMode.BOTH, FreelookConfig.parse("""{"mode":"nope"}""").mode)
        assertTrue(FreelookConfig.parse("{}").enabled)
        assertEquals(FreelookConfig.DEFAULT_KEY, FreelookConfig.parse("{}").key)
    }

    @Test
    fun modeCycleWalksHoldToggleAndBoth() {
        assertEquals(FreelookMode.TOGGLE, FreelookMode.HOLD.next())
        assertEquals(FreelookMode.BOTH, FreelookMode.TOGGLE.next())
        assertEquals(FreelookMode.HOLD, FreelookMode.BOTH.next())
    }
}

class FreelookMathTest {
    @Test
    fun turnUsesVanillaScaleAndClampsPitch() {
        val moved = FreelookMath.applyTurn(10f, 80f, 20.0, 200.0)
        assertEquals(13f, moved.yaw, 0.0001f)
        assertEquals(90f, moved.pitch, 0.0001f)
        val down = FreelookMath.applyTurn(0f, -80f, 0.0, -200.0)
        assertEquals(-90f, down.pitch, 0.0001f)
    }
}

class FreelookCameraTest {
    @Test
    fun firstPersonBecomesThirdPersonBack() {
        assertEquals(CameraType.THIRD_PERSON_BACK, FreelookCamera.whileActive(CameraType.FIRST_PERSON))
    }

    @Test
    fun alreadyThirdPersonStays() {
        assertEquals(CameraType.THIRD_PERSON_BACK, FreelookCamera.whileActive(CameraType.THIRD_PERSON_BACK))
        assertEquals(CameraType.THIRD_PERSON_FRONT, FreelookCamera.whileActive(CameraType.THIRD_PERSON_FRONT))
    }
}

class FreelookKeysTest {
    @Test
    fun holdStartsOnPressAndStopsOnRelease() {
        var state = FreelookKeyState()
        val start = FreelookKeys.step(state, FreelookMode.HOLD, down = true, now = 0L)
        assertEquals(FreelookPulse.START, start.pulse)
        assertTrue(start.state.active)
        state = start.state
        val hold = FreelookKeys.step(state, FreelookMode.HOLD, down = true, now = 40L)
        assertEquals(FreelookPulse.NONE, hold.pulse)
        val stop = FreelookKeys.step(hold.state, FreelookMode.HOLD, down = false, now = 80L)
        assertEquals(FreelookPulse.STOP, stop.pulse)
        assertFalse(stop.state.active)
    }

    @Test
    fun toggleFlipsOnEachPress() {
        var state = FreelookKeyState()
        val on = FreelookKeys.step(state, FreelookMode.TOGGLE, down = true, now = 0L)
        assertEquals(FreelookPulse.START, on.pulse)
        state = FreelookKeys.step(on.state, FreelookMode.TOGGLE, down = false, now = 10L).state
        val off = FreelookKeys.step(state, FreelookMode.TOGGLE, down = true, now = 20L)
        assertEquals(FreelookPulse.STOP, off.pulse)
        assertFalse(off.state.active)
    }

    @Test
    fun bothTapsLatchAndLongHoldPeeks() {
        var state = FreelookKeyState()
        val press = FreelookKeys.step(state, FreelookMode.BOTH, down = true, now = 1000L)
        assertEquals(FreelookPulse.START, press.pulse)
        val tap = FreelookKeys.step(press.state, FreelookMode.BOTH, down = false, now = 1100L)
        assertEquals(FreelookPulse.NONE, tap.pulse)
        assertTrue(tap.state.active)
        assertTrue(tap.state.latched)
        val second = FreelookKeys.step(tap.state, FreelookMode.BOTH, down = true, now = 1200L)
        assertEquals(FreelookPulse.STOP, second.pulse)

        val holdPress = FreelookKeys.step(FreelookKeyState(), FreelookMode.BOTH, down = true, now = 0L)
        val holdRelease = FreelookKeys.step(holdPress.state, FreelookMode.BOTH, down = false, now = 400L)
        assertEquals(FreelookPulse.STOP, holdRelease.pulse)
        assertFalse(holdRelease.state.active)
    }
}
