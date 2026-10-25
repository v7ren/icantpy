package net.icantpy.qol.pets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PetSwapConfigTest {
    @Test
    fun badJsonGivesDefaults() {
        assertEquals(PetSwapConfig(), PetSwapConfig.parse("not json"))
        assertEquals(PetSwapConfig(), PetSwapConfig.parse("[1, 2]"))
    }

    @Test
    fun readsPetsAndSkipsBrokenEntries() {
        val config = PetSwapConfig.parse(
            """{"enabled": false, "pets": [{"name": "Golden Dragon", "key": 71}, {"key": 5}, 7, {"name": "  ", "key": 9}, {"name": "Tiger"}]}""",
        )
        assertFalse(config.enabled)
        assertEquals(listOf(PetBind("Golden Dragon", 71), PetBind("Tiger", PetSwapConfig.UNBOUND)), config.pets)
    }

    @Test
    fun zeroOrNegativeKeysCountAsUnbound() {
        val config = PetSwapConfig.parse("""{"pets": [{"name": "Tiger", "key": 0}, {"name": "Wolf", "key": -1}]}""")
        assertTrue(config.pets.all { it.key == PetSwapConfig.UNBOUND })
    }

    @Test
    fun addEditAndRemove() {
        val added = PetSwapConfig().withAdded(" [Lvl 5] Tiger ")
        assertEquals(listOf(PetBind("Tiger")), added.pets)
        assertEquals(added, added.withAdded("   "))
        val bound = added.withPet(0) { it.copy(key = 70) }
        assertEquals(70, bound.pets.single().key)
        assertEquals(bound, bound.withPet(5) { it.copy(key = 1) })
        assertTrue(bound.withRemoved(0).pets.isEmpty())
        assertEquals(bound, bound.withRemoved(3))
    }

    @Test
    fun readsDelaySettingsAndOrdersAndClampsThem() {
        val config = PetSwapConfig.parse("""{"instant": true, "delayMinMs": 500, "delayMaxMs": 100}""")
        assertTrue(config.instant)
        assertEquals(100, config.delayMinMs)
        assertEquals(500, config.delayMaxMs)
        val clamped = PetSwapConfig.parse("""{"delayMinMs": -5, "delayMaxMs": 99999}""")
        assertEquals(0, clamped.delayMinMs)
        assertEquals(PetSwapConfig.MAX_DELAY_MS, clamped.delayMaxMs)
        val defaults = PetSwapConfig.parse("{}")
        assertFalse(defaults.instant)
        assertEquals(PetSwapConfig.DEFAULT_DELAY_MIN_MS, defaults.delayMinMs)
    }

    @Test
    fun readsCastSwapSettings() {
        val config = PetSwapConfig.parse("""{"swapOnCast": true, "castPet": " [Lvl 3] Flying Fish "}""")
        assertTrue(config.swapOnCast)
        assertEquals("Flying Fish", config.castPet)
        val defaults = PetSwapConfig.parse("{}")
        assertFalse(defaults.swapOnCast)
        assertEquals("", defaults.castPet)
    }

    @Test
    fun petCountIsCapped() {
        var config = PetSwapConfig()
        repeat(PetSwapConfig.MAX_PETS + 4) { config = config.withAdded("Pet $it") }
        assertEquals(PetSwapConfig.MAX_PETS, config.pets.size)
    }
}
