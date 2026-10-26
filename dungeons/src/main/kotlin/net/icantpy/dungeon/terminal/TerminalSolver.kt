package net.icantpy.dungeon.terminal

/**
 * Pure terminal solvers. They never click: they only say which slots still need a click, which are done and which
 * are wrong. Item ids are modern registry paths, as Hypixel's 1.8 items reach a current client through ViaVersion.
 *
 * [pending] holds clicks sent but not yet confirmed by the server, keyed by slot. For the colour cycle the value is
 * the signed number of steps (left click +1, right click -1); for every other terminal any entry means "clicked".
 */
object TerminalSolver {
    private const val RED_PANE = "red_stained_glass_pane"
    private const val LIME_PANE = "lime_stained_glass_pane"
    private const val PANE_SUFFIX = "_stained_glass_pane"
    private const val PLAIN_PANE = "glass_pane"
    private const val MAGENTA_PANE = "magenta_stained_glass_pane"
    private const val LIME_TERRACOTTA = "lime_terracotta"
    private const val RED_TERRACOTTA = "red_terracotta"
    private const val FIRST_ITEM_COLUMN = 1
    private const val LAST_ITEM_COLUMN = 7

    /** 1.8 dye items whose modern ids do not start with their colour. */
    private val DYE_ALIASES = mapOf(
        "blue" to "lapis_lazuli",
        "brown" to "cocoa_beans",
        "white" to "bone_meal",
        "black" to "ink_sac",
    )

    fun solve(
        type: TerminalType,
        snapshot: TerminalSnapshot,
        pending: Map<Int, Int> = emptyMap(),
        lockedRubixTarget: RubixColor? = null,
        confirmed: Set<Int> = emptySet(),
    ): TerminalSolution = when (type) {
        TerminalType.PANES -> panes(snapshot, pending)
        TerminalType.IN_ORDER -> inOrder(snapshot, pending)
        TerminalType.STARTS_WITH -> startsWith(snapshot, pending, confirmed)
        TerminalType.SELECT_COLOR -> selectColor(snapshot, pending, confirmed)
        TerminalType.RUBIX -> rubix(snapshot, pending, lockedRubixTarget)
        TerminalType.MELODY -> melody(snapshot)
    }

    private fun panes(snapshot: TerminalSnapshot, pending: Map<Int, Int>): TerminalSolution {
        val cells = snapshot.items.mapNotNull { item ->
            when (item.itemId) {
                RED_PANE -> TerminalCell(item, if (item.slot in pending) TerminalCellState.DONE else TerminalCellState.TARGET)
                LIME_PANE -> TerminalCell(item, TerminalCellState.DONE)
                else -> null
            }
        }
        return TerminalSolution(TerminalType.PANES, cells)
    }

    private fun inOrder(snapshot: TerminalSnapshot, pending: Map<Int, Int>): TerminalSolution {
        val panes = snapshot.items.filter { it.itemId == RED_PANE || it.itemId == LIME_PANE }
        val open = panes.filter { it.itemId == RED_PANE && it.slot !in pending }.sortedBy { it.count }
        val rank = open.withIndex().associate { (index, item) -> item.slot to index }
        val cells = panes.map { item ->
            val order = rank[item.slot]
            TerminalCell(
                item = item,
                state = if (order != null) TerminalCellState.TARGET else TerminalCellState.DONE,
                order = order,
                number = item.count,
            )
        }
        return TerminalSolution(TerminalType.IN_ORDER, cells)
    }

    private fun startsWith(snapshot: TerminalSnapshot, pending: Map<Int, Int>, confirmed: Set<Int>): TerminalSolution {
        val letter = TerminalType.startsWithLetter(snapshot.title)
        val cells = itemArea(snapshot).map { item ->
            val matches = letter != null && item.name.startsWith(letter, ignoreCase = true)
            TerminalCell(item, pickState(item, pending, confirmed, matches))
        }
        return TerminalSolution(TerminalType.STARTS_WITH, cells)
    }

    private fun selectColor(snapshot: TerminalSnapshot, pending: Map<Int, Int>, confirmed: Set<Int>): TerminalSolution {
        val color = TerminalType.selectColorWords(snapshot.title)?.let(::colorPrefix)
        val cells = itemArea(snapshot).map { item ->
            val matches = color != null && matchesColor(item.itemId, color)
            TerminalCell(item, pickState(item, pending, confirmed, matches))
        }
        return TerminalSolution(TerminalType.SELECT_COLOR, cells)
    }

    private fun rubix(snapshot: TerminalSnapshot, pending: Map<Int, Int>, locked: RubixColor?): TerminalSolution {
        val panes = snapshot.items.mapNotNull { item ->
            val observed = RubixColor.fromItemId(item.itemId) ?: return@mapNotNull null
            item to step(observed, pending[item.slot] ?: 0)
        }
        if (panes.isEmpty()) return TerminalSolution(TerminalType.RUBIX, emptyList())
        val target = locked ?: cheapestTarget(panes.map { it.second })
        val cells = panes.map { (item, color) ->
            val left = leftClicks(color, target)
            val right = (RubixColor.entries.size - left) % RubixColor.entries.size
            TerminalCell(
                item = item,
                state = if (left == 0) TerminalCellState.DONE else TerminalCellState.TARGET,
                leftClicks = left,
                rightClicks = right,
                rubixColor = color,
            )
        }
        return TerminalSolution(TerminalType.RUBIX, cells, target)
    }

