package net.icantpy.qol.invsort

/** Menu slot indices of the vanilla InventoryMenu that inventory sorting may touch. */
object InvSortSlots {
    const val MAIN_FIRST = 9
    const val MAIN_LAST = 35
    const val HOTBAR_FIRST = 36
    const val HOTBAR_LAST = 43

    /** Hotbar slot 9 (menu index 44) is the Hypixel SkyBlock menu star and is never touched. */
    const val LOCKED_STAR = 44

    /** Vanilla armor slots, in the order head, chest, legs, feet. */
    const val ARMOR_FIRST = 5
    const val ARMOR_LAST = 8

    val SORTABLE: List<Int> = (MAIN_FIRST..HOTBAR_LAST).toList()
    val ARMOR: List<Int> = (ARMOR_FIRST..ARMOR_LAST).toList()

    /** Every slot a preset may record: armor first, then the inventory. */
    val PRESET_SLOTS: List<Int> = ARMOR + SORTABLE

    /** Tooltip names for [ARMOR], in the same order. */
    val ARMOR_NAMES: List<String> = listOf("Helmet", "Chest", "Legs", "Boots")

    fun isSortable(index: Int): Boolean = index in MAIN_FIRST..HOTBAR_LAST

    fun isArmor(index: Int): Boolean = index in ARMOR_FIRST..ARMOR_LAST

    fun isPresetSlot(index: Int): Boolean = isSortable(index) || isArmor(index)

    /** Keeps only recordable slots whose stack was present; empty stacks are passed as null. */
    fun capturedSlots(entries: Map<Int, InvSortSlot?>): Map<Int, InvSortSlot> =
        entries.filterKeys { isPresetSlot(it) }.mapNotNull { (index, slot) -> slot?.let { index to it } }.toMap()
}

object InvSortLimits {
    const val MAX_PRESETS = 50
    const val MAX_NAME_LENGTH = 32
    const val MIN_CLICK_DELAY_MS = 0
    const val MAX_CLICK_DELAY_MS = 1_000
    const val MAX_OPEN_DELAY_MS = 2_000
    const val MAX_BUTTON_OFFSET = 1_000
    const val MAX_KEY_LENGTH = 200
    const val MAX_LABEL_LENGTH = 120

    /** GLFW key code meaning "no hotkey". */
    const val NO_HOTKEY = -1
}

/**
 * One desired inventory slot. [key] identifies the stack (SkyBlock id, or the vanilla registry id
 * when the item has none). [itemId] is only used to draw the icon, [label] is the display name.
 */
data class InvSortSlot(val key: String, val itemId: String, val label: String)

/**
 * A named target layout. [hotkey] is a GLFW key code, or [InvSortLimits.NO_HOTKEY].
 * [showButton] only controls the button beside the inventory; hotkeys, triggers and commands work either way.
 */
data class InvSortPreset(
    val name: String,
    val slots: Map<Int, InvSortSlot> = emptyMap(),
    val hotkey: Int = InvSortLimits.NO_HOTKEY,
    val showButton: Boolean = true,
    /** Hotbar-only mode: the sort uses number-key swaps and never holds an item on the cursor. */
    val hotbarSwap: Boolean = false,
) {
    companion object {
        /** Returns the trimmed name, or null when it is blank or too long. */
        fun cleanName(raw: String): String? {
            val trimmed = raw.trim()
            return trimmed.takeIf { it.isNotEmpty() && it.length <= InvSortLimits.MAX_NAME_LENGTH }
        }
    }
}
