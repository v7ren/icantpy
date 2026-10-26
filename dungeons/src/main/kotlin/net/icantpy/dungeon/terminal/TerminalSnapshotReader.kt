package net.icantpy.dungeon.terminal

import net.icantpy.util.Text
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

/** Turns a live container menu into a [TerminalSnapshot]. Client thread only. */
object TerminalSnapshotReader {
    private const val PLAYER_INVENTORY_SLOTS = 36

    fun read(menu: AbstractContainerMenu, title: String): TerminalSnapshot {
        val containerSlots = containerSlotCount(menu)
        val items = (0 until containerSlots).mapNotNull { index -> itemOf(index, menu.slots[index].item) }
        return TerminalSnapshot(title, containerSlots / TerminalSnapshot.COLUMNS, items)
    }

    /** Menu slots that belong to the terminal itself; the player inventory follows them in every chest menu. */
    fun containerSlotCount(menu: AbstractContainerMenu): Int =
        (menu.slots.size - PLAYER_INVENTORY_SLOTS).coerceIn(0, menu.slots.size)

    fun itemOf(slot: Int, stack: ItemStack): TerminalItem? {
        if (stack.isEmpty) return null
        return TerminalItem(
            slot = slot,
            itemId = BuiltInRegistries.ITEM.getKey(stack.item).path,
            count = stack.count,
            name = Text.strip(stack.hoverName.string),
            glint = serverGlint(stack),
        )
    }

    /**
     * The glint the server asked for. ItemStack.hasFoil is not used because icantpy's own item customizer can
     * override it through a mixin.
     */
    private fun serverGlint(stack: ItemStack): Boolean =
        stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) ?: stack.item.isFoil(stack)
}
