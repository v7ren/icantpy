package net.icantpy.dungeon.leap

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.icantpy.dungeon.timer.TimerClock
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

enum class LeapTriggerEvent {
    BOSS_STORM_END,
    BOSS_STORM_START,
    BOSS_GOLDOR,
    BOSS_CORE,
    BOSS_GOLDOR_DEATH,
    BOSS_NECRON,
    BOSS_NECRON_DEATH,
    BOSS_P5,
    RELIC_PICKUP,
    LEAPED_TO,
    SELF_LEAP,
    LEAP_CHAIN,
    CLOCK,
    ;

    fun label(): String = when (this) {
        BOSS_STORM_END -> "Storm dies"
        BOSS_STORM_START -> "Maxor dies"
        BOSS_GOLDOR -> "Goldor start"
        BOSS_CORE -> "Core opens"
        BOSS_GOLDOR_DEATH -> "Goldor dies"
        BOSS_NECRON -> "Necron drop"
        BOSS_NECRON_DEATH -> "Necron dies"
        BOSS_P5 -> "P5 relics"
        RELIC_PICKUP -> "Relic picked up"
        LEAPED_TO -> "Leaped to"
        SELF_LEAP -> "I leaped"
        LEAP_CHAIN -> "After my leap"
        CLOCK -> "Clock hit"
    }

    fun nativeState(): String? = when (this) {
        BOSS_STORM_END, BOSS_STORM_START -> "p2"
        BOSS_GOLDOR -> "p3"
        BOSS_CORE -> "p3s4"
        BOSS_GOLDOR_DEATH -> "p4"
        BOSS_NECRON -> "p4"
        BOSS_NECRON_DEATH -> "p5"
        BOSS_P5 -> "p5"
        RELIC_PICKUP -> "p5"
        LEAPED_TO, SELF_LEAP, LEAP_CHAIN, CLOCK -> null
    }

    fun nativePhase(): LeapPhase? = when (this) {
        BOSS_STORM_END, BOSS_STORM_START -> LeapPhase.P2
        BOSS_GOLDOR, BOSS_CORE -> LeapPhase.P3
        BOSS_GOLDOR_DEATH, BOSS_NECRON -> LeapPhase.P4
        BOSS_NECRON_DEATH -> LeapPhase.P4
        BOSS_P5 -> LeapPhase.P5
        RELIC_PICKUP -> LeapPhase.P5
        LEAPED_TO, SELF_LEAP, LEAP_CHAIN, CLOCK -> null
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
) {
    val isPreset: Boolean get() = !presetId.isNullOrBlank()

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

    fun destLabel(): String = when (target) {
        LeapCenterTarget.CLASS -> LeapDungeonClass.fromToken(targetValue)?.displayName ?: targetValue
        LeapCenterTarget.NAME -> targetValue.ifBlank { targetPlayer.ifBlank { "name" } }
        LeapCenterTarget.SENDER -> "sender"
        LeapCenterTarget.LEAPED_TO -> "leaped-to"
        LeapCenterTarget.LEAPED_FROM -> "leaper"
    }

    fun title(): String = when {
        event == LeapTriggerEvent.CLOCK && clock != null -> clock.summary()
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

    fun subtitle(): String {
        val dest = destLabel()
        val at = zone.takeIf { it.isNotBlank() }?.let { " at $it" }.orEmpty()
        val why = reason.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val requires = requiresTargetClass.takeIf { it.isReal() }?.let { " · after ${it.displayName}" }.orEmpty()
        return "Leap to $dest$at$why$requires"
    }

    fun summary(): String = "${title()} → ${destLabel()}"

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
        fun create(
            event: LeapTriggerEvent = LeapTriggerEvent.BOSS_STORM_END,
            targetValue: String = "healer",
        ): LeapOrientTrigger = LeapOrientTrigger(
            id = UUID.randomUUID().toString(),
            event = event,
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
            item.addProperty("chainDelaySeconds", trigger.chainDelaySeconds)
            item.addProperty("chainDurationSeconds", trigger.chainDurationSeconds)
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
            )
        }
        return parsed
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
