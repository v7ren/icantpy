package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapAutoLeapInputTest {
    @Test
    fun oneHeldPressOnlyProducesOneRisingEdge() {
        val first = LeapAutoLeapInputState().poll(true)
        assertTrue(first.pressed)
        assertFalse(first.state.poll(true).pressed)
        assertFalse(first.state.poll(true).state.poll(true).pressed)
    }

    @Test
    fun releasingAllowsTheNextPress() {
        val held = LeapAutoLeapInputState().poll(true).state
        val released = held.poll(false)
        assertFalse(released.pressed)
        assertTrue(released.state.poll(true).pressed)
    }

    @Test
    fun hookedAttackPreventsDuplicateTickFallback() {
        val handled = LeapAutoLeapInputState().handled()
        assertFalse(handled.poll(true).pressed)
        assertTrue(handled.poll(false).state.poll(true).pressed)
    }

    @Test
    fun idleAndLifecycleResetStartWithoutHeldState() {
        assertFalse(LeapAutoLeapInputState().poll(false).pressed)
        assertTrue(LeapAutoLeapInputState().poll(true).pressed)
    }
}
