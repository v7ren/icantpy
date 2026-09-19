package net.icantpy.gui.customize

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.item.equipment.trim.TrimMaterial
import net.minecraft.world.item.equipment.trim.TrimMaterials
import net.minecraft.world.item.equipment.trim.TrimPattern

/** A single trim pattern or material button, ported from Skyblocker's `TrimElementButton`. */
sealed class TrimElementButton(
    val element: Identifier?,
    message: Component,
    private val onPress: (TrimElementButton) -> Unit,
) : AbstractButton(0, 0, 20, 20, message) {
    protected var stack: ItemStack = BARRIER

    init {
        setTooltip(Tooltip.create(getMessage()))
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        extractDefaultSprite(graphics)
        extract(graphics)
    }

    abstract fun extract(graphics: GuiGraphicsExtractor)

    override fun onPress(input: InputWithModifiers) = onPress(this)

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    /** The "none" button plus a pattern preview on the current armour piece. */
    class Pattern(element: Identifier?, pattern: TrimPattern?, onPress: (TrimElementButton) -> Unit) :
        TrimElementButton(element, if (pattern == null) Component.translatable("gui.none") else pattern.description(), onPress) {

        private val trim: ArmorTrim? = run {
            if (element == null) return@run null
            val lookup = Minecraft.getInstance().level?.registryAccess() ?: return@run null
            val materialHolder = lookup.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.QUARTZ)
            ArmorTrim(materialHolder, Holder.direct(pattern!!))
        }

        fun setPreviewStack(newStack: ItemStack) {
            if (trim == null) {
                stack = BARRIER
                return
            }
            val copy = newStack.copy()
            val custom = copy.get(DataComponents.CUSTOM_DATA)
            if (custom != null) {
                val tag = custom.copyTag()
                tag.remove("uuid")
                tag.remove("UUID")
                copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            }
            copy.set(DataComponents.TRIM, trim)
            stack = copy
        }

        override fun extract(graphics: GuiGraphicsExtractor) {
            graphics.item(stack, getX() + getWidth() / 2 - 8, getY() + getHeight() / 2 - 8)
        }
    }

    /** Renders the item that provides the trim material. */
    class Material(element: Identifier?, material: TrimMaterial, onPress: (TrimElementButton) -> Unit) :
        TrimElementButton(element, material.description(), onPress) {

        init {
            stack = BuiltInRegistries.ITEM.stream()
                .filter { item -> item.components().get(DataComponents.PROVIDES_TRIM_MATERIAL)?.`is`(element!!) == true }
                .findAny()
                .map { ItemStack(it) }
                .orElse(BARRIER)
        }

        override fun extract(graphics: GuiGraphicsExtractor) {
            graphics.item(stack, getX() + getWidth() / 2 - 8, getY() + getHeight() / 2 - 8)
        }
    }

    companion object {
        val BARRIER: ItemStack = ItemStack(Items.BARRIER)
    }
}
