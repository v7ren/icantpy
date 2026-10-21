package net.icantpy.dungeon.leap

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.icantpy.dungeon.timer.TimerClock
import net.icantpy.hud.markers.MarkerLook
import net.icantpy.hud.markers.MarkerShapes
import java.util.UUID

enum class LeapPreferKind {
    LOCATION,
    CLASS,
    ;

    fun label(): String = when (this) {
        LOCATION -> "Spot"
        CLASS -> "Class"
    }

    fun next(): LeapPreferKind = when (this) {
        LOCATION -> CLASS
        CLASS -> LOCATION
    }
}

enum class LeapTriggerEvent(val group: String) {
    // Chat
    CHAT_MESSAGE("Chat"),
    // Run
    RUN_START("Run"),
    RUN_END("Run"),
    BOSS_ENTRY("Run"),
    BLOOD_OPEN("Run"),
    BLOOD_CAMP_DONE("Run"),
    BLOOD_CLEAR("Run"),
    // Clear
    WITHER_KEY("Clear"),
    BLOOD_KEY("Clear"),
    WITHER_DOOR("Clear"),
    PRINCE_KILLED("Clear"),
    PUZZLE_FAIL("Clear"),
    BAT_KILLED("Clear"),
    // F7/M7 boss
    MAXOR_START("F7/M7 boss"),
    MAXOR_DEATH("F7/M7 boss"),
    BOSS_STORM_START("F7/M7 boss"),
    BOSS_STORM_END("F7/M7 boss"),
    BOSS_GOLDOR("F7/M7 boss"),
    BOSS_CORE("F7/M7 boss"),
    BOSS_GOLDOR_DEATH("F7/M7 boss"),
    BOSS_NECRON("F7/M7 boss"),
    BOSS_NECRON_DEATH("F7/M7 boss"),
    P5_START("F7/M7 boss"),
    BOSS_P5("F7/M7 boss"),
    RELIC_PICKUP("F7/M7 boss"),
    RELIC_PLACED_SELF("F7/M7 boss"),
    RELIC_PLACED_ANY("F7/M7 boss"),
    WITHER_KING_DEATH("F7/M7 boss"),
    BONZO_DEAD("F7/M7 boss"),
    MAXOR_STUNNED("F7/M7 boss"),
    BOSS_ENRAGED("F7/M7 boss"),
    CRYSTAL_PICKUP("F7/M7 boss"),
    STORM_CRUSHED("F7/M7 boss"),
    STORM_PAD_PHASE("F7/M7 boss"),
    WK_DRAGON_KILLED("F7/M7 boss"),
    DRAGON_SPAWNING("F7/M7 boss"),
    DRAGON_SPAWNED("F7/M7 boss"),
    DRAGON_KILLED("F7/M7 boss"),
    DRAGON_PRIORITY("F7/M7 boss"),
    // F7/M7 terminals
    TERMINAL_DONE("F7/M7 terminals"),
    SECTION_DONE("F7/M7 terminals"),
    GATE_DESTROYED("F7/M7 terminals"),
    // Other floors
    LIVID_START("Other floors"),
    SADAN_GIANTS("Other floors"),
    SADAN_DONE("Other floors"),
    PROFESSOR_GUARDIAN("Other floors"),
    // Player
    SELF_DEATH("Player"),
    TEAMMATE_DEATH("Player"),
    MASK_PROC("Player"),
    ABILITY_COOLDOWN("Player"),
    HEALTH_LOW("Player"),
    // Leap and timer
    LEAPED_TO("Leap"),
    SELF_LEAP("Leap"),
    LEAP_CHAIN("Leap"),
    CLOCK("Timer"),
    // Location (tab list, sidebar)
    ISLAND_ENTERED("Location"),
    ISLAND_LEFT("Location"),
    DUNGEON_ENTERED("Location"),
    DUNGEON_LEFT("Location"),
    // Lobby (chat)
    PLAYER_READY("Lobby"),
    COUNTDOWN("Lobby"),
    CLASS_SELECTED("Lobby"),
    PF_JOINED("Lobby"),
    // Progress (tab list and sidebar)
    SECRETS_REACHED("Progress"),
    CRYPTS_REACHED("Progress"),
    CLEARED_REACHED("Progress"),
    DEATHS_CHANGED("Progress"),
    // Puzzles (chat)
    PUZZLE_SOLVED("Puzzles"),
    MIMIC_DEAD("Puzzles"),
    BLESSING("Puzzles"),
    // Kuudra (chat)
    KUUDRA_SUPPLIES("Kuudra"),
    KUUDRA_BUILD_DONE("Kuudra"),
    KUUDRA_STUN("Kuudra"),
    KUUDRA_LAIR("Kuudra"),
    // Slayer
    SLAYER_STARTED("Slayer"),
    SLAYER_BOSS_SPAWNED("Slayer"),
    SLAYER_COMPLETE("Slayer"),
    SLAYER_FAILED("Slayer"),
    ;

    /** Progress events that compare a count against the trigger's [LeapOrientTrigger.threshold]. */
    fun usesThreshold(): Boolean = this == SECRETS_REACHED || this == CRYPTS_REACHED || this == CLEARED_REACHED

