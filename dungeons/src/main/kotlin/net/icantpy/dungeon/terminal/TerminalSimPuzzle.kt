package net.icantpy.dungeon.terminal

import kotlin.random.Random

/** One slot of a simulated terminal. [customName] is set where the name matters (the letter terminal). */
data class SimSlot(val itemId: String, val count: Int = 1, val customName: String? = null, val glint: Boolean = false)

/** What a click on the simulator did. */
enum class SimClickResult { APPLIED, MISCLICK, IGNORED }

/**
 * A randomly generated terminal that answers clicks the way Hypixel's do, for practice outside phase 3.
 * Pure: the caller turns [slots] into item stacks and opens a client-only chest. Layouts follow the real terminals:
 * panes 5 rows, in order 4 rows, letter and colour 6 rows, colour cycle a 3 by 3 grid in 5 rows, melody four
 * tracks of five panes in 6 rows. Melody is the only one that changes on its own: the caller calls [advance] on a timer.
 */
class TerminalSimPuzzle private constructor(
    val type: TerminalType,
    val title: String,
    val rows: Int,
    private val slots: MutableMap<Int, SimSlot>,
) {
    var misclicks: Int = 0
        private set

    // Melody state: the row being played (0-based among the tracks), the moving pane's column and direction, and
    // the column it has to be in when the button is pressed.
    private var melodyRow = 0
    private var pointerColumn = FIRST_TRACK_COLUMN
    private var pointerStep = 1
    private var melodyColumn = FIRST_TRACK_COLUMN
    private var melodyRandom: Random = Random.Default

    fun slot(index: Int): SimSlot? = slots[index]

    fun snapshot(): Map<Int, SimSlot> = slots.toMap()

    /** Applies a click with the server-side mouse [button] (0 left, 1 right, 2 middle). */
    fun click(index: Int, button: Int): SimClickResult {
        val slot = slots[index] ?: return SimClickResult.IGNORED
        val result = when (type) {
            TerminalType.PANES -> togglePane(index, slot)
            TerminalType.IN_ORDER -> clickInOrder(index, slot)
            TerminalType.STARTS_WITH -> pick(index, slot, startsWithLetter(slot))
            TerminalType.SELECT_COLOR -> pick(index, slot, colorMatches(slot))
            TerminalType.RUBIX -> turn(index, slot, button)
            TerminalType.MELODY -> press(index)
        }
        if (result == SimClickResult.MISCLICK) misclicks++
        return result
    }

    fun solved(): Boolean = when (type) {
        TerminalType.PANES -> slots.values.none { it.itemId == RED }
        TerminalType.IN_ORDER -> slots.values.none { it.itemId == RED }
        TerminalType.STARTS_WITH -> slots.values.none { !it.glint && startsWithLetter(it) }
        TerminalType.SELECT_COLOR -> slots.values.none { !it.glint && colorMatches(it) }
        TerminalType.RUBIX -> slots.values.mapNotNull { RubixColor.fromItemId(it.itemId) }.distinct().size <= 1
        TerminalType.MELODY -> melodyRow >= MELODY_TRACKS
    }

    /** Moves the melody pane one column, bouncing between the ends of the track. No effect on other terminals. */
    fun advance() {
        if (type != TerminalType.MELODY || solved()) return
        if (pointerColumn + pointerStep !in FIRST_TRACK_COLUMN..LAST_TRACK_COLUMN) pointerStep = -pointerStep
        pointerColumn += pointerStep
        drawMelody()
    }

    private fun press(index: Int): SimClickResult {
        if (solved() || index != trackSlot(melodyRow, BUTTON_COLUMN)) return SimClickResult.IGNORED
        if (pointerColumn != melodyColumn) return SimClickResult.MISCLICK
        melodyRow++
        startMelodyRow()
        return SimClickResult.APPLIED
    }

    private fun startMelodyRow() {
        pointerColumn = FIRST_TRACK_COLUMN
        pointerStep = 1
        melodyColumn = melodyRandom.nextInt(FIRST_TRACK_COLUMN, LAST_TRACK_COLUMN + 1)
        drawMelody()
    }

    /** Magenta markers above and below the target column, the tracks, and red buttons except the active lime one. */
    private fun drawMelody() {
        for (column in FIRST_TRACK_COLUMN..LAST_TRACK_COLUMN) {
            val marker = if (column == melodyColumn && !solved()) MAGENTA else FILLER
            slots[column] = SimSlot(marker)
            slots[(MELODY_TRACKS + 1) * 9 + column] = SimSlot(marker)
        }
        for (row in 0 until MELODY_TRACKS) {
            for (column in FIRST_TRACK_COLUMN..LAST_TRACK_COLUMN) {
                val pane = when {
                    row < melodyRow -> GREEN
                    row == melodyRow && column == pointerColumn -> LIME
                    else -> WHITE
                }
                slots[trackSlot(row, column)] = SimSlot(pane)
            }
            val button = when {
                row < melodyRow -> GREEN_TERRACOTTA
                row == melodyRow -> LIME_TERRACOTTA
                else -> RED_TERRACOTTA
            }
            slots[trackSlot(row, BUTTON_COLUMN)] = SimSlot(button)
        }
    }

    private fun trackSlot(row: Int, column: Int): Int = (row + 1) * 9 + column

    private fun togglePane(index: Int, slot: SimSlot): SimClickResult = when (slot.itemId) {
        RED -> SimClickResult.APPLIED.also { slots[index] = slot.copy(itemId = LIME) }
        // Hypixel turns a finished pane back to red, which is why it counts as a misclick.
        LIME -> SimClickResult.MISCLICK.also { slots[index] = slot.copy(itemId = RED) }
        else -> SimClickResult.IGNORED
    }

    private fun clickInOrder(index: Int, slot: SimSlot): SimClickResult {
        if (slot.itemId != RED) return if (slot.itemId == LIME) SimClickResult.MISCLICK else SimClickResult.IGNORED
        val next = slots.values.filter { it.itemId == RED }.minOf { it.count }
        if (slot.count != next) return SimClickResult.MISCLICK
        slots[index] = slot.copy(itemId = LIME)
        return SimClickResult.APPLIED
    }

    private fun pick(index: Int, slot: SimSlot, matches: Boolean): SimClickResult {
        if (isPane(slot.itemId)) return SimClickResult.IGNORED
        if (slot.glint || !matches) return SimClickResult.MISCLICK
        slots[index] = slot.copy(glint = true)
        return SimClickResult.APPLIED
    }

    private fun turn(index: Int, slot: SimSlot, button: Int): SimClickResult {
        val color = RubixColor.fromItemId(slot.itemId) ?: return SimClickResult.IGNORED
        // Middle turns forward like left; that is the click the solver panel sends.
        val step = when (button) {
            0, 2 -> 1
            1 -> -1
            else -> return SimClickResult.IGNORED
        }
        val size = RubixColor.entries.size
        slots[index] = slot.copy(itemId = RubixColor.entries[((color.ordinal + step) % size + size) % size].itemId)
        return SimClickResult.APPLIED
    }

    private fun startsWithLetter(slot: SimSlot): Boolean {
        val letter = TerminalType.startsWithLetter(title) ?: return false
        return slot.customName?.startsWith(letter, ignoreCase = true) == true
    }

    private fun colorMatches(slot: SimSlot): Boolean {
        val words = TerminalType.selectColorWords(title) ?: return false
        return !isPane(slot.itemId) && TerminalSolver.matchesColor(slot.itemId, TerminalSolver.colorPrefix(words))
    }

    companion object {
        const val RED = "red_stained_glass_pane"
        const val LIME = "lime_stained_glass_pane"
        private const val FILLER = "black_stained_glass_pane"
        private const val MAGENTA = "magenta_stained_glass_pane"
        private const val WHITE = "white_stained_glass_pane"
        private const val GREEN = "green_stained_glass_pane"
        private const val LIME_TERRACOTTA = "lime_terracotta"
        private const val RED_TERRACOTTA = "red_terracotta"
        private const val GREEN_TERRACOTTA = "green_terracotta"
        private const val MELODY_TRACKS = 4
        private const val FIRST_TRACK_COLUMN = 1
        private const val LAST_TRACK_COLUMN = 5
        private const val BUTTON_COLUMN = 7
        private val PANES_AREA = (1..3).flatMap { row -> (2..6).map { column -> row * 9 + column } }
        private val IN_ORDER_AREA = (1..2).flatMap { row -> (1..7).map { column -> row * 9 + column } }
        private val ITEM_AREA = (1..4).flatMap { row -> (1..7).map { column -> row * 9 + column } }
        private val RUBIX_AREA = listOf(12, 13, 14, 21, 22, 23, 30, 31, 32)

        /** English names, so the letter terminal works whatever language the client uses. */
        private val NAMED_ITEMS: Map<Char, List<Pair<String, String>>> = mapOf(
            'A' to listOf("apple" to "Apple", "arrow" to "Arrow", "anvil" to "Anvil", "acacia_log" to "Acacia Log"),
            'B' to listOf("bread" to "Bread", "bone" to "Bone", "book" to "Book", "bucket" to "Bucket", "brick" to "Brick", "bowl" to "Bowl"),
            'C' to listOf("coal" to "Coal", "cookie" to "Cookie", "carrot" to "Carrot", "clock" to "Clock", "compass" to "Compass", "cactus" to "Cactus"),
            'D' to listOf("diamond" to "Diamond", "dirt" to "Dirt", "dandelion" to "Dandelion", "dead_bush" to "Dead Bush"),
            'G' to listOf("gold_ingot" to "Gold Ingot", "gunpowder" to "Gunpowder", "glass" to "Glass", "gravel" to "Gravel", "ghast_tear" to "Ghast Tear"),
            'L' to listOf("leather" to "Leather", "ladder" to "Ladder", "lever" to "Lever", "lily_pad" to "Lily Pad", "lantern" to "Lantern"),
            'M' to listOf("melon_slice" to "Melon Slice", "map" to "Map", "minecart" to "Minecart", "mushroom_stew" to "Mushroom Stew"),
            'P' to listOf("paper" to "Paper", "potato" to "Potato", "pumpkin" to "Pumpkin", "painting" to "Painting", "poppy" to "Poppy"),
            'S' to listOf("stone" to "Stone", "sand" to "Sand", "sugar" to "Sugar", "stick" to "Stick", "snowball" to "Snowball", "sponge" to "Sponge"),
            'T' to listOf("torch" to "Torch", "tnt" to "TNT", "tripwire_hook" to "Tripwire Hook", "terracotta" to "Terracotta"),
            'W' to listOf("wheat" to "Wheat", "water_bucket" to "Water Bucket", "white_wool" to "White Wool", "wooden_sword" to "Wooden Sword"),
        )

        /** Title words (Hypixel's 1.8 names) and the modern id prefix they select. */
        private val TITLE_COLORS = listOf(
            "RED" to "red", "ORANGE" to "orange", "YELLOW" to "yellow", "GREEN" to "green", "BLUE" to "blue",
            "LIME" to "lime", "PINK" to "pink", "PURPLE" to "purple", "WHITE" to "white", "BLACK" to "black",
            "LIGHT BLUE" to "light_blue", "SILVER" to "light_gray", "CYAN" to "cyan", "BROWN" to "brown",
        )
        private val COLOR_KINDS = listOf("wool", "stained_glass", "concrete", "terracotta", "dye")
        private val DYE_ALIASES = mapOf("blue" to "lapis_lazuli", "brown" to "cocoa_beans", "white" to "bone_meal", "black" to "ink_sac")

        fun random(type: TerminalType, random: Random = Random.Default): TerminalSimPuzzle = when (type) {
            TerminalType.PANES -> panes(random)
            TerminalType.IN_ORDER -> inOrder(random)
            TerminalType.STARTS_WITH -> startsWith(random)
            TerminalType.SELECT_COLOR -> selectColor(random)
            TerminalType.RUBIX -> rubix(random)
            TerminalType.MELODY -> melody(random)
        }

        private fun melody(random: Random): TerminalSimPuzzle {
            val puzzle = TerminalSimPuzzle(TerminalType.MELODY, "Click the button on time!", 6, border(6))
            puzzle.melodyRandom = random
            puzzle.startMelodyRow()
            return puzzle
        }

        private fun panes(random: Random): TerminalSimPuzzle {
            val slots = border(5)
            val redIndex = random.nextInt(PANES_AREA.size)
            PANES_AREA.forEachIndexed { index, slot ->
                val red = index == redIndex || random.nextInt(100) < 70
                slots[slot] = SimSlot(if (red) RED else LIME)
            }
            return TerminalSimPuzzle(TerminalType.PANES, "Correct all the panes!", 5, slots)
        }

        private fun inOrder(random: Random): TerminalSimPuzzle {
            val slots = border(4)
            val numbers = (1..IN_ORDER_AREA.size).shuffled(random)
            IN_ORDER_AREA.forEachIndexed { index, slot -> slots[slot] = SimSlot(RED, numbers[index]) }
            return TerminalSimPuzzle(TerminalType.IN_ORDER, "Click in order!", 4, slots)
        }

        private fun startsWith(random: Random): TerminalSimPuzzle {
            val letter = NAMED_ITEMS.keys.random(random)
            val matching = NAMED_ITEMS.getValue(letter)
            val others = NAMED_ITEMS.filterKeys { it != letter }.values.flatten()
            val matchCount = random.nextInt(3, minOf(6, matching.size) + 1)
            val chosen = (List(matchCount) { matching.random(random) } + List(ITEM_AREA.size - matchCount) { others.random(random) })
                .shuffled(random)
            val slots = border(6)
            ITEM_AREA.forEachIndexed { index, slot ->
                val (id, name) = chosen[index]
                slots[slot] = SimSlot(id, customName = name)
            }
            return TerminalSimPuzzle(TerminalType.STARTS_WITH, "What starts with: '$letter'?", 6, slots)
        }

        private fun selectColor(random: Random): TerminalSimPuzzle {
            val (word, prefix) = TITLE_COLORS.random(random)
            val matchCount = random.nextInt(3, 9)
            val others = TITLE_COLORS.map { it.second }.filter { it != prefix }
            val colors = (List(matchCount) { prefix } + List(ITEM_AREA.size - matchCount) { others.random(random) }).shuffled(random)
            val slots = border(6)
            ITEM_AREA.forEachIndexed { index, slot -> slots[slot] = SimSlot(coloredItem(colors[index], random)) }
            return TerminalSimPuzzle(TerminalType.SELECT_COLOR, "Select all the $word items!", 6, slots)
        }

        private fun rubix(random: Random): TerminalSimPuzzle {
            val slots = border(5)
            var colors = List(RUBIX_AREA.size) { RubixColor.entries.random(random) }
            if (colors.distinct().size == 1) colors = colors.mapIndexed { i, c -> if (i == 0) RubixColor.entries[(c.ordinal + 1) % 5] else c }
            RUBIX_AREA.forEachIndexed { index, slot -> slots[slot] = SimSlot(colors[index].itemId) }
            return TerminalSimPuzzle(TerminalType.RUBIX, "Change all to same color!", 5, slots)
        }

        private fun coloredItem(prefix: String, random: Random): String = when (val kind = COLOR_KINDS.random(random)) {
            "dye" -> DYE_ALIASES[prefix] ?: "${prefix}_dye"
            else -> "${prefix}_$kind"
        }

        /** Black panes everywhere; puzzles overwrite their own area. */
        private fun border(rows: Int): MutableMap<Int, SimSlot> =
            (0 until rows * 9).associateWith { SimSlot(FILLER) }.toMutableMap()

        private fun isPane(itemId: String): Boolean = itemId == "glass_pane" || itemId.endsWith("_stained_glass_pane")
    }
}
