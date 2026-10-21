package net.icantpy.qol.shards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShardQueryTest {
    private val catalog get() = ShardCatalog.definitions
    private val harpy get() = ShardCatalog.resolve("Harpy")!!

    @Test fun findsHarpyByShardOrAttributeOrId() {
        assertEquals("Harpy", harpy.displayName)
        assertEquals("Tree Lurker", harpy.abilityName)
        assertEquals("Harpy Shard", harpy.shardLabel)
        assertEquals("C24", harpy.shardId)
        listOf("harpy", "Harpy Shard", "tree lurker", "treelurker", "TreeLurker", "c24").forEach { query ->
            assertEquals("Harpy", catalog.single { ShardQuery.matches(it, query) }.displayName, query)
        }
        assertTrue(catalog.none { ShardQuery.matches(it, "c2") && it.displayName == "Harpy" })
        assertTrue(catalog.all { ShardQuery.matches(it, "shard") })
    }

    @Test fun fillerOnlyQueryMatchesEverything() {
        listOf("shard", "Shards", "  shard  ").forEach { query ->
            assertEquals(catalog.size, catalog.count { ShardQuery.matches(it, query) }, query)
        }
        assertTrue(catalog.none { ShardQuery.matches(it, "zzqx") })
    }

    @Test fun trackedSearchSurfacesUnlistedCatalogHits() {
        val flash = ShardCatalog.resolve("Flash")!!
        val result = ShardQuery.browse("tree lurker", ShardBrowseMode.TRACKED, catalog, listOf(flash)) { false }
        assertEquals(emptyList(), result.listed)
        assertEquals(listOf("Harpy"), result.extra.map { it.displayName })
    }

    @Test fun catalogAndMissingModes() {
        val flash = ShardCatalog.resolve("Flash")!!
        val harpy = this.harpy
        val missing = ShardQuery.browse("", ShardBrowseMode.MISSING, catalog, listOf(flash, harpy)) { it == harpy }
        assertEquals(listOf("Harpy"), missing.listed.map { it.displayName })
        val catalogHits = ShardQuery.browse("light elemental", ShardBrowseMode.CATALOG, catalog, emptyList()) { false }
        assertEquals(listOf("Flash"), catalogHits.listed.map { it.displayName })
        assertEquals(emptyList(), catalogHits.extra)
    }

    @Test fun withTrackedAddsAndRemovesWithoutMovingExisting() {
        val flash = ShardCatalog.resolve("Flash")!!
        val start = ShardChecklist("x", listOf(ShardChecklistEntry("Flash", 7)))
        assertEquals(start, ShardQuery.withTracked(start, flash, true))
        val added = ShardQuery.withTracked(start, harpy, true)
        assertEquals(listOf("Flash", "Harpy"), added.shards.map { it.name })
        assertEquals(listOf(7, 10), added.shards.map { it.targetLevel })
        assertEquals(listOf("Flash"), ShardQuery.withTracked(added, harpy, false).shards.map { it.name })
    }
}
