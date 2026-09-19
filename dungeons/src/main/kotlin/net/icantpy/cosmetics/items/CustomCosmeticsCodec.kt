package net.icantpy.cosmetics.items

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.icantpy.cosmetics.morph.PlayerDisguiseConfig

data class SharedCosmeticSlot(
    val skyblockId: String? = null,
    val appearance: CustomRenameItemData,
    val displayName: String? = null,
)

data class SharedCosmetics(
    val playerUuid: String,
    val playerName: String = "",
    val updatedAt: Long = 0L,
    val slots: Map<String, SharedCosmeticSlot> = emptyMap(),
    val morphEntityId: String? = null,
) {
    fun appearanceFor(skyblockId: String?, displayName: String? = null): CustomRenameItemData? {
        val id = skyblockId?.takeIf { it.isNotBlank() }
        if (id != null) {
            slots.values.firstOrNull { it.skyblockId == id }?.appearance?.let { return it }
        }
        val name = displayName?.takeIf { it.isNotBlank() }?.lowercase()
        if (name != null) {
            slots.values.firstOrNull { it.displayName?.equals(name, ignoreCase = true) == true }?.appearance?.let { return it }
        }
        return null
    }
}

object CustomCosmeticsCodec {
    fun toJson(payload: SharedCosmetics): JsonObject = JsonObject().apply {
        addProperty("playerUuid", payload.playerUuid)
        addProperty("playerName", payload.playerName)
        addProperty("updatedAt", payload.updatedAt)
        payload.morphEntityId?.let { addProperty("morphEntityId", it) }
        add("slots", JsonObject().apply {
            payload.slots.forEach { (slot, value) ->
                add(slot, JsonObject().apply {
                    value.skyblockId?.let { addProperty("skyblockId", it) }
                    value.displayName?.let { addProperty("displayName", it) }
                    add("appearance", appearanceToJson(value.appearance))
                })
            }
        })
    }

