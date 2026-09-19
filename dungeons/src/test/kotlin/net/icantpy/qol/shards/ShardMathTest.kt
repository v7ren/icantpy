package net.icantpy.qol.shards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShardMathTest {
    private val flash = ShardDefinition("Flash", "Light Elemental", "COMMON", costs = listOf(1, 3, 5, 6, 7, 8, 10, 14, 18, 24))

    @Test fun consumedUsesUntilNextAndNeededSubtractsSyphoned() {
        assertEquals(13, ShardMath.consumed(flash, 3, 2))
        assertEquals(83, ShardMath.neededToMax(flash, ShardProgress(3, syphoned = 13)))
        assertEquals(63, ShardMath.stillToObtain(flash, ShardProgress(3, owned = 20, syphoned = 13)))
    }

    @Test fun unknownProgressIsNotInvented() {
        assertNull(ShardMath.stillToObtain(flash, ShardProgress(level = 3)))
        assertNull(ShardMath.stillToObtain(flash, ShardProgress(owned = 20)))
        assertEquals(0, ShardMath.stillToObtain(flash, ShardProgress(level = 10), target = 10))
        assertNull(ShardMath.consumed(flash, 3, 0))
        assertNull(ShardMath.stillToObtain(flash, ShardProgress(3, owned = -1, syphoned = 13)))
        assertNull(ShardMath.neededToMax(flash, ShardProgress(11, syphoned = 0)))
        assertNull(ShardMath.neededToMax(flash, ShardProgress(3, syphoned = 13), target = 11))
        assertNull(ShardMath.neededToMax(flash, ShardProgress(3, syphoned = 1)))
    }
}
