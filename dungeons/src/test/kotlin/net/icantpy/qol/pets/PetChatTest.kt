package net.icantpy.qol.pets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetChatTest {
    @Test
    fun readsAnAutopetEquip() {
        val change = PetChat.parse("§cAutopet §eequipped your §7[Lvl 100] §dFlying Fish§e! §a§lVIEW RULE")
        assertEquals(PetChange("Flying Fish"), change)
    }

    @Test
    fun readsAManualSummonAndDespawn() {
        assertEquals(PetChange("Golden Dragon"), PetChat.parse("§aYou summoned your §6Golden Dragon§a!"))
        assertEquals(PetChange(null), PetChat.parse("§aYou despawned your §6Golden Dragon§a!"))
    }

    @Test
    fun ignoresOtherLines() {
        assertNull(PetChat.parse("A Flaming Worm surfaces from the depths!"))
        assertNull(PetChat.parse("[MVP+] Someone: you summoned your friend!"))
    }

    @Test
    fun matchesTheWantedPetIgnoringCaseAndLevelTag() {
        assertTrue(PetChat.isSamePet("Flying Fish", "flying fish"))
        assertTrue(PetChat.isSamePet("Flying Fish", "[Lvl 3] Flying Fish"))
        assertFalse(PetChat.isSamePet("Flying Fish", "Fish"))
        assertFalse(PetChat.isSamePet(null, "Flying Fish"))
        assertFalse(PetChat.isSamePet("Flying Fish", ""))
    }
}
