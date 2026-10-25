package net.icantpy.slayer.aim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DungeonAimTargetsTest {
    @Test
    fun woolColourNamesTheLivid() {
        assertEquals("Frog Livid", DungeonAimTargets.lividNameFor("green_wool"))
        assertEquals("Hockey Livid", DungeonAimTargets.lividNameFor("red_wool"))
        assertEquals("Vendetta Livid", DungeonAimTargets.lividNameFor("white_wool"))
    }

    @Test
    fun otherBlocksNameNoLivid() {
        assertNull(DungeonAimTargets.lividNameFor("stone"))
        assertNull(DungeonAimTargets.lividNameFor("orange_wool"))
    }

    @Test
    fun configRoundTripKeepsDungeonAim() {
        val c = AimConfig(bloodAim = true, lividAim = true)
        val back = AimConfig.fromJsonObject(c.toJsonObject())
        assertTrue(back.bloodAim && back.lividAim)
        assertFalse(AimConfig.fromJsonObject(AimConfig().toJsonObject()).bloodAim)
    }
}
