package net.icantpy.qol.invsort

import net.icantpy.gui.McUi
import net.icantpy.gui.config.icantpyKeyLabel
import net.icantpy.qol.invsort.InvSortLayout.ButtonColumn
import net.icantpy.qol.invsort.InvSortLayout.ColumnTarget
import net.icantpy.qol.invsort.InvSortLayout.GuiRect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * One sort button per preset, stacked beside the player inventory, plus a "+" button that saves the
 * current layout. Drawn after the vanilla inventory renders (see [InvSortScreenEvents]).
 * Left click sorts with the preset (or saves), right click opens the editor on it.
 */
object InvSortButton {
    private const val TEXT_Y_OFFSET = 4
    private const val BACKGROUND_COLOR = 0xE0101010.toInt()
    private const val BORDER_COLOR = 0xFFA0A0A0.toInt()
    private const val HOVER_BORDER_COLOR = 0xFFFFFFFF.toInt()
    private const val RUNNING_BORDER_COLOR = 0xFF55FF55.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val SAVE_LABEL = "+"
    private const val SAVE_TOOLTIP = "Save current layout as a preset"
    private const val TOOLTIP_SEPARATOR = " · "

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** Column geometry plus the (possibly truncated) label drawn for each visible preset. */
    private class Laid(val column: ButtonColumn, val labels: List<String>)

    fun render(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!isVisibleOn(screen)) return
        val laid = layoutFor(screen)
        val presets = InvSortController.settings.presets
        val runningIndex = InvSortRunner.runningPresetIndex
        laid.column.presets.forEachIndexed { row, rect ->
            val presetIndex = laid.column.presetIndices[row]
            val hovered = rect.contains(mouseX, mouseY)
            val border = when {
                presetIndex == runningIndex -> RUNNING_BORDER_COLOR
                hovered -> HOVER_BORDER_COLOR
                else -> BORDER_COLOR
            }
            drawButton(graphics, rect, laid.labels[row], border)
            if (hovered) setTooltip(graphics, presetTooltip(presets[presetIndex]), mouseX, mouseY)
        }
        val save = laid.column.save
        val saveHovered = save.contains(mouseX, mouseY)
        drawButton(graphics, save, SAVE_LABEL, if (saveHovered) HOVER_BORDER_COLOR else BORDER_COLOR)
        if (saveHovered) setTooltip(graphics, SAVE_TOOLTIP, mouseX, mouseY)
    }

    /** Returns true when the click landed on one of the buttons and was consumed. */
    fun handleMouse(screen: Screen, event: MouseButtonEvent): Boolean {
        if (!isVisibleOn(screen)) return false
        val target = layoutFor(screen).column.targetAt(event.x().toInt(), event.y().toInt()) ?: return false
        return when (event.button()) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                activate(target)
                true
            }
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
                openEditor(screen, target)
                true
            }
            else -> false
        }
    }

    private fun isVisibleOn(screen: Screen): Boolean =
        InvSortController.settings.enabled && InvSortLayout.isPlayerInventory(screen)

    private fun activate(target: ColumnTarget) {
        when (target) {
            is ColumnTarget.Preset -> InvSortRunner.start(
                target.index,
                closeWhenDone = InvSortController.settings.closeWhenDone,
                perPress = true,
            )?.let(InvSortItems::say)
            ColumnTarget.Save -> saveCurrentLayout()
        }
    }

    private fun saveCurrentLayout() {
        val name = nextPresetName(InvSortController.settings.presets.map { it.name })
        val error = InvSortController.saveCurrentAsPreset(name)
        InvSortItems.say(error ?: "Saved current layout as $name")
    }

    /** "Preset N" with the first N, starting at count + 1, that no existing preset uses. */
    private fun nextPresetName(existingNames: List<String>): String {
        val taken = existingNames.toSet()
        return generateSequence(existingNames.size + 1) { it + 1 }
            .map { "Preset $it" }
            .first { it !in taken }
    }

    private fun openEditor(screen: Screen, target: ColumnTarget) {
        val initialPreset = when (target) {
            is ColumnTarget.Preset -> target.index
            ColumnTarget.Save -> InvSortController.settings.presets.lastIndex.coerceAtLeast(0)
        }
        McUi.setScreen(mc, InvSortEditorScreen(screen, initialPreset))
    }

    /** Only presets with [InvSortPreset.showButton] get a button; their real indices are kept for running. */
    private fun layoutFor(screen: Screen): Laid {
        val settings = InvSortController.settings
        val shown = settings.presets.withIndex().filter { it.value.showButton }
        val labels = shown.map { InvSortLayout.fitLabel(it.value.name, ::measure) }
        val widths = labels.map(::measure)
        val origin = InvSortLayout.origin(screen)
        val column = InvSortLayout.stackColumn(
            originX = origin.left + origin.imageWidth + settings.buttonOffsetX,
            originY = origin.top + settings.buttonOffsetY,
            labelWidths = widths,
            screenHeight = screen.height,
            presetIndices = shown.map { it.index },
        )
        return Laid(column, labels)
    }

    private fun measure(text: String): Int = mc.font.width(text)

    private fun drawButton(graphics: GuiGraphicsExtractor, rect: GuiRect, label: String, borderColor: Int) {
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, borderColor)
        graphics.fill(rect.x + 1, rect.y + 1, rect.x + rect.width - 1, rect.y + rect.height - 1, BACKGROUND_COLOR)
        graphics.centeredText(mc.font, label, rect.x + rect.width / 2, rect.y + TEXT_Y_OFFSET, TEXT_COLOR)
    }

    private fun setTooltip(graphics: GuiGraphicsExtractor, text: String, mouseX: Int, mouseY: Int) {
        graphics.setTooltipForNextFrame(mc.font, Component.literal(text), mouseX, mouseY)
    }

    private fun presetTooltip(preset: InvSortPreset): String =
        buildList {
            add(preset.name)
            if (preset.hotkey >= 0) add("Hotkey ${icantpyKeyLabel(preset.hotkey)}")
            if (preset.hotbarSwap) add("Hotbar swap")
            if (preset.hotbarSwap && InvSortController.settings.swapOnePerPress) add("1 swap per click")
            add("Left: sort")
            add("Right: edit")
        }.joinToString(TOOLTIP_SEPARATOR)
}
