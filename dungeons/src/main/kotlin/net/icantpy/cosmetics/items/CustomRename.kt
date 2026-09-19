package net.icantpy.cosmetics.items

import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.gui.customize.HeadTextures
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object CustomRename {
    const val DEFAULT_GLINT_ENCODED: String = ItemCustomizeColor.DEFAULT_GLINT_ENCODED
    private const val USAGE =
        "rename <name> | rename clear | rename uuid | rename copyuuid | rename clearall"

    @Volatile
    var config: CustomRenameConfig = CustomRenameConfig()
        private set

    /**
     * Resolves server-sent cosmetic clones that drop the Hypixel UUID (e.g. Hypixel's animated
     * dyed-item frames) by their SkyBlock item id. Populated from UUID-bearing stacks and scoped so
     * other players' items with their own UUIDs are never affected.
     */
    private val skyblockIdIndex = java.util.concurrent.ConcurrentHashMap<String, CustomRenameItemData>()
    private val nameIndex = java.util.concurrent.ConcurrentHashMap<String, CustomRenameItemData>()

    fun load() {
        skyblockIdIndex.clear()
        nameIndex.clear()
        config = CustomRenameStore.load()
        rebuildIndexesFromConfig()
    }

    fun clearTransientCache() {
        skyblockIdIndex.clear()
        nameIndex.clear()
        AppearanceOwnerScope.clear()
        rebuildIndexesFromConfig()
    }

    fun identity(stack: ItemStack): ItemIdentity? =
        ItemIdentityResolver.resolve(stack)

    fun customName(stack: ItemStack): String? = appearance(stack)?.customName

    fun customItemName(stack: ItemStack, vanilla: Component): Component {
        val data = appearance(stack) ?: return vanilla
        val name = data.customName ?: return vanilla
        // Apply the captured leading formatting of the original name before the custom name.
        val combined = data.customNamePrefix + name
        return CustomRenameText.componentChroma(
            combined,
            vanilla.style,
            ItemCustomizeClock.elapsedMillis(),
            nameChromaSpeed(),
        ) { character -> Minecraft.getInstance().font.width(character.toString()) }
    }

    fun nameChromaSpeed(): Int = CustomRenameConfig.clampNameChromaSpeed(config.nameChromaSpeed)

    fun customItemModel(stack: ItemStack): String? {
        val data = appearance(stack) ?: return null
        data.customItemModel?.let { return it }
        // Hypixel often gives player heads a 2D CIT icon. Force the vanilla skull model so
        // inventory slots use PROFILE the same way the worn head layer does.
        if (stack.`is`(Items.PLAYER_HEAD) && (data.headTexture != null || data.animatedHeadId != null)) {
            return "minecraft:player_head"
        }
        return null
    }

    fun customArmorModel(stack: ItemStack): String? = appearance(stack)?.customArmorModel

    fun customHeadTexture(stack: ItemStack): String? {
        // Catalog preview skulls have no Hypixel data; substituting their PROFILE
        // makes every grid tile look like the currently saved custom head.
        if (HypixelItemData.uuid(stack) == null &&
            HypixelItemData.skyblockId(stack) == null &&
            HypixelItemData.raw(stack, DataComponents.CUSTOM_DATA) == null
        ) {
            return null
        }
        val data = appearance(stack) ?: return null
        data.animatedHeadId?.let { id ->
            HeadTextures.animateTexture(id)?.let { return it }
        }
        return data.headTexture
    }

    /** Builds the custom armour trim from the stored material/pattern ids, or null when unset. */
    fun customTrim(stack: ItemStack): net.minecraft.world.item.equipment.trim.ArmorTrim? {
        val data = appearance(stack) ?: return null
        val material = data.trimMaterial?.let { net.minecraft.resources.Identifier.tryParse(it) } ?: return null
        val pattern = data.trimPattern?.let { net.minecraft.resources.Identifier.tryParse(it) } ?: return null
        val registries = Minecraft.getInstance().level?.registryAccess() ?: return null
        val materialKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TRIM_MATERIAL, material)
        val patternKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TRIM_PATTERN, pattern)
        val materialHolder = registries.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL)
            .get(materialKey).orElse(null) ?: return null
        val patternHolder = registries.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN)
            .get(patternKey).orElse(null) ?: return null
        return net.minecraft.world.item.equipment.trim.ArmorTrim(materialHolder, patternHolder)
    }

    /** Applies a transform to an item's record, preserving the UUID key. */
    fun edit(identity: ItemIdentity, update: (CustomRenameItemData) -> CustomRenameItemData): CustomRenameConfig =
        setAppearance(identity, update(config.item(identity) ?: CustomRenameItemData()))

    /** Client-side custom tooltip lines for an item, empty when none is configured. */
    fun customTooltip(stack: ItemStack): List<Component> {
        val raw = appearance(stack)?.customTooltip ?: return emptyList()
        return raw.split('\n').map { line -> CustomRenameText.component(line, Style.EMPTY) }
    }

    fun appearance(stack: ItemStack): CustomRenameItemData? {
        val uuid = HypixelItemData.uuid(stack)
        val skyblockId = HypixelItemData.skyblockId(stack)
        val displayName = HypixelItemData.matchName(stack)
        val owner = AppearanceOwnerScope.current()
        val kind = AppearanceOwnerScope.kind(owner)
        if (kind == AppearanceOwnerKind.LOCAL || kind == AppearanceOwnerKind.UNKNOWN) {
            remember(uuid, skyblockId, displayName)
        }
        return AppearanceResolver.resolve(
            uuid = uuid,
            skyblockId = skyblockId,
            displayName = displayName,
            kind = kind,
            localByUuid = { id -> config.item(ItemIdentity(uuid = id)) },
            localBySkyblockId = { id -> skyblockIdIndex[id] ?: config.item(ItemIdentity(skyblockId = id)) },
            localByName = { name -> nameIndex[name] ?: config.item(ItemIdentity(displayName = name)) },
            remoteBySkyblockId = { id ->
                AppearanceOwnerScope.playerUuid(owner)?.let { playerUuid ->
                    CustomCosmeticsShare.appearance(playerUuid.toString(), id, null)
                }
            },
            remoteByName = { name ->
                AppearanceOwnerScope.playerUuid(owner)?.let { playerUuid ->
                    CustomCosmeticsShare.appearance(playerUuid.toString(), null, name)
                }
            },
        )
    }

    /** Local config only, ignoring whoever is currently being rendered. */
    fun localAppearance(stack: ItemStack): CustomRenameItemData? {
        val uuid = HypixelItemData.uuid(stack)
        val skyblockId = HypixelItemData.skyblockId(stack)
        val displayName = HypixelItemData.matchName(stack)
        remember(uuid, skyblockId, displayName)
        return AppearanceResolver.resolve(
            uuid = uuid,
            skyblockId = skyblockId,
            displayName = displayName,
            kind = AppearanceOwnerKind.LOCAL,
            localByUuid = { id -> config.item(ItemIdentity(uuid = id)) },
            localBySkyblockId = { id -> skyblockIdIndex[id] ?: config.item(ItemIdentity(skyblockId = id)) },
            localByName = { name -> nameIndex[name] ?: config.item(ItemIdentity(displayName = name)) },
            remoteBySkyblockId = { null },
            remoteByName = { null },
        )
    }

    fun refreshLocalIndex() {
        rebuildIndexesFromConfig()
        val player = Minecraft.getInstance().player ?: return
        var next = config
        var stamped = false
        fun consider(stack: ItemStack) {
            if (stack.isEmpty) return
            val uuid = HypixelItemData.uuid(stack)
            val skyblockId = HypixelItemData.skyblockId(stack)
            val displayName = HypixelItemData.matchName(stack)
            remember(uuid, skyblockId, displayName)
            if (uuid.isNullOrBlank()) return
            val identity = ItemIdentity(uuid = uuid, skyblockId = skyblockId, displayName = displayName)
            val data = next.item(identity) ?: return
            val updated = data.copy(
                matchSkyblockId = skyblockId ?: data.matchSkyblockId,
                matchName = displayName ?: data.matchName,
            )
            if (updated != data) {
                next = next.update(identity, updated)
                stamped = true
            }
        }
        player.inventory.getNonEquipmentItems().forEach(::consider)
        for (slot in listOf(
            net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET,
            net.minecraft.world.entity.EquipmentSlot.MAINHAND,
            net.minecraft.world.entity.EquipmentSlot.OFFHAND,
        )) {
            consider(player.getItemBySlot(slot))
        }
        if (stamped) commit(next)
    }

    fun shareWithOthers(): Boolean = config.shareWithOthers

    fun setShareWithOthers(enabled: Boolean): CustomRenameConfig {
        val next = config.copy(shareWithOthers = enabled)
        commit(next)
        CustomCosmeticsShare.onShareToggled(enabled)
        return next
    }

    internal fun parseShareCommand(rest: String): ShareCommand =
        when (rest.trim().lowercase()) {
            "", "status" -> ShareCommand.STATUS
            "on", "true", "enable", "yes" -> ShareCommand.ON
            "off", "false", "disable", "no" -> ShareCommand.OFF
            else -> ShareCommand.USAGE
        }

    private fun applyShare(rest: String): String = when (parseShareCommand(rest)) {
        ShareCommand.STATUS -> if (shareWithOthers()) {
            "sharing worn cosmetics with other icantpy users"
        } else {
            "not sharing cosmetics; use share on"
        }
        ShareCommand.ON -> {
            setShareWithOthers(true)
            "sharing worn cosmetics with other icantpy users"
        }
        ShareCommand.OFF -> {
            setShareWithOthers(false)
            "stopped sharing cosmetics"
        }
        ShareCommand.USAGE -> "share on|off"
    }

    /** Captures the immutable baseline used by the editor. Returns null when the item has no UUID. */
    fun baseline(stack: ItemStack): ItemCustomizeBaseline? {
        val identity = identity(stack) ?: return null
        return ItemCustomizeBaseline(
            uuid = identity.storageKey(),
            vanillaGlint = vanillaGlint(stack),
            leatherArmor = isLeatherDyeable(stack),
            vanillaLeatherColor = underlyingLeatherColor(stack),
            originalNamePrefix = originalNamePrefix(stack),
        )
    }

    fun editorState(stack: ItemStack): ItemCustomizeState? =
        baseline(stack)?.let { ItemCustomizeState.from(it, localAppearance(stack)) }

    fun customGlintOverride(stack: ItemStack): Boolean? = appearance(stack)?.let {
        if (it.overrideEnchantGlint) it.enchantGlintValue else null
    }

    /** Original foil value, independent of our override and of the glint-override component. */
    fun vanillaGlint(stack: ItemStack): Boolean {
        val override = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
        // Item.isFoil is the vanilla half of ItemStack.hasFoil; our mixin overrides hasFoil only.
        return override ?: stack.item.isFoil(stack)
    }

    fun effectiveGlint(stack: ItemStack): Boolean = customGlintOverride(stack) ?: stack.hasFoil()

    /** Explicit glint colour as ARGB, or null to leave vanilla glint untinted. */
    fun customGlintColor(stack: ItemStack): Int? =
        appearance(stack)?.customGlintColor?.argbAt(ItemCustomizeClock.elapsedSeconds())

    fun customLeatherColor(stack: ItemStack, vanilla: Int): Int {
        val data = appearance(stack) ?: return vanilla
        // Custom animated keyframes take priority, then a Hypixel animated dye preset, then an
        // explicitly picked colour (evaluated for chroma). Leather is always opaque.
        data.animatedKeyframes?.let { frames ->
            if (frames.size >= 2) {
                return 0xFF000000.toInt() or AnimatedDyeEvaluator.colorAt(
                    frames, data.animatedCycleBack, data.animatedDelay, data.animatedDuration,
                    ItemCustomizeClock.elapsedSeconds(),
                )
            }
        }
        data.customAnimatedDye?.let { id ->
            RepoDyes.animatedColor(id, ItemCustomizeClock.elapsedMillis())?.let { return 0xFF000000.toInt() or it }
        }
        data.customLeatherColor?.let { return 0xFF000000.toInt() or (it.argbAt(ItemCustomizeClock.elapsedSeconds()) and 0xFFFFFF) }
        return vanilla
    }

    fun glintColorText(stack: ItemStack): String =
        appearance(stack)?.customGlintColor?.let(ItemCustomizeColorCodec::format)
            ?: DEFAULT_GLINT_ENCODED

    fun leatherColorText(stack: ItemStack): String? =
        appearance(stack)?.customLeatherColor?.let(ItemCustomizeColorCodec::format)

    fun isLeatherDyeable(stack: ItemStack): Boolean = stack.item.let {
        it == Items.LEATHER_HELMET || it == Items.LEATHER_CHESTPLATE ||
            it == Items.LEATHER_LEGGINGS || it == Items.LEATHER_BOOTS
    }

    /** The item's actual underlying dye, used to seed and reset the leather picker. */
    private fun underlyingLeatherColor(stack: ItemStack): ItemCustomizeColor? {
        if (!isLeatherDyeable(stack)) return null
        val rgb = HypixelItemData.raw(stack, DataComponents.DYED_COLOR)?.rgb()?.and(0xFFFFFF)
            ?: (net.minecraft.world.item.component.DyedItemColor.LEATHER_COLOR and 0xFFFFFF)
        return ItemCustomizeColor(rgb)
    }

    /** Leading legacy `§x` codes of the item's *unmodified* name, as NEU reads from NBT. */
    private fun originalNamePrefix(stack: ItemStack): String {
        val name = (HypixelItemData.raw(stack, DataComponents.CUSTOM_NAME) ?: stack.getItemName()).string
        var index = 0
        while (index + 1 < name.length && name[index] == '§') index += 2
        return name.substring(0, index)
    }

    fun inspect(stack: ItemStack): RenameInspect = RenameInspect(
        identity = identity(stack),
        originalName = stack.getItemName().string,
        customName = customName(stack),
    )

    fun setName(identity: ItemIdentity, name: String): CustomRenameConfig {
        val next = config.withName(identity, name)
        commit(next)
        return next
    }

    fun clear(identity: ItemIdentity): CustomRenameConfig {
        val next = config.without(identity)
        commit(next)
        return next
    }

    fun resetNameAndDescription(identity: ItemIdentity): CustomRenameConfig =
        edit(identity) { it.copy(customName = null, customNamePrefix = "", customTooltip = null) }

    fun clearAll(): CustomRenameConfig {
        val next = config.copy(names = emptyMap(), items = emptyMap())
        commit(next)
        return next
    }

    fun setAppearance(identity: ItemIdentity, appearance: CustomRenameItemData): CustomRenameConfig {
        val next = config.update(identity, appearance)
        commit(next)
        return next
    }

    fun setState(identity: ItemIdentity, state: ItemCustomizeState): CustomRenameConfig =
        setAppearance(identity, state.toItemData(config.item(identity)))

    /** Applies editor state; [persist] false updates memory only for live preview during dragging. */
    fun applyState(identity: ItemIdentity, state: ItemCustomizeState, persist: Boolean): CustomRenameConfig {
        val next = config.update(identity, state.toItemData(config.item(identity)))
        if (persist) commit(next) else config = next
        return next
    }

    /** Flushes the in-memory configuration to disk. */
    fun persist() {
        CustomRenameStore.save(config)
    }

    /** Restores a previously captured configuration (used by editor "Cancel"). */
    fun restore(snapshot: CustomRenameConfig) {
        config = snapshot
        CustomRenameStore.save(snapshot)
        rebuildIndexesFromConfig()
        CustomCosmeticsShare.onLocalAppearanceChanged()
    }

    fun setGlint(identity: ItemIdentity, value: Boolean, vanilla: Boolean): CustomRenameConfig {
        val current = config.item(identity) ?: CustomRenameItemData()
        return setAppearance(
            identity,
            current.copy(
                overrideEnchantGlint = value != vanilla,
                enchantGlintValue = if (value != vanilla) value else false,
            ),
        )
    }

    fun setGlintColor(identity: ItemIdentity, color: ItemCustomizeColor?): CustomRenameConfig {
        val current = config.item(identity) ?: CustomRenameItemData()
        return setAppearance(identity, current.copy(customGlintColor = color))
    }

    fun setLeatherColor(identity: ItemIdentity, color: ItemCustomizeColor?): CustomRenameConfig {
        val current = config.item(identity) ?: CustomRenameItemData()
        return setAppearance(identity, current.copy(customLeatherColor = color))
    }

    fun setNameChromaSpeed(speed: Int): CustomRenameConfig {
        val next = config.copy(nameChromaSpeed = CustomRenameConfig.clampNameChromaSpeed(speed))
        commit(next)
        return next
    }

    fun heldItem(): ItemStack? = Minecraft.getInstance().player?.mainHandItem

    fun handleCommand(raw: String): String? {
        directRest(raw)?.let { return handleRest(it) }
        val body = IcantpyCommandPrefix.body(raw) ?: return null
        val command = body.trim()
        val lower = command.lowercase()
        if (lower == "share" || lower.startsWith("share ")) {
            return applyShare(command.substringAfter(' ', missingDelimiterValue = "").trim())
        }
        val recognized = lower == "rename" || lower.startsWith("rename ") ||
            lower == "neurename" || lower.startsWith("neurename ") ||
            lower == "neucustomize" || lower.startsWith("neucustomize ")
        if (!recognized) return null
        val rest = command.substringAfter(' ', missingDelimiterValue = "").trim()
        return handleRest(rest)
    }

    private fun handleRest(rest: String): String {
        when (rest.lowercase()) {
            "" -> return "neurename: open the editor; use set <name>, clear, clearall, uuid, or copyuuid"
            "help" -> return "neurename: open the editor; use set <name>, clear, clearall, uuid, or copyuuid"
            "clearall" -> {
                clearAll()
                return "cleared custom names for all items"
            }
            "uuid" -> return identity(heldItem() ?: ItemStack.EMPTY)?.uuid?.let { "held item UUID: $it" }
                ?: "this item has no Hypixel UUID"
            "copyuuid" -> {
                val uuid = identity(heldItem() ?: ItemStack.EMPTY)?.uuid ?: return "this item has no Hypixel UUID"
                Minecraft.getInstance().keyboardHandler.setClipboard(uuid)
                return "copied held item UUID to clipboard"
            }
        }
        val setRest = setArgument(rest)
        return applyCommand(setRest ?: rest)
    }

    /** Returns the payload after a `set ` prefix, or null when [rest] is not a set command. */
    internal fun setArgument(rest: String): String? =
        if (rest.length > 4 && rest.startsWith("set ", ignoreCase = true)) rest.substring(4) else null

    fun isEditorCommand(raw: String): Boolean {
        directRest(raw)?.let { return it.isBlank() }
        val body = IcantpyCommandPrefix.body(raw)?.trim()?.lowercase() ?: return false
        return body == "rename" || body == "neurename" || body == "neucustomize"
    }

    fun applyCommand(rest: String, stack: ItemStack? = heldItem()): String {
        val item = stack ?: return "hold an item in your main hand"
        if (item.isEmpty) return "hold an item in your main hand"
        val identity = identity(item)
            ?: return "this item has no UUID, SkyBlock id, or name to bind"
        val value = rest.trim()
        if (value.isBlank()) return USAGE
        if (value.equals("clear", ignoreCase = true) || value.equals("reset", ignoreCase = true)) {
            clear(identity)
            return "cleared custom name for ${identity.uuid}"
        }
        val name = CustomRenameText.normalize(value)
        if (name.isBlank()) return USAGE
        setName(identity, name)
        return "renamed item to $name"
    }

    private fun commit(next: CustomRenameConfig) {
        config = next
        CustomRenameStore.save(next)
        rebuildIndexesFromConfig()
        CustomCosmeticsShare.onLocalAppearanceChanged()
    }

    private fun remember(uuid: String?, skyblockId: String?, displayName: String?) {
        val data = when {
            !uuid.isNullOrBlank() -> config.item(ItemIdentity(uuid = uuid))
            !skyblockId.isNullOrBlank() -> config.item(ItemIdentity(skyblockId = skyblockId))
            !displayName.isNullOrBlank() -> config.item(ItemIdentity(displayName = displayName))
            else -> null
        } ?: return
        if (!skyblockId.isNullOrBlank()) skyblockIdIndex[skyblockId] = data
        if (!displayName.isNullOrBlank()) nameIndex[displayName] = data
    }

    private fun rebuildIndexesFromConfig() {
        skyblockIdIndex.clear()
        nameIndex.clear()
        config.items.forEach { (key, data) ->
            val skyblock = data.matchSkyblockId ?: key.removePrefix("id:").takeIf { key.startsWith("id:") }
            val name = data.matchName ?: key.removePrefix("name:").takeIf { key.startsWith("name:") }
            if (!skyblock.isNullOrBlank()) skyblockIdIndex[skyblock] = data
            if (!name.isNullOrBlank()) nameIndex[name.lowercase()] = data
        }
    }

    private fun directRest(raw: String): String? {
        val command = raw.trim()
        val prefix = EDITOR_PREFIXES
            .firstOrNull { command.equals(it, ignoreCase = true) || command.startsWith("$it ", ignoreCase = true) }
            ?: return null
        return command.substring(prefix.length).trim()
    }

    private val EDITOR_PREFIXES = listOf(
        ".neurename", ",neurename", "/neurename",
        ".neucustomize", ",neucustomize", "/neucustomize",
    )
}

internal enum class ShareCommand {
    ON,
    OFF,
    STATUS,
    USAGE,
}

data class RenameInspect(
    val identity: ItemIdentity?,
    val originalName: String,
    val customName: String?,
)
