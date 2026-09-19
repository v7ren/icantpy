package net.icantpy.cosmetics.items

import net.icantpy.api.IcantpyCommandPrefix
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

/**
 * Lightweight held/equipped item inspection for troubleshooting custom appearance. Prints the
 * components that can drive leather colour/model animation without requiring an external NBT viewer.
 *
 * Usage: `iteminfo [held|head|chest|legs|feet|all]` (default `all`).
 */
object ItemInfo {
    private val SLOTS = linkedMapOf(
        "head" to EquipmentSlot.HEAD,
        "chest" to EquipmentSlot.CHEST,
        "legs" to EquipmentSlot.LEGS,
        "feet" to EquipmentSlot.FEET,
    )

    fun handle(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw)?.trim()?.lowercase() ?: return null
        val parts = body.split(Regex("\\s+"))
        if (parts.firstOrNull() != "iteminfo") return null
        return when (val target = parts.getOrNull(1) ?: "all") {
            "held", "hand" -> describeHeld()
            "all" -> describeAll()
            in SLOTS.keys -> describeSlot(target, SLOTS.getValue(target))
            else -> "iteminfo slots: held, head, chest, legs, feet, all"
        }
    }

    private fun describeHeld(): String {
        val held = CustomRename.heldItem()
        return if (held == null || held.isEmpty) "held: none" else "held:\n" + describe(held)
    }

    private fun describeAll(): String {
        val lines = mutableListOf(describeHeld())
        for ((label, slot) in SLOTS) lines += describeSlot(label, slot)
        return lines.joinToString("\n\n")
    }

    private fun describeSlot(label: String, slot: EquipmentSlot): String {
        val player = Minecraft.getInstance().player ?: return "$label: no player"
        val stack = player.getItemBySlot(slot)
        return if (stack.isEmpty) "$label: none" else "$label:\n" + describe(stack)
    }

    fun describe(stack: ItemStack): String {
        val identity = CustomRename.identity(stack)
        val lines = mutableListOf<String>()
        lines += "item: ${stack.item} x${stack.count}"
        lines += "uuid: ${identity?.uuid ?: "none"}"
        lines += "leather override: ${CustomRename.leatherColorText(stack) ?: "none"}"
        lines += "applied colour: ${if (CustomRename.customLeatherColor(stack, 0) != 0) "yes" else "no"}"
        lines += "dyed_color: ${stack.get(DataComponents.DYED_COLOR) ?: "none"}"
        lines += "custom_model_data: ${stack.get(DataComponents.CUSTOM_MODEL_DATA) ?: "none"}"
        lines += "item_model: ${stack.get(DataComponents.ITEM_MODEL) ?: "default"}"
        lines += "custom_name: ${stack.get(DataComponents.CUSTOM_NAME)?.string ?: "none"}"
        val data = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.toString().orEmpty()
        lines += "custom_data: ${if (data.isEmpty()) "none" else data.take(400)}"
        return lines.joinToString("\n")
    }
}
