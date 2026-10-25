package net.icantpy.gui.config

/** Stable identities for features. Each id belongs to exactly one [ConfigPage]. */
internal enum class AetherModuleId {
    CLOCKS,
    MECHANICS,
    ALERTS,
    SPLITS,
    SCORE,
    DRAGONS,
    TERMINALS,
    LEAP,
    RENAME,
    MORPH,
    SHARE,
    FREELOOK,
    BOXES,
    STATS,
    LOADOUT,
    SHARDS,
    INVENTORY_SORT,
    COMBAT,
    CARRY,
    CLICK_GUI,
    HUD_EDIT,
    PERFORMANCE,
    VISUALS,
    FIRE_PITS,
    BOSS_AIM,
}

/** Display name, search hint and default enable state for a feature. */
internal data class AetherModule(
    val id: AetherModuleId,
    val name: String,
    val hint: String,
    val defaultEnabled: Boolean,
)

internal object AetherModules {
    val all: List<AetherModule> = listOf(
        AetherModule(AetherModuleId.CLOCKS, "Phase Clocks", "F1–F7 phase boss times in one synced HUD", true),
        AetherModule(AetherModuleId.MECHANICS, "Mechanic Clocks", "Pad, lightning, PY, Goldor, & Necron ticks", true),
        AetherModule(AetherModuleId.ALERTS, "Sound Alerts", "Visual flash and audio alerts on critical timing", true),
        AetherModule(AetherModuleId.SPLITS, "Splits & PBs", "Run splits, personal bests & sum of best", true),
        AetherModule(AetherModuleId.SCORE, "Score", "Live score estimate & 270/300 alerts", true),
        AetherModule(AetherModuleId.DRAGONS, "Wither Dragons", "Spawn priority, timers & dragon kill tracking in P5", true),
        AetherModule(AetherModuleId.TERMINALS, "Terminal Solver", "Solver panel for P3 terminals with custom colours & layout", true),
        AetherModule(AetherModuleId.LEAP, "Spirit Leap", "Smart routes, targeted cards & auto-leap", true),
        AetherModule(AetherModuleId.RENAME, "Item Customize", "Custom item tags, glints, dyes & lore", true),
        AetherModule(AetherModuleId.MORPH, "Morph Engine", "Client-side player model overrides", false),
        AetherModule(AetherModuleId.SHARE, "Cosmetic Sync", "Allow other icantpy users to see equipped items", false),
        AetherModule(AetherModuleId.FREELOOK, "Freelook 360", "Decouple camera rotation (Default: F6)", true),
        AetherModule(AetherModuleId.BOXES, "Waypoints 3D", "Walk-on trigger areas & command beacons", true),
        AetherModule(AetherModuleId.STATS, "Stats & Masks", "Gear presets and Bonzo/Spirit mask cooldowns", true),
        AetherModule(AetherModuleId.LOADOUT, "Fast Loadouts", "Hotbar fast-swapping without opening menus", false),
        AetherModule(AetherModuleId.SHARDS, "Attribute Shards", "Find by shard or attribute name, then track and buy", true),
        AetherModule(
            AetherModuleId.INVENTORY_SORT, "Inventory Sort",
            "Sort your inventory into saved preset layouts from a button next to the crafting grid.", false,
        ),
        AetherModule(AetherModuleId.COMBAT, "Class Drop Ult", "Press Q while holding weapon to cast ultimate", true),
        AetherModule(AetherModuleId.CARRY, "Carry Counter", "Automatic customer boss completion tracking", true),
        AetherModule(AetherModuleId.CLICK_GUI, "Click GUI", "Custom window theme, blur & responsive scaling", true),
        AetherModule(AetherModuleId.HUD_EDIT, "GUI Layout", "Edit every HUD widget, overlay, and on-screen card", true),
        AetherModule(AetherModuleId.PERFORMANCE, "Performance", "Motion blur and a max-FPS video preset", true),
        AetherModule(AetherModuleId.FIRE_PITS, "Blaze Fire Pits", "Warns or walks you out of Inferno Demonlord fire pits", false),
        AetherModule(AetherModuleId.BOSS_AIM, "Boss Aim", "Smoothly turns your camera toward your own slayer boss", false),
        AetherModule(AetherModuleId.VISUALS, "Visuals", "Viewmodel, zoom, perspective, fullbright, fog, time & render cleanup", false),
    )

    private val byIdMap: Map<AetherModuleId, AetherModule> = all.associateBy { it.id }

    /** Features whose enable toggle only changes UI state today, so they get no master switch. */
    private val overrideOnly: Set<AetherModuleId> = setOf(
        AetherModuleId.SHARE,
        AetherModuleId.LOADOUT,
        AetherModuleId.CLICK_GUI,
        AetherModuleId.HUD_EDIT,
        AetherModuleId.PERFORMANCE,
        AetherModuleId.VISUALS,
    )

    fun byId(id: AetherModuleId): AetherModule? = byIdMap[id]

    /** True when [id] has a persisted enable flag that the header and overview toggles can drive. */
    fun hasMasterSwitch(id: AetherModuleId): Boolean = id !in overrideOnly
}
