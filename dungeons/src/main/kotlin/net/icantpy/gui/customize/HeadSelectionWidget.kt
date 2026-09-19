package net.icantpy.gui.customize

import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.ItemIdentity
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Searchable grid of skyblock heads, ported from Skyblocker's `HeadSelectionWidget`. */
class HeadSelectionWidget(x: Int, y: Int, width: Int, height: Int) :
    SearchableGridWidget(x + 2, y + 2, width - 4, height - 4, Component.nullToEmpty("HeadSelection"), 20, packed = true) {

    private val allButtons = mutableListOf<HeadButton>()
    private val noneButton: HeadButton
    private var currentIdentity: ItemIdentity? = null
    private var currentStack: ItemStack = ItemStack.EMPTY
    private var selectedButton: HeadButton

    init {
        for (tex in HeadTextures.staticHeads()) {
            allButtons.add(HeadButton(tex.name, tex.texture, HeadTextures.skull(tex.texture), ::onClick))
        }
        for (head in HeadTextures.animatedHeads()) {
            allButtons.add(AnimatedHeadButton(head.id, ::onClick))
        }
        noneButton = HeadButton("", null, ItemStack(Items.BARRIER), ::onClick)
        selectedButton = noneButton
        allButtons.add(noneButton)
        setSearch("")
    }

    override fun setX(x: Int) = super.setX(x + 2)

    override fun setY(y: Int) = super.setY(y + 2)

    override fun setWidth(width: Int) = super.setWidth(width - 4)

    override fun setHeight(height: Int) = super.setHeight(height - 4)

    private fun onClick(button: HeadButton) {
        selectedButton = button
        updateConfig()
        updateButtons()
    }

    private fun updateConfig() {
        val identity = currentIdentity ?: CustomRename.identity(currentStack) ?: return
        CustomRename.edit(identity) { data ->
            when (val button = selectedButton) {
                noneButton -> data.copy(headTexture = null, animatedHeadId = null)
                is AnimatedHeadButton -> data.copy(animatedHeadId = button.id, headTexture = null)
                else -> data.copy(headTexture = button.texture, animatedHeadId = null)
            }
        }
    }

    private fun updateButtons() {
        allButtons.forEach { it.selected = it == selectedButton }
    }

    override fun filterWidgets(search: String): Collection<AbstractWidget> {
        setScrollAmount(0.0)
        updateButtons()
        val s = search.lowercase()
        return allButtons.filter { it == noneButton || it.name.lowercase().contains(s) }
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        CustomizeDraw.panel(graphics, getX() - 2, getY() - 2, getWidth() + 4, getHeight() + 4)
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a)
    }

    fun setCurrentItem(item: ItemStack) {
        currentStack = item
        currentIdentity = CustomRename.identity(item)
        val data = CustomRename.localAppearance(item)
        val intended: HeadButton = when {
            data?.animatedHeadId != null -> allButtons.filterIsInstance<AnimatedHeadButton>()
                .firstOrNull { it.id == data.animatedHeadId } ?: noneButton
            data?.headTexture != null -> allButtons.filterNot { it is AnimatedHeadButton }
                .firstOrNull { it.texture == data.headTexture } ?: noneButton
            else -> noneButton
        }
        selectedButton = intended
        updateButtons()
    }

    private open class HeadButton(
        val name: String,
        val texture: String?,
        private val head: ItemStack,
        onPress: (HeadButton) -> Unit,
    ) : AbstractWidget(0, 0, 20, 20, Component.empty()) {
        var selected = false
        private val onPressRef = onPress

        init {
            if (name.isNotEmpty()) setTooltip(Tooltip.create(Component.nullToEmpty(name)))
        }

        protected open fun getHead(): ItemStack = head

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            graphics.item(getHead(), getX() + 2, getY() + 2)
            if (selected) graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x3000FF00.toInt())
            if (isHovered) graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFFFFF)
            handleCursor(graphics)
        }

        override fun onClick(click: MouseButtonEvent, doubled: Boolean) = onPressRef(this)

        override fun updateWidgetNarration(builder: NarrationElementOutput) {}
    }

    private class AnimatedHeadButton(val id: String, onPress: (HeadButton) -> Unit) :
        HeadButton(HeadTextures.formatName(id), null, ItemStack(Items.BARRIER), onPress) {
        override fun getHead(): ItemStack =
            HeadTextures.animate(id)?.let { HeadTextures.skull(it) } ?: ItemStack(Items.BARRIER)
    }
}
