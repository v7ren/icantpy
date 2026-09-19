package net.icantpy.cosmetics.items

import net.icantpy.api.IcantpyCommandPrefix

/**
 * Command entry points for the Skyblocker-style visual overrides. These are a stopgap while the
 * tabbed customizer GUI exposes the same data.
 */
object RenameVisuals {
    private val HEADS = setOf("model", "armormodel", "head", "glint", "clearvisual")

    fun handle(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw)?.trim() ?: return null
        val parts = body.split(Regex("\\s+"), limit = 2)
        val command = parts[0].lowercase()
        if (command !in HEADS) return null
        val stack = CustomRename.heldItem() ?: return "hold an item in your main hand"
        if (stack.isEmpty) return "hold an item in your main hand"
        val identity = CustomRename.identity(stack) ?: return "this item has no UUID, SkyBlock id, or name to bind"
        val argument = parts.getOrNull(1)?.trim().orEmpty()
        val clear = argument.isBlank() || argument.equals("clear", ignoreCase = true)

        return when (command) {
            "clearvisual" -> {
                CustomRename.edit(identity) {
                    it.copy(
                        customItemModel = null,
                        customArmorModel = null,
                        headTexture = null,
                        animatedHeadId = null,
                        overrideEnchantGlint = false,
                        enchantGlintValue = false,
                    )
                }
                "cleared item model, head texture and glint override"
            }
            "model" -> {
                CustomRename.edit(identity) { it.copy(customItemModel = argument.takeUnless { clear }) }
                "item model: ${if (clear) "(default)" else argument}"
            }
            "armormodel" -> {
                CustomRename.edit(identity) { it.copy(customArmorModel = argument.takeUnless { clear }) }
                "armour model: ${if (clear) "(default)" else argument}"
            }
            "head" -> {
                CustomRename.edit(identity) { it.copy(headTexture = argument.takeUnless { clear }) }
                "head texture: ${if (clear) "(default)" else "(set)"}"
            }
            "glint" -> when (argument.lowercase()) {
                "on" -> {
                    CustomRename.edit(identity) { it.copy(overrideEnchantGlint = true, enchantGlintValue = true) }
                    "glint forced on"
                }
                "off" -> {
                    CustomRename.edit(identity) { it.copy(overrideEnchantGlint = true, enchantGlintValue = false) }
                    "glint forced off"
                }
                else -> {
                    CustomRename.edit(identity) { it.copy(overrideEnchantGlint = false, enchantGlintValue = false) }
                    "glint reset to vanilla"
                }
            }
            else -> null
        }
    }
}
