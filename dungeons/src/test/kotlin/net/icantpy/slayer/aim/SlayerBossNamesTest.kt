package net.icantpy.slayer.aim

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlayerBossNamesTest {
    @Test
    fun recognisesBossTagsWithDecorations() {
        assertTrue(SlayerBossNames.isBossTag("☠ Inferno Demonlord IV 52.1M❤"))
        assertTrue(SlayerBossNames.isBossTag("ASHEN ♨8 Quazii 3.2M❤"))
        assertTrue(SlayerBossNames.isBossTag("☠ Voidgloom Seraph IV 210M❤"))
        assertTrue(SlayerBossNames.isBossTag("ASHEN ♨8 ⓆⓊⒶⓏⒾⒾ 9.8M❤"))
        assertTrue(SlayerBossNames.isBossTag("AURIC ♨4 ⓉⓎⓅⒽⓄⒺⓊⓈ 9.8M❤"))
    }

    @Test
    fun ignoresOwnerTagsAndMinions() {
        assertFalse(SlayerBossNames.isBossTag("Spawned by: xirenaaa"))
        assertFalse(SlayerBossNames.isBossTag("Smoldering Blaze 1.2M❤"))
        assertFalse(SlayerBossNames.isBossTag(""))
    }
}
