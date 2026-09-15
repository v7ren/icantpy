package net.icantpy.modules.impl.appearance

import net.icantpy.api.IcantpyCommandPrefix
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

    fun load() {
        skyblockIdIndex.clear()
        config = CustomRenameStore.load()
    }

    fun clearTransientCache() {
        skyblockIdIndex.clear()
    }

    fun identity(stack: ItemStack): ItemIdentity? =
        ItemIdentityResolver.resolve(ItemStackIdentity(stack))

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

    /** Client-side custom tooltip lines for an item, empty when none is configured. */
    fun customTooltip(stack: ItemStack): List<Component> {
        val raw = appearance(stack)?.customTooltip ?: return emptyList()
        return raw.split('\n').map { line -> CustomRenameText.component(line, Style.EMPTY) }
    }

    fun appearance(stack: ItemStack): CustomRenameItemData? {
        val identity = identity(stack)
        if (identity != null) {
            val data = config.item(identity)
            // Keep the SkyBlock-id fallback in sync with the real (UUID-bearing) item.
            HypixelItemData.skyblockId(stack)?.let { id ->
                if (data != null) skyblockIdIndex[id] = data else skyblockIdIndex.remove(id)
            }
            return data
        }
        // Only UUID-less stacks reach here, so this cannot recolour other players' own items.
        return HypixelItemData.skyblockId(stack)?.let { skyblockIdIndex[it] }
    }

    /** Captures the immutable baseline used by the editor. Returns null when the item has no UUID. */
    fun baseline(stack: ItemStack): ItemCustomizeBaseline? {
        val identity = identity(stack) ?: return null
        return ItemCustomizeBaseline(
            uuid = identity.uuid,
            vanillaGlint = vanillaGlint(stack),
            leatherArmor = isLeatherDyeable(stack),
            vanillaLeatherColor = underlyingLeatherColor(stack),
            originalNamePrefix = originalNamePrefix(stack),
        )
    }

    fun editorState(stack: ItemStack): ItemCustomizeState? =
        baseline(stack)?.let { ItemCustomizeState.from(it, appearance(stack)) }

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

    fun customLeatherColor(stack: ItemStack, vanilla: Int): Int =
        // Leather is always opaque; evaluate chroma so the colour can animate like a Hypixel dye.
        appearance(stack)?.customLeatherColor?.let {
            0xFF000000.toInt() or (it.argbAt(ItemCustomizeClock.elapsedSeconds()) and 0xFFFFFF)
        } ?: vanilla

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
        val rgb = stack.get(DataComponents.DYED_COLOR)?.rgb()?.and(0xFFFFFF)
            ?: (net.minecraft.world.item.component.DyedItemColor.LEATHER_COLOR and 0xFFFFFF)
        return ItemCustomizeColor(rgb)
    }

    /** Leading legacy `§x` codes of the item's *unmodified* name, as NEU reads from NBT. */
    private fun originalNamePrefix(stack: ItemStack): String {
        val name = (stack.getCustomName() ?: stack.getItemName()).string
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
        setAppearance(identity, state.toItemData())

    /** Applies editor state; [persist] false updates memory only for live preview during dragging. */
    fun applyState(identity: ItemIdentity, state: ItemCustomizeState, persist: Boolean): CustomRenameConfig {
        val next = config.update(identity, state.toItemData())
        if (persist) commit(next) else config = next
        return next
    }

    /** Flushes the in-memory configuration to disk. */
    fun persist() {
        CustomRenameStore.save(config)
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
            ?: return "this item has no Hypixel UUID; persistent renaming is unavailable"
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

data class RenameInspect(
    val identity: ItemIdentity?,
    val originalName: String,
    val customName: String?,
)