    fun parse(json: String): SharedCosmetics? {
        val root = try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            return null
        }
        if (!root.isJsonObject) return null
        return parse(root.asJsonObject)
    }

    fun parse(obj: JsonObject): SharedCosmetics? {
        val uuid = obj.string("playerUuid")?.let(::normalizeUuid) ?: return null
        val slots = linkedMapOf<String, SharedCosmeticSlot>()
        val source = obj.get("slots")?.takeIf { it.isJsonObject }?.asJsonObject
        source?.entrySet()?.forEach { (slot, value) ->
            val body = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val skyblockId = body.string("skyblockId")?.takeIf { it.isNotBlank() }
            val displayName = body.string("displayName")?.lowercase()?.takeIf { it.isNotBlank() }
            if (skyblockId == null && displayName == null) return@forEach
            val appearance = appearanceFromJson(body.get("appearance")?.takeIf { it.isJsonObject }?.asJsonObject)
            slots[slot.lowercase()] = SharedCosmeticSlot(skyblockId, appearance, displayName)
        }
        return SharedCosmetics(
            playerUuid = uuid,
            playerName = obj.string("playerName").orEmpty(),
            updatedAt = obj.get("updatedAt")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L,
            slots = slots,
            morphEntityId = PlayerDisguiseConfig.normalizeEntityId(obj.string("morphEntityId")),
        )
    }

    fun parseBatch(json: String): Map<String, SharedCosmetics> {
        val root = try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            return emptyMap()
        }
        if (!root.isJsonObject) return emptyMap()
        val players = root.asJsonObject.get("players")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return parse(root.asJsonObject)?.let { mapOf(it.playerUuid to it) } ?: emptyMap()
        val result = linkedMapOf<String, SharedCosmetics>()
        players.entrySet().forEach { (key, value) ->
            val body = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val parsed = parse(body) ?: return@forEach
            result[parsed.playerUuid] = parsed
            if (normalizeUuid(key) == null) result[key] = parsed
        }
        return result
    }

    fun normalizeUuid(raw: String?): String? {
        val hex = raw?.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.orEmpty()
        if (hex.length != 32) return null
        val h = hex.lowercase()
        return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
    }

    internal fun appearanceToJson(item: CustomRenameItemData): JsonObject = JsonObject().apply {
        item.customName?.let { addProperty("customName", CustomRenameText.normalize(it)) }
        if (item.customNamePrefix.isNotEmpty()) addProperty("customNamePrefix", item.customNamePrefix)
        if (item.overrideEnchantGlint) {
            addProperty("overrideEnchantGlint", true)
            addProperty("enchantGlintValue", item.enchantGlintValue)
        }
        item.customGlintColor?.let { addProperty("customGlintColor", ItemCustomizeColorCodec.format(it)) }
        item.customLeatherColor?.let { addProperty("customLeatherColor", ItemCustomizeColorCodec.format(it)) }
        item.customTooltip?.let { addProperty("customTooltip", it) }
        item.customItemModel?.let { addProperty("customItemModel", it) }
        item.customArmorModel?.let { addProperty("customArmorModel", it) }
        item.headTexture?.let { addProperty("headTexture", it) }
        item.animatedHeadId?.let { addProperty("animatedHeadId", it) }
        item.trimMaterial?.let { addProperty("trimMaterial", it) }
        item.trimPattern?.let { addProperty("trimPattern", it) }
        item.customAnimatedDye?.let { addProperty("customAnimatedDye", it) }
        item.animatedKeyframes?.let { frames ->
            add("animatedKeyframes", JsonArray().apply {
                frames.forEach { frame ->
                    add(JsonObject().apply {
                        addProperty("color", frame.color and 0xFFFFFF)
                        addProperty("time", frame.time)
                    })
                }
            })
            addProperty("animatedCycleBack", item.animatedCycleBack)
            addProperty("animatedDelay", item.animatedDelay)
            addProperty("animatedDuration", item.animatedDuration)
        }
    }

    internal fun appearanceFromJson(obj: JsonObject?): CustomRenameItemData {
        if (obj == null) return CustomRenameItemData()
        return CustomRenameItemData(
            customName = obj.string("customName")?.let(CustomRenameText::normalize)?.takeIf { it.isNotBlank() },
            customNamePrefix = obj.string("customNamePrefix").orEmpty(),
            overrideEnchantGlint = obj.bool("overrideEnchantGlint"),
            enchantGlintValue = obj.bool("enchantGlintValue"),
            customGlintColor = ItemCustomizeColorCodec.parseOrNull(obj.string("customGlintColor")),
            customLeatherColor = ItemCustomizeColorCodec.parseOrNull(obj.string("customLeatherColor")),
            customTooltip = obj.string("customTooltip")?.takeIf { it.isNotBlank() },
            customItemModel = obj.string("customItemModel")?.takeIf { it.isNotBlank() },
            customArmorModel = obj.string("customArmorModel")?.takeIf { it.isNotBlank() },
            headTexture = obj.string("headTexture")?.takeIf { it.isNotBlank() },
            animatedHeadId = obj.string("animatedHeadId")?.takeIf { it.isNotBlank() },
            trimMaterial = obj.string("trimMaterial")?.takeIf { it.isNotBlank() },
            trimPattern = obj.string("trimPattern")?.takeIf { it.isNotBlank() },
            customAnimatedDye = obj.string("customAnimatedDye")?.takeIf { it.isNotBlank() },
            animatedKeyframes = obj.keyframes(),
            animatedCycleBack = obj.bool("animatedCycleBack", true),
            animatedDelay = obj.number("animatedDelay") ?: 0f,
            animatedDuration = obj.number("animatedDuration") ?: 1f,
        )
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

    private fun JsonObject.number(key: String): Float? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat

    private fun JsonObject.keyframes(): List<ItemCustomizeKeyframe>? {
        val array = get("animatedKeyframes")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val frames = array.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val color = obj.get("color")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                ?: return@mapNotNull null
            val time = obj.get("time")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat
                ?: return@mapNotNull null
            ItemCustomizeKeyframe(color and 0xFFFFFF, time.coerceIn(0f, 1f))
        }
        return frames.takeIf { it.size >= 2 }
    }
}
