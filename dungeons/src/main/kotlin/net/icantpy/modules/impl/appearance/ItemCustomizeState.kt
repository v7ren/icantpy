package net.icantpy.modules.impl.appearance

/**
 * Immutable snapshot of everything that determines an item's *effective* appearance before the
 * editor opens. The baseline is captured once when the editor is opened so later preview changes
 * cannot shift what "reset" restores or whether a glint override is actually different.
 *
 * @param vanillaGlint the item's original foil state, excluding any custom override and excluding
 *   built-in/special renderers (NEU treats those as glint-off by default).
 * @param specialRenderer whether the item uses a built-in/special model renderer.
 * @param leatherArmor whether a leather-colour override is applicable.
 * @param vanillaLeatherColor the item's actual underlying dye, or null when not dyed/not leather.
 * @param originalNamePrefix leading legacy `§x` codes of the *unmodified* item name.
 */
data class ItemCustomizeBaseline(
    val uuid: String,
    val vanillaGlint: Boolean,
    val specialRenderer: Boolean = false,
    val leatherArmor: Boolean = false,
    val vanillaLeatherColor: ItemCustomizeColor? = null,
    val originalNamePrefix: String = "",
)

/**
 * Pure, immutable editor state and NEU-compatible reducer. All Minecraft access, rendering and
 * persistence stay outside so initialization, normalization, resets and the effective appearance
 * can be tested without a client.
 */
data class ItemCustomizeState(
    val baseline: ItemCustomizeBaseline,
    val customName: String = "",
    val glintEnabled: Boolean = baseline.vanillaGlint,
    val glintColor: ItemCustomizeColor? = null,
    val leatherColor: ItemCustomizeColor? = null,
    val customTooltip: String = "",
) {
    val effectiveGlint: Boolean get() = glintEnabled

    val leatherArmor: Boolean get() = baseline.leatherArmor

    /** Colour the glint picker should start from when no explicit override is set. */
    val glintPickerSeed: ItemCustomizeColor get() = glintColor ?: ItemCustomizeColor.DEFAULT_GLINT

    /** Colour the leather picker should start from; seeds from the item's actual dye. */
    val leatherPickerSeed: ItemCustomizeColor?
        get() = leatherColor ?: baseline.vanillaLeatherColor

    fun withName(raw: String): ItemCustomizeState = copy(customName = raw)

    fun setGlint(enabled: Boolean): ItemCustomizeState = copy(glintEnabled = enabled)

    fun setGlintColor(color: ItemCustomizeColor?): ItemCustomizeState = copy(glintColor = color)

    fun setLeatherColor(color: ItemCustomizeColor?): ItemCustomizeState = copy(leatherColor = color)

    fun withTooltip(value: String): ItemCustomizeState = copy(customTooltip = value)

    /** Reset only the glint colour, keeping the glint on/off toggle. */
    fun resetGlintColor(): ItemCustomizeState = copy(glintColor = null)

    /** Reset only the leather colour, restoring the item's underlying dye. */
    fun resetLeatherColor(): ItemCustomizeState = copy(leatherColor = null)

    /** Reset every editable value to the captured baseline. */
    fun resetAll(): ItemCustomizeState = ItemCustomizeState(baseline)

    /** Collapses editor state into the record that should be persisted. */
    fun toItemData(): CustomRenameItemData {
        val name = CustomRenameText.normalize(customName)
        val hasName = name.isNotBlank()
        val overrideGlint = glintEnabled != baseline.vanillaGlint
        val storedGlintColor = when {
            glintColor != null && glintColor != ItemCustomizeColor.DEFAULT_GLINT -> glintColor
            baseline.specialRenderer && overrideGlint && glintEnabled -> ItemCustomizeColor.DEFAULT_GLINT
            else -> null
        }
        val storedLeatherColor =
            leatherColor?.takeIf { baseline.leatherArmor && it != baseline.vanillaLeatherColor }
        return CustomRenameItemData(
            customName = name.takeIf { hasName },
            customNamePrefix = if (hasName) baseline.originalNamePrefix else "",
            overrideEnchantGlint = overrideGlint,
            enchantGlintValue = if (overrideGlint) glintEnabled else false,
            customGlintColor = storedGlintColor,
            customLeatherColor = storedLeatherColor,
            customTooltip = customTooltip.takeIf { it.isNotBlank() },
        )
    }

    companion object {
        /** Initializes editor state from stored data, falling back to the captured baseline. */
        fun from(baseline: ItemCustomizeBaseline, data: CustomRenameItemData?): ItemCustomizeState {
            if (data == null) return ItemCustomizeState(baseline)
            return ItemCustomizeState(
                baseline = baseline,
                customName = data.customName.orEmpty(),
                glintEnabled = if (data.overrideEnchantGlint) data.enchantGlintValue else baseline.vanillaGlint,
                glintColor = data.customGlintColor,
                leatherColor = data.customLeatherColor,
                customTooltip = data.customTooltip.orEmpty(),
            )
        }
    }
}
