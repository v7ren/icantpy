package net.icantpy.cosmetics.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomRenameEnabledTest {
    private val named = CustomRenameItemData(customName = "Sharp")

    @Test
    fun enabledFlagRoundTripsThroughStoreJson() {
        for (enabled in listOf(true, false)) {
            val config = CustomRenameConfig(enabled = enabled, shareWithOthers = true)
            assertEquals(enabled, CustomRenameStore.parse(CustomRenameStore.serialize(config)).enabled)
        }
    }

    @Test
    fun oldFileWithoutEnabledKeyStaysOn() {
        assertTrue(CustomRenameStore.parse("""{"version":3,"names":{},"items":{}}""").enabled)
    }

    @Test
    fun localOwnerIsIgnoredWhileDisabled() {
        val resolved = AppearanceResolver.resolve(
            uuid = "u1", skyblockId = null, displayName = null, kind = AppearanceOwnerKind.LOCAL,
            localByUuid = { named }, localBySkyblockId = { null },
            remoteBySkyblockId = { null }, localEnabled = false,
        )
        assertNull(resolved)
    }

    @Test
    fun localOwnerResolvesWhenEnabled() {
        val resolved = AppearanceResolver.resolve(
            uuid = "u1", skyblockId = null, displayName = null, kind = AppearanceOwnerKind.LOCAL,
            localByUuid = { named }, localBySkyblockId = { null },
            remoteBySkyblockId = { null }, localEnabled = true,
        )
        assertEquals(named, resolved)
    }

    @Test
    fun otherPlayersSharedCosmeticsIgnoreTheLocalSwitch() {
        val shared = CustomRenameItemData(customName = "Shared")
        val resolved = AppearanceResolver.resolve(
            uuid = null, skyblockId = "HYPERION", displayName = null, kind = AppearanceOwnerKind.REMOTE_PLAYER,
            localByUuid = { null }, localBySkyblockId = { null },
            remoteBySkyblockId = { shared }, localEnabled = false,
        )
        assertEquals(shared, resolved)
        assertFalse(resolved == null)
    }
}
