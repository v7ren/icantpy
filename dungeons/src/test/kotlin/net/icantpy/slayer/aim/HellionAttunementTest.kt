package net.icantpy.slayer.aim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HellionAttunementTest {
    @Test
    fun readsAttunementFromNameTags() {
        assertEquals(HellionAttunement.ASHEN, HellionAttunement.fromTag("ASHEN ♨8 ⓆⓊⒶⓏⒾⒾ 9.8M❤"))
        assertEquals(HellionAttunement.AURIC, HellionAttunement.fromTag("AURIC ♨4 ⓉⓎⓅⒽⓄⒺⓊⓈ 9.8M❤"))
        assertEquals(HellionAttunement.SPIRIT, HellionAttunement.fromTag("SPIRIT ♨6 Quazii 3.2M❤"))
        assertEquals(HellionAttunement.CRYSTAL, HellionAttunement.fromTag("CRYSTAL ♨2 Typhoeus"))
    }

    @Test
    fun ignoresTagsWithoutAWholeAttunementWord() {
        assertNull(HellionAttunement.fromTag("☠ Inferno Demonlord IV"))
        assertNull(HellionAttunement.fromTag("CRYSTALLINE"))
        assertNull(HellionAttunement.fromTag("Spawned by: xirenaaa"))
        assertNull(HellionAttunement.fromTag(""))
    }

    @Test
    fun readsAttunedLoreLine() {
        val lore = listOf(
            "Deals massive damage to demons",
            "Attuned: Spirit",
            "Right-click to change",
        )
        assertEquals(HellionAttunement.SPIRIT, HellionAttunement.fromLore(lore))
        assertEquals(HellionAttunement.AURIC, HellionAttunement.fromLore(listOf("Attuned: Auric")))
        assertNull(HellionAttunement.fromLore(listOf("Deals damage", "Right-click to change")))
    }

    @Test
    fun mapsDaggerNames() {
        assertEquals(Dagger.TWILIGHT, Dagger.daggerOf("Twilight Dagger"))
        assertEquals(Dagger.TWILIGHT, Dagger.daggerOf("Mawdredge Dagger"))
        assertEquals(Dagger.TWILIGHT, Dagger.daggerOf("Deathripper Dagger"))
        assertEquals(Dagger.FIREDUST, Dagger.daggerOf("Firedust Dagger"))
        assertEquals(Dagger.FIREDUST, Dagger.daggerOf("Kindlebane Dagger"))
        assertEquals(Dagger.FIREDUST, Dagger.daggerOf("Pyrochaos Dagger"))
        assertNull(Dagger.daggerOf("Aspect of the End"))
    }

    @Test
    fun daggersCarryTheirAttunements() {
        assertEquals(setOf(HellionAttunement.SPIRIT, HellionAttunement.CRYSTAL), Dagger.TWILIGHT.attunements)
        assertEquals(setOf(HellionAttunement.ASHEN, HellionAttunement.AURIC), Dagger.FIREDUST.attunements)
    }
}
