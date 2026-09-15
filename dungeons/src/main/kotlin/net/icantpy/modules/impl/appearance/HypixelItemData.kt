package net.icantpy.modules.impl.appearance

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

object HypixelItemData {
    fun skyblockId(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val custom = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = custom.copyTag()
        val extra = tag.getCompoundOrEmpty("ExtraAttributes")
        return string(extra, "id") ?: string(extra, "ID") ?: string(tag, "id") ?: string(tag, "ID")
    }

    fun uuid(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val custom = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        return uuidFromTag(custom.copyTag())
    }

    fun uuidFromTag(tag: CompoundTag): String? {
        string(tag, "uuid")?.let { return it }
        string(tag, "UUID")?.let { return it }
        val extra = tag.getCompoundOrEmpty("ExtraAttributes")
        string(extra, "uuid")?.let { return it }
        string(extra, "UUID")?.let { return it }
        return null
    }

    private fun string(tag: net.minecraft.nbt.CompoundTag, key: String): String? {
        val value = tag.getStringOr(key, "")
        return value.takeIf { it.isNotBlank() }
    }
}

class ItemStackIdentity(
    private val stack: ItemStack,
) : ItemIdentitySource {
    override fun uuid(): String? = HypixelItemData.uuid(stack)
}