    /**
     * Melody: a lime pane moves along a row of panes and the row's lime terracotta button must be clicked while it is
     * under the magenta column marker. Other rows keep red terracotta. Clicks are never predicted: the pane moves on
     * its own, so only the server's answer says whether a press landed.
     */
    private fun melody(snapshot: TerminalSnapshot): TerminalSolution {
        val items = snapshot.items
        val buttons = items.filter { it.itemId == LIME_TERRACOTTA || it.itemId == RED_TERRACOTTA }
        val active = buttons.lastOrNull { it.itemId == LIME_TERRACOTTA }
        val activeRow = active?.let { snapshot.row(it.slot) }
        val column = items.firstOrNull { it.itemId == MAGENTA_PANE }?.let { snapshot.column(it.slot) }
        // The track is the panes of each button's row, between the left border and the button.
        val buttonColumnByRow = buttons.associate { snapshot.row(it.slot) to snapshot.column(it.slot) }
        val track = items.filter { item ->
            val buttonColumn = buttonColumnByRow[snapshot.row(item.slot)] ?: return@filter false
            isPane(item.itemId) && snapshot.column(item.slot) in FIRST_ITEM_COLUMN until buttonColumn
        }
        val pointer = track.lastOrNull { it.itemId == LIME_PANE && snapshot.row(it.slot) == activeRow }
            ?: track.lastOrNull { it.itemId == LIME_PANE }
        val aligned = pointer != null && column != null && snapshot.column(pointer.slot) == column
        val live = if (aligned) TerminalCellState.TARGET else TerminalCellState.WRONG
        val trackSlots = track.map { it.slot }.toSet()
        val cells = items.mapNotNull { item ->
            when {
                item.slot == pointer?.slot -> TerminalCell(item, live, melodyRole = MelodyRole.POINTER)
                item.slot in trackSlots -> TerminalCell(item, TerminalCellState.DONE, melodyRole = MelodyRole.TRACK)
                item.itemId == MAGENTA_PANE -> TerminalCell(item, TerminalCellState.DONE, melodyRole = MelodyRole.COLUMN)
                item.slot == active?.slot -> TerminalCell(item, live, melodyRole = MelodyRole.BUTTON)
                item in buttons -> TerminalCell(item, TerminalCellState.DONE, melodyRole = MelodyRole.BUTTON)
                else -> null
            }
        }
        val rowsLeft = if (activeRow == null) 0 else buttons.count { snapshot.row(it.slot) >= activeRow }
        return TerminalSolution(
            TerminalType.MELODY, cells,
            melodyButton = active?.slot, melodyColumn = column, melodyRowsLeft = rowsLeft,
        )
    }

    /** The target colour that needs the fewest clicks in total, using the shorter direction for each pane. */
    fun cheapestTarget(colors: List<RubixColor>): RubixColor = RubixColor.entries.minBy { target ->
        colors.sumOf { color ->
            val left = leftClicks(color, target)
            minOf(left, (RubixColor.entries.size - left) % RubixColor.entries.size)
        }
    }

    fun leftClicks(from: RubixColor, to: RubixColor): Int {
        val size = RubixColor.entries.size
        return ((to.ordinal - from.ordinal) % size + size) % size
    }

    private fun step(color: RubixColor, steps: Int): RubixColor {
        val size = RubixColor.entries.size
        return RubixColor.entries[((color.ordinal + steps) % size + size) % size]
    }

    /** Items in the inner columns of the middle rows, skipping the glass-pane border. */
    private fun itemArea(snapshot: TerminalSnapshot): List<TerminalItem> = snapshot.items.filter { item ->
        val row = snapshot.row(item.slot)
        val column = snapshot.column(item.slot)
        row in 1..(snapshot.rows - 2) &&
            column in FIRST_ITEM_COLUMN..LAST_ITEM_COLUMN &&
            !isPane(item.itemId)
    }

    /**
     * A glint marks an item as clicked, except on items that glint by themselves: those only count once the click
     * was sent and the server answered ([confirmed]) or is still pending.
     */
    private fun pickState(item: TerminalItem, pending: Map<Int, Int>, confirmed: Set<Int>, matches: Boolean): TerminalCellState = when {
        item.slot in pending || item.slot in confirmed -> TerminalCellState.DONE
        item.glint && !item.naturalGlint -> TerminalCellState.DONE
        matches -> TerminalCellState.TARGET
        else -> TerminalCellState.WRONG
    }

    private fun isPane(itemId: String): Boolean = itemId == PLAIN_PANE || itemId.endsWith(PANE_SUFFIX)

    /**
     * Modern id prefix for a title colour. Hypixel still writes 1.8 names, so "SILVER" is light gray.
     * Unknown words are returned as written, lowercased, which simply matches nothing.
     */
    fun colorPrefix(words: String): String = when (val normalized = words.trim().uppercase().replace(' ', '_')) {
        "SILVER", "LIGHT_GREY" -> "light_gray"
        else -> normalized.lowercase()
    }

    fun matchesColor(itemId: String, colorPrefix: String): Boolean =
        itemId.startsWith("${colorPrefix}_") || DYE_ALIASES[colorPrefix] == itemId
}
