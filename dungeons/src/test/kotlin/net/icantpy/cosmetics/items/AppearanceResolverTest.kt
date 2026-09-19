package net.icantpy.cosmetics.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppearanceResolverTest {
    private val local = CustomRenameItemData(customLeatherColor = ItemCustomizeColor(0x112233))
    private val remote = CustomRenameItemData(customLeatherColor = ItemCustomizeColor(0xAABBCC))

    @Test
    fun uuidBearingLocalItemsUseTheStoredUuidRecord() {
        val found = AppearanceResolver.resolve(
            uuid = "item-uuid",
            skyblockId = "SUPERIOR_DRAGON_HELMET",
            kind = AppearanceOwnerKind.LOCAL,
            localByUuid = { if (it == "item-uuid") local else null },
            localBySkyblockId = { error("skyblock fallback should not run when a UUID record exists") },
            remoteBySkyblockId = { error("remote cosmetics should not apply to local items") },
        )
        assertEquals(local, found)
    }

    @Test
    fun uuidMissFallsBackToSkyblockIdThenName() {
        val byId = AppearanceResolver.resolve(
            uuid = "other-uuid",
            skyblockId = "SUPERIOR_DRAGON_HELMET",
            displayName = "superior dragon helmet",
            kind = AppearanceOwnerKind.LOCAL,
            localByUuid = { null },
            localBySkyblockId = { if (it == "SUPERIOR_DRAGON_HELMET") local else null },
            localByName = { error("name should not run when skyblock id matched") },
            remoteBySkyblockId = { error("remote cosmetics should not apply to local items") },
        )
        assertEquals(local, byId)
        val byName = AppearanceResolver.resolve(
            uuid = null,
            skyblockId = null,
            displayName = "superior dragon helmet",
            kind = AppearanceOwnerKind.UNKNOWN,
            localByUuid = { error("UUID lookup should not run") },
            localBySkyblockId = { error("skyblock lookup should not run") },
            localByName = { if (it == "superior dragon helmet") local else null },
            remoteBySkyblockId = { error("remote cosmetics should not apply in the local GUI") },
        )
        assertEquals(local, byName)
    }

    @Test
    fun uuidLessLocalAndGuiItemsUseSkyblockId() {
        val found = AppearanceResolver.resolve(
            uuid = null,
            skyblockId = "SUPERIOR_DRAGON_HELMET",
            kind = AppearanceOwnerKind.UNKNOWN,
            localByUuid = { error("UUID lookup should not run") },
            localBySkyblockId = { if (it == "SUPERIOR_DRAGON_HELMET") local else null },
            remoteBySkyblockId = { error("remote cosmetics should not apply in the local GUI") },
        )
        assertEquals(local, found)
    }

    @Test
    fun otherPlayersDoNotReceiveLocalSkyblockFallback() {
        val found = AppearanceResolver.resolve(
            uuid = null,
            skyblockId = "SUPERIOR_DRAGON_HELMET",
            displayName = "superior dragon helmet",
            kind = AppearanceOwnerKind.REMOTE_PLAYER,
            localByUuid = { error("local UUID config must not apply to other players") },
            localBySkyblockId = { error("local skyblock fallback must not apply to other players") },
            localByName = { error("local name fallback must not apply to other players") },
            remoteBySkyblockId = { if (it == "SUPERIOR_DRAGON_HELMET") remote else null },
        )
        assertEquals(remote, found)
    }

    @Test
    fun droppedAndNonPlayerEntitiesStayVanilla() {
        val found = AppearanceResolver.resolve(
            uuid = null,
            skyblockId = "SUPERIOR_DRAGON_HELMET",
            displayName = "superior dragon helmet",
            kind = AppearanceOwnerKind.OTHER,
            localByUuid = { local },
            localBySkyblockId = { local },
            localByName = { local },
            remoteBySkyblockId = { remote },
            remoteByName = { remote },
        )
        assertNull(found)
    }
}

class ItemIdentityTest {
    @Test
    fun storageKeyPrefersUuidThenSkyblockIdThenName() {
        assertEquals("item-uuid", ItemIdentity("item-uuid", "SUPERIOR", "helmet").storageKey())
        assertEquals("id:SUPERIOR", ItemIdentity(skyblockId = "SUPERIOR", displayName = "helmet").storageKey())
        assertEquals("name:helmet", ItemIdentity(displayName = "helmet").storageKey())
        assertEquals("", ItemIdentity().storageKey())
    }
}
