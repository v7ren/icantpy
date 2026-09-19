package net.icantpy.dungeon.leap

import com.google.gson.JsonObject

enum class LeapSortMode {
    ODIN,
    CLASS,
    NAME,
    NONE,
    ;

    fun label(): String = when (this) {
        ODIN -> "Odin"
        CLASS -> "A-Z Class"
        NAME -> "A-Z Name"
        NONE -> "None"
    }

    fun next(): LeapSortMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

enum class LeapKeybindMode {
    CORNERS,
    CLASS,
    ;

    fun label(): String = when (this) {
        CORNERS -> "Corners"
        CLASS -> "Class"
    }

    fun next(): LeapKeybindMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

data class LeapOrientSettings(
    val enabled: Boolean = true,
    val menuEnabled: Boolean = true,
    val orientEnabled: Boolean = true,
    val durationSeconds: Int = 10,
    val keyword: String = "[icantpy]",
    val showLockHud: Boolean = true,
    val showBossDeathNotifier: Boolean = true,
    val showChatDebugHud: Boolean = true,
    val showChatDebugMessages: Boolean = false,
    val showChatDebugSkips: Boolean = false,
    val leapAnnounce: Boolean = false,
    val announceTemplate: String = LeapChatPatterns.DEFAULT_ANNOUNCE,
    val leapedMessages: List<String> = emptyList(),
    val onlyClass: Boolean = false,
    val colorStyle: Boolean = false,
    val onRelease: Boolean = false,
    val scale: Float = 1f,
    val menuOpacity: Int = 100,
    val menuGap: Int = 8,
    val boxesOnly: Boolean = false,
    val boxPositions: Map<String, String> = emptyMap(),
    val boxSizes: Map<String, String> = emptyMap(),
    val boxOrder: List<String> = emptyList(),
    val autoLeapEnabled: Boolean = false,
    val autoLeapBossOnly: Boolean = true,
    val autoLeapDelayMs: Int = 100,
    val doorOpenerLeapEnabled: Boolean = false,
    val sortMode: LeapSortMode = LeapSortMode.ODIN,
    val keybindMode: LeapKeybindMode = LeapKeybindMode.CORNERS,
    val topLeftKey: Int = UNBOUND,
    val topRightKey: Int = UNBOUND,
    val bottomLeftKey: Int = UNBOUND,
    val bottomRightKey: Int = UNBOUND,
    val archerKey: Int = UNBOUND,
    val berserkKey: Int = UNBOUND,
    val healerKey: Int = UNBOUND,
    val mageKey: Int = UNBOUND,
    val tankKey: Int = UNBOUND,
    val selfClass: LeapDungeonClass = LeapDungeonClass.EMPTY,
    val stormRoute: LeapStormRoute = LeapStormRoute.PY,
    val ee2Class: LeapDungeonClass = LeapDungeonClass.MAGE,
    val classPlayers: Map<String, String> = emptyMap(),
    val disabledPresetIds: Set<String> = emptySet(),
    val priorities: List<LeapPriorityRule> = DEFAULT_PRIORITIES,
    val triggers: List<LeapOrientTrigger> = DEFAULT_TRIGGERS,
) {
    val durationMs: Long get() = durationSeconds.coerceIn(1, 60) * 1000L

    fun cardFillAlpha(): Float = menuOpacity.coerceIn(0, 100) / 100f

    fun preferred(gameStateId: String): LeapPriorityRule? {
        val id = gameStateId.lowercase()
        fun match(state: String) = priorities.firstOrNull { it.state.equals(state, ignoreCase = true) }
        val section = Regex("""p3s([1-4])""").find(id)?.groupValues?.get(1)
        return match(id)
            ?: section?.let { match("p3s$it") }
            ?: section?.let { match("m7p3s$it") }
            ?: when {
                id.contains("p2") -> match("m7p2") ?: match("p2")
                id.contains("p4") -> match("m7p4") ?: match("p4")
                id.contains("p1") -> match("m7p1") ?: match("p1")
                else -> null
            }
    }

    fun preferredLocation(gameStateId: String): String? {
        val rule = preferred(gameStateId) ?: return null
        return if (rule.kind == LeapPreferKind.LOCATION) rule.value else null
    }

    fun locationForGoldorSection(section: Int): String =
        preferred("m7p3s$section")?.value ?: preferred("p3s$section")?.value ?: when (section) {
            1 -> "sss"
            2 -> "ee2"
            3 -> "ee3"
            else -> "s4"
        }

    fun withPriority(state: String, kind: LeapPreferKind, value: String): LeapOrientSettings {
        val aliases = aliasesFor(state)
        val next = priorities.toMutableList()
        aliases.forEach { alias ->
            val index = next.indexOfFirst { it.state.equals(alias, ignoreCase = true) }
            val rule = LeapPriorityRule(alias, kind, value)
            if (index < 0) next += rule else next[index] = rule
        }
        return copy(priorities = next)
    }

    fun withGoldorPriority(section: Int, location: String): LeapOrientSettings =
        withPriority("m7p3s$section", LeapPreferKind.LOCATION, location)

    fun upsertTrigger(trigger: LeapOrientTrigger): LeapOrientSettings {
        val index = triggers.indexOfFirst { it.id == trigger.id }
        val next = triggers.toMutableList()
        if (index < 0) next += trigger else next[index] = trigger
        val disabled = disabledPresetIds.toMutableSet()
        if (trigger.isPreset) {
            if (trigger.enabled) disabled.remove(trigger.id) else disabled += trigger.id
        }
        return copy(triggers = next, disabledPresetIds = disabled)
    }

    fun removeTrigger(id: String): LeapOrientSettings {
        val removed = triggers.firstOrNull { it.id == id }
        if (removed?.isPreset == true) {
            return copy(
                triggers = triggers.map { if (it.id == id) it.copy(enabled = false) else it },
                disabledPresetIds = disabledPresetIds + id,
            )
        }
        return copy(triggers = triggers.filterNot { it.id == id })
    }

    fun applyPreset(): LeapOrientSettings = LeapRoutePreset.apply(this)

    fun resetPreset(): LeapOrientSettings =
        LeapRoutePreset.replace(copy(disabledPresetIds = emptySet()))

    fun classPlayerMap(): Map<LeapDungeonClass, String> =
        classPlayers.mapNotNull { (token, name) ->
            val clazz = LeapDungeonClass.fromToken(token)?.takeIf { it.isReal() } ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            clazz to name
        }.toMap()

    fun visibleTriggers(viewClass: LeapDungeonClass): List<LeapOrientTrigger> {
        val activeRoute = if (stormRoute == LeapStormRoute.ANY) LeapStormRoute.PY else stormRoute
        return triggers.filter { trigger ->
            val classOk = !viewClass.isReal() || !trigger.forClass.isReal() || trigger.forClass == viewClass
            val routeOk = trigger.route == LeapStormRoute.ANY || trigger.route == activeRoute
            classOk && routeOk
        }
    }

    fun routeContext(
        floor: LeapFloor,
        phase: LeapPhase,
        section: Int,
        known: List<LeapPlayer>,
        self: LeapDungeonClass,
    ): LeapRouteContext = LeapRouteContext(
        floor = floor,
        phase = phase,
        section = section,
        route = if (stormRoute == LeapStormRoute.ANY) LeapStormRoute.PY else stormRoute,
        selfClass = self,
        ee2Owner = if (ee2Class.isReal()) ee2Class else LeapDungeonClass.MAGE,
        knownPlayers = known,
        classPlayers = classPlayerMap(),
    )

    companion object {
        const val UNBOUND: Int = -1

        val PRIORITY_STATES: List<Pair<String, String>> = listOf(
            "m7p2" to "Storm",
            "m7p3s1" to "P3 SSS",
            "m7p3s2" to "P3 EE2",
            "m7p3s3" to "P3 EE3",
            "m7p3s4" to "P3 S4",
            "m7p4" to "Necron",
        )

        val DEFAULT_PRIORITIES: List<LeapPriorityRule> = listOf(
            LeapPriorityRule("m7p2", LeapPreferKind.CLASS, "healer"),
            LeapPriorityRule("m7p3s1", LeapPreferKind.LOCATION, "sss"),
            LeapPriorityRule("m7p3s2", LeapPreferKind.LOCATION, "ee2"),
            LeapPriorityRule("m7p3s3", LeapPreferKind.LOCATION, "ee3"),
            LeapPriorityRule("m7p3s4", LeapPreferKind.LOCATION, "core"),
            LeapPriorityRule("m7p4", LeapPreferKind.CLASS, "tank"),
            LeapPriorityRule("f7p3s1", LeapPreferKind.LOCATION, "sss"),
            LeapPriorityRule("f7p3s2", LeapPreferKind.LOCATION, "ee2"),
            LeapPriorityRule("f7p3s3", LeapPreferKind.LOCATION, "ee3"),
            LeapPriorityRule("f7p3s4", LeapPreferKind.LOCATION, "core"),
            LeapPriorityRule("p3s1", LeapPreferKind.LOCATION, "sss"),
            LeapPriorityRule("p3s2", LeapPreferKind.LOCATION, "ee2"),
            LeapPriorityRule("p3s3", LeapPreferKind.LOCATION, "ee3"),
            LeapPriorityRule("p3s4", LeapPreferKind.LOCATION, "core"),
        )

        val DEFAULT_TRIGGERS: List<LeapOrientTrigger> = LeapRoutePreset.rows(LeapStormRoute.PY, LeapDungeonClass.MAGE)

        fun fromJson(obj: JsonObject): LeapOrientSettings {
            val defaults = LeapOrientSettings()
            val loaded = LeapOrientSettings(
                enabled = LeapTriggerJson.bool(obj, "enabled", defaults.enabled),
                menuEnabled = LeapTriggerJson.bool(obj, "menuEnabled", defaults.menuEnabled),
                orientEnabled = LeapTriggerJson.bool(obj, "orientEnabled", defaults.orientEnabled),
                durationSeconds = LeapTriggerJson.int(obj, "durationSeconds", defaults.durationSeconds).coerceIn(1, 60),
                keyword = LeapTriggerJson.string(obj, "keyword", defaults.keyword).ifBlank { defaults.keyword },
                showLockHud = LeapTriggerJson.bool(obj, "showLockHud", defaults.showLockHud),
                showBossDeathNotifier = LeapTriggerJson.bool(
                    obj,
                    "showBossDeathNotifier",
                    defaults.showBossDeathNotifier,
                ),
                showChatDebugHud = LeapTriggerJson.bool(obj, "showChatDebugHud", defaults.showChatDebugHud),
                showChatDebugMessages = LeapTriggerJson.bool(
                    obj,
                    "showChatDebugMessages",
                    defaults.showChatDebugMessages,
                ),
                showChatDebugSkips = LeapTriggerJson.bool(obj, "showChatDebugSkips", defaults.showChatDebugSkips),
                leapAnnounce = LeapTriggerJson.bool(obj, "leapAnnounce", defaults.leapAnnounce),
                announceTemplate = LeapTriggerJson.string(obj, "announceTemplate", defaults.announceTemplate)
                    .ifBlank { defaults.announceTemplate },
                leapedMessages = LeapTriggerJson.stringsFromJson(obj, "leapedMessages"),
                onlyClass = LeapTriggerJson.bool(obj, "onlyClass", defaults.onlyClass),
                colorStyle = LeapTriggerJson.bool(obj, "colorStyle", defaults.colorStyle),
                onRelease = LeapTriggerJson.bool(obj, "onRelease", defaults.onRelease),
                scale = LeapTriggerJson.float(obj, "scale", defaults.scale).coerceIn(0.1f, 2f),
                menuOpacity = LeapTriggerJson.int(obj, "menuOpacity", defaults.menuOpacity).coerceIn(0, 100),
                menuGap = LeapTriggerJson.int(obj, "menuGap", defaults.menuGap).coerceIn(0, 64),
                boxesOnly = LeapTriggerJson.bool(obj, "boxesOnly", defaults.boxesOnly),
                boxPositions = LeapTriggerJson.stringMap(obj, "boxPositions"),
                boxSizes = LeapTriggerJson.stringMap(obj, "boxSizes"),
                boxOrder = LeapTriggerJson.stringsFromJson(obj, "boxOrder"),
                autoLeapEnabled = LeapTriggerJson.bool(obj, "autoLeapEnabled", defaults.autoLeapEnabled),
                autoLeapBossOnly = LeapTriggerJson.bool(obj, "autoLeapBossOnly", defaults.autoLeapBossOnly),
                autoLeapDelayMs = LeapTriggerJson.int(obj, "autoLeapDelayMs", defaults.autoLeapDelayMs).coerceIn(0, 2000),
                doorOpenerLeapEnabled = LeapTriggerJson.bool(obj, "doorOpenerLeapEnabled", defaults.doorOpenerLeapEnabled),
                sortMode = LeapTriggerJson.enumValue(obj, "sortMode", defaults.sortMode),
                keybindMode = LeapTriggerJson.enumValue(obj, "keybindMode", defaults.keybindMode),
                topLeftKey = LeapTriggerJson.int(obj, "topLeftKey", defaults.topLeftKey),
                topRightKey = LeapTriggerJson.int(obj, "topRightKey", defaults.topRightKey),
                bottomLeftKey = LeapTriggerJson.int(obj, "bottomLeftKey", defaults.bottomLeftKey),
                bottomRightKey = LeapTriggerJson.int(obj, "bottomRightKey", defaults.bottomRightKey),
                archerKey = LeapTriggerJson.int(obj, "archerKey", defaults.archerKey),
                berserkKey = LeapTriggerJson.int(obj, "berserkKey", defaults.berserkKey),
                healerKey = LeapTriggerJson.int(obj, "healerKey", defaults.healerKey),
                mageKey = LeapTriggerJson.int(obj, "mageKey", defaults.mageKey),
                tankKey = LeapTriggerJson.int(obj, "tankKey", defaults.tankKey),
                selfClass = LeapDungeonClass.fromToken(LeapTriggerJson.string(obj, "selfClass", "empty"))
                    ?: LeapDungeonClass.EMPTY,
                stormRoute = LeapTriggerJson.enumValue(obj, "stormRoute", defaults.stormRoute),
                ee2Class = LeapDungeonClass.fromToken(LeapTriggerJson.string(obj, "ee2Class", "mage"))
                    ?: LeapDungeonClass.MAGE,
                classPlayers = LeapTriggerJson.stringMap(obj, "classPlayers"),
                disabledPresetIds = LeapTriggerJson.stringsFromJson(obj, "disabledPresetIds").toSet(),
                priorities = LeapTriggerJson.prioritiesFromJson(obj) ?: defaults.priorities,
                triggers = LeapTriggerJson.triggersFromJson(obj) ?: defaults.triggers,
            )
            return LeapRoutePreset.migrate(loaded)
        }

        fun toJson(settings: LeapOrientSettings): JsonObject {
            val obj = JsonObject()
            obj.addProperty("enabled", settings.enabled)
            obj.addProperty("menuEnabled", settings.menuEnabled)
            obj.addProperty("orientEnabled", settings.orientEnabled)
            obj.addProperty("durationSeconds", settings.durationSeconds)
            obj.addProperty("keyword", settings.keyword)
            obj.addProperty("showLockHud", settings.showLockHud)
            obj.addProperty("showBossDeathNotifier", settings.showBossDeathNotifier)
            obj.addProperty("showChatDebugHud", settings.showChatDebugHud)
            obj.addProperty("showChatDebugMessages", settings.showChatDebugMessages)
            obj.addProperty("showChatDebugSkips", settings.showChatDebugSkips)
            obj.addProperty("leapAnnounce", settings.leapAnnounce)
            obj.addProperty("announceTemplate", settings.announceTemplate)
            obj.add("leapedMessages", LeapTriggerJson.stringsToJson(settings.leapedMessages))
            obj.addProperty("onlyClass", settings.onlyClass)
            obj.addProperty("colorStyle", settings.colorStyle)
            obj.addProperty("onRelease", settings.onRelease)
            obj.addProperty("scale", settings.scale)
            obj.addProperty("menuOpacity", settings.menuOpacity)
            obj.addProperty("menuGap", settings.menuGap)
            obj.addProperty("boxesOnly", settings.boxesOnly)
            obj.add("boxPositions", LeapTriggerJson.stringMapToJson(settings.boxPositions))
            obj.add("boxSizes", LeapTriggerJson.stringMapToJson(settings.boxSizes))
            obj.add("boxOrder", LeapTriggerJson.stringsToJson(settings.boxOrder))
            obj.addProperty("autoLeapEnabled", settings.autoLeapEnabled)
            obj.addProperty("autoLeapBossOnly", settings.autoLeapBossOnly)
            obj.addProperty("autoLeapDelayMs", settings.autoLeapDelayMs)
            obj.addProperty("doorOpenerLeapEnabled", settings.doorOpenerLeapEnabled)
            obj.addProperty("sortMode", settings.sortMode.name)
            obj.addProperty("keybindMode", settings.keybindMode.name)
            obj.addProperty("topLeftKey", settings.topLeftKey)
            obj.addProperty("topRightKey", settings.topRightKey)
            obj.addProperty("bottomLeftKey", settings.bottomLeftKey)
            obj.addProperty("bottomRightKey", settings.bottomRightKey)
            obj.addProperty("archerKey", settings.archerKey)
            obj.addProperty("berserkKey", settings.berserkKey)
            obj.addProperty("healerKey", settings.healerKey)
            obj.addProperty("mageKey", settings.mageKey)
            obj.addProperty("tankKey", settings.tankKey)
            obj.addProperty("selfClass", settings.selfClass.name)
            obj.addProperty("stormRoute", settings.stormRoute.name)
            obj.addProperty("ee2Class", settings.ee2Class.name)
            obj.add("classPlayers", LeapTriggerJson.stringMapToJson(settings.classPlayers))
            obj.add("disabledPresetIds", LeapTriggerJson.stringsToJson(settings.disabledPresetIds.toList()))
            obj.add("priorities", LeapTriggerJson.prioritiesToJson(settings.priorities))
            obj.add("triggers", LeapTriggerJson.triggersToJson(settings.triggers))
            return obj
        }

        private fun aliasesFor(state: String): List<String> {
            val token = state.lowercase()
            val section = Regex("""p3s([1-4])""").find(token)?.groupValues?.get(1)
            return when {
                section != null -> listOf("m7p3s$section", "f7p3s$section", "p3s$section")
                token.contains("p2") || token == "storm" -> listOf("m7p2", "f7p2", "p2")
                token.contains("p4") || token == "necron" -> listOf("m7p4", "f7p4", "p4")
                token.contains("p1") || token == "maxor" -> listOf("m7p1", "f7p1", "p1")
                else -> listOf(token)
            }
        }
    }
}
