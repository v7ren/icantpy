package net.icantpy.cosmetics.items

import net.minecraft.world.item.ItemStack

data class ItemIdentity(
    val uuid: String = "",
    val skyblockId: String? = null,
    val displayName: String? = null,
) {
    fun storageKey(): String {
        if (uuid.isNotBlank()) return uuid
        val id = skyblockId?.trim().orEmpty()
        if (id.isNotBlank()) return "id:$id"
        val name = displayName?.trim().orEmpty()
        if (name.isNotBlank()) return "name:$name"
        return ""
    }

    fun isBound(): Boolean = storageKey().isNotBlank()
}

interface ItemIdentitySource {
    fun uuid(): String?
}

object ItemIdentityResolver {
    fun resolve(source: ItemIdentitySource): ItemIdentity? {
        val uuid = source.uuid()?.trim().orEmpty()
        if (uuid.isEmpty()) return null
        return ItemIdentity(uuid = uuid)
    }

    fun resolve(stack: ItemStack): ItemIdentity? {
        if (stack.isEmpty) return null
        val uuid = HypixelItemData.uuid(stack)?.trim().orEmpty()
        val skyblockId = HypixelItemData.skyblockId(stack)
        val displayName = HypixelItemData.matchName(stack)
        if (uuid.isEmpty() && skyblockId == null && displayName == null) return null
        return ItemIdentity(uuid = uuid, skyblockId = skyblockId, displayName = displayName)
    }
}
