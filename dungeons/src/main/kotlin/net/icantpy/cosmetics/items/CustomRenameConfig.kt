package net.icantpy.cosmetics.items

data class CustomRenameConfig(
    val version: Int = CURRENT_VERSION,
    val names: Map<String, String> = emptyMap(),
    val items: Map<String, CustomRenameItemData> = emptyMap(),
    val nameChromaSpeed: Int = DEFAULT_NAME_CHROMA_SPEED,
    val shareWithOthers: Boolean = false,
) {
    fun withName(identity: ItemIdentity, name: String): CustomRenameConfig {
        val key = identity.storageKey()
        if (key.isBlank()) return this
        val normalized = CustomRenameText.normalize(name)
        return if (normalized.isBlank()) {
            withoutName(identity)
        } else {
            copy(
                names = names + (key to normalized),
                items = items + (key to stamp(identity, item(identity) ?: CustomRenameItemData()).copy(customName = normalized)),
            )
        }
    }

    fun withoutName(identity: ItemIdentity): CustomRenameConfig {
        val key = identity.storageKey()
        if (key.isBlank()) return this
        return copy(
            names = names - key,
            items = item(identity)?.let { items + (key to it.copy(customName = null)) } ?: items,
        )
    }

    fun item(identity: ItemIdentity): CustomRenameItemData? {
        val key = identity.storageKey()
        if (key.isNotBlank()) {
            items[key]?.let { return it }
            names[key]?.let { return CustomRenameItemData(customName = it) }
        }
        if (identity.uuid.isNotBlank()) {
            items[identity.uuid]?.let { return it }
            names[identity.uuid]?.let { return CustomRenameItemData(customName = it) }
        }
        identity.skyblockId?.let { id ->
            items["id:$id"]?.let { return it }
            items.values.firstOrNull { it.matchSkyblockId.equals(id, ignoreCase = true) }?.let { return it }
        }
        identity.displayName?.let { name ->
            val folded = name.lowercase()
            items["name:$folded"]?.let { return it }
            items.values.firstOrNull { it.matchName.equals(folded, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    fun update(identity: ItemIdentity, data: CustomRenameItemData): CustomRenameConfig {
        val key = identity.storageKey()
        if (key.isBlank()) return this
        val stamped = stamp(identity, data)
        val nextNames = if (stamped.customName.isNullOrBlank()) names - key
        else names + (key to stamped.customName)
        return copy(names = nextNames, items = items + (key to stamped))
    }

    fun without(identity: ItemIdentity): CustomRenameConfig {
        val key = identity.storageKey()
        if (key.isBlank()) return this
        return copy(
            names = names - key,
            items = items - key,
        )
    }

    private fun stamp(identity: ItemIdentity, data: CustomRenameItemData): CustomRenameItemData = data.copy(
        matchSkyblockId = identity.skyblockId?.takeIf { it.isNotBlank() } ?: data.matchSkyblockId,
        matchName = identity.displayName?.takeIf { it.isNotBlank() } ?: data.matchName,
    )

    companion object {
        // Version 3 stores structured colours (rgb/alpha/chromaSpeed) instead of raw strings.
        const val CURRENT_VERSION: Int = 3
        const val DEFAULT_NAME_CHROMA_SPEED: Int = 100
        const val MIN_NAME_CHROMA_SPEED: Int = 10
        const val MAX_NAME_CHROMA_SPEED: Int = 5000

        fun clampNameChromaSpeed(value: Int): Int =
            value.coerceIn(MIN_NAME_CHROMA_SPEED, MAX_NAME_CHROMA_SPEED)
    }
}

data class CustomRenameItemData(
    val customName: String? = null,
    val customNamePrefix: String = "",
    val overrideEnchantGlint: Boolean = false,
    val enchantGlintValue: Boolean = false,
    val customGlintColor: ItemCustomizeColor? = null,
    val customLeatherColor: ItemCustomizeColor? = null,
    /** Client-side custom tooltip/lore lines, `\n`-separated, legacy `§` formatting. */
    val customTooltip: String? = null,
    /** Skyblocker-style visual overrides (all optional, keyed by Hypixel UUID). */
    val customItemModel: String? = null,
    val customArmorModel: String? = null,
    val headTexture: String? = null,
    val animatedHeadId: String? = null,
    val trimMaterial: String? = null,
    val trimPattern: String? = null,
    /** Hypixel animated dye preset id (played client-side); overrides the static dye colour. */
    val customAnimatedDye: String? = null,
    /** Skyblocker-style custom animated dye keyframes; overrides the preset dye when set. */
    val animatedKeyframes: List<ItemCustomizeKeyframe>? = null,
    val animatedCycleBack: Boolean = true,
    val animatedDelay: Float = 0f,
    val animatedDuration: Float = 1f,
    /** SkyBlock id of the item this record was saved from, used when Hypixel drops the UUID. */
    val matchSkyblockId: String? = null,
    /** Formatting-stripped display name used when UUID and SkyBlock id are both missing. */
    val matchName: String? = null,
) {
    fun hasOverride(): Boolean =
        !customName.isNullOrBlank() ||
            customNamePrefix.isNotBlank() ||
            overrideEnchantGlint ||
            customGlintColor != null ||
            customLeatherColor != null ||
            !customTooltip.isNullOrBlank() ||
            !customItemModel.isNullOrBlank() ||
            !customArmorModel.isNullOrBlank() ||
            !headTexture.isNullOrBlank() ||
            !animatedHeadId.isNullOrBlank() ||
            !trimMaterial.isNullOrBlank() ||
            !trimPattern.isNullOrBlank() ||
            !customAnimatedDye.isNullOrBlank() ||
            !animatedKeyframes.isNullOrEmpty()
}

/** A single colour/time stop of a custom animated dye. [time] is `0..1`. */
data class ItemCustomizeKeyframe(val color: Int, val time: Float)
