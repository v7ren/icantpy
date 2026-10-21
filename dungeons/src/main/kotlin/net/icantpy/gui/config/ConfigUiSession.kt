package net.icantpy.gui.config

import net.icantpy.dungeon.leap.LeapDungeonClass

/**
 * Config window state kept for the whole game session: the open page, the selected subtab of
 * each page and the scroll position of every (page, subtab) pair.
 */
internal object ConfigUiSession {
    var page: ConfigPage = ConfigPage.OVERVIEW
        private set

    private val subtabs = HashMap<ConfigPage, Int>()
    private val scrolls = HashMap<ScrollKey, Int>()

    // null follows the live class; EMPTY explicitly browses all classes.
    var leapViewClass: LeapDungeonClass? = null
    val leapExpandedCards: MutableSet<String> = linkedSetOf()

    private data class ScrollKey(val page: ConfigPage, val subtab: Int)

    fun subtabOf(page: ConfigPage): Int = subtabs[page] ?: 0

    fun scrollOf(page: ConfigPage, subtab: Int = subtabOf(page)): Int = scrolls[ScrollKey(page, subtab)] ?: 0

    fun save(page: ConfigPage, subtab: Int, value: Int) {
        this.page = page
        scrolls[ScrollKey(page, subtab)] = value.coerceAtLeast(0)
    }

    fun select(page: ConfigPage) {
        this.page = page
    }

    /** Selects a subtab of the current page, clamped to the page's tab list. */
    fun selectSubtab(index: Int) {
        val count = page.subtabs.size
        if (count == 0) return
        subtabs[page] = index.coerceIn(0, count - 1)
    }

    fun selectCosmeticsItems() {
        page = ConfigPage.ITEMS
    }

    fun selectShards() {
        page = ConfigPage.SHARDS
        scrolls[ScrollKey(ConfigPage.SHARDS, subtabOf(ConfigPage.SHARDS))] = 0
    }

    fun reset() {
        page = ConfigPage.OVERVIEW
        subtabs.clear()
        scrolls.clear()
        leapViewClass = null
        leapExpandedCards.clear()
    }
}
