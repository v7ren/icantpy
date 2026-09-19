package net.icantpy.dungeon.leap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeapRelicStateTest {
    @Test
    fun parsePickupAcceptsAnyColor() {
        listOf("Red", "Orange", "Green", "Blue", "Purple", "Cyan", "Magenta").forEach { color ->
            assertEquals(
                LeapRelicState.Pickup("Ren_123", "${color.uppercase()}_KING_RELIC"),
                LeapRelicState.parsePickup("Ren_123 picked the Corrupted $color Relic!"),
                "Relic color: $color",
            )
        }
    }

    @Test
    fun parsePickupAcceptsPrefixIconAndFormatting() {
        assertEquals(
            LeapRelicState.Pickup("Ren_123", "PURPLE_KING_RELIC"),
            LeapRelicState.parsePickup("§6♲ §bRen_123 §fpicked the Corrupted §5Purple §fRelic!"),
        )
    }

    @Test
    fun parsePickupIgnoresUnrelatedChat() {
        assertNull(LeapRelicState.parsePickup("Ren_123 picked the Purple Relic!"))
        assertNull(LeapRelicState.parsePickup("[BOSS] Necron: All this, for nothing..."))
    }
}
