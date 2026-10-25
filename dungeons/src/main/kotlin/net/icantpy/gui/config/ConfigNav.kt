package net.icantpy.gui.config

/** Sidebar groups. HOME holds only the overview and has no visible heading. */
internal enum class ConfigSection(val label: String) {
    HOME("Home"),
    DUNGEONS("Dungeons"),
    BOSSES("Bosses"),
    SLAYER("Slayer"),
    COSMETICS("Items & Cosmetics"),
    QOL("Utility"),
    INTERFACE("Interface"),
}

/**
 * Every page the config window can show. [moduleIds] lists the features a page owns; the first one
 * is the page's primary feature, whose enable toggle appears in the header and on overview cards.
 * [subtabs] are shown as a tab bar under the header and selected by index.
 */
internal enum class ConfigPage(
    val section: ConfigSection,
    val title: String,
    val caption: String,
    val moduleIds: List<AetherModuleId> = emptyList(),
    val subtabs: List<String> = emptyList(),
) {
    OVERVIEW(ConfigSection.HOME, "Overview", "Every feature at a glance"),
    CLOCKS(
        ConfigSection.DUNGEONS, "Clocks", "Phase & mechanic timers",
        listOf(AetherModuleId.CLOCKS, AetherModuleId.MECHANICS), listOf("Display", "Dungeon", "Mechanics", "Debug"),
    ),
    ALERTS(ConfigSection.DUNGEONS, "Alerts", "Sound and visual warnings on critical timings", listOf(AetherModuleId.ALERTS)),
    SPLITS(
        ConfigSection.DUNGEONS, "Splits & PBs", "Run splits, personal bests & sum of best",
        listOf(AetherModuleId.SPLITS),
    ),
    SCORE(
        ConfigSection.DUNGEONS, "Score", "Live score estimate & 270/300 alerts",
        listOf(AetherModuleId.SCORE),
    ),
    DRAGONS(
        ConfigSection.DUNGEONS, "Wither Dragons", "Priority, spawn timers & dragon kills",
        listOf(AetherModuleId.DRAGONS),
    ),
    LEAP(
        ConfigSection.DUNGEONS, "Spirit Leap", "Routes, trigger cards & auto-leap",
        listOf(AetherModuleId.LEAP),
        listOf("Routes", "Layout", "Look", "Quick leap", "Keybinds", "Debug"),
    ),
    BLOOD_HELPER(ConfigSection.DUNGEONS, "Blood Helper", "Aim at the box where each blood mob drops", listOf(AetherModuleId.BLOOD_HELPER)),
    LIVID_HELPER(ConfigSection.BOSSES, "Livid Helper", "Aim at the real Livid in F5 and M5", listOf(AetherModuleId.LIVID_HELPER)),
    CARRIES(ConfigSection.SLAYER, "Carry Counter", "Automatic customer boss completion tracking", listOf(AetherModuleId.CARRY), listOf("Counter", "Keys", "Players")),
    FIRE_PITS(
        ConfigSection.SLAYER, "Blaze", "Avoid fire, Quazii and Typhoeus in the Inferno Demonlord fight",
        listOf(AetherModuleId.FIRE_PITS), listOf("Avoid fire", "Avoid Quazii", "Avoid Typhoeus"),
    ),
    BOSS_AIM(ConfigSection.SLAYER, "Aim Assist", "Curved, never-instant aim at your slayer boss", listOf(AetherModuleId.BOSS_AIM)),
    ITEMS(ConfigSection.COSMETICS, "Item Customize", "Custom item tags, glints, dyes & lore", listOf(AetherModuleId.RENAME, AetherModuleId.SHARE), listOf("Customize", "Sharing")),
    MORPH(ConfigSection.COSMETICS, "Morph", "Client-side player model overrides", listOf(AetherModuleId.MORPH)),
    VISUALS(
        ConfigSection.COSMETICS, "Visuals", "Viewmodel, camera & world look",
        listOf(AetherModuleId.VISUALS), listOf("Cleanup", "World", "Camera", "Viewmodel", "Hand", "Effects", "Model"),
    ),
    FREELOOK(ConfigSection.QOL, "Freelook", "Decouple camera rotation from movement", listOf(AetherModuleId.FREELOOK)),
    WAYPOINTS(ConfigSection.QOL, "Waypoints", "Walk-on trigger areas & command beacons", listOf(AetherModuleId.BOXES)),
    STATS(ConfigSection.QOL, "Stats & Loadouts", "Gear presets, mask cooldowns, fast swaps & pet keys", listOf(AetherModuleId.STATS, AetherModuleId.LOADOUT)),
    SHARDS(ConfigSection.QOL, "Attribute Shards", "Find by shard or attribute, then track and buy", listOf(AetherModuleId.SHARDS)),
    INVENTORY_SORT(ConfigSection.QOL, "Inventory Sort", "Sort into saved preset layouts", listOf(AetherModuleId.INVENTORY_SORT)),
    DROP_ULT(ConfigSection.QOL, "Drop Ult", "Press Q while holding a weapon to cast ultimate", listOf(AetherModuleId.COMBAT)),
    PERFORMANCE(ConfigSection.QOL, "Performance", "Motion blur & max-FPS preset", listOf(AetherModuleId.PERFORMANCE)),
    APPEARANCE(ConfigSection.INTERFACE, "Appearance", "Window theme, blur & responsive scaling", listOf(AetherModuleId.CLICK_GUI), listOf("Theme", "Text & shape", "Advanced")),
    HUD_LAYOUT(ConfigSection.INTERFACE, "HUD Layout", "Edit every HUD widget, overlay & card", listOf(AetherModuleId.HUD_EDIT)),
    ;

    val primaryModuleId: AetherModuleId? get() = moduleIds.firstOrNull()

    /** HUD Layout opens the HUD editor instead of showing a page of its own. */
    val opensHudEditor: Boolean get() = this == HUD_LAYOUT

    /** Lowercase text the search matches against. */
    internal fun searchText(): String = buildString {
        append(section.label).append(' ').append(title).append(' ').append(caption)
        subtabs.forEach { append(' ').append(it) }
        moduleIds.forEach { id ->
            AetherModules.byId(id)?.let { module -> append(' ').append(module.name).append(' ').append(module.hint) }
        }
    }.lowercase()
}

internal object ConfigNav {
    /** Pages grouped by section in declaration order, skipping sections with no pages. */
    fun sidebarGroups(): List<Pair<ConfigSection, List<ConfigPage>>> =
        ConfigSection.entries.map { section -> section to ConfigPage.entries.filter { it.section == section } }
            .filter { (_, pages) -> pages.isNotEmpty() }

    /**
     * Pages whose title, caption, section or feature names contain every word of [query].
     * Blank queries match nothing so the search dropdown stays closed.
     */
    fun search(query: String): List<ConfigPage> {
        val words = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return ConfigPage.entries.filter { page ->
            val haystack = page.searchText()
            words.all { haystack.contains(it) }
        }
    }

    /** Pages whose primary feature has a real enable flag, so the header and overview show its toggle. */
    fun togglePages(): List<ConfigPage> = ConfigPage.entries.filter { page ->
        page.primaryModuleId?.let(AetherModules::hasMasterSwitch) == true
    }

    /** Overview cards: every feature page except the overview itself and HUD Layout. */
    fun overviewPages(): List<ConfigPage> = ConfigPage.entries.filter { it.moduleIds.isNotEmpty() && !it.opensHudEditor }

    private val WHITESPACE = Regex("\\s+")
}
