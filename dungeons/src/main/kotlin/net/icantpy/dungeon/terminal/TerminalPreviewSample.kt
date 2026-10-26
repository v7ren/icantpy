package net.icantpy.dungeon.terminal

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/**
 * A made-up terminal for the preview editor, so the look can be tuned outside phase 3. Clicks change it locally the
 * way the server would; nothing is sent anywhere. Uses vanilla item names, so "starts with" matches the client's
 * language only for the letter it was written for (English). Items are looked up by registry id because 26.2 groups
 * the coloured items into collections while 26.1.2 still has one field per colour; the ids are the same in both.
 */
class TerminalPreviewSample(val type: TerminalType) {
    val title: String = when (type) {
        TerminalType.PANES -> "Correct all the panes!"
        TerminalType.IN_ORDER -> "Click in order!"
        TerminalType.STARTS_WITH -> "What starts with: 'S'?"
        TerminalType.SELECT_COLOR -> "Select all the RED items!"
        TerminalType.RUBIX -> "Change all to same color!"
        TerminalType.MELODY -> "Click the button on time!"
    }

    val rows: Int = if (type == TerminalType.IN_ORDER) 4 else ROWS

    private val stacks: MutableMap<Int, ItemStack> = initialStacks().toMutableMap()

    /** Melody: the row being played and when the sample was made, which drives the moving pane. */
    private var melodyRow = 0
    private val createdMs = System.currentTimeMillis()

    fun stackAt(slot: Int): ItemStack = stacks[slot] ?: ItemStack.EMPTY

    fun snapshot(): TerminalSnapshot {
        if (type == TerminalType.MELODY) drawMelody()
        return snapshotOfStacks()
    }

    private fun snapshotOfStacks(): TerminalSnapshot = TerminalSnapshot(
        title,
        rows,
        stacks.entries.sortedBy { it.key }.mapNotNull { (slot, stack) -> TerminalSnapshotReader.itemOf(slot, stack) },
    )

    /** Applies a click the way Hypixel would answer it. */
    fun apply(cell: TerminalCell, click: TerminalClick) {
        val stack = stacks[cell.slot] ?: return
        when (type) {
            TerminalType.PANES -> stacks[cell.slot] = stack(if (cell.item.itemId == LIME) RED else LIME, 1)
            TerminalType.IN_ORDER -> if (cell.order == 0) stacks[cell.slot] = stack(LIME, stack.count)
            TerminalType.STARTS_WITH, TerminalType.SELECT_COLOR -> stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            TerminalType.RUBIX -> {
                val color = cell.rubixColor ?: return
                val size = RubixColor.entries.size
                val next = RubixColor.entries[((color.ordinal + click.steps) % size + size) % size]
                stacks[cell.slot] = stack(next.itemId, 1)
            }
            TerminalType.MELODY -> if (cell.state == TerminalCellState.TARGET && melodyRow < MELODY_COLUMNS.size) melodyRow++
        }
    }

    /** The pane bounces across the five track columns, one step every [MELODY_STEP_MS]. */
    private fun drawMelody() {
        val step = ((System.currentTimeMillis() - createdMs) / MELODY_STEP_MS).toInt()
        val bounce = step % MELODY_BOUNCE
        val pointer = 1 + if (bounce < MELODY_TRACK_WIDTH) bounce else MELODY_BOUNCE - bounce
        val column = MELODY_COLUMNS.getOrNull(melodyRow)
        for (trackColumn in 1..MELODY_TRACK_WIDTH) {
            val marker = if (trackColumn == column) "magenta_stained_glass_pane" else "black_stained_glass_pane"
            stacks[trackColumn] = stack(marker, 1)
            stacks[(MELODY_COLUMNS.size + 1) * 9 + trackColumn] = stack(marker, 1)
        }
        MELODY_COLUMNS.indices.forEach { row ->
            val base = (row + 1) * 9
            for (trackColumn in 1..MELODY_TRACK_WIDTH) {
                val pane = when {
                    row < melodyRow -> "green_stained_glass_pane"
                    row == melodyRow && trackColumn == pointer -> LIME
                    else -> "white_stained_glass_pane"
                }
                stacks[base + trackColumn] = stack(pane, 1)
            }
            val button = when {
                row < melodyRow -> "green_terracotta"
                row == melodyRow -> "lime_terracotta"
                else -> "red_terracotta"
            }
            stacks[base + MELODY_BUTTON_COLUMN] = stack(button, 1)
        }
    }

