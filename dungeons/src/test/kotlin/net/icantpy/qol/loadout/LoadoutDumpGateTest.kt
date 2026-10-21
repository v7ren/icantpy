package net.icantpy.qol.loadout

import net.icantpy.qol.loadout.LoadoutDumpGate.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadoutDumpGateTest {
    private val gate = LoadoutDumpGate(armWindowMs = 30_000L, populateTimeoutMs = 2_000L)

    @Test fun firstPopulatedMenuAutoDumpsOncePerSession() {
        assertEquals(Decision.DUMP_AUTO, gate.update(0L, menuOpen = true, populated = true))
        assertTrue(gate.sessionDumped)
        assertEquals(Decision.NONE, gate.update(10L, menuOpen = true, populated = true))
        gate.update(20L, menuOpen = false, populated = false)
        assertEquals(Decision.NONE, gate.update(30L, menuOpen = true, populated = true))
    }

    @Test fun armedDumpFiresOnNextPopulatedMenuAndDisarms() {
        gate.arm(1_000L)
        assertEquals(Decision.NONE, gate.update(1_100L, menuOpen = false, populated = false))
        assertEquals(Decision.DUMP_ARMED, gate.update(5_000L, menuOpen = true, populated = true))
        assertEquals(0L, gate.armedUntil)
        assertEquals(Decision.NONE, gate.update(5_100L, menuOpen = true, populated = true))
        gate.update(5_200L, menuOpen = false, populated = false)
        assertEquals(Decision.NONE, gate.update(5_300L, menuOpen = true, populated = true))
    }

    @Test fun armedDumpWaitsForPopulatedSlots() {
        gate.arm(0L)
        assertEquals(Decision.NONE, gate.update(100L, menuOpen = true, populated = false))
        assertEquals(Decision.NONE, gate.update(1_500L, menuOpen = true, populated = false))
        assertEquals(Decision.DUMP_ARMED, gate.update(1_800L, menuOpen = true, populated = true))
    }

    @Test fun armedDumpStillRunsWhenSlotsNeverPopulate() {
        gate.arm(0L)
        assertEquals(Decision.NONE, gate.update(0L, menuOpen = true, populated = false))
        assertEquals(Decision.DUMP_ARMED, gate.update(2_000L, menuOpen = true, populated = false))
        assertTrue(gate.sessionDumped)
    }

    @Test fun expiredArmFallsBackToSessionAutoDump() {
        gate.arm(0L)
        assertEquals(Decision.DUMP_AUTO, gate.update(31_000L, menuOpen = true, populated = true))
        assertEquals(0L, gate.armedUntil)
    }

    @Test fun expiredArmAfterSessionDumpDoesNothing() {
        assertEquals(Decision.DUMP_AUTO, gate.update(0L, menuOpen = true, populated = true))
        gate.update(10L, menuOpen = false, populated = false)
        gate.arm(100L)
        gate.update(40_000L, menuOpen = false, populated = false)
        assertEquals(Decision.NONE, gate.update(40_100L, menuOpen = true, populated = true))
    }

    @Test fun emptyMenuTimeoutDoesNotConsumeSessionDump() {
        assertEquals(Decision.NONE, gate.update(0L, menuOpen = true, populated = false))
        assertEquals(Decision.NONE, gate.update(2_000L, menuOpen = true, populated = false))
        assertFalse(gate.sessionDumped)
        gate.update(2_100L, menuOpen = false, populated = false)
        assertEquals(Decision.DUMP_AUTO, gate.update(2_200L, menuOpen = true, populated = true))
    }

    @Test fun disarmCancelsPendingArm() {
        gate.arm(0L)
        gate.disarm()
        assertEquals(Decision.DUMP_AUTO, gate.update(100L, menuOpen = true, populated = true))
    }
}
