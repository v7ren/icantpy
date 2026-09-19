package net.icantpy.qol.shards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShardCatalogTest {
    @Test fun bundledCatalogHasRealCurves() {
        assertEquals(322, ShardCatalog.definitions.size)
        val flash = ShardCatalog.resolve("Flash")!!
        assertEquals("Light Elemental", flash.abilityName)
        assertEquals("ATTRIBUTE_SHARD_LIGHT_ELEMENTAL;1", flash.internalName)
        assertEquals("SHARD_FLASH", flash.bazaarName)
        assertEquals(96, flash.costs.sum())
    }

    @Test fun legacyNamesResolveToCanonicalDefinitions() {
        assertEquals("Tempest", ShardCatalog.resolve("Storm")!!.displayName)
        assertEquals("Inferno Demonlord", ShardCatalog.resolve("Burningsoul")!!.displayName)
        assertEquals("Inferno Demonlord", ShardCatalog.resolve("Burning Soul")!!.displayName)
        assertEquals("Barbarian Duke X", ShardCatalog.resolve("Barbarian Duke")!!.displayName)
        assertEquals("End Stone Protector", ShardCatalog.resolve("Endstone Protector")!!.displayName)
    }

    @Test fun defaultsAreExactTargets() {
        val checklist = ShardCatalog.defaultChecklist()
        assertEquals("Blaze Slayer shards", checklist.name)
        assertEquals(45, checklist.shards.size)
        assertEquals(45, checklist.shards.map { it.name }.distinct().size)
        assertTrue(checklist.shards.all { it.targetLevel == 10 })
        assertTrue(checklist.shards.any { it.name == "Ghost" })
        assertTrue(checklist.shards.any { it.name == "Tempest" })
    }

    @Test fun resolvesIdentifiersAndRejectsUnknown() {
        assertNull(ShardCatalog.resolve(""))
        assertEquals("Flash", ShardCatalog.resolve("ATTRIBUTE_SHARD_LIGHT_ELEMENTAL")!!.displayName)
        assertEquals("Flash", ShardCatalog.resolve("ATTRIBUTE_SHARD_LIGHT_ELEMENTAL;1")!!.displayName)
        assertEquals("Flash", ShardCatalog.resolve("SHARD_FLASH")!!.displayName)
        assertNull(ShardCatalog.resolve("definitely not a shard"))
        assertEquals("Echo of Elemental", ShardCatalog.resolve("Starborn")!!.abilityName)
    }
}
