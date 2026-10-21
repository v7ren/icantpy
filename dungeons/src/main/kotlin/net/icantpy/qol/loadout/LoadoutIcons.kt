package net.icantpy.qol.loadout

import net.icantpy.util.Text

/**
 * Free of Minecraft types. Finds loadout icons in the Loadouts menu and numbers them.
 *
 * Real menu (dumped from Hypixel, page "(1/3) Loadouts", 54 container slots):
 * - Loadout icons are heads/chestplates/dyes in a 4x3 grid: rows 1-4 (slots 9-17, 18-26, 27-35, 36-44) and
 *   columns 5-7 of each row (slots 14-16, 23-25, 32-34, 41-43). That is 12 per page, numbered row-major from 1.
 * - Each loadout icon has a "Right-click to edit" lore line. Configured icons also list "Helmet:", "Pet:", etc.
 *   Empty ones have "Helmet: None" and "You must customize this loadout". Icons named "Loadout N" carry N.
 * - Decorative equipment icons (slots 10-11, 19-21, 28-29, 37-38, the equipped set's pieces) use "Right-click to
 *   equip!" and are NOT loadouts. The tree/power-stone/tuning/nav icons use "Click to view!" or are arrows.
 *
 * Numbering: explicit "Loadout N" / "Loadout #N" text (name first, then lore) wins. Otherwise the grid position
 * gives (page - 1) * 12 + row-major index + 1. Icons outside the grid fall back to slot order among candidates.
 */
object LoadoutIcons {
    /** Icons per page used by the fallback numbering when the grid position is unknown. */
    const val FALLBACK_ICONS_PER_PAGE = 12

    /** Loadout grid: 4 rows x 3 columns, rows start at slot row 1 (slot 9), columns at slot column 5. */
    private val GRID_ROWS = 1..4
    private val GRID_COLUMNS = 5..7
    const val GRID_PER_PAGE = 12

    /** A container slot: index, hover name and lore lines (§ codes kept). */
    data class Candidate(val slotIndex: Int, val name: String, val lore: List<String>)

    private const val EDIT_MARKER = "click to edit"
    private val NAV_NAMES = setOf("close", "back", "go back", "next", "previous", "next page", "previous page")
    private val NAV_PREFIXES = listOf("go back", "next page", "previous page")
    private val EXPLICIT = Regex("loadout\\s*#?\\s*(\\d+)(?!\\d)", RegexOption.IGNORE_CASE)

    fun isNavigationName(name: String): Boolean {
        val clean = Text.strip(name).trim().lowercase()
        return clean in NAV_NAMES || NAV_PREFIXES.any { clean.startsWith(it) }
    }

    /** Row-major index (0..11) of [slotIndex] inside the loadout grid, or null when it lies outside the grid. */
    fun gridIndex(slotIndex: Int): Int? {
        val row = slotIndex / 9
        val column = slotIndex % 9
        if (row !in GRID_ROWS || column !in GRID_COLUMNS) return null
        return (row - GRID_ROWS.first) * GRID_COLUMNS.count() + (column - GRID_COLUMNS.first)
    }

    /**
     * True for a non-blank, non-navigation slot that is a loadout: its name is "Loadout N", or its lore has the
     * "Right-click to edit" line that every loadout icon carries. Equip-hint lines are not enough, since decorative
     * equipment icons also say "Right-click to equip!".
     */
    fun isLoadoutCandidate(name: String, lore: List<String>): Boolean {
        val clean = Text.strip(name).trim()
        if (clean.isEmpty() || isNavigationName(clean)) return false
        if (LoadoutEquipped.numberFromName(clean) != null) return true
        return lore.any { Text.strip(it).lowercase().contains(EDIT_MARKER) }
    }

    /** Explicit number from the name first, then from any lore line ("Loadout 4", "Loadout #4"); null when none. */
    fun explicitNumber(name: String, lore: List<String>): Int? =
        (listOf(name) + lore).firstNotNullOfOrNull { line ->
            EXPLICIT.find(Text.strip(line))?.groupValues?.get(1)?.toIntOrNull()
        }

    /**
     * Numbers the candidates as (slot index, loadout number), sorted by number. Explicit numbers win; grid icons take
     * (page - 1) * 12 + grid index + 1; any other icon takes pageOffset + position + 1, with positions counted in
     * slot order among all candidates.
     */
    fun number(candidates: List<Candidate>, page: Int, iconsPerPage: Int): List<Pair<Int, Int>> {
        val pageIndex = (page - 1).coerceAtLeast(0)
        val offset = pageIndex * iconsPerPage.coerceAtLeast(0)
        return candidates.sortedBy { it.slotIndex }
            .mapIndexed { position, candidate ->
                val number = explicitNumber(candidate.name, candidate.lore)
                    ?: gridIndex(candidate.slotIndex)?.let { pageIndex * GRID_PER_PAGE + it + 1 }
                    ?: (offset + position + 1)
                candidate.slotIndex to number
            }
            .sortedBy { it.second }
    }
}
