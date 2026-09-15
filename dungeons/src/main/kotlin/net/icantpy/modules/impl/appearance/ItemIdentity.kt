package net.icantpy.modules.impl.appearance

@JvmInline
value class ItemIdentity(val uuid: String)

interface ItemIdentitySource {
    fun uuid(): String?
}

object ItemIdentityResolver {
    fun resolve(source: ItemIdentitySource): ItemIdentity? {
        val uuid = source.uuid()?.trim().orEmpty()
        if (uuid.isEmpty()) return null
        return ItemIdentity(uuid)
    }
}
