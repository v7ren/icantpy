package net.icantpy.gui.config

import net.icantpy.dungeon.leap.LeapDungeonClass

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
    var tab: ConfigTab = ConfigTab.DUNGEONS
        private set
    var dungeonPage: DungeonPage = DungeonPage.TIMERS
    var cosmeticsPage: CosmeticsPage = CosmeticsPage.ITEMS
    var qolPage: QolPage = QolPage.CAMERA
    var slayerPage: SlayerPage = SlayerPage.CARRIES
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

    fun pageIndex(): Int = when (tab) {
        ConfigTab.DUNGEONS -> dungeonPage.ordinal
        ConfigTab.COSMETICS -> cosmeticsPage.ordinal
        ConfigTab.QOL -> qolPage.ordinal
        ConfigTab.SLAYER -> slayerPage.ordinal
        ConfigTab.GUI -> 0
    }

    fun selectPage(index: Int) {
        val clamped = index.coerceIn(0, (tab.pages().size - 1).coerceAtLeast(0))
        when (tab) {
            ConfigTab.DUNGEONS -> dungeonPage = DungeonPage.entries[clamped]
            ConfigTab.COSMETICS -> cosmeticsPage = CosmeticsPage.entries[clamped]
            ConfigTab.QOL -> qolPage = QolPage.entries[clamped]
            ConfigTab.SLAYER -> slayerPage = SlayerPage.entries[clamped]
            ConfigTab.GUI -> {}
        }
    }

    fun selectCosmeticsItems() {
        tab = ConfigTab.COSMETICS
        cosmeticsPage = CosmeticsPage.ITEMS
    }

    fun selectShards() {
        tab = ConfigTab.QOL
        qolPage = QolPage.SHARDS
        scroll[ConfigTab.QOL.ordinal] = 0
    }

    fun reset() {
        tab = ConfigTab.DUNGEONS
        dungeonPage = DungeonPage.TIMERS
        cosmeticsPage = CosmeticsPage.ITEMS
        qolPage = QolPage.CAMERA
        slayerPage = SlayerPage.CARRIES
        leapPage = LeapConfigPage.ROUTE
        leapViewClass = null
        leapCollapsed.clear()
        leapExpandedCards.clear()
        scroll.fill(0)
    }
}
