package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeapPySequenceTest {
    @Test
    fun healerWaitsForTankOnlyWhenTankRuleIsActive() {
        assertTrue(LeapPySequence.shouldDefer("mage-py-35s-healer", tankRuleActive = true, tankLeaped = false))
        assertFalse(LeapPySequence.shouldDefer("mage-py-35s-healer", tankRuleActive = false, tankLeaped = false))
        assertFalse(LeapPySequence.shouldDefer("mage-py-35s-healer", tankRuleActive = true, tankLeaped = true))
        assertFalse(LeapPySequence.shouldDefer("custom-healer", tankRuleActive = true, tankLeaped = false))
    }
}
