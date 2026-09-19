package net.icantpy.cosmetics.items

/**
 * Decides which cosmetic record applies to a rendered stack. UUID is preferred when present,
 * then SkyBlock id, then the stripped display name — Hypixel animated dye frames drop UUID.
 */
object AppearanceResolver {
    fun resolve(
        uuid: String?,
        skyblockId: String?,
        displayName: String? = null,
        kind: AppearanceOwnerKind,
        localByUuid: (String) -> CustomRenameItemData?,
        localBySkyblockId: (String) -> CustomRenameItemData?,
        localByName: (String) -> CustomRenameItemData? = { null },
        remoteBySkyblockId: (String) -> CustomRenameItemData?,
        remoteByName: (String) -> CustomRenameItemData? = { null },
    ): CustomRenameItemData? = when (kind) {
        AppearanceOwnerKind.LOCAL, AppearanceOwnerKind.UNKNOWN ->
            uuid?.let(localByUuid)
                ?: skyblockId?.let(localBySkyblockId)
                ?: displayName?.let(localByName)
        AppearanceOwnerKind.REMOTE_PLAYER ->
            skyblockId?.let(remoteBySkyblockId)
                ?: displayName?.let(remoteByName)
        AppearanceOwnerKind.OTHER -> null
    }
}
