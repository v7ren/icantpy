package net.icantpy.cosmetics.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomCosmeticsCodecTest {
    @Test
    fun roundTripsWornSlotsWithoutItemUuids() {
        val payload = SharedCosmetics(
            playerUuid = "123e4567-e89b-12d3-a456-426614174000",
            playerName = "Ren",
            updatedAt = 42L,
            slots = mapOf(
                "head" to SharedCosmeticSlot(
                    "SUPERIOR_DRAGON_HELMET",
                    CustomRenameItemData(customLeatherColor = ItemCustomizeColor(0xFF00AA)),
                    "superior dragon helmet",
                ),
            ),
        )
        val parsed = CustomCosmeticsCodec.parse(CustomCosmeticsCodec.toJson(payload).toString())
        assertEquals(payload.playerUuid, parsed?.playerUuid)
        assertEquals("SUPERIOR_DRAGON_HELMET", parsed?.slots?.get("head")?.skyblockId)
        assertEquals(0xFF00AA, parsed?.appearanceFor("SUPERIOR_DRAGON_HELMET")?.customLeatherColor?.rgb)
        assertEquals(0xFF00AA, parsed?.appearanceFor(null, "superior dragon helmet")?.customLeatherColor?.rgb)
        assertNull(parsed?.appearanceFor("HYPERION"))
    }

    @Test
    fun morphPublishesEvenWhenWornCosmeticsAreNotShared() {
        val snapshot = SharedCosmetics(
            playerUuid = "123e4567-e89b-12d3-a456-426614174000",
            slots = mapOf(
                "head" to SharedCosmeticSlot(
                    "SUPERIOR_DRAGON_HELMET",
                    CustomRenameItemData(customLeatherColor = ItemCustomizeColor(0xFF00AA)),
                ),
            ),
            morphEntityId = "minecraft:cat",
        )
        val hidden = CustomCosmeticsShare.snapshotForPublish(snapshot, shareCosmetics = false)
        assertEquals("minecraft:cat", hidden.morphEntityId)
        assertEquals(emptyMap(), hidden.slots)
        val shared = CustomCosmeticsShare.snapshotForPublish(snapshot, shareCosmetics = true)
        assertEquals(snapshot.slots, shared.slots)
        assertEquals("minecraft:cat", shared.morphEntityId)
    }

    @Test
    fun roundTripsMorphEntityId() {
        val payload = SharedCosmetics(
            playerUuid = "123e4567-e89b-12d3-a456-426614174000",
            morphEntityId = "minecraft:cat",
        )
        val parsed = CustomCosmeticsCodec.parse(CustomCosmeticsCodec.toJson(payload).toString())
        assertEquals("minecraft:cat", parsed?.morphEntityId)
    }

    @Test
    fun batchLookupIsKeyedByNormalizedPlayerUuid() {
        val uuid = "123e4567e89b12d3a456426614174000"
        val json = """
            {"players":{"$uuid":{"playerUuid":"$uuid","slots":{
              "chest":{"skyblockId":"SUPERIOR_DRAGON_CHESTPLATE","appearance":{"customName":"Hi"}}
            }}}}
        """.trimIndent()
        val parsed = CustomCosmeticsCodec.parseBatch(json)
        val dashed = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals("Hi", parsed[dashed]?.appearanceFor("SUPERIOR_DRAGON_CHESTPLATE")?.customName)
    }

    @Test
    fun rejectsMalformedPlayerUuids() {
        assertNull(CustomCosmeticsCodec.normalizeUuid("not-a-uuid"))
        assertNull(CustomCosmeticsCodec.parse("""{"playerUuid":"nope","slots":{}}"""))
    }
}
