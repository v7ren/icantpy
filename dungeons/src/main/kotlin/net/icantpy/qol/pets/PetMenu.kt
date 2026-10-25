package net.icantpy.qol.pets

import net.icantpy.cosmetics.items.HypixelItemData
import net.icantpy.util.Text
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.AbstractContainerMenu

/** A pet icon in the vanilla Pets menu. [active] is true for the pet that is out now. */
data class PetIcon(val slotIndex: Int, val level: Int, val name: String, val active: Boolean)

/** Reading the Hypixel `/pets` menu. The string rules are pure so they can be tested without a client. */
object PetMenu {
    private val TITLE = Regex("^(\\(\\d+\\s*/\\s*\\d+\\)\\s+)?Pets(\\s+\\(\\d+\\s*/\\s*\\d+\\))?$", RegexOption.IGNORE_CASE)
    private val PAGE = Regex("\\((\\d+)\\s*/\\s*(\\d+)\\)")
    private val PET_NAME = Regex("^\\[Lvl\\s*(\\d+)]\\s*(.+)$", RegexOption.IGNORE_CASE)

    /** The Hypixel menu appends the 36 player-inventory slots after its container slots. */
    private const val PLAYER_INVENTORY_SLOTS = 36

    fun isPetsTitle(title: String): Boolean = TITLE.matches(Text.strip(title).trim())

    /** Page number from a title such as "Pets (2/3)", or null when the title shows none. */
    fun pageOf(title: String): Int? =
        PAGE.find(Text.strip(title))?.groupValues?.get(1)?.toIntOrNull()

    /** Trims a typed pet name and drops a leading "[Lvl N]" the player may have copied. */
    fun cleanTarget(raw: String): String {
        val text = raw.trim().take(MAX_NAME_LENGTH)
        return PET_NAME.matchEntire(text)?.groupValues?.get(2)?.trim() ?: text
    }

    /** Reads an icon from its item name and lore; null when the item is not a pet ("Next Page", "Close"...). */
    fun iconOf(slotIndex: Int, itemName: String, lore: List<String>): PetIcon? {
        val match = PET_NAME.matchEntire(Text.strip(itemName).trim()) ?: return null
        val name = match.groupValues[2].trim().trimEnd('✦', '⭐', '★', ' ')
        if (name.isEmpty()) return null
        val active = lore.any { Text.strip(it).contains("click to despawn", ignoreCase = true) }
        return PetIcon(slotIndex, match.groupValues[1].toIntOrNull() ?: 0, name, active)
    }

    /** Exact name first, then a name that starts with the target, then one that contains it. First on the page wins. */
    fun find(icons: List<PetIcon>, target: String): PetIcon? {
        val wanted = cleanTarget(target).lowercase()
        if (wanted.isEmpty()) return null
        return icons.firstOrNull { it.name.lowercase() == wanted }
            ?: icons.firstOrNull { it.name.lowercase().startsWith(wanted) }
            ?: icons.firstOrNull { it.name.lowercase().contains(wanted) }
    }

    fun isNextPageName(itemName: String): Boolean = Text.strip(itemName).contains("next page", ignoreCase = true)

    fun icons(menu: AbstractContainerMenu): List<PetIcon> {
        val end = (menu.slots.size - PLAYER_INVENTORY_SLOTS).coerceAtLeast(0)
        return menu.slots.filter { it.index < end && !it.item.isEmpty }.mapNotNull { slot ->
            val lore = HypixelItemData.raw(slot.item, DataComponents.LORE)?.lines?.map { it.string }.orEmpty()
            iconOf(slot.index, slot.item.hoverName.string, lore)
        }
    }

    fun nextPageSlot(menu: AbstractContainerMenu): Int? {
        val end = (menu.slots.size - PLAYER_INVENTORY_SLOTS).coerceAtLeast(0)
        return menu.slots.firstOrNull { it.index < end && !it.item.isEmpty && isNextPageName(it.item.hoverName.string) }?.index
    }

    const val MAX_NAME_LENGTH = 40
}
