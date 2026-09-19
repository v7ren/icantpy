package net.icantpy.qol.stats

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.icantpy.cosmetics.items.HypixelItemData
import net.icantpy.util.Text
import net.minecraft.world.item.ItemStack
import java.util.UUID

import net.minecraft.world.entity.EquipmentSlot

enum class StatsArmorSlot(val label: String, val equipmentSlot: EquipmentSlot) {
    HEAD("Helmet", EquipmentSlot.HEAD),
    CHEST("Chestplate", EquipmentSlot.CHEST),
    LEGS("Leggings", EquipmentSlot.LEGS),
    FEET("Boots", EquipmentSlot.FEET),
    ;

    companion object {
        fun parse(value: String?): StatsArmorSlot? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

data class ItemFingerprint(
    val itemId: String?,
    val itemName: String,
    val itemUuid: String?,
) {
    fun matches(actual: ItemFingerprint): Boolean {
        if (itemUuid != null) return itemUuid == actual.itemUuid
        if (itemId != null && itemId != actual.itemId) return false
        return itemName.isBlank() || itemName.equals(actual.itemName, ignoreCase = true)
    }

    companion object {
        fun fromStack(stack: ItemStack): ItemFingerprint = ItemFingerprint(
            itemId = HypixelItemData.skyblockId(stack),
            itemName = Text.strip(stack.hoverName.string),
            itemUuid = HypixelItemData.uuid(stack),
        )
    }
}

data class ArmorPreset(
    val id: String = UUID.randomUUID().toString(),
    val armorSlot: StatsArmorSlot = StatsArmorSlot.HEAD,
    val inventorySlot: Int = -1,
    val itemId: String? = null,
    val itemName: String = "",
    val itemUuid: String? = null,
) {
    fun fingerprint(): ItemFingerprint = ItemFingerprint(itemId, itemName, itemUuid)
}

data class StatsArmorSettings(
    val enabled: Boolean = true,
    val closeAfterSwap: Boolean = false,
    val showMaskTimers: Boolean = true,
    val presets: List<ArmorPreset> = emptyList(),
) {
    fun presets(slot: StatsArmorSlot): List<ArmorPreset> = presets.filter { it.armorSlot == slot }

    fun presets(): List<ArmorPreset> = presets

    fun add(slot: StatsArmorSlot, preset: ArmorPreset): StatsArmorSettings {
        val normalized = preset.copy(armorSlot = slot)
        if (presets.any { it.armorSlot == slot && it.fingerprint() == normalized.fingerprint() }) return this
        return copy(presets = presets + normalized)
    }

    fun remove(id: String): StatsArmorSettings = copy(presets = presets.filterNot { it.id == id })

    fun move(id: String, delta: Int): StatsArmorSettings {
        val index = presets.indexOfFirst { it.id == id }
        if (index < 0 || delta == 0) return this
        val slot = presets[index].armorSlot
        val sameSlot = presets.withIndex().filter { it.value.armorSlot == slot }
        val groupIndex = sameSlot.indexOfFirst { it.index == index }
        val targetGroupIndex = (groupIndex + delta).coerceIn(0, sameSlot.lastIndex)
        if (groupIndex == targetGroupIndex) return this
        val targetIndex = sameSlot[targetGroupIndex].index
        return copy(
            presets = presets.mapIndexed { currentIndex, preset ->
                when (currentIndex) {
                    index -> presets[targetIndex]
                    targetIndex -> presets[index]
                    else -> preset
                }
            },
        )
    }

    fun clear(): StatsArmorSettings = copy(presets = emptyList())

    fun toJson(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("enabled", enabled)
        obj.addProperty("closeAfterSwap", closeAfterSwap)
        obj.addProperty("showMaskTimers", showMaskTimers)
        val array = JsonArray()
        presets.forEach { preset ->
            val item = JsonObject()
            item.addProperty("id", preset.id)
            item.addProperty("armorSlot", preset.armorSlot.name)
            item.addProperty("inventorySlot", preset.inventorySlot)
            preset.itemId?.let { item.addProperty("itemId", it) }
            item.addProperty("itemName", preset.itemName)
            preset.itemUuid?.let { item.addProperty("itemUuid", it) }
            array.add(item)
        }
        obj.add("presets", array)
        return obj
    }

    companion object {
        fun fromJson(obj: JsonObject): StatsArmorSettings {
            val defaults = StatsArmorSettings()
            val loaded = if (!obj.has("presets") || !obj.get("presets").isJsonArray) {
                emptyList()
            } else {
                obj.getAsJsonArray("presets").mapNotNull { element ->
                    runCatching {
                        val item = element.asJsonObject
                        ArmorPreset(
                            id = string(item, "id", UUID.randomUUID().toString()),
                            armorSlot = StatsArmorSlot.parse(string(item, "armorSlot", "HEAD"))
                                ?: return@runCatching null,
                            inventorySlot = int(item, "inventorySlot", -1),
                            itemId = string(item, "itemId", "").ifBlank { null },
                            itemName = string(item, "itemName", ""),
                            itemUuid = string(item, "itemUuid", "").ifBlank { null },
                        ).takeIf { it.inventorySlot >= 0 && it.itemName.isNotBlank() }
                    }.getOrNull()
                }
            }
            return StatsArmorSettings(
                enabled = bool(obj, "enabled", defaults.enabled),
                closeAfterSwap = bool(obj, "closeAfterSwap", defaults.closeAfterSwap),
                showMaskTimers = bool(obj, "showMaskTimers", defaults.showMaskTimers),
                presets = loaded,
            )
        }

        private fun string(obj: JsonObject, key: String, default: String): String =
            if (obj.has(key)) obj.get(key).asString else default

        private fun int(obj: JsonObject, key: String, default: Int): Int =
            if (obj.has(key)) obj.get(key).asInt else default

        private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
            if (obj.has(key)) obj.get(key).asBoolean else default
    }
}
