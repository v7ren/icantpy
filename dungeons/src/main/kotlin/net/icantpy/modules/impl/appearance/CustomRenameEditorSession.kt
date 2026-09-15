package net.icantpy.modules.impl.appearance

import net.minecraft.world.item.ItemStack

/**
 * Captures the item and UUID that the editor was opened for. The stack is copied so changing the
 * held item cannot redirect edits to a different item.
 */
object CustomRenameEditorSession {
    data class Open(val identity: ItemIdentity, val stack: ItemStack)

    @Volatile
    private var current: Open? = null

    /** Captures [stack] for editing. Returns null when there is no item or no Hypixel UUID. */
    fun capture(stack: ItemStack?): Open? {
        val item = stack ?: return null
        if (item.isEmpty) return null
        val identity = CustomRename.identity(item) ?: return null
        val open = Open(identity, item.copy())
        current = open
        return open
    }

    fun current(): Open? = current

    fun close() {
        current = null
    }
}
