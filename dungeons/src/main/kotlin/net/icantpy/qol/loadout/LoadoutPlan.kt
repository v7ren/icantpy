package net.icantpy.qol.loadout

import net.icantpy.util.Text

/** What the auto loadout run does next, given the loadouts visible on the current menu page. */
enum class LoadoutStep { PRESS, NEXT_PAGE, PREVIOUS_PAGE, MISSING }

/** Free of Minecraft types so loadout numbering can be tested directly. */
object LoadoutPlan {
    const val MAX_LOADOUT = 18

    private val ICON_NAME = Regex("^Loadout\\s+(\\d+)$", RegexOption.IGNORE_CASE)
    private val PAGE_TITLE = Regex("\\((\\d+)\\s*/\\s*(\\d+)\\)")

    /** The loadout number shown by an icon's hover name ("Loadout 3"), or null for anything else. */
    fun numberFromIconName(name: String): Int? =
        ICON_NAME.matchEntire(Text.strip(name).trim())?.groupValues?.get(1)?.toIntOrNull()

    /**
     * The menu slot showing loadout [number], from [entries] as (slot index, loadout number) pairs.
     * Null when no entry carries that number.
     */
    fun pickByNumber(entries: List<Pair<Int, Int>>, number: Int): Int? =
        entries.firstOrNull { (_, shown) -> shown == number }?.first

    /**
     * Decides how to reach loadout [number] from a page showing [numbersOnPage], where [hasNext] and
     * [hasPrev] say whether the page offers arrows. Numbers above the page go forward and numbers
     * below go back; anything else cannot be reached from here.
     */
    fun pageDirection(numbersOnPage: List<Int>, number: Int, hasNext: Boolean, hasPrev: Boolean): LoadoutStep {
        if (number in numbersOnPage) return LoadoutStep.PRESS
        val low = numbersOnPage.minOrNull()
        val high = numbersOnPage.maxOrNull()
        return when {
            low == null || high == null -> when {
                hasNext -> LoadoutStep.NEXT_PAGE
                hasPrev -> LoadoutStep.PREVIOUS_PAGE
                else -> LoadoutStep.MISSING
            }
            number > high && hasNext -> LoadoutStep.NEXT_PAGE
            number < low && hasPrev -> LoadoutStep.PREVIOUS_PAGE
            else -> LoadoutStep.MISSING
        }
    }

    /** Current and total 1-based page numbers from a title such as "(2 / 3) Loadouts". */
    fun pageOf(title: String): Pair<Int, Int>? {
        val match = PAGE_TITLE.find(Text.strip(title)) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        return current to total
    }
}