    private fun initialStacks(): Map<Int, ItemStack> = when (type) {
        TerminalType.PANES -> PANES_AREA.withIndex().associate { (index, slot) ->
            slot to stack(if (index % 3 == 0) LIME else RED, 1)
        }
        TerminalType.IN_ORDER -> IN_ORDER_AREA.withIndex().associate { (index, slot) ->
            val number = IN_ORDER_NUMBERS[index]
            slot to stack(if (number <= 3) LIME else RED, number)
        }
        TerminalType.STARTS_WITH -> itemArea(STARTS_WITH_ITEMS, glintEvery = 7)
        TerminalType.SELECT_COLOR -> itemArea(SELECT_COLOR_ITEMS, glintEvery = 9)
        TerminalType.RUBIX -> RUBIX_AREA.withIndex().associate { (index, slot) ->
            slot to stack(RUBIX_PATTERN[index].itemId, 1)
        }
        TerminalType.MELODY -> emptyMap()
    }

    private fun itemArea(itemIds: List<String>, glintEvery: Int): Map<Int, ItemStack> = ITEM_AREA.withIndex().associate { (index, slot) ->
        val stack = stack(itemIds[index % itemIds.size], 1)
        if (index % glintEvery == glintEvery - 1) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        slot to stack
    }

    /** A stack of the vanilla item [itemId], or an empty stack if this version does not have it. */
    private fun stack(itemId: String, count: Int): ItemStack {
        val item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(itemId)).orElse(null)
            ?: return ItemStack.EMPTY
        return ItemStack(item, count)
    }

    private companion object {
        const val ROWS = 6
        const val LIME = "lime_stained_glass_pane"
        const val RED = "red_stained_glass_pane"
        val PANES_AREA = (1..3).flatMap { row -> (2..6).map { column -> row * 9 + column } }
        val IN_ORDER_AREA = (1..2).flatMap { row -> (1..7).map { column -> row * 9 + column } }
        val IN_ORDER_NUMBERS = listOf(9, 2, 13, 5, 11, 1, 7, 14, 4, 10, 3, 12, 6, 8)
        val ITEM_AREA = (1..4).flatMap { row -> (1..7).map { column -> row * 9 + column } }
        val RUBIX_AREA = listOf(12, 13, 14, 21, 22, 23, 30, 31, 32)
        val MELODY_COLUMNS = listOf(3, 1, 5, 2)
        const val MELODY_TRACK_WIDTH = 5
        const val MELODY_BOUNCE = (MELODY_TRACK_WIDTH - 1) * 2
        const val MELODY_BUTTON_COLUMN = 7
        const val MELODY_STEP_MS = 220L
        val RUBIX_PATTERN = listOf(
            RubixColor.RED, RubixColor.ORANGE, RubixColor.RED,
            RubixColor.BLUE, RubixColor.RED, RubixColor.YELLOW,
            RubixColor.GREEN, RubixColor.RED, RubixColor.ORANGE,
        )
        val STARTS_WITH_ITEMS = listOf(
            "stone", "apple", "sugar", "bread", "sand", "bone", "stick",
            "arrow", "snowball", "feather", "sponge", "coal", "slime_ball",
        )
        val SELECT_COLOR_ITEMS = listOf(
            "red_wool", "blue_wool", "red_dye", "lime_concrete", "red_stained_glass", "yellow_wool",
            "lapis_lazuli", "red_terracotta", "orange_concrete", "cyan_wool", "red_concrete",
        )
    }
}
