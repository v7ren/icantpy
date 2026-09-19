package net.icantpy.cosmetics.items

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

object HypixelItemData {
    /**
     * Reads a component from the backing map, skipping appearance overlays on
     * [net.minecraft.core.component.DataComponentHolder.get]. Matching and editor
     * baselines must see Hypixel's original name/dye, not the customized copy.
     */
    fun <T : Any> raw(stack: ItemStack, type: DataComponentType<T>): T? = stack.components.get(type)

    fun skyblockId(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val custom = raw(stack, DataComponents.CUSTOM_DATA) ?: return null
        val tag = custom.copyTag()
        val extra = tag.getCompoundOrEmpty("ExtraAttributes")
        return (string(extra, "id") ?: string(extra, "ID") ?: string(tag, "id") ?: string(tag, "ID"))
            ?.trim()?.takeIf { it.isNotBlank() }
    }

    fun uuid(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val custom = raw(stack, DataComponents.CUSTOM_DATA) ?: return null
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

    /** Formatting-stripped item name, used when UUID and SkyBlock id are missing. */
    fun matchName(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val rawName = raw(stack, DataComponents.CUSTOM_NAME)?.string
            ?: raw(stack, DataComponents.ITEM_NAME)?.string
            ?: stack.getItemName().string
        val stripped = ChatFormatting.stripFormatting(rawName)?.trim().orEmpty()
        return stripped.lowercase().takeIf { it.isNotBlank() }
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
