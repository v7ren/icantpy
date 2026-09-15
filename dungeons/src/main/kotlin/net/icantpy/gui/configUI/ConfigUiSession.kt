package net.icantpy.gui.configUI

import net.icantpy.modules.impl.dungeon.leaporient.LeapDungeonClass

internal enum class LeapConfigPage {
    ROUTE,
    MENU,
    DEBUG,
    ;

    fun label(): String = when (this) {
        ROUTE -> "Route"
        MENU -> "Menu"
        DEBUG -> "Debug"
    }
}

internal object ConfigUiSession {
    var tab: ConfigTab = ConfigTab.TIMERS
        private set
    var leapPage: LeapConfigPage = LeapConfigPage.ROUTE
    // null follows the live class; EMPTY explicitly browses all classes.
    var leapViewClass: LeapDungeonClass? = null
    val leapCollapsed: MutableSet<String> = linkedSetOf()
    val leapExpandedCards: MutableSet<String> = linkedSetOf()

    private val scroll = IntArray(ConfigTab.entries.size)

    fun scrollOf(tab: ConfigTab): Int = scroll[tab.ordinal]

    fun save(tab: ConfigTab, value: Int) {
        this.tab = tab
        scroll[tab.ordinal] = value.coerceAtLeast(0)
    }

    fun select(tab: ConfigTab) {
        this.tab = tab
    }

    fun reset() {
        tab = ConfigTab.TIMERS
        leapPage = LeapConfigPage.ROUTE
        leapViewClass = null
        leapCollapsed.clear()
        leapExpandedCards.clear()
        scroll.fill(0)
    }
}