    fun label(): String = when (this) {
        ISLAND_ENTERED -> "Island entered"
        ISLAND_LEFT -> "Island left"
        DUNGEON_ENTERED -> "Dungeon entered"
        DUNGEON_LEFT -> "Dungeon left"
        PLAYER_READY -> "Player ready"
        COUNTDOWN -> "Run countdown (1s)"
        CLASS_SELECTED -> "Class selected"
        PF_JOINED -> "Party finder join"
        SECRETS_REACHED -> "Secrets found at least N"
        CRYPTS_REACHED -> "Crypts at least N"
        CLEARED_REACHED -> "Cleared at least N%"
        DEATHS_CHANGED -> "Team death count increased"
        PUZZLE_SOLVED -> "Puzzle solved"
        MIMIC_DEAD -> "Mimic killed (party chat)"
        BLESSING -> "Blessing picked"
        KUUDRA_SUPPLIES -> "Kuudra supplies phase"
        KUUDRA_BUILD_DONE -> "Kuudra ballista ready"
        KUUDRA_STUN -> "Kuudra stunned"
        KUUDRA_LAIR -> "Kuudra lair (final)"
        SLAYER_STARTED -> "Slayer quest started"
        SLAYER_BOSS_SPAWNED -> "Slayer boss spawned"
        SLAYER_COMPLETE -> "Slayer quest complete"
        SLAYER_FAILED -> "Slayer quest failed"
        MAXOR_START -> "Maxor starts (P1)"
        MAXOR_DEATH -> "Maxor dies"
        BOSS_STORM_START -> "Storm starts (P2)"
        BOSS_STORM_END -> "Storm dies"
        BOSS_GOLDOR -> "Goldor starts (P3)"
        BOSS_CORE -> "Core opens"
        BOSS_GOLDOR_DEATH -> "Goldor dies"
        BOSS_NECRON -> "Necron starts (P4)"
        BOSS_NECRON_DEATH -> "Necron dies"
        BOSS_P5 -> "P5 relics"
        RELIC_PICKUP -> "Relic picked up"
        RELIC_PLACED_SELF -> "Your relic placed"
        RELIC_PLACED_ANY -> "Any relic placed"
        MAXOR_STUNNED -> "Maxor stunned (crystal beam)"
        BOSS_ENRAGED -> "Boss enraged"
        CRYSTAL_PICKUP -> "Energy crystal picked"
        STORM_CRUSHED -> "Storm crushed"
        STORM_PAD_PHASE -> "Storm lightning/pad phase"
        WK_DRAGON_KILLED -> "Wither King dragon killed"
        DRAGON_SPAWNING -> "Dragon spawning"
        DRAGON_SPAWNED -> "Dragon spawned"
        DRAGON_KILLED -> "Dragon killed"
        DRAGON_PRIORITY -> "Priority dragon chosen"
        BAT_KILLED -> "Bonus bat killed"
        LIVID_START -> "Livid starts"
        SADAN_GIANTS -> "Sadan giants"
        SADAN_DONE -> "Sadan defeated"
        PROFESSOR_GUARDIAN -> "Professor ultimate"
        ABILITY_COOLDOWN -> "Ability on cooldown"
        WITHER_KING_DEATH -> "Wither King dies"
        LEAPED_TO -> "Leaped to"
        SELF_LEAP -> "I leaped"
        LEAP_CHAIN -> "After my leap"
        CLOCK -> "Clock hit"
        RUN_START -> "Run starts"
        RUN_END -> "Run ends"
        BOSS_ENTRY -> "Boss room entered"
        BLOOD_OPEN -> "Blood door opened"
        BLOOD_CAMP_DONE -> "Watcher done"
        BLOOD_CLEAR -> "Blood cleared"
        WITHER_KEY -> "Wither key picked"
        BLOOD_KEY -> "Blood key picked"
        WITHER_DOOR -> "Wither door opened"
        PRINCE_KILLED -> "Prince killed"
        PUZZLE_FAIL -> "Puzzle failed"
        TERMINAL_DONE -> "Terminal/device/lever done"
        SECTION_DONE -> "P3 section done"
        GATE_DESTROYED -> "Gate destroyed"
        P5_START -> "Wither King starts (P5)"
        BONZO_DEAD -> "Bonzo dies"
        SELF_DEATH -> "You died"
        TEAMMATE_DEATH -> "Teammate died"
        MASK_PROC -> "Mask/Phoenix proc"
        HEALTH_LOW -> "Health below %"
        CHAT_MESSAGE -> "Chat message"
    }

    fun nativeState(): String? = when (this) {
        BOSS_STORM_END, BOSS_STORM_START, MAXOR_DEATH -> "p2"
        BOSS_GOLDOR -> "p3"
        BOSS_CORE -> "p3s4"
        BOSS_GOLDOR_DEATH -> "p4"
        BOSS_NECRON -> "p4"
        BOSS_NECRON_DEATH -> "p5"
        BOSS_P5 -> "p5"
        RELIC_PICKUP, RELIC_PLACED_SELF, RELIC_PLACED_ANY -> "p5"
        else -> null
    }

    fun nativePhase(): LeapPhase? = when (this) {
        BOSS_STORM_END, BOSS_STORM_START, MAXOR_DEATH -> LeapPhase.P2
        BOSS_GOLDOR, BOSS_CORE -> LeapPhase.P3
        BOSS_GOLDOR_DEATH, BOSS_NECRON -> LeapPhase.P4
        BOSS_NECRON_DEATH -> LeapPhase.P4
        BOSS_P5 -> LeapPhase.P5
        RELIC_PICKUP, RELIC_PLACED_SELF, RELIC_PLACED_ANY -> LeapPhase.P5
        else -> null
    }

