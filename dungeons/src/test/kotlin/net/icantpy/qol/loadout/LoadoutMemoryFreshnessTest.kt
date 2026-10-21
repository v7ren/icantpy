package net.icantpy.qol.loadout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadoutMemoryFreshnessTest {
    private val t0 = 1_000_000L
    private val fiveMin = LoadoutEquipped.MEMORY_FRESH_MS

    @Test
    fun unmappedEquipClearsOldNumber() {
        val memory = LoadoutMemory()
        memory.onChat("Loadout 8 is already equipped!", t0)
        assertTrue(memory.isKnownEquipped(8))
        assertFalse(memory.onChat("You equipped terms!", t0 + 1))
        assertNull(memory.equipped)
        assertFalse(memory.isKnownEquipped(8))
    }

    @Test
    fun mappedEquipUpdatesNumber() {
        val memory = LoadoutMemory()
        memory.rememberNames(listOf("terms" to 5))
        memory.onChat("Loadout 8 is already equipped!", t0)
        assertTrue(memory.onChat("You equipped terms!", t0 + 1))
        assertEquals(5, memory.equipped)
    }

    @Test
    fun staleMemoryDoesNotSkip() {
        val memory = LoadoutMemory()
        memory.onChat("You equipped Loadout 8!", t0)
        assertTrue(memory.isFreshlyKnown(8, t0 + 1_000L))
        assertTrue(memory.isFreshlyKnown(8, t0 + fiveMin))
        assertFalse(memory.isFreshlyKnown(8, t0 + fiveMin + 1))
        assertTrue(memory.isKnownEquipped(8))
    }

    @Test
    fun lookupOfMemoryNeverSkipsWhenNumberDiffers() {
        val memory = LoadoutMemory()
        memory.learn(3, t0)
        assertFalse(memory.isFreshlyKnown(8, t0))
    }

    @Test
    fun renamedAlreadyEquippedMapsThroughNameMemory() {
        val memory = LoadoutMemory()
        memory.rememberNames(listOf("lcm" to 3))
        assertTrue(memory.onChat("lcm is already equipped!", t0))
        assertEquals(3, memory.equipped)
        assertTrue(memory.isFreshlyKnown(3, t0))
    }

    @Test
    fun forgetClearsLoreLearnedNumber() {
        val memory = LoadoutMemory()
        memory.learn(8, t0)
        memory.forget()
        assertFalse(memory.isKnownEquipped(8))
        assertFalse(memory.isFreshlyKnown(8, t0))
    }
}
