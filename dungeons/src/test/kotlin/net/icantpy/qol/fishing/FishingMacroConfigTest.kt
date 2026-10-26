package net.icantpy.qol.fishing

import kotlin.test.Test
import kotlin.test.assertEquals

class FishingMacroConfigTest {
    @Test
    fun badJsonGivesDefaults() {
        assertEquals(FishingMacroConfig(), FishingMacroConfig.parse("nope"))
        assertEquals(FishingMacroConfig(), FishingMacroConfig.parse("[]"))
    }

    @Test
    fun ordersAndClampsValues() {
        val config = FishingMacroConfig.parse(
            """{"key": 0, "reelMinMs": 900, "reelMaxMs": 100, "castMinMs": -4, "castMaxMs": 99999, "noBiteSeconds": 1}""",
        )
        assertEquals(FishingMacroConfig.UNBOUND, config.key)
        assertEquals(100, config.reelMinMs)
        assertEquals(900, config.reelMaxMs)
        assertEquals(0, config.castMinMs)
        assertEquals(FishingMacroConfig.MAX_DELAY_MS, config.castMaxMs)
        assertEquals(FishingMacroConfig.MIN_NO_BITE_SECONDS, config.noBiteSeconds)
    }

    @Test
    fun keepsABoundKey() {
        assertEquals(71, FishingMacroConfig.parse("""{"key": 71}""").key)
    }
}
