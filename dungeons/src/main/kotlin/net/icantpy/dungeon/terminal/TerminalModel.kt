package net.icantpy.dungeon.terminal

/**
 * One non-empty slot of the terminal's own container (never the player inventory), reduced to what the solvers
 * read. [itemId] is the registry path without namespace ("red_stained_glass_pane"); [name] has colour codes removed.
 * [glint] is the server's enchanted look, which Hypixel uses to mark items that were already clicked.
 */
data class TerminalItem(
    val slot: Int,
    val itemId: String,
    val count: Int,
    val name: String,
    val glint: Boolean,
)

/** Everything a solver needs: the window title, the container's row count and its items. Built on the client thread. */
data class TerminalSnapshot(
    val title: String,
    val rows: Int,
    val items: List<TerminalItem>,
) {
    fun row(slot: Int): Int = slot / COLUMNS

    fun column(slot: Int): Int = slot % COLUMNS

    companion object {
        const val COLUMNS = 9
    }
}

enum class TerminalCellState {
    /** Still has to be clicked. */
    TARGET,

    /** Already solved, or predicted to be after a click the server has not confirmed yet. */
    DONE,

    /** Part of the puzzle but must not be clicked. */
    WRONG,
}

/** What a melody cell shows: the pane track, the target column marker, the moving pane, or a row's button. */
enum class MelodyRole { TRACK, COLUMN, POINTER, BUTTON }

/**
 * Solver output for one slot. [order] is the 0-based position among the remaining clicks of "Click in order"
 * (0 is the next pane), [number] the pane number written on it. For the colour cycle, [leftClicks] and [rightClicks]
 * are how many clicks each mouse button needs to reach the target colour.
 */
data class TerminalCell(
    val item: TerminalItem,
    val state: TerminalCellState,
    val order: Int? = null,
    val number: Int? = null,
    val leftClicks: Int = 0,
    val rightClicks: Int = 0,
    val rubixColor: RubixColor? = null,
    val melodyRole: MelodyRole? = null,
) {
    val slot: Int get() = item.slot
}

/**
 * [melodyButton] is the slot of the active melody button, which every click on the melody panel goes to;
 * [melodyColumn] the target column and [melodyRowsLeft] how many buttons are still to press.
 */
data class TerminalSolution(
    val type: TerminalType,
    val cells: List<TerminalCell>,
    val rubixTarget: RubixColor? = null,
    val melodyButton: Int? = null,
    val melodyColumn: Int? = null,
    val melodyRowsLeft: Int? = null,
) {
    val remaining: Int get() = melodyRowsLeft ?: cells.count { it.state == TerminalCellState.TARGET }

    fun cellAt(slot: Int): TerminalCell? = cells.firstOrNull { it.slot == slot }
}

/** The colour-cycle terminal's pane colours, in the order a left click advances them. */
enum class RubixColor(val itemId: String, val rgb: Int) {
    RED("red_stained_glass_pane", 0xB02E26),
    ORANGE("orange_stained_glass_pane", 0xF9801D),
    YELLOW("yellow_stained_glass_pane", 0xFED83D),
    GREEN("green_stained_glass_pane", 0x5E7C16),
    BLUE("blue_stained_glass_pane", 0x3C44AA),
    ;

    companion object {
        fun fromItemId(itemId: String): RubixColor? = entries.firstOrNull { it.itemId == itemId }
    }
}
