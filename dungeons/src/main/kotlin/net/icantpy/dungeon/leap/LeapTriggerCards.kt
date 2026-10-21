package net.icantpy.dungeon.leap

/** Presentation only: underlying class-specific rules keep their own IDs and filters. */
data class LeapTriggerCard(val triggers: List<LeapOrientTrigger>) {
    init {
        require(triggers.isNotEmpty()) { "A trigger card must contain a rule" }
    }

    val trigger: LeapOrientTrigger get() = triggers.first()

    fun actorLabel(): String = triggers.map { it.forClass }.distinct().joinToString(", ") {
        if (it.isReal()) it.displayName else "Any class"
    }
}

object LeapTriggerCards {
    fun group(rows: List<LeapOrientTrigger>): List<LeapTriggerCard> = rows.withIndex()
        .groupBy { (index, trigger) ->
            if (trigger.isPreset) trigger.copy(id = "", presetId = "preset", forClass = LeapDungeonClass.EMPTY)
            else index
        }
        .values.map { group -> LeapTriggerCard(group.map { it.value }) }

    /**
     * Stable key of the UI card: a grouped preset card is keyed by its sorted member ids, any other row by its
     * own id. Every member of a card therefore shares one key for queue dedupe.
     */
    fun cardKey(card: LeapTriggerCard): String =
        if (card.trigger.isPreset) "card:" + card.triggers.map { it.id }.sorted().joinToString(",")
        else card.trigger.id

    /** Key of the card [trigger] belongs to among [all] rows; falls back to the trigger id when it is not listed. */
    fun cardKeyOf(trigger: LeapOrientTrigger, all: List<LeapOrientTrigger>): String {
        val card = group(all).firstOrNull { c -> c.triggers.any { it.id == trigger.id } } ?: return trigger.id
        return cardKey(card)
    }

    /**
     * Keeps one eligible trigger per card. Among members of the same card the highest [LeapOrientTrigger.lockPriority]
     * wins, and the first in card order on a tie. Cards keep the order of their first eligible member.
     */
    fun pickOnePerCard(eligible: List<LeapOrientTrigger>, all: List<LeapOrientTrigger>): List<LeapOrientTrigger> {
        val keyById = HashMap<String, String>()
        group(all).forEach { card -> val key = cardKey(card); card.triggers.forEach { keyById[it.id] = key } }
        val best = LinkedHashMap<String, LeapOrientTrigger>()
        eligible.forEach { trigger ->
            val key = keyById[trigger.id] ?: trigger.id
            val current = best[key]
            if (current == null || trigger.lockPriority() > current.lockPriority()) best[key] = trigger
        }
        return best.values.toList()
    }

    /** Member a Test press plans for: the one matching the player's class, else the first member. */
    fun pickTestMember(members: List<LeapOrientTrigger>, selfClass: LeapDungeonClass): LeapOrientTrigger =
        members.firstOrNull { it.forClass == selfClass } ?: members.first()

    fun applyShared(settings: LeapOrientSettings, card: LeapTriggerCard, template: LeapOrientTrigger): LeapOrientSettings {
        return card.triggers.fold(settings) { current, member ->
            val latest = current.triggers.firstOrNull { it.id == member.id } ?: member
            current.upsertTrigger(
                latest.copy(
                    enabled = template.enabled,
                    event = template.event,
                    whenState = template.whenState,
                    match = template.match,
                    target = template.target,
                    targetValue = template.targetValue,
                    requiresTargetClass = template.requiresTargetClass,
                    targetPlayer = template.targetPlayer,
                    priority = template.priority,
                    floor = template.floor,
                    phase = template.phase,
                    section = template.section,
                    route = template.route,
                    ee2Owner = template.ee2Owner,
                    clock = template.clock,
                    chainDelaySeconds = template.chainDelaySeconds,
                    chainDurationSeconds = template.chainDurationSeconds,
                    zone = template.zone,
                    reason = template.reason,
                    group = template.group,
                    steps = template.steps,
                    healthBelowPercent = template.healthBelowPercent,
                    threshold = template.threshold,
                    chatSender = template.chatSender,
                    chatPlayer = template.chatPlayer,
                    chatChannel = template.chatChannel,
                    chatMode = template.chatMode,
                    chatText = template.chatText,
                    chatCooldownMs = template.chatCooldownMs,
                ),
            )
        }
    }

    fun deletePresets(settings: LeapOrientSettings, card: LeapTriggerCard): LeapOrientSettings =
        settings.deletePresets(card.triggers.map { it.id }.toSet())

    fun setEnabled(settings: LeapOrientSettings, card: LeapTriggerCard, enabled: Boolean): LeapOrientSettings {
        val ids = card.triggers.map { it.id }.toSet()
        // Read current rules rather than overwriting edits with a stale rendered card.
        return settings.triggers.filter { it.id in ids }.fold(settings) { current, trigger ->
            current.upsertTrigger(trigger.copy(enabled = enabled))
        }
    }
}
