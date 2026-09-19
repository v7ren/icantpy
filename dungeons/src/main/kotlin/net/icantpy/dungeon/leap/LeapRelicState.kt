package net.icantpy.dungeon.leap

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/** Detects the local player's M7 relic pickup without depending on another mod's private state. */
object LeapRelicState {
    private val relicIdPattern = Regex("^[A-Z]+_KING_RELIC$")
    private val pickupPattern = Regex("(\\w{1,16}) picked the Corrupted (\\w+) Relic!$")

    data class Pickup(val player: String, val relicId: String)

    fun parsePickup(line: String): Pickup? {
        val match = pickupPattern.find(net.icantpy.util.Text.strip(line)) ?: return null
        return Pickup(match.groupValues[1], "${match.groupValues[2].uppercase()}_KING_RELIC")
    }

    private var active = false
    private var heldIds: Set<String> = emptySet()

    fun update(inP5: Boolean): String? {
        if (!inP5) {
            reset()
            return null
        }

        val current = heldRelics()
        if (!active) {
            active = true
        }

        val pickedUp = (current - heldIds).firstOrNull()
        heldIds = current
        return pickedUp
    }

    fun reset() {
        active = false
        heldIds = emptySet()
    }

    internal fun isKingRelicId(id: String): Boolean = relicIdPattern.matches(id.uppercase())

    private fun heldRelics(): Set<String> {
        val player = Minecraft.getInstance().player ?: return emptySet()
        return (0 until 41).asSequence()
            .map(player.inventory::getItem)
            .mapNotNull(::relicId)
            .toSet()
    }

    private fun relicId(stack: ItemStack): String? {
        if (stack.isEmpty || stack.get(DataComponents.CUSTOM_DATA) == null) return null
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        val extra = tag.getCompoundOrEmpty("ExtraAttributes")
        val id = extra.getStringOr("id", tag.getStringOr("id", ""))
        return id.takeIf(::isKingRelicId)
    }
}
