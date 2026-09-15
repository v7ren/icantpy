package net.icantpy.modules.impl.appearance

data class CustomRenameConfig(
    val version: Int = CURRENT_VERSION,
    val names: Map<String, String> = emptyMap(),
    val items: Map<String, CustomRenameItemData> = emptyMap(),
    val nameChromaSpeed: Int = DEFAULT_NAME_CHROMA_SPEED,
) {
    fun withName(identity: ItemIdentity, name: String): CustomRenameConfig {
        val normalized = CustomRenameText.normalize(name)
        return if (normalized.isBlank()) {
            withoutName(identity)
        } else {
            copy(
                names = names + (identity.uuid to normalized),
                items = items + (identity.uuid to (item(identity) ?: CustomRenameItemData()).copy(customName = normalized)),
            )
        }
    }

    fun withoutName(identity: ItemIdentity): CustomRenameConfig = copy(
        names = names - identity.uuid,
        items = item(identity)?.let { items + (identity.uuid to it.copy(customName = null)) } ?: items,
    )

    fun item(identity: ItemIdentity): CustomRenameItemData? =
        items[identity.uuid] ?: names[identity.uuid]?.let { CustomRenameItemData(customName = it) }

    fun update(identity: ItemIdentity, data: CustomRenameItemData): CustomRenameConfig {
        val nextNames = if (data.customName.isNullOrBlank()) names - identity.uuid
        else names + (identity.uuid to data.customName)
        return copy(names = nextNames, items = items + (identity.uuid to data))
    }

    fun without(identity: ItemIdentity): CustomRenameConfig = copy(
        names = names - identity.uuid,
        items = items - identity.uuid,
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
)
