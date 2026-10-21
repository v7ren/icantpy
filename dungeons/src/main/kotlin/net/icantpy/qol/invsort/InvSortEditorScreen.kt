package net.icantpy.qol.invsort

import net.icantpy.gui.McUi
import net.icantpy.gui.config.icantpyKeyLabel
import net.icantpy.qol.auto.AutoActionQueue
import net.icantpy.qol.auto.AutoTarget
import net.icantpy.qol.invsort.InvSortEditorLayout.EditorLayout
import net.icantpy.qol.invsort.InvSortEditorLayout.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.min

/**
 * Edits inventory sort presets. Closing (Esc or Apply) returns to [previous].
 * Geometry comes from [InvSortEditorLayout], which fits the panel to the current screen size.
 */
class InvSortEditorScreen(private val previous: Screen?, initialPreset: Int = 0) :
    Screen(Component.literal("Inventory sort presets")) {
    private val mc = Minecraft.getInstance()
    private var selected = initialPreset.coerceAtLeast(0)
    private var status = ""
    private var confirmDelete = false
    private var renameBox: EditBox? = null
    private var capturingHotkey = false
    private var hits: List<Hit> = emptyList()

    /** First preset row shown in the list. Kept clamped by [currentLayout]. */
    private var listScroll = 0

    /** Row pressed with the left button; becomes a drag once the mouse moves past the threshold. */
    private var pressRow: Int? = null
    private var pressY = 0
    private var dragging = false
    private var dragMouseY = 0

    override fun init() {
        super.init()
        McUi.releaseMouse(mc)
        instance = this
        revealSelected()
    }

    override fun tick() {
        super.tick()
        if (mc.player == null) McUi.setScreen(mc, null)
    }

    override fun removed() {
        if (instance === this) instance = null
        super.removed()
    }

    override fun onClose() {
        McUi.setScreen(mc, previous)
    }

    /** Drawn inside [extractRenderState] so the rename box stays above the panel. */
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0x50000000)
        var layout = currentLayout()
        if (dragging) {
            // Near the list edges, keep scrolling while the drag is held still.
            val step = InvSortEditorLayout.autoScrollStep(dragMouseY, layout.list.y, layout.list.bottom)
            if (step != 0) {
                listScroll = InvSortEditorLayout.clampScroll(listScroll + step, presetCount(), layout.visibleRows)
                layout = currentLayout()
            }
        }
        val panel = layout.panel
        graphics.fill(panel.x, panel.y, panel.right, panel.bottom, 0xE5101015.toInt())
        graphics.fill(panel.x, panel.y, panel.right, panel.y + 2, ACCENT_COLOR)
        graphics.text(mc.font, "Inventory sort presets", panel.x + InvSortEditorLayout.PAD, panel.y + 12, 0xFFFFFFFF.toInt(), true)
        drawPresetList(graphics, layout)
        val nextHits = mutableListOf<Hit>()
        nextHits += drawGrid(graphics, layout, mouseX, mouseY)
        nextHits += drawArmorColumn(graphics, layout, mouseX, mouseY)
        positionRenameBox(layout.renameBox.x, layout.renameBox.y)
        nextHits += drawHotkeyButton(graphics, layout.hotkey)
        nextHits += drawMoveButtons(graphics, layout)
        nextHits += drawButtons(graphics, layout)
        val line = status.ifEmpty {
            if (selectedPreset()?.hotbarSwap == true) HOTBAR_SWAP_NOTE else "Click a grid cell to clear that slot to any item"
        }
        graphics.text(
            mc.font,
            InvSortEditorLayout.fitText(line, layout.status.w) { mc.font.width(it) },
            layout.status.x,
            layout.status.y,
            0xFFAAAAAA.toInt(),
            false,
        )
        hits = nextHits
        drawDragPreview(graphics, layout)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        capturingHotkey = false
        val x = event.x().toInt()
        val y = event.y().toInt()
        val box = renameBox
        if (box != null && x in box.x until box.x + box.width && y in box.y until box.y + box.height) {
            return super.mouseClicked(event, doubleClick)
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            val layout = currentLayout()
            checkboxRowAt(x, y, layout)?.let { row ->
                toggleShownInInventory(row)
                return true
            }
            presetRowAt(x, y, layout)?.let { row ->
                armPresetDrag(row, y)
                return true
            }
        }
        val hit = hits.lastOrNull { it.contains(x, y) }
        if (hit == null) {
            confirmDelete = false
            return true
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) hit.action()
        return true
    }

    /** Mouse wheel over the preset list scrolls it one row per notch. */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val layout = currentLayout()
        if (layout.list.contains(mouseX.toInt(), mouseY.toInt())) {
            listScroll = InvSortEditorLayout.wheelScroll(listScroll, scrollY, presetCount(), layout.visibleRows)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (pressRow == null) return super.mouseDragged(event, dragX, dragY)
        dragMouseY = event.y().toInt()
        if (InvSortPresetDrag.exceedsThreshold(pressY, dragMouseY)) dragging = true
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val from = pressRow ?: return super.mouseReleased(event)
        dragMouseY = event.y().toInt()
        val drop = if (dragging) dropIndexFor(from) else from
        clearPress()
        if (drop != from) {
            InvSortController.movePreset(from, drop)?.let { moved ->
                selected = moved
                status = "Moved ${InvSortController.settings.presets[moved].name}"
                revealSelected()
            }
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (capturingHotkey) return captureHotkey(event.key())
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (renameBox != null) cancelRename() else onClose()
            return true
        }
        return super.keyPressed(event)
    }

    /** Button that starts hotkey capture for the selected preset. */
    private fun drawHotkeyButton(graphics: GuiGraphicsExtractor, rect: Rect): List<Hit> {
        val preset = selectedPreset() ?: return emptyList()
        val label = when {
            capturingHotkey -> "Press a key..."
            preset.hotkey < 0 -> "Hotkey: None"
            else -> "Hotkey: ${icantpyKeyLabel(preset.hotkey)}"
        }
        graphics.fill(rect.x, rect.y, rect.right, rect.bottom, if (capturingHotkey) 0xFFB05A20.toInt() else 0xFF713AA6.toInt())
        val shown = InvSortEditorLayout.fitText(label, rect.w - 6) { mc.font.width(it) }
        graphics.centeredText(mc.font, shown, rect.x + rect.w / 2, rect.y + 5, 0xFFFFFFFF.toInt())
        return listOf(Hit(rect.x, rect.y, rect.w, rect.h) { startHotkeyCapture() })
    }

    private fun startHotkeyCapture() {
        if (selectedPreset() == null) return
        capturingHotkey = true
        status = "Press a key to bind. Esc cancels, Backspace clears."
    }

    /** Handles the next key while capturing: Esc cancels, Backspace/Delete clears, others bind. */
    private fun captureHotkey(key: Int): Boolean {
        capturingHotkey = false
        when (key) {
            GLFW.GLFW_KEY_ESCAPE -> status = "Hotkey unchanged"
            GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> applyHotkey(InvSortLimits.NO_HOTKEY)
            else -> applyHotkey(key)
        }
        return true
    }

    private fun applyHotkey(key: Int) {
        val error = InvSortController.setHotkey(selected, key)
        status = error ?: if (key < 0) "Hotkey cleared" else "Hotkey set to ${icantpyKeyLabel(key)}"
    }

    /** Draws only the rows inside the scroll window; the dragged row is shown as a gap. */
    private fun drawPresetList(graphics: GuiGraphicsExtractor, layout: EditorLayout) {
        val list = layout.list
        val presets = InvSortController.settings.presets
        if (presets.isEmpty()) graphics.text(mc.font, "No presets yet.", list.x, list.y, 0xFF777777.toInt(), false)
        val first = listScroll
        val last = min(presets.size, first + layout.visibleRows)
        val labelW = list.w - CHECK_SIZE - CHECK_INSET_X - 10
        for (index in first until last) {
            val preset = presets[index]
            val rowY = list.y + (index - first) * LIST_ROW
            if (dragging && index == pressRow) {
                // The dragged row follows the mouse; leave an empty slot where it was.
                graphics.fill(list.x, rowY, list.right, rowY + LIST_ROW - 2, 0xFF1A1A20.toInt())
                continue
            }
            val color = if (index == selected) 0xFF713AA6.toInt() else 0xFF26262E.toInt()
            graphics.fill(list.x, rowY, list.right, rowY + LIST_ROW - 2, color)
            val nameColor = if (preset.showButton) 0xFFFFFFFF.toInt() else 0xFF8A8A94.toInt()
            val label = InvSortEditorLayout.fitText(preset.name, labelW) { mc.font.width(it) }
            graphics.text(mc.font, label, list.x + 4, rowY + 4, nameColor, false)
            drawCheckbox(graphics, checkboxX(layout), rowY + CHECK_INSET_Y, preset.showButton)
        }
        layout.scrollbar?.let { track ->
            graphics.fill(track.x, track.y, track.right, track.bottom, 0xFF18181E.toInt())
            InvSortEditorLayout.thumb(track, presets.size, layout.visibleRows, listScroll)?.let { thumb ->
                graphics.fill(thumb.x, thumb.y, thumb.right, thumb.bottom, 0xFF5A5A66.toInt())
            }
        }
    }

    /** Vector checkbox: filled accent square when the preset's inventory button is shown. */
    private fun drawCheckbox(graphics: GuiGraphicsExtractor, x: Int, y: Int, checked: Boolean) {
        graphics.fill(x, y, x + CHECK_SIZE, y + CHECK_SIZE, if (checked) ACCENT_COLOR else 0xFF5A5A66.toInt())
        graphics.fill(x + 1, y + 1, x + CHECK_SIZE - 1, y + CHECK_SIZE - 1, 0xFF18181E.toInt())
        if (checked) graphics.fill(x + 3, y + 3, x + CHECK_SIZE - 3, y + CHECK_SIZE - 3, ACCENT_COLOR)
    }

    private fun checkboxX(layout: EditorLayout): Int = layout.list.right - CHECK_INSET_X - CHECK_SIZE

    /** Row whose inventory-button checkbox contains (x, y), or null. */
    private fun checkboxRowAt(x: Int, y: Int, layout: EditorLayout): Int? {
        val boxX = checkboxX(layout)
        if (x !in boxX - 2 until boxX + CHECK_SIZE + 2) return null
        val row = InvSortEditorLayout.rowAt(y, layout.list.y, presetCount(), layout.visibleRows, listScroll) ?: return null
        val rowTop = layout.list.y + (row - listScroll) * LIST_ROW
        val inRow = y - rowTop
        return row.takeIf { inRow in CHECK_INSET_Y - 2 until CHECK_INSET_Y + CHECK_SIZE + 2 }
    }

    /** Shows or hides one preset's inventory button. Hidden presets still run from hotkeys and commands. */
    private fun toggleShownInInventory(row: Int) {
        val preset = InvSortController.settings.presets.getOrNull(row) ?: return
        val shown = !preset.showButton
        InvSortController.setShowButton(row, shown)
        status = if (shown) "${preset.name} shows an inventory button" else "${preset.name} hidden from the inventory; hotkeys still work"
    }

    /** Ghost of the dragged row under the mouse, plus the insertion line at the drop position. Clipped to the list. */
    private fun drawDragPreview(graphics: GuiGraphicsExtractor, layout: EditorLayout) {
        if (!dragging) return
        val from = pressRow ?: return
        val name = InvSortController.settings.presets.getOrNull(from)?.name ?: return
        val list = layout.list
        val visibleBottom = list.y + layout.visibleRows * LIST_ROW
        val ghostY = (dragMouseY - LIST_ROW / 2).coerceIn(list.y, (visibleBottom - LIST_ROW + 2).coerceAtLeast(list.y))
        graphics.fill(list.x, ghostY, list.right, ghostY + LIST_ROW - 2, 0x99713AA6.toInt())
        val ghostLabel = InvSortEditorLayout.fitText(name, list.w - 8) { mc.font.width(it) }
        graphics.text(mc.font, ghostLabel, list.x + 4, ghostY + 4, 0xCCFFFFFF.toInt(), false)
        val gap = InvSortPresetDrag.insertionGap(from, dropIndexFor(from))
        val lineY = list.y + (gap - listScroll) * LIST_ROW - 1
        if (lineY >= list.y - 1 && lineY + DROP_LINE_H <= visibleBottom) {
            graphics.fill(list.x, lineY, list.right, lineY + DROP_LINE_H, ACCENT_COLOR)
        }
    }

    /** Up and down buttons beside the hotkey button; they move the selected preset one place. */
    private fun drawMoveButtons(graphics: GuiGraphicsExtractor, layout: EditorLayout): List<Hit> {
        if (selectedPreset() == null) return emptyList()
        val count = presetCount()
        val nextHits = mutableListOf<Hit>()
        listOf(layout.moveUp to -1, layout.moveDown to 1).forEach { (rect, delta) ->
            val label = if (delta < 0) "▲" else "▼"
            val enabled = selected + delta in 0 until count
            graphics.fill(rect.x, rect.y, rect.right, rect.bottom, if (enabled) 0xFF713AA6.toInt() else 0xFF3B3B45.toInt())
            graphics.centeredText(mc.font, label, rect.x + rect.w / 2, rect.y + 5, 0xFFFFFFFF.toInt())
            if (enabled) nextHits += Hit(rect.x, rect.y, rect.w, rect.h) { moveSelected(delta) }
        }
        return nextHits
    }

    private fun moveSelected(delta: Int) {
        val moved = InvSortController.movePreset(selected, selected + delta) ?: return
        selected = moved
        status = "Moved ${InvSortController.settings.presets[moved].name}"
        revealSelected()
    }

    /** Preset row under (x, y), or null. Only the rows drawn by [drawPresetList] count. */
    private fun presetRowAt(x: Int, y: Int, layout: EditorLayout): Int? {
        if (x !in layout.list.x until layout.list.right) return null
        return InvSortEditorLayout.rowAt(y, layout.list.y, presetCount(), layout.visibleRows, listScroll)
    }

    private fun armPresetDrag(row: Int, y: Int) {
        select(row)
        pressRow = row
        pressY = y
        dragMouseY = y
        dragging = false
    }

    private fun clearPress() {
        pressRow = null
        dragging = false
    }

    private fun dropIndexFor(from: Int): Int =
        InvSortEditorLayout.dropIndex(dragMouseY, currentLayout().list.y, presetCount(), listScroll, from)

    private fun drawGrid(graphics: GuiGraphicsExtractor, layout: EditorLayout, mouseX: Int, mouseY: Int): List<Hit> {
        val preset = selectedPreset() ?: return emptyList()
        val nextHits = mutableListOf<Hit>()
        val cell = layout.cell
        gridLayout().forEach { (slot, col, row) ->
            val cellX = layout.grid.x + col * cell
            val cellY = layout.grid.y + row * cell
            val locked = slot == InvSortSlots.LOCKED_STAR
            // Hotbar-swap presets ignore the main inventory rows (rows 0..2), so they are dimmed.
            val dim = preset.hotbarSwap && row < 3
            drawCell(graphics, cellX, cellY, cell, preset.slots[slot], locked, "Any item (click to ignore)", mouseX, mouseY, dim)
            if (!locked) nextHits += Hit(cellX, cellY, cell - 2, cell - 2) { clearSlot(slot) }
        }
        return nextHits
    }

    /** Helmet, chest, legs and boots cells in a single column beside the main grid. Clicking clears a cell. */
    private fun drawArmorColumn(graphics: GuiGraphicsExtractor, layout: EditorLayout, mouseX: Int, mouseY: Int): List<Hit> {
        val preset = selectedPreset() ?: return emptyList()
        val x = layout.armor.x
        val top = layout.armor.y
        val cell = layout.cell
        graphics.text(mc.font, "Armor", x, top - ARMOR_LABEL_OFFSET, 0xFFAAAAAA.toInt(), false)
        val nextHits = mutableListOf<Hit>()
        InvSortSlots.ARMOR.forEachIndexed { row, slot ->
            val cellY = top + row * cell
            drawCell(graphics, x, cellY, cell, preset.slots[slot], false, InvSortSlots.ARMOR_NAMES[row], mouseX, mouseY, preset.hotbarSwap)
            nextHits += Hit(x, cellY, cell - 2, cell - 2) { clearSlot(slot) }
        }
        return nextHits
    }

    private fun drawCell(
        graphics: GuiGraphicsExtractor,
        cellX: Int,
        cellY: Int,
        cell: Int,
        entry: InvSortSlot?,
        locked: Boolean,
        emptyTooltip: String,
        mouseX: Int,
        mouseY: Int,
        dim: Boolean = false,
    ) {
        graphics.fill(cellX, cellY, cellX + cell - 2, cellY + cell - 2, if (locked) 0xFF1A1A1F.toInt() else 0xFF33333B.toInt())
        graphics.fill(cellX + 2, cellY + 2, cellX + cell - 4, cellY + cell - 4, 0xFF18181E.toInt())
        val stack = entry?.let { stackFor(it.itemId) }
        if (stack != null) {
            // 16 px icon centred in the cell; it is 6 px in from the edge at the default 28 px cell.
            graphics.item(stack, cellX + (cell - ICON_SIZE) / 2, cellY + (cell - ICON_SIZE) / 2)
        } else if (locked) {
            graphics.centeredText(mc.font, "X", cellX + cell / 2 - 1, cellY + cell / 2 - 4, 0xFF555555.toInt())
        }
        if (dim) graphics.fill(cellX, cellY, cellX + cell - 2, cellY + cell - 2, DIM_OVERLAY)
        val hovered = mouseX in cellX until cellX + cell - 2 && mouseY in cellY until cellY + cell - 2
        if (hovered && !locked) {
            graphics.setTooltipForNextFrame(mc.font, Component.literal(entry?.label ?: emptyTooltip), mouseX, mouseY)
        }
    }

    private fun drawButtons(graphics: GuiGraphicsExtractor, layout: EditorLayout): List<Hit> {
        val hasPreset = selectedPreset() != null
        val labels = buttonLabels()
        val actions = listOf(
            ::createFromInventory,
            ::overwriteSelected,
            ::toggleRename,
            ::deleteSelected,
            ::clearArmor,
            ::toggleMode,
            ::testSelected,
            ::apply,
        )
        val nextHits = mutableListOf<Hit>()
        labels.forEachIndexed { index, label ->
            val rect = layout.buttons[index]
            // "New" and "Apply" do not need a selected preset.
            val enabled = index == 0 || index == labels.lastIndex || hasPreset
            graphics.fill(rect.x, rect.y, rect.right, rect.bottom, if (enabled) 0xFF713AA6.toInt() else 0xFF3B3B45.toInt())
            graphics.centeredText(mc.font, label, rect.x + rect.w / 2, rect.y + 5, 0xFFFFFFFF.toInt())
            if (enabled) nextHits += Hit(rect.x, rect.y, rect.w, rect.h) { actions[index]() }
        }
        return nextHits
    }

    private fun buttonLabels(): List<String> = listOf(
        "New",
        "Overwrite",
        if (renameBox == null) "Rename" else "Save name",
        if (confirmDelete) "Confirm delete" else "Delete",
        "Clear armor",
        if (selectedPreset()?.hotbarSwap == true) "Mode: Hotbar swap" else "Mode: Full sort",
        "Test",
        "Apply",
    )

    /** Triples of (menu slot, column, row) for the 9x4 grid: three main rows, then the hotbar row. */
    private fun gridLayout(): List<Triple<Int, Int, Int>> {
        val cells = mutableListOf<Triple<Int, Int, Int>>()
        for (row in 0..2) for (col in 0..8) cells += Triple(InvSortSlots.MAIN_FIRST + row * 9 + col, col, row)
        for (col in 0..8) {
            val slot = if (col == 8) InvSortSlots.LOCKED_STAR else InvSortSlots.HOTBAR_FIRST + col
            cells += Triple(slot, col, 3)
        }
        return cells
    }

    /** Layout for the current screen size and preset count. Also clamps [listScroll]. */
    private fun currentLayout(): EditorLayout {
        val widths = buttonLabels().map { mc.font.width(it) + BUTTON_PAD }
        val layout = InvSortEditorLayout.compute(width, height, presetCount(), listScroll, widths)
        listScroll = layout.listScroll
        return layout
    }

    /** Scrolls the list so the selected preset is visible. Call after any change to [selected]. */
    private fun revealSelected() {
        val visibleRows = currentLayout().visibleRows
        listScroll = InvSortEditorLayout.scrollIntoView(listScroll, selected, presetCount(), visibleRows)
    }

    private fun presetCount(): Int = InvSortController.settings.presets.size

    private fun positionRenameBox(x: Int, y: Int) {
        renameBox?.setX(x)
        renameBox?.setY(y)
    }

    private fun selectedPreset(): InvSortPreset? = InvSortController.settings.presets.getOrNull(selected)

    private fun select(index: Int) {
        selected = index
        confirmDelete = false
        cancelRename()
        revealSelected()
    }

    private fun clearSlot(slot: Int) {
        InvSortController.clearSlot(selected, slot)
        status = "Cleared slot $slot"
    }

    private fun createFromInventory() {
        val name = "Preset ${presetCount() + 1}"
        val error = InvSortController.saveCurrentAsPreset(name)
        if (error != null) {
            status = error
            return
        }
        selected = InvSortController.settings.presets.lastIndex
        status = "Created $name from your inventory"
        revealSelected()
    }

    private fun overwriteSelected() {
        status = InvSortController.overwritePresetFromInventory(selected) ?: "Overwrote from your inventory"
    }

    private fun toggleRename() {
        val box = renameBox
        if (box == null) startRename() else commitRename(box.value)
    }

    private fun startRename() {
        val preset = selectedPreset() ?: return
        val box = EditBox(mc.font, currentLayout().renameBox.w, 20, Component.literal("Preset name"))
        box.setMaxLength(InvSortLimits.MAX_NAME_LENGTH)
        box.setValue(preset.name)
        renameBox = addRenderableWidget(box)
        setFocused(box)
        status = "Type a new name, then press Save name"
    }

    private fun commitRename(value: String) {
        val error = InvSortController.renamePreset(selected, value)
        if (error != null) {
            status = error
            return
        }
        cancelRename()
        status = "Renamed"
    }

    private fun cancelRename() {
        val box = renameBox ?: return
        removeWidget(box)
        renameBox = null
        setFocused(null)
    }

    private fun deleteSelected() {
        if (selectedPreset() == null) return
        if (!confirmDelete) {
            confirmDelete = true
            status = "Press Confirm delete to remove this preset"
            return
        }
        InvSortController.deletePreset(selected)
        confirmDelete = false
        selected = selected.coerceAtMost(InvSortController.settings.presets.lastIndex.coerceAtLeast(0))
        status = "Deleted"
        revealSelected()
    }

    /** Removes every armor entry from the selected preset. Inventory entries are kept. */
    private fun clearArmor() {
        val preset = selectedPreset() ?: return
        if (preset.slots.keys.none { InvSortSlots.isArmor(it) }) {
            status = "${preset.name} has no armor entries"
            return
        }
        InvSortController.clearArmor(selected)
        status = "Cleared armor from ${preset.name}"
    }

    /** Cycles the selected preset between full sort and hotbar-only swap mode. */
    private fun toggleMode() {
        val preset = selectedPreset() ?: return
        val on = !preset.hotbarSwap
        InvSortController.setHotbarSwap(selected, on)
        status = if (on) "${preset.name} sorts the hotbar only, with number-key swaps" else "${preset.name} uses the full sort"
    }

    /**
     * Closes the editor fully (not back to the inventory), then queues the selected preset as a sort.
     * The queue opens the inventory like a player would and closes it when the sort is done.
     */
    private fun testSelected() {
        val preset = selectedPreset() ?: return
        McUi.setScreen(mc, null)
        AutoActionQueue.requestBatch(
            triggerId = "test-sort:${preset.name}",
            targets = listOf(AutoTarget.Sort(preset.name)),
            reason = "Test ${preset.name}",
            bypassDedupe = true,
        )
    }

    private fun apply() {
        InvSortItems.say("Open your inventory and use the sort button to apply a preset")
        onClose()
    }

    private fun stackFor(itemId: String): ItemStack? {
        val identifier = runCatching { Identifier.parse(itemId) }.getOrNull() ?: return null
        val item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null) ?: return null
        return ItemStack(item)
    }

    private data class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX in x until x + w && mouseY in y until y + h
    }

    companion object {
        private const val LIST_ROW = InvSortEditorLayout.LIST_ROW
        private const val ARMOR_LABEL_OFFSET = 10
        private const val ICON_SIZE = 16
        private const val BUTTON_PAD = 12
        private const val CHECK_SIZE = 10
        private const val CHECK_INSET_X = 5
        private const val CHECK_INSET_Y = 3
        private const val DROP_LINE_H = 2
        private const val ACCENT_COLOR = 0xFF9B4DFF.toInt()
        private const val DIM_OVERLAY = 0xB0000000.toInt()
        const val HOTBAR_SWAP_NOTE = "Hotbar only · uses number-key swaps · nothing held on cursor"

        var instance: InvSortEditorScreen? = null
            private set

        fun closeIfOpen() {
            val editor = instance ?: return
            val mc = Minecraft.getInstance()
            if (McUi.currentScreen(mc) === editor) McUi.setScreen(mc, null)
            instance = null
        }
    }
}
