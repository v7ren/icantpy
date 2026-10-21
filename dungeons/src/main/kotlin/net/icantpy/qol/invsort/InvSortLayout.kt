package net.icantpy.qol.invsort

import net.icantpy.Icantpy
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.world.inventory.InventoryMenu
import java.lang.reflect.Field

/** Screen geometry for the player's inventory screen, read from vanilla's protected layout fields. */
object InvSortLayout {
    private const val DEFAULT_IMAGE_WIDTH = 176
    private const val DEFAULT_IMAGE_HEIGHT = 166
    private const val SLOT_CENTER_OFFSET = 8

    data class Origin(val left: Int, val top: Int, val imageWidth: Int)

    /** Axis-aligned rectangle in GUI pixels. */
    data class GuiRect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(pointX: Int, pointY: Int): Boolean =
            pointX in x until x + width && pointY in y until y + height
    }

    /** What a stacked button stands for: a preset by index, or the save ("+") button. */
    sealed interface ColumnTarget {
        data class Preset(val index: Int) : ColumnTarget
        data object Save : ColumnTarget
    }

    /**
     * Buttons in the column. [presets] holds one rect per drawn preset button, in column order;
     * [presetIndices] gives each button's real index in the settings list, so hidden presets keep their indices.
     */
    data class ButtonColumn(
        val presets: List<GuiRect>,
        val presetIndices: List<Int>,
        val save: GuiRect,
        val hiddenPresetCount: Int,
    ) {
        fun targetAt(pointX: Int, pointY: Int): ColumnTarget? {
            val row = presets.indexOfFirst { it.contains(pointX, pointY) }
            if (row >= 0) return ColumnTarget.Preset(presetIndices[row])
            return if (save.contains(pointX, pointY)) ColumnTarget.Save else null
        }
    }

    private const val PRESET_HEIGHT = 16
    private const val SAVE_SIZE = 16
    private const val ROW_GAP = 2
    private const val MIN_WIDTH = 60
    private const val MAX_WIDTH = 110
    private const val LABEL_PADDING = 10
    private const val MAX_LABEL_WIDTH = MAX_WIDTH - LABEL_PADDING
    private const val ELLIPSIS = "…"

    private var warnedFallback = false

    private val layoutFields: List<Field>? by lazy {
        runCatching {
            listOf("leftPos", "topPos", "imageWidth").map { name ->
                AbstractContainerScreen::class.java.getDeclaredField(name).apply { isAccessible = true }
            }
        }.getOrNull()
    }

    /** True for any container screen showing the player's own InventoryMenu (creative excluded). */
    fun isPlayerInventory(screen: Screen?): Boolean {
        val container = screen as? AbstractContainerScreen<*> ?: return false
        return container.menu is InventoryMenu && container !is CreativeModeInventoryScreen
    }

    fun origin(screen: Screen): Origin {
        val fields = layoutFields
        if (fields != null) {
            val read = runCatching {
                Origin(fields[0].getInt(screen), fields[1].getInt(screen), fields[2].getInt(screen))
            }
            read.getOrNull()?.let { return it }
        }
        warnOnce()
        return Origin(
            (screen.width - DEFAULT_IMAGE_WIDTH) / 2,
            (screen.height - DEFAULT_IMAGE_HEIGHT) / 2,
            DEFAULT_IMAGE_WIDTH,
        )
    }

    /** GUI-pixel center of a menu slot on [screen]. */
    fun slotCenter(screen: Screen, slotX: Int, slotY: Int): Pair<Int, Int> {
        val origin = origin(screen)
        return (origin.left + slotX + SLOT_CENTER_OFFSET) to (origin.top + slotY + SLOT_CENTER_OFFSET)
    }

    /**
     * Stacks one button per shown preset below the inventory origin, then the save button. [labelWidths]
     * and [presetIndices] describe only the shown presets, in order. Only as many as fit above the bottom
     * of [screenHeight] are kept; the save button always stays on screen. Pure geometry in GUI pixels.
     */
    fun stackColumn(
        originX: Int,
        originY: Int,
        labelWidths: List<Int>,
        screenHeight: Int,
        presetIndices: List<Int> = labelWidths.indices.toList(),
    ): ButtonColumn {
        require(presetIndices.size == labelWidths.size) { "one label width per shown preset" }
        val width = buttonWidth(labelWidths)
        val pitch = PRESET_HEIGHT + ROW_GAP
        // n presets plus the save button need n * pitch + SAVE_SIZE pixels below originY.
        val room = screenHeight - originY - SAVE_SIZE
        val fitting = if (room < 0) 0 else room / pitch
        val visible = minOf(fitting, labelWidths.size)
        val presets = (0 until visible).map { index ->
            GuiRect(originX, originY + index * pitch, width, PRESET_HEIGHT)
        }
        val save = GuiRect(originX, originY + visible * pitch, SAVE_SIZE, SAVE_SIZE)
        return ButtonColumn(
            presets = presets,
            presetIndices = presetIndices.take(visible),
            save = save,
            hiddenPresetCount = labelWidths.size - visible,
        )
    }

    /** Shared column width: the widest label plus padding, kept between [MIN_WIDTH] and [MAX_WIDTH]. */
    fun buttonWidth(labelWidths: List<Int>): Int {
        val widest = labelWidths.maxOrNull() ?: 0
        return (widest + LABEL_PADDING).coerceIn(MIN_WIDTH, MAX_WIDTH)
    }

    /** Returns [label] unchanged when it fits [MAX_LABEL_WIDTH], otherwise trims it and adds an ellipsis. */
    fun fitLabel(label: String, measure: (String) -> Int): String {
        if (measure(label) <= MAX_LABEL_WIDTH) return label
        var end = label.length
        while (end > 0 && measure(label.substring(0, end) + ELLIPSIS) > MAX_LABEL_WIDTH) end--
        return label.substring(0, end) + ELLIPSIS
    }

    private fun warnOnce() {
        if (warnedFallback) return
        warnedFallback = true
        Icantpy.LOGGER.warn("Inventory sort could not read the inventory screen position; using the vanilla layout")
    }
}
