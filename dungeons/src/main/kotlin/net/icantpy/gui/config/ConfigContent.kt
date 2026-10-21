package net.icantpy.gui.config

/** One body the config window can draw. Pages with subtabs have one entry per subtab, in tab order. */
internal enum class ConfigContent {
    OVERVIEW,
    CLOCKS_DISPLAY,
    CLOCKS_DUNGEON,
    CLOCKS_MECHANICS,
    CLOCKS_DEBUG,
    ALERTS,
    SPLITS,
    SCORE,
    DRAGONS,
    LEAP_ROUTES,
    LEAP_LAYOUT,
    LEAP_LOOK,
    LEAP_QUICK_LEAP,
    LEAP_KEYBINDS,
    LEAP_DEBUG,
    ITEMS_CUSTOMIZE,
    ITEMS_SHARING,
    MORPH,
    VISUALS_CLEANUP,
    VISUALS_WORLD,
    VISUALS_CAMERA,
    VISUALS_VIEWMODEL,
    VISUALS_HAND,
    VISUALS_EFFECTS,
    VISUALS_MODEL,
    FREELOOK,
    WAYPOINTS,
    STATS,
    SHARDS,
    INVENTORY_SORT,
    DROP_ULT,
    CARRIES_COUNTER,
    CARRIES_KEYS,
    CARRIES_PLAYERS,
    FIRE_PITS,
    BOSS_AIM,
    APPEARANCE_THEME,
    APPEARANCE_TEXT_SHAPE,
    APPEARANCE_ADVANCED,
    HUD_LAYOUT,
    PERFORMANCE,
}

/**
 * Maps each page's subtab index to the body the screen draws. Pure, so the dispatch in
 * IcantpyConfigScreen and the subtab lists in [ConfigPage] can be checked against each other.
 */
internal object ConfigContents {
    private val byPage: Map<ConfigPage, List<ConfigContent>> = mapOf(
        ConfigPage.OVERVIEW to listOf(ConfigContent.OVERVIEW),
        ConfigPage.CLOCKS to listOf(
            ConfigContent.CLOCKS_DISPLAY,
            ConfigContent.CLOCKS_DUNGEON,
            ConfigContent.CLOCKS_MECHANICS,
            ConfigContent.CLOCKS_DEBUG,
        ),
        ConfigPage.ALERTS to listOf(ConfigContent.ALERTS),
        ConfigPage.SPLITS to listOf(ConfigContent.SPLITS),
        ConfigPage.SCORE to listOf(ConfigContent.SCORE),
        ConfigPage.DRAGONS to listOf(ConfigContent.DRAGONS),
        ConfigPage.LEAP to listOf(
            ConfigContent.LEAP_ROUTES,
            ConfigContent.LEAP_LAYOUT,
            ConfigContent.LEAP_LOOK,
            ConfigContent.LEAP_QUICK_LEAP,
            ConfigContent.LEAP_KEYBINDS,
            ConfigContent.LEAP_DEBUG,
        ),
        ConfigPage.ITEMS to listOf(ConfigContent.ITEMS_CUSTOMIZE, ConfigContent.ITEMS_SHARING),
        ConfigPage.MORPH to listOf(ConfigContent.MORPH),
        ConfigPage.VISUALS to listOf(
            ConfigContent.VISUALS_CLEANUP,
            ConfigContent.VISUALS_WORLD,
            ConfigContent.VISUALS_CAMERA,
            ConfigContent.VISUALS_VIEWMODEL,
            ConfigContent.VISUALS_HAND,
            ConfigContent.VISUALS_EFFECTS,
            ConfigContent.VISUALS_MODEL,
        ),
        ConfigPage.FREELOOK to listOf(ConfigContent.FREELOOK),
        ConfigPage.PERFORMANCE to listOf(ConfigContent.PERFORMANCE),
        ConfigPage.WAYPOINTS to listOf(ConfigContent.WAYPOINTS),
        ConfigPage.STATS to listOf(ConfigContent.STATS),
        ConfigPage.SHARDS to listOf(ConfigContent.SHARDS),
        ConfigPage.INVENTORY_SORT to listOf(ConfigContent.INVENTORY_SORT),
        ConfigPage.DROP_ULT to listOf(ConfigContent.DROP_ULT),
        ConfigPage.CARRIES to listOf(
            ConfigContent.CARRIES_COUNTER,
            ConfigContent.CARRIES_KEYS,
            ConfigContent.CARRIES_PLAYERS,
        ),
        ConfigPage.FIRE_PITS to listOf(ConfigContent.FIRE_PITS),
        ConfigPage.BOSS_AIM to listOf(ConfigContent.BOSS_AIM),
        ConfigPage.APPEARANCE to listOf(
            ConfigContent.APPEARANCE_THEME,
            ConfigContent.APPEARANCE_TEXT_SHAPE,
            ConfigContent.APPEARANCE_ADVANCED,
        ),
        ConfigPage.HUD_LAYOUT to listOf(ConfigContent.HUD_LAYOUT),
    )

    /** Bodies for [page] in subtab order. Every page has at least one. */
    fun of(page: ConfigPage): List<ConfigContent> = byPage.getValue(page)

    /** Body for [page]'s [subtab] index; out-of-range indexes clamp to the nearest subtab. */
    fun at(page: ConfigPage, subtab: Int): ConfigContent {
        val contents = of(page)
        return contents[subtab.coerceIn(0, contents.lastIndex)]
    }
}
