package net.icantpy.cosmetics.items

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object CustomRenameStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(): CustomRenameConfig {
        val path = path()
        if (!Files.isRegularFile(path)) return CustomRenameConfig()
        return try {
            parse(Files.readString(path))
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to read custom rename config; using empty", exception)
            CustomRenameConfig()
        }
    }

    fun save(config: CustomRenameConfig) {
        val path = path()
        try {
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            Files.writeString(tmp, gson.toJson(toJson(config)) + "\n")
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Icantpy.LOGGER.warn("Failed to write custom rename config", exception)
        }
    }

    internal fun serialize(config: CustomRenameConfig): String = gson.toJson(toJson(config))

    fun parse(json: String): CustomRenameConfig {
        val root = try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            return CustomRenameConfig()
        }
        if (!root.isJsonObject) return CustomRenameConfig()
        val obj = root.asJsonObject
        val names = linkedMapOf<String, String>()
        val items = linkedMapOf<String, CustomRenameItemData>()
        val source = obj.get("names") ?: obj.get("customNames")
        if (source?.isJsonObject == true) {
            source.asJsonObject.entrySet().forEach { (uuid, value) ->
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return@forEach
                val name = CustomRenameText.normalize(value.asString)
                if (uuid.isNotBlank() && name.isNotBlank()) names[uuid] = name
            }
        }
        val itemSource = obj.get("items") ?: obj.get("itemData")
        if (itemSource?.isJsonObject == true) {
            itemSource.asJsonObject.entrySet().forEach { (uuid, value) ->
                if (!value.isJsonObject || uuid.isBlank()) return@forEach
                val item = value.asJsonObject
                val customName = item.string("customName")?.let(CustomRenameText::normalize)?.takeIf { it.isNotBlank() }
                    ?: names[uuid]
                items[uuid] = CustomRenameItemData(
                    customName = customName,
                    customNamePrefix = item.string("customNamePrefix").orEmpty(),
                    overrideEnchantGlint = item.bool("overrideEnchantGlint"),
                    enchantGlintValue = item.bool("enchantGlintValue"),
                    customGlintColor = item.color("customGlintColor", "customGlintColour"),
                    customLeatherColor = item.color("customLeatherColor", "customLeatherColour"),
                    customTooltip = item.string("customTooltip")?.takeIf { it.isNotBlank() },
                    customItemModel = item.string("customItemModel")?.takeIf { it.isNotBlank() },
                    customArmorModel = item.string("customArmorModel")?.takeIf { it.isNotBlank() },
                    headTexture = item.string("headTexture")?.takeIf { it.isNotBlank() },
                    animatedHeadId = item.string("animatedHeadId")?.takeIf { it.isNotBlank() },
                    trimMaterial = item.string("trimMaterial")?.takeIf { it.isNotBlank() },
                    trimPattern = item.string("trimPattern")?.takeIf { it.isNotBlank() },
                    customAnimatedDye = item.string("customAnimatedDye")?.takeIf { it.isNotBlank() },
                    animatedKeyframes = item.keyframes("animatedKeyframes"),
                    animatedCycleBack = item.bool("animatedCycleBack", default = true),
                    animatedDelay = item.float("animatedDelay") ?: 0f,
                    animatedDuration = item.float("animatedDuration") ?: 1f,
                    matchSkyblockId = item.string("matchSkyblockId")?.takeIf { it.isNotBlank() },
                    matchName = item.string("matchName")?.lowercase()?.takeIf { it.isNotBlank() },
                )
            }
        }
        names.forEach { (uuid, name) -> items.putIfAbsent(uuid, CustomRenameItemData(customName = name)) }
        val version = obj.get("version")?.asIntOrNull() ?: CustomRenameConfig.CURRENT_VERSION
        val nameChromaSpeed = obj.get("nameChromaSpeed")?.asIntOrNull()
            ?.let(CustomRenameConfig::clampNameChromaSpeed)
            ?: CustomRenameConfig.DEFAULT_NAME_CHROMA_SPEED
        return CustomRenameConfig(
            version = version,
            names = names,
            items = items,
            nameChromaSpeed = nameChromaSpeed,
            shareWithOthers = obj.get("shareWithOthers")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean == true,
        )
    }

    private fun path() = FabricLoader.getInstance().configDir.resolve("icantpy-custom-name.json")

    private fun toJson(config: CustomRenameConfig) = com.google.gson.JsonObject().apply {
        addProperty("version", CustomRenameConfig.CURRENT_VERSION)
        addProperty("nameChromaSpeed", CustomRenameConfig.clampNameChromaSpeed(config.nameChromaSpeed))
        addProperty("shareWithOthers", config.shareWithOthers)
        add("names", com.google.gson.JsonObject().apply {
            config.names.forEach { (uuid, name) -> addProperty(uuid, CustomRenameText.normalize(name)) }
        })
        add("items", com.google.gson.JsonObject().apply {
            config.items.forEach { (uuid, item) ->
                add(uuid, com.google.gson.JsonObject().apply {
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
                    item.matchSkyblockId?.let { addProperty("matchSkyblockId", it) }
                    item.matchName?.let { addProperty("matchName", it) }
                    item.animatedKeyframes?.let { frames ->
                        add("animatedKeyframes", com.google.gson.JsonArray().apply {
                            frames.forEach { frame ->
                                add(com.google.gson.JsonObject().apply {
                                    addProperty("color", frame.color and 0xFFFFFF)
                                    addProperty("time", frame.time)
                                })
                            }
                        })
                    }
                    if (item.animatedKeyframes != null) {
                        addProperty("animatedCycleBack", item.animatedCycleBack)
                        addProperty("animatedDelay", item.animatedDelay)
                        addProperty("animatedDuration", item.animatedDuration)
                    }
                })
            }
        })
    }

    private fun JsonElement.asIntOrNull(): Int? = try {
        if (isJsonPrimitive && asJsonPrimitive.isNumber) asInt else null
    } catch (_: Exception) {
        null
    }

    private fun com.google.gson.JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun com.google.gson.JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true

    private fun com.google.gson.JsonObject.bool(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

    private fun com.google.gson.JsonObject.float(key: String): Float? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat

    private fun com.google.gson.JsonObject.keyframes(key: String): List<ItemCustomizeKeyframe>? {
        val array = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
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

    private fun com.google.gson.JsonObject.color(vararg keys: String): ItemCustomizeColor? {
        for (key in keys) {
            val parsed = ItemCustomizeColorCodec.parseOrNull(string(key))
            if (parsed != null) return parsed
            if (string(key) != null) {
                Icantpy.LOGGER.warn("Skipping invalid colour '{}' for '{}'", string(key), key)
            }
        }
        return null
    }
}
