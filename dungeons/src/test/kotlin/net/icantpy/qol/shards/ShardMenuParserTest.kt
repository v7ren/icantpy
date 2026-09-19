package net.icantpy.qol.shards

import kotlin.test.*

class ShardMenuParserTest {
    @Test fun recognizesPaginationAndInventoryUnknown() {
        assertTrue(ShardMenuParser.isMenu("(2/7) Attribute Menu"))
        assertNull(ShardMenuParser.parse("Diamond Sword", emptyList()).observation)
    }
    @Test fun canonicalXUsesAbilityLoreTier() {
        val d = ShardCatalog.resolve("Barbarian Duke X")!!
        val next = d.costs[3]
        val p = ShardMenuParser.parse("Barbarian Duke X", listOf("Warrior III (Combat)", "Syphon $next shards to level up!"))
        assertEquals(3, p.observation!!.level)
    }
    @Test fun parsesTitleTierAndRemaining() {
        val p=ShardMenuParser.parse("Light Elemental III",listOf("Syphon 2 shards to level up!"),"ATTRIBUTE_SHARD_LIGHT_ELEMENTAL")
        assertEquals("Flash",p.observation!!.name); assertEquals(3,p.observation.level); assertEquals(13,p.observation.syphoned)
    }
    @Test fun parsesLoreTierOwnedAndBoxIdentity() {
        val p=ShardMenuParser.parse("Flash",listOf("", "Light Elemental III (Combat)","Owned: 1 Shard","Syphon 2 more to level up!"))
        assertEquals(3,p.observation!!.level); assertEquals(1,p.observation.owned); assertEquals(13,p.observation.syphoned)
    }
    @Test fun handlesUnlockMaxAndMissingData() {
        assertEquals(0,ShardMenuParser.parse("Flash",listOf("Syphon 1 shard to unlock!")).observation!!.syphoned)
        assertEquals(96,ShardMenuParser.parse("Flash X",listOf("Syphon 1 shard to level up!")).observation!!.syphoned)
        assertNull(ShardMenuParser.parse("Flash III",emptyList()).observation!!.syphoned)
    }
    @Test fun rejectsUnknownAndDescriptionRomans() {
        assertNull(ShardMenuParser.parse("No Such Shard",emptyList()).observation)
        val p=ShardMenuParser.parse("Flash",listOf("Description says IX is good","Syphon 2 shards to level up!"))
        assertNull(p.observation!!.level)
        assertNull(ShardMenuParser.parse("Flash III",listOf("Syphon 99 shards to level up!")).observation!!.syphoned)
    }
}
