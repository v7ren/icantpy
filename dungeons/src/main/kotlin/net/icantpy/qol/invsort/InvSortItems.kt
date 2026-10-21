package net.icantpy.qol.invsort

import net.icantpy.cosmetics.items.HypixelItemData
import net.icantpy.util.Text
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

/** Minecraft-facing item identity helpers shared by capture, planning, runner and editor. */
object InvSortItems {
    /** SkyBlock id when present, otherwise the vanilla registry id. Null for empty stacks. */
    fun keyOf(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        return HypixelItemData.skyblockId(stack) ?: vanillaId(stack)
    }

    fun vanillaId(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).toString()

    fun currentKeys(menu: AbstractContainerMenu): Map<Int, String?> =
        InvSortSlots.PRESET_SLOTS.associateWith { index -> keyAt(menu, index) }

    fun keyAt(menu: AbstractContainerMenu, index: Int): String? =
        menu.slots.getOrNull(index)?.item?.let(::keyOf)

    /** Captures every non-empty armor and inventory slot. Empty slots are omitted so they stay "any item". */
    fun capture(menu: AbstractContainerMenu): Map<Int, InvSortSlot> =
        InvSortSlots.capturedSlots(InvSortSlots.PRESET_SLOTS.associateWith { index -> slotFor(menu, index) })

    private fun slotFor(menu: AbstractContainerMenu, index: Int): InvSortSlot? {
        val stack = menu.slots.getOrNull(index)?.item ?: return null
        val key = keyOf(stack) ?: return null
        return InvSortSlot(key = key, itemId = vanillaId(stack), label = Text.strip(stack.hoverName.string))
    }

    fun say(text: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(text))
    }
}
