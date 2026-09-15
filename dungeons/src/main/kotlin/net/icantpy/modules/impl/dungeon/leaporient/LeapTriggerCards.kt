package net.icantpy.modules.impl.dungeon.leaporient

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

    fun setEnabled(settings: LeapOrientSettings, card: LeapTriggerCard, enabled: Boolean): LeapOrientSettings {
        val ids = card.triggers.map { it.id }.toSet()
        // Read current rules rather than overwriting edits with a stale rendered card.
        return settings.triggers.filter { it.id in ids }.fold(settings) { current, trigger ->
            current.upsertTrigger(trigger.copy(enabled = enabled))
        }
    }
}