    fun next(): LeapTriggerEvent {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    fun previous(): LeapTriggerEvent {
        val values = entries
        return values[(ordinal + values.size - 1) % values.size]
    }
}

enum class LeapCenterTarget {
    SENDER,
    CLASS,
    NAME,
    LEAPED_TO,
    LEAPED_FROM,
    ;

    fun label(): String = when (this) {
        SENDER -> "Sender"
        CLASS -> "Class"
        NAME -> "Name"
        LEAPED_TO -> "Leaped to"
        LEAPED_FROM -> "Leaper"
    }

    fun next(): LeapCenterTarget {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

data class LeapPriorityRule(
    val state: String,
    val kind: LeapPreferKind = LeapPreferKind.LOCATION,
    val value: String,
)

data class LeapOrientTrigger(
    val id: String,
    val enabled: Boolean = true,
    val event: LeapTriggerEvent,
    val whenState: String = "any",
    val match: String = "self",
    val target: LeapCenterTarget = LeapCenterTarget.CLASS,
    val targetValue: String = "healer",
    val requiresTargetClass: LeapDungeonClass = LeapDungeonClass.EMPTY,
    val targetPlayer: String = "",
    val priority: Int = 0,
    val floor: LeapFloor = LeapFloor.ANY,
    val phase: LeapPhase = LeapPhase.ANY,
    val section: Int = 0,
    val route: LeapStormRoute = LeapStormRoute.ANY,
    val forClass: LeapDungeonClass = LeapDungeonClass.EMPTY,
    val ee2Owner: LeapDungeonClass = LeapDungeonClass.EMPTY,
    val clock: ClockSpec? = null,
    val chainDelaySeconds: Int = 0,
    val chainDurationSeconds: Int = 0,
    val zone: String = "",
    val reason: String = "",
    val presetId: String? = null,
    val group: String = "",
    /**
     * Ordered card rows, first element runs first. At most [LeapAutoStep.MAX_STEPS]. A [LeapAutoKind.LEAP_TARGET]
     * row arms the leap fields above; the other rows are queued auto actions.
     */
    val steps: List<LeapAutoStep> = emptyList(),
    /** HEALTH_LOW threshold in percent of max health, 10..90. */
    val healthBelowPercent: Int = 50,
    /** Count for the progress events in [LeapTriggerEvent.usesThreshold]; 0 means the card never fires. */
    val threshold: Int = 0,
    /** CHAT_MESSAGE: who must have sent the line. [ChatSender.PLAYER] uses [chatPlayer]. */
    val chatSender: ChatSender = ChatSender.SELF,
    val chatPlayer: String = "",
    val chatChannel: ChatChannel = ChatChannel.ANY,
    val chatMode: ChatTextMode = ChatTextMode.CONTAINS,
    val chatText: String = "",
    /** Minimum gap between two firings of this card from chat, in milliseconds. */
    val chatCooldownMs: Int = 1000,
    /** How the armed leap target lasts. Defaults keep older cards timed at the global duration. */
    val holdMode: LeapHoldMode = LeapHoldMode.TIMED,
    /** Seconds for [LeapHoldMode.TIMED]; 0 uses the global default duration. */
    val holdSeconds: Int = 0,
    /** A locked target cannot be replaced by a lower or equal priority trigger while it is active. */
    val holdLocked: Boolean = false,
) {
    val isPreset: Boolean get() = !presetId.isNullOrBlank()

    /**
     * Hold settings for the leap this card arms. Chain cards saved before [holdSeconds] existed keep
     * their chainDurationSeconds as the hold length.
     */
    fun holdSpec(): LeapHoldSpec {
        val legacy = if (event == LeapTriggerEvent.LEAP_CHAIN) chainDurationSeconds else 0
        return LeapHoldSpec(
            mode = holdMode,
            seconds = if (holdSeconds > 0) holdSeconds else legacy,
            locked = holdLocked,
        )
    }

    /** Derived from [steps]: a card arms its leap exactly when it holds a leap target row. */
    val armLeap: Boolean get() = LeapAutoStepList.hasLeapTarget(steps)

    /**
     * The rows that queue game actions, in run order. Leap target and follow-ups are excluded because
     * they are applied instantly.
     */
    fun queueableSteps(): List<LeapAutoStep> = steps.filter { !LeapAutoStepList.isInstant(it) }

    fun sourceKind(): LeapSourceKind = LeapSourceKind.fromEvent(event, isPreset)

    fun lockPriority(): Int {
        val penalty = if (isPreset) 0 else 15
        return (sourceKind().rank() - penalty).coerceAtLeast(1) + priority
    }

    fun resolvedPhase(): LeapPhase {
        if (phase != LeapPhase.ANY && phase != LeapPhase.UNKNOWN) return phase
        event.nativePhase()?.let { return it }
        return LeapPhase.fromToken(whenState)
    }

    fun resolvedSection(): Int {
        if (section in 1..4) return section
        return Regex("""p3s([1-4])""").find(whenState.lowercase())?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /** Text for each row in order, such as "Sort: P1", "Loadout 3", or the leap destination for a leap row. */
    fun stepLabels(presetLabels: Map<String, String> = emptyMap()): List<String> =
        steps.map { step ->
            if (step.kind == LeapAutoKind.LEAP_TARGET) leapText() ?: step.label(presetLabels) else step.label(presetLabels)
        }

    /** Leap destination text, or null when this trigger does not arm a leap. */
    fun leapText(): String? {
        if (!armLeap) return null
        val at = zone.takeIf { it.isNotBlank() }?.let { " at $it" }.orEmpty()
        val why = reason.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val requires = requiresTargetClass.takeIf { it.isReal() }?.let { " · after ${it.displayName}" }.orEmpty()
        return "Leap to ${destLabel()}$at$why$requires${LeapHoldText.cardSuffix(LeapHoldSpec(holdMode, holdSeconds, holdLocked))}"
    }

    /** Destination of the leap target, such as a class name or "sender". */
    fun destLabel(): String = when (target) {
        LeapCenterTarget.CLASS -> LeapDungeonClass.fromToken(targetValue)?.displayName ?: targetValue
        LeapCenterTarget.NAME -> targetValue.ifBlank { targetPlayer.ifBlank { "name" } }
        LeapCenterTarget.SENDER -> "sender"
        LeapCenterTarget.LEAPED_TO -> "leaped-to"
        LeapCenterTarget.LEAPED_FROM -> "leaper"
    }

    fun title(): String = when {
        event == LeapTriggerEvent.CLOCK && clock != null -> clock.summary()
        event == LeapTriggerEvent.CHAT_MESSAGE -> chatTitle()
        event == LeapTriggerEvent.LEAP_CHAIN -> {
            val after = when {
                match.equals("any", true) -> "anyone"
                else -> LeapDungeonClass.fromToken(match)?.displayName ?: match
            }
            val delay = if (chainDelaySeconds > 0) " +${chainDelaySeconds}s" else ""
            "After leap → $after$delay"
        }
        else -> event.label()
    }

    /** Such as `Chat (Me, Party) contains "abc"`. */
    fun chatTitle(): String {
        val who = when (chatSender) {
            ChatSender.PLAYER -> chatPlayer.ifBlank { "Player" }
            else -> chatSender.label
        }
        val how = when (chatMode) {
            ChatTextMode.CONTAINS -> "contains"
            ChatTextMode.EXACT -> "is exactly"
            ChatTextMode.STARTS_WITH -> "starts with"
            ChatTextMode.REGEX -> "matches regex"
        }
        val text = chatText.ifBlank { "..." }
        return "Chat ($who, ${chatChannel.label}) $how \"$text\""
    }

    /**
     * Every row's text in run order, joined with " · ". A card with follow-ups shows its leap as a chain,
     * such as "Leap: Healer → then Tank", in place of the leap row.
     */
    fun subtitle(presetLabels: Map<String, String> = emptyMap()): String {
        val followUps = LeapFollowUp.steps(this)
        if (followUps.isEmpty()) return stepLabels(presetLabels).joinToString(" · ").ifEmpty { "no action" }
        val chain = "Leap: ${destLabel()}" + followUps.joinToString("") { " → then ${LeapFollowUp.destLabel(it)}" }
        val queued = steps.filter { it.kind != LeapAutoKind.LEAP_TARGET && it.kind != LeapAutoKind.FOLLOWUP_LEAP }
            .map { it.label(presetLabels) }
        return (listOf(chain) + queued).joinToString(" · ")
    }

    fun summary(presetLabels: Map<String, String> = emptyMap()): String = "${title()} → ${subtitle(presetLabels)}"

    fun withStepMoved(from: Int, to: Int): LeapOrientTrigger = copy(steps = LeapAutoStepList.moved(steps, from, to))

    fun withStepAdded(step: LeapAutoStep): LeapOrientTrigger = copy(steps = LeapAutoStepList.added(steps, step))

    fun withStepRemoved(index: Int): LeapOrientTrigger = copy(steps = LeapAutoStepList.removed(steps, index))

    fun withStepChanged(index: Int, transform: (LeapAutoStep) -> LeapAutoStep): LeapOrientTrigger =
        copy(steps = LeapAutoStepList.replaced(steps, index, transform))

    fun guiGroup(): String = group.ifBlank {
        when (resolvedPhase()) {
            LeapPhase.P3 -> when (resolvedSection()) {
                1 -> "S1"
                2 -> "S2"
                3 -> "S3"
                4 -> "S4"
                else -> "P3"
            }
            else -> resolvedPhase().group()
        }
    }

    companion object {
        const val MAX_THRESHOLD: Int = 100

        fun create(
            event: LeapTriggerEvent = LeapTriggerEvent.BOSS_STORM_END,
            targetValue: String = "healer",
        ): LeapOrientTrigger = LeapOrientTrigger(
            id = UUID.randomUUID().toString(),
            event = event,
            threshold = LeapTriggerEventMenu.defaultThreshold(event),
            whenState = event.nativeState() ?: "any",
            match = when (event) {
                LeapTriggerEvent.LEAPED_TO -> "self"
                LeapTriggerEvent.LEAP_CHAIN -> "any"
                else -> "any"
            },
            target = LeapCenterTarget.CLASS,
            targetValue = targetValue,
            priority = 5,
            phase = event.nativePhase() ?: LeapPhase.ANY,
            clock = if (event == LeapTriggerEvent.CLOCK) ClockSpec.stormElapsed(35.0) else null,
            chainDelaySeconds = if (event == LeapTriggerEvent.LEAP_CHAIN) 0 else 0,
        )
    }
}

data class LeapedChatMatch(
    val rawTarget: String,
    val asClass: LeapDungeonClass?,
    val asName: String?,
)

object LeapChatPatterns {
    const val DEFAULT_ANNOUNCE: String = "[icantpy] leaped to {class}"

    val BUILTIN: List<String> = listOf(
        DEFAULT_ANNOUNCE,
        "[icantpy] leaped to {name}",
        "Leaped to {name}!",
        "Leaped to {class}!",
    )

    private val classAlt = LeapDungeonClass.PLAYABLE.joinToString("|") { clazz ->
        listOf(clazz.displayName, clazz.shortName, clazz.name)
            .distinctBy { it.lowercase() }
            .joinToString("|")
    }

    fun formatAnnounce(template: String, name: String, clazz: LeapDungeonClass): String {
        val className = if (clazz.isReal()) clazz.displayName else name
        return template
            .replace("{name}", name, ignoreCase = true)
            .replace("{class}", className, ignoreCase = true)
            .trim()
            .ifBlank { "Leaped to $name!" }
    }

    fun allTemplates(extra: List<String>, announce: String): List<String> =
        (listOf(announce) + extra + BUILTIN)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun parseLeaped(message: String, templates: List<String>): LeapedChatMatch? {
        val text = message.trim()
        if (text.isBlank()) return null
        templates.forEach { template ->
            parseOne(text, template)?.let { return it }
        }
        return null
    }

    fun parseOne(message: String, template: String): LeapedChatMatch? {
        val regex = compile(template) ?: return null
        val match = regex.find(message) ?: return null
        val classCap = namedGroup(match, "clazz")
        val nameCap = namedGroup(match, "name")
        val raw = (classCap ?: nameCap ?: return null).trim()
        val asClass = classCap?.let { LeapDungeonClass.fromToken(it) }?.takeIf { it.isReal() }
            ?: LeapDungeonClass.fromToken(raw)?.takeIf { it.isReal() }
        val asName = nameCap?.takeIf { it.matches(Regex("\\w{1,16}")) }
            ?: raw.takeIf { asClass == null && it.matches(Regex("\\w{1,16}")) }
        return LeapedChatMatch(raw, asClass, asName)
    }

    fun compile(template: String): Regex? {
        val trimmed = template.trim()
        if (trimmed.isBlank()) return null
        val builder = StringBuilder()
        var index = 0
        while (index < trimmed.length) {
            when {
                trimmed.regionMatches(index, "{class}", 0, 7, ignoreCase = true) -> {
                    builder.append("(?<clazz>").append(classAlt).append(")")
                    index += 7
                }
                trimmed.regionMatches(index, "{name}", 0, 6, ignoreCase = true) -> {
                    builder.append("(?<name>\\w{1,16})")
                    index += 6
                }
                else -> {
                    builder.append(Regex.escape(trimmed[index].toString()))
                    index += 1
                }
            }
        }
        return Regex(builder.toString(), RegexOption.IGNORE_CASE)
    }

    private fun namedGroup(match: MatchResult, name: String): String? = try {
        match.groups[name]?.value
    } catch (_: IllegalArgumentException) {
        null
    }
}

object LeapTriggerJson {
    fun triggersToJson(triggers: List<LeapOrientTrigger>): JsonArray {
        val array = JsonArray()
        triggers.forEach { trigger ->
            val item = JsonObject()
            item.addProperty("id", trigger.id)
            item.addProperty("enabled", trigger.enabled)
            item.addProperty("event", trigger.event.name)
            item.addProperty("whenState", trigger.whenState)
            item.addProperty("match", trigger.match)
            item.addProperty("target", trigger.target.name)
            item.addProperty("targetValue", trigger.targetValue)
            item.addProperty("requiresTargetClass", trigger.requiresTargetClass.name)
            item.addProperty("targetPlayer", trigger.targetPlayer)
            item.addProperty("priority", trigger.priority)
            item.addProperty("floor", trigger.floor.name)
            item.addProperty("phase", trigger.phase.name)
            item.addProperty("section", trigger.section)
            item.addProperty("route", trigger.route.name)
            item.addProperty("forClass", trigger.forClass.name)
            item.addProperty("ee2Owner", trigger.ee2Owner.name)
            item.addProperty("zone", trigger.zone)
            item.addProperty("reason", trigger.reason)
            item.addProperty("group", trigger.group)
            // Leap target is a row in "steps"; no separate armLeap flag is written.
            item.add("steps", stepsToJson(trigger.steps))
            item.addProperty("chainDelaySeconds", trigger.chainDelaySeconds)
            item.addProperty("chainDurationSeconds", trigger.chainDurationSeconds)
            item.addProperty("healthBelowPercent", trigger.healthBelowPercent)
            item.addProperty("threshold", trigger.threshold)
            item.addProperty("chatSender", trigger.chatSender.name)
            item.addProperty("chatPlayer", trigger.chatPlayer)
            item.addProperty("chatChannel", trigger.chatChannel.name)
            item.addProperty("chatMode", trigger.chatMode.name)
            item.addProperty("chatText", trigger.chatText)
            item.addProperty("chatCooldownMs", trigger.chatCooldownMs)
            item.addProperty("holdMode", trigger.holdMode.name)
            item.addProperty("holdSeconds", trigger.holdSeconds)
            item.addProperty("holdLocked", trigger.holdLocked)
            trigger.presetId?.let { item.addProperty("presetId", it) }
            trigger.clock?.let { spec ->
                val clock = JsonObject()
                clock.addProperty("clock", spec.clock.name)
                item.addProperty("clockTicks", spec.thresholdTicks)
                clock.addProperty("metric", spec.metric.name)
                clock.addProperty("thresholdTicks", spec.thresholdTicks)
                clock.addProperty("crossing", spec.crossing.name)
                item.add("clock", clock)
            }
            array.add(item)
        }
        return array
    }

    fun triggersFromJson(obj: JsonObject): List<LeapOrientTrigger>? {
        if (!obj.has("triggers") || !obj.get("triggers").isJsonArray) return null
        val parsed = obj.getAsJsonArray("triggers").mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val id = string(item, "id", "").ifBlank { UUID.randomUUID().toString() }
            val event = enumValue(item, "event", LeapTriggerEvent.BOSS_STORM_END)
            val whenState = string(item, "whenState", event.nativeState() ?: "any").ifBlank { "any" }
            val requiredTargetClass = if (item.has("requiresTargetClass")) {
                LeapDungeonClass.fromToken(string(item, "requiresTargetClass", "empty")) ?: LeapDungeonClass.EMPTY
            } else {
                // The first configurable lock is intentionally migrated for existing PY configs.
                if (id == "mage-py-35s-healer") LeapDungeonClass.TANK else LeapDungeonClass.EMPTY
            }
            LeapOrientTrigger(
                id = id,
                enabled = bool(item, "enabled", true),
                event = event,
                whenState = whenState,
                match = string(item, "match", "self").ifBlank { "self" },
                target = enumValue(item, "target", LeapCenterTarget.CLASS),
                targetValue = string(item, "targetValue", "healer"),
                requiresTargetClass = requiredTargetClass,
                targetPlayer = string(item, "targetPlayer", ""),
                priority = int(item, "priority", 0),
                floor = enumValue(item, "floor", LeapFloor.ANY),
                phase = enumValue(item, "phase", event.nativePhase() ?: LeapPhase.fromToken(whenState)),
                section = int(item, "section", 0),
                route = enumValue(item, "route", LeapStormRoute.ANY),
                forClass = LeapDungeonClass.fromToken(string(item, "forClass", "empty")) ?: LeapDungeonClass.EMPTY,
                ee2Owner = LeapDungeonClass.fromToken(string(item, "ee2Owner", "empty")) ?: LeapDungeonClass.EMPTY,
                clock = clockFromJson(item),
                chainDelaySeconds = int(item, "chainDelaySeconds", 0).coerceAtLeast(0),
                chainDurationSeconds = int(item, "chainDurationSeconds", 0).coerceAtLeast(0),
                zone = string(item, "zone", ""),
                reason = string(item, "reason", ""),
                presetId = string(item, "presetId", "").ifBlank { null },
                group = string(item, "group", ""),
                steps = stepsFromJson(item),
                healthBelowPercent = int(item, "healthBelowPercent", 50).coerceIn(10, 90),
                threshold = int(item, "threshold", 0).coerceIn(0, LeapOrientTrigger.MAX_THRESHOLD),
                chatSender = enumValue(item, "chatSender", ChatSender.SELF),
                chatPlayer = string(item, "chatPlayer", ""),
                chatChannel = enumValue(item, "chatChannel", ChatChannel.ANY),
                chatMode = enumValue(item, "chatMode", ChatTextMode.CONTAINS),
                chatText = string(item, "chatText", ""),
                chatCooldownMs = int(item, "chatCooldownMs", 1000).coerceAtLeast(0),
                holdMode = LeapHoldMode.fromToken(string(item, "holdMode", "")) ?: LeapHoldMode.TIMED,
                holdSeconds = int(item, "holdSeconds", 0).coerceIn(0, 60),
                holdLocked = bool(item, "holdLocked", false),
            )
        }
        return parsed
    }

    /**
     * Current saves have a "steps" array and no "armLeap" key; the leap target is a row in it.
     * Older saves wrote "armLeap" next to "steps" (or a single legacy "action" with no "steps"). Those
     * convert to rows: a true armLeap becomes a leap-target row at index 0, followed by the old rows.
     */
    private fun stepsFromJson(item: JsonObject): List<LeapAutoStep> {
        val hasStepArray = item.has("steps") && item.get("steps").isJsonArray
        if (hasStepArray && !item.has("armLeap")) {
            return LeapAutoStepList.withSingleLeapTarget(readStepArray(item)).take(LeapAutoStep.MAX_STEPS)
        }
        val oldRows = if (hasStepArray) readStepArray(item) else listOfNotNull(legacySingleStep(item))
        val rows = if (armLeapFromLegacy(item)) listOf(LeapAutoStep.leapTarget()) + oldRows else oldRows
        return LeapAutoStepList.withSingleLeapTarget(rows).take(LeapAutoStep.MAX_STEPS)
    }

    /** Old "armLeap" flag. Without it, a trigger arms a leap unless its legacy action is a real auto action. */
    private fun armLeapFromLegacy(item: JsonObject): Boolean {
        if (item.has("armLeap")) return bool(item, "armLeap", true)
        return legacyAutoKind(item) == null
    }

    private fun readStepArray(item: JsonObject): List<LeapAutoStep> =
        item.getAsJsonArray("steps").mapNotNull { element ->
            if (element.isJsonObject) stepFromJson(element.asJsonObject) else null
        }

    /** The one action an old save stored in "action", converted to a row. */
    private fun legacySingleStep(item: JsonObject): LeapAutoStep? = when (legacyAutoKind(item)) {
        LeapAutoKind.SORT_INVENTORY -> LeapAutoStep(LeapAutoKind.SORT_INVENTORY, sortPreset = string(item, "sortPreset", ""))
        LeapAutoKind.EQUIP_STATS -> LeapAutoStep(LeapAutoKind.EQUIP_STATS, statsPreset = string(item, "statsPreset", ""))
        LeapAutoKind.SWITCH_LOADOUT -> LeapAutoStep(LeapAutoKind.SWITCH_LOADOUT, loadout = int(item, "loadout", 0).coerceAtLeast(0))
        LeapAutoKind.LEAP_TARGET, LeapAutoKind.FOLLOWUP_LEAP, LeapAutoKind.MARK_PLAYER, null -> null
    }

    /** The auto kind named by a legacy "action" value, or null for leap targets and unknown names. */
    private fun legacyAutoKind(item: JsonObject): LeapAutoKind? {
        val name = string(item, "action", "")
        return when (name.uppercase()) {
            "SORT_INVENTORY" -> LeapAutoKind.SORT_INVENTORY
            "EQUIP_STATS" -> LeapAutoKind.EQUIP_STATS
            "SWITCH_LOADOUT" -> LeapAutoKind.SWITCH_LOADOUT
            else -> null
        }
    }

    private fun stepFromJson(item: JsonObject): LeapAutoStep? {
        val kind = LeapAutoKind.entries.firstOrNull { it.name.equals(string(item, "kind", ""), ignoreCase = true) }
            ?: return null
        return LeapAutoStep(
            kind = kind,
            sortPreset = string(item, "sortPreset", ""),
            statsPreset = string(item, "statsPreset", ""),
            loadout = int(item, "loadout", 0).coerceAtLeast(0),
            sortArmed = bool(item, "sortArmed", false),
            sortLockUntilDone = bool(item, "sortLockUntilDone", true),
            sortReopenBlockMs = int(item, "sortReopenBlockMs", 1000).coerceAtLeast(0),
            sortArmTimeoutMs = int(item, "sortArmTimeoutMs", 30_000).coerceAtLeast(0),
            followTarget = enumValue(item, "followTarget", LeapCenterTarget.CLASS),
            followTargetValue = string(item, "followTargetValue", "healer"),
            followTargetPlayer = string(item, "followTargetPlayer", ""),
            followHoldMode = LeapHoldMode.fromToken(string(item, "followHoldMode", "")) ?: LeapHoldMode.TIMED,
            followHoldSeconds = int(item, "followHoldSeconds", 0).coerceIn(0, 60),
            followHoldLocked = bool(item, "followHoldLocked", false),
            followWithinSeconds = int(item, "followWithinSeconds", 20).coerceAtLeast(0),
            markBy = string(item, "markBy", LeapMarkRouter.BY_CLASS),
            markClass = string(item, "markClass", "HEALER"),
            markPlayer = string(item, "markPlayer", ""),
            markStyle = string(item, "markStyle", "ARROW"),
            markColor = int(item, "markColor", 0xFFFF5555.toInt()),
            markSeconds = int(item, "markSeconds", 10).coerceAtLeast(0),
            markShowName = bool(item, "markShowName", true),
            markOpacity = int(item, "markOpacity", MarkerLook.DEFAULT_OPACITY).coerceIn(0, 100),
            markBrightness = int(item, "markBrightness", MarkerLook.DEFAULT_BRIGHTNESS).let { if (it in MarkerLook.BRIGHTNESS_CHOICES) it else MarkerLook.DEFAULT_BRIGHTNESS },
            markSize = int(item, "markSize", MarkerLook.DEFAULT_SIZE).coerceIn(10, 1000),
            markHeight = int(item, "markHeight", MarkerLook.DEFAULT_HEIGHT).coerceIn(0, 10),
            markPulse = bool(item, "markPulse", true),
            markPlayerGlow = bool(item, "markPlayerGlow", false),
            markMarkerGlow = bool(item, "markMarkerGlow", false),
            markArrowShape = string(item, "markArrowShape", MarkerShapes.CLASSIC).let { if (it in MarkerShapes.SHAPES) it else MarkerShapes.CLASSIC },
            markSpin = bool(item, "markSpin", false),
            markBob = bool(item, "markBob", true),
            markThroughWalls = bool(item, "markThroughWalls", false),
        )
    }

    fun stepsToJson(steps: List<LeapAutoStep>): JsonArray {
        val array = JsonArray()
        steps.forEach { step ->
            val item = JsonObject()
            item.addProperty("kind", step.kind.name)
            item.addProperty("sortPreset", step.sortPreset)
            item.addProperty("statsPreset", step.statsPreset)
            item.addProperty("loadout", step.loadout)
            item.addProperty("sortArmed", step.sortArmed)
            item.addProperty("sortLockUntilDone", step.sortLockUntilDone)
            item.addProperty("sortReopenBlockMs", step.sortReopenBlockMs)
            item.addProperty("sortArmTimeoutMs", step.sortArmTimeoutMs)
            item.addProperty("followTarget", step.followTarget.name)
            item.addProperty("followTargetValue", step.followTargetValue)
            item.addProperty("followTargetPlayer", step.followTargetPlayer)
            item.addProperty("followHoldMode", step.followHoldMode.name)
            item.addProperty("followHoldSeconds", step.followHoldSeconds)
            item.addProperty("followHoldLocked", step.followHoldLocked)
            item.addProperty("followWithinSeconds", step.followWithinSeconds)
            item.addProperty("markBy", step.markBy)
            item.addProperty("markClass", step.markClass)
            item.addProperty("markPlayer", step.markPlayer)
            item.addProperty("markStyle", step.markStyle)
            item.addProperty("markColor", step.markColor)
            item.addProperty("markSeconds", step.markSeconds)
            item.addProperty("markShowName", step.markShowName)
            item.addProperty("markOpacity", step.markOpacity)
            item.addProperty("markBrightness", step.markBrightness)
            item.addProperty("markSize", step.markSize)
            item.addProperty("markHeight", step.markHeight)
            item.addProperty("markPulse", step.markPulse)
            item.addProperty("markPlayerGlow", step.markPlayerGlow)
            item.addProperty("markMarkerGlow", step.markMarkerGlow)
            item.addProperty("markArrowShape", step.markArrowShape)
            item.addProperty("markSpin", step.markSpin)
            item.addProperty("markBob", step.markBob)
            item.addProperty("markThroughWalls", step.markThroughWalls)
            array.add(item)
        }
        return array
    }

    fun prioritiesToJson(rules: List<LeapPriorityRule>): JsonArray {
        val array = JsonArray()
        rules.forEach { rule ->
            val item = JsonObject()
            item.addProperty("state", rule.state)
            item.addProperty("kind", rule.kind.name)
            item.addProperty("value", rule.value)
            item.addProperty("location", rule.value)
            array.add(item)
        }
        return array
    }

    fun prioritiesFromJson(obj: JsonObject): List<LeapPriorityRule>? {
        if (!obj.has("priorities") || !obj.get("priorities").isJsonArray) return null
        val parsed = obj.getAsJsonArray("priorities").mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val state = string(item, "state", "")
            val value = string(item, "value", string(item, "location", ""))
            if (state.isBlank() || value.isBlank()) return@mapNotNull null
            LeapPriorityRule(
                state = state,
                kind = enumValue(item, "kind", LeapPreferKind.LOCATION),
                value = value,
            )
        }
        return parsed.ifEmpty { null }
    }

    fun stringsFromJson(obj: JsonObject, key: String): List<String> {
        if (!obj.has(key) || !obj.get(key).isJsonArray) return emptyList()
        return obj.getAsJsonArray(key).mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    fun stringsToJson(values: List<String>): JsonArray {
        val array = JsonArray()
        values.forEach { array.add(it) }
        return array
    }

    fun stringMap(obj: JsonObject, key: String): Map<String, String> {
        if (!obj.has(key) || !obj.get(key).isJsonObject) return emptyMap()
        val mapped = LinkedHashMap<String, String>()
        obj.getAsJsonObject(key).entrySet().forEach { (name, value) ->
            if (value.isJsonPrimitive) mapped[name] = value.asString
        }
        return mapped
    }

    fun stringMapToJson(values: Map<String, String>): JsonObject {
        val obj = JsonObject()
        values.forEach { (key, value) -> obj.addProperty(key, value) }
        return obj
    }

    fun clockFromJson(item: JsonObject): ClockSpec? {
        if (item.has("clock") && item.get("clock").isJsonObject) {
            val spec = item.getAsJsonObject("clock")
            return ClockSpec(
                clock = enumValue(spec, "clock", TimerClock.STORM_ELAPSED),
                metric = enumValue(spec, "metric", ClockMetric.ELAPSED),
                thresholdTicks = int(spec, "thresholdTicks", int(item, "clockTicks", 0)),
                crossing = enumValue(spec, "crossing", ClockCrossing.RISING),
            )
        }
        if (!item.has("clockTicks")) return null
        val ticks = int(item, "clockTicks", 0)
        val clock = enumValue(item, "clockName", TimerClock.STORM_ELAPSED)
        return ClockSpec(
            clock = clock,
            metric = if (clock.countsUp()) ClockMetric.ELAPSED else ClockMetric.REMAINING,
            thresholdTicks = ticks,
            crossing = if (clock.countsUp()) ClockCrossing.RISING else ClockCrossing.FALLING,
        )
    }

    fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
        if (obj.has(key)) obj.get(key).asBoolean else default

    fun int(obj: JsonObject, key: String, default: Int): Int =
        if (obj.has(key)) obj.get(key).asInt else default

    fun float(obj: JsonObject, key: String, default: Float): Float =
        if (obj.has(key)) obj.get(key).asFloat else default

    fun string(obj: JsonObject, key: String, default: String): String =
        if (obj.has(key)) obj.get(key).asString else default

    inline fun <reified T : Enum<T>> enumValue(obj: JsonObject, key: String, default: T): T {
        if (!obj.has(key)) return default
        return enumValues<T>().firstOrNull { it.name.equals(obj.get(key).asString, ignoreCase = true) } ?: default
    }
}
