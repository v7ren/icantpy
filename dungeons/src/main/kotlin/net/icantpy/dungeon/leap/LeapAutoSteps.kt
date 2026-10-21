package net.icantpy.dungeon.leap

import net.icantpy.hud.markers.MarkerLook
import net.icantpy.hud.markers.MarkerShapes
import net.icantpy.qol.auto.AutoTarget

/** Which action a card row runs. [label] is the picker text in the config card. */
enum class LeapAutoKind(val label: String) {
    SORT_INVENTORY("Sort inventory"),
    EQUIP_STATS("Equip stats"),
    SWITCH_LOADOUT("Switch loadout"),
    /** Arms the card's center leap. Instant: it clicks no menus, so it is never queued. At most one per card. */
    LEAP_TARGET("Leap target"),
    /**
     * Arms a leap target after the card's leap happens. Follow-up #1 follows the main leap target, each later
     * one follows the previous follow-up. Needs a [LEAP_TARGET] row; at most [LeapFollowUp.MAX_FOLLOW_UPS] per card.
     * Instant like the leap target, so it is never queued.
     */
    FOLLOWUP_LEAP("Follow-up: leap target"),
    /** Marks a player in the world. Instant like the leap target, so it is never queued. */
    MARK_PLAYER("Mark player"),
    ;

    fun next(): LeapAutoKind {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

/**
 * One ordered action on a trigger card. Only the field matching [kind] is meaningful:
 * [sortPreset] for sorts, [statsPreset] (armor preset id) for stats, [loadout] (1-based) for loadouts.
 * [LeapAutoKind.LEAP_TARGET] carries no value; its leap settings live on the trigger.
 * [LeapAutoKind.FOLLOWUP_LEAP] carries its own leap settings in the follow* fields.
 */
data class LeapAutoStep(
    val kind: LeapAutoKind,
    val sortPreset: String = "",
    val statsPreset: String = "",
    val loadout: Int = 0,
    /** SORT_INVENTORY only: wait for the player to open inventory before sorting ("Wait until I open inventory"). */
    val sortArmed: Boolean = false,
    /** SORT_INVENTORY only, when armed: keep the inventory open until the sort finishes. */
    val sortLockUntilDone: Boolean = true,
    /** SORT_INVENTORY only, when armed: ms after a finished armed sort during which reopening is blocked. */
    val sortReopenBlockMs: Int = 1000,
    /** SORT_INVENTORY only, when armed: ms the arm lasts before it expires. 0 means never. */
    val sortArmTimeoutMs: Int = 30_000,
    /** FOLLOWUP_LEAP only: where the follow-up leap lands; same choices as the card's leap target. */
    val followTarget: LeapCenterTarget = LeapCenterTarget.CLASS,
    /** FOLLOWUP_LEAP only: class token for [LeapCenterTarget.CLASS], player name for [LeapCenterTarget.NAME]. */
    val followTargetValue: String = "healer",
    /** FOLLOWUP_LEAP only: explicit player name for [LeapCenterTarget.NAME]; blank falls back to [followTargetValue]. */
    val followTargetPlayer: String = "",
    /** FOLLOWUP_LEAP only: how long the follow-up target lasts. */
    val followHoldMode: LeapHoldMode = LeapHoldMode.TIMED,
    /** FOLLOWUP_LEAP only: seconds for a timed hold; 0 uses the global default. */
    val followHoldSeconds: Int = 0,
    /** FOLLOWUP_LEAP only: a locked follow-up target cannot be replaced by a lower or equal priority one. */
    val followHoldLocked: Boolean = false,
    /** FOLLOWUP_LEAP only: seconds after the previous target is armed that the leap must happen; 0 means until it ends. */
    val followWithinSeconds: Int = 20,
    /** MARK_PLAYER only: [LeapMarkRouter.BY_CLASS] or [LeapMarkRouter.BY_PLAYER]; picks which field names the target. */
    val markBy: String = LeapMarkRouter.BY_CLASS,
    /** MARK_PLAYER only, class target: class token such as "HEALER". */
    val markClass: String = "HEALER",
    /** MARK_PLAYER only, player target: player name, matched case-insensitively. */
    val markPlayer: String = "",
    /** MARK_PLAYER only: "ARROW", "BEAM" or "BOX". */
    val markStyle: String = "ARROW",
    /** MARK_PLAYER only: ARGB color of the marker. */
    val markColor: Int = 0xFFFF5555.toInt(),
    /** MARK_PLAYER only: seconds the marker lasts; 0 means until the run resets. */
    val markSeconds: Int = 10,
    /** MARK_PLAYER only: whether the marker shows the player's name. Labels are not drawn yet. */
    val markShowName: Boolean = true,
    /** MARK_PLAYER only: marker opacity in percent (see MarkerLook.OPACITY_CHOICES). */
    val markOpacity: Int = MarkerLook.DEFAULT_OPACITY,
    /** MARK_PLAYER only: brightness in percent (see MarkerLook.BRIGHTNESS_CHOICES); brightens color and glow. */
    val markBrightness: Int = MarkerLook.DEFAULT_BRIGHTNESS,
    /** MARK_PLAYER only: size in percent (see MarkerLook.SIZE_CHOICES). */
    val markSize: Int = MarkerLook.DEFAULT_SIZE,
    /** MARK_PLAYER only: extra blocks above the player head. */
    val markHeight: Int = MarkerLook.DEFAULT_HEIGHT,
    /** MARK_PLAYER only: pulse the alpha gently. */
    val markPulse: Boolean = true,
    /** MARK_PLAYER only: vanilla glow outline on the marked player (client-side). */
    val markPlayerGlow: Boolean = false,
    /** MARK_PLAYER only: extra glowing halo drawn around the marker geometry. */
    val markMarkerGlow: Boolean = false,
    /** MARK_PLAYER only: arrow shape name from MarkerShapes (arrow style only). */
    val markArrowShape: String = MarkerShapes.CLASSIC,
    /** MARK_PLAYER only: rotate the arrow (segment shapes). */
    val markSpin: Boolean = false,
    /** MARK_PLAYER only: bob the arrow up and down. */
    val markBob: Boolean = true,
    /** MARK_PLAYER only: draw without depth testing. */
    val markThroughWalls: Boolean = false,
) {
    /** True when a MARK_PLAYER row names its target by player name instead of class. */
    fun markTargetsPlayer(): Boolean = markBy.equals(LeapMarkRouter.BY_PLAYER, ignoreCase = true)

    /** Text such as "Sort: P1" or "Sort: P1 (armed)". Stats ids missing from [presetLabels] show as "(missing)". */
    fun label(presetLabels: Map<String, String> = emptyMap()): String = when (kind) {
        LeapAutoKind.LEAP_TARGET -> LeapAutoKind.LEAP_TARGET.label
        LeapAutoKind.FOLLOWUP_LEAP -> "Then: " + LeapFollowUp.destLabel(this)
        LeapAutoKind.MARK_PLAYER -> "Mark: " + LeapMarkRouter.summary(this)
        LeapAutoKind.SORT_INVENTORY -> "Sort: " + sortPreset.ifBlank { "no preset" } + if (sortArmed) " (armed)" else ""
        LeapAutoKind.EQUIP_STATS -> "Stats: " + when {
            statsPreset.isBlank() -> "no preset"
            else -> presetLabels[statsPreset] ?: "(missing)"
        }
        LeapAutoKind.SWITCH_LOADOUT -> if (loadout > 0) "Loadout $loadout" else "no loadout"
    }

    /** Switches the kind, keeping any value already typed for the new kind. Defaults come from the caller. */
    fun withKind(next: LeapAutoKind, defaultSortPreset: String, defaultStatsPreset: String): LeapAutoStep =
        when (next) {
            LeapAutoKind.LEAP_TARGET -> leapTarget()
            LeapAutoKind.FOLLOWUP_LEAP, LeapAutoKind.MARK_PLAYER -> copy(kind = next)
            else -> copy(
                kind = next,
                sortPreset = if (next == LeapAutoKind.SORT_INVENTORY) sortPreset.ifBlank { defaultSortPreset } else sortPreset,
                statsPreset = if (next == LeapAutoKind.EQUIP_STATS) statsPreset.ifBlank { defaultStatsPreset } else statsPreset,
                loadout = if (next == LeapAutoKind.SWITCH_LOADOUT && loadout == 0) 1 else loadout,
            )
        }

    companion object {
        /** A card holds at most this many ordered steps. */
        const val MAX_STEPS = 6

        /** The step that arms the card's center leap. */
        fun leapTarget(): LeapAutoStep = LeapAutoStep(LeapAutoKind.LEAP_TARGET)

        /** Choices for [followWithinSeconds]. 0 means until the previous target ends. */
        val FOLLOW_WITHIN_CHOICES_S: List<Int> = listOf(5, 10, 20, 30, 60, 0)

        fun followWithinLabel(seconds: Int): String = if (seconds <= 0) "Until target ends" else "${seconds}s"

        /** Choices for [sortReopenBlockMs], in ms. */
        val SORT_REOPEN_CHOICES_MS: List<Int> = listOf(0, 500, 1000, 2000, 3000, 5000)

        /** Choices for [sortArmTimeoutMs], in ms. 0 never expires. */
        val SORT_ARM_TIMEOUT_CHOICES_MS: List<Int> = listOf(10_000, 30_000, 60_000, 120_000, 0)

        fun reopenBlockLabel(ms: Int): String = when {
            ms <= 0 -> "Off"
            ms % 1000 == 0 -> "${ms / 1000}s"
            else -> "${ms / 1000.0}s"
        }

        fun armTimeoutLabel(ms: Int): String = when {
            ms <= 0 -> "Never"
            ms % 60_000 == 0 -> "${ms / 60_000} min"
            ms % 1000 == 0 -> "${ms / 1000}s"
            else -> "${ms / 1000.0}s"
        }

        /** Default step for a new row: the first saved sort preset, or an empty one. */
        fun newStep(firstSortPreset: String): LeapAutoStep =
            LeapAutoStep(kind = LeapAutoKind.SORT_INVENTORY, sortPreset = firstSortPreset)

        /** The queued routine for [step], or null when the step is not complete enough to run. */
        fun target(step: LeapAutoStep): AutoTarget? = when (step.kind) {
            LeapAutoKind.SORT_INVENTORY -> step.sortPreset.takeIf { it.isNotBlank() }?.let { AutoTarget.Sort(it) }
            LeapAutoKind.EQUIP_STATS -> step.statsPreset.takeIf { it.isNotBlank() }?.let { AutoTarget.Stats(it) }
            LeapAutoKind.SWITCH_LOADOUT -> step.loadout.takeIf { it > 0 }?.let { AutoTarget.Loadout(it) }
            LeapAutoKind.LEAP_TARGET, LeapAutoKind.FOLLOWUP_LEAP, LeapAutoKind.MARK_PLAYER -> null
        }

        /** Why [step] cannot run, shown in chat when it is skipped. */
        fun problem(step: LeapAutoStep): String = when (step.kind) {
            LeapAutoKind.SORT_INVENTORY -> "no sort preset chosen"
            LeapAutoKind.EQUIP_STATS -> "no stats preset chosen"
            LeapAutoKind.SWITCH_LOADOUT -> "no loadout chosen"
            LeapAutoKind.LEAP_TARGET -> "leap target is applied instantly, not queued"
            LeapAutoKind.FOLLOWUP_LEAP -> "follow-up leap is applied after a leap, not queued"
            LeapAutoKind.MARK_PLAYER -> if (step.markTargetsPlayer()) "no player chosen for mark" else "no class chosen for mark"
        }

        /**
         * The kind a row at [index] cycles to from [current]. Leap target is skipped while another row
         * already holds one, so a card never has two. Follow-up is skipped unless [canBeFollowUp] holds.
         */
        fun nextKind(current: LeapAutoKind, index: Int, steps: List<LeapAutoStep>): LeapAutoKind {
            val leapElsewhere = steps.withIndex().any { (i, step) -> i != index && step.kind == LeapAutoKind.LEAP_TARGET }
            val followAllowed = canBeFollowUp(steps, index)
            var next = current.next()
            while ((next == LeapAutoKind.LEAP_TARGET && leapElsewhere) ||
                (next == LeapAutoKind.FOLLOWUP_LEAP && !followAllowed)
            ) {
                next = next.next()
            }
            return next
        }

        /**
         * Whether the row at [index] may hold a follow-up: another row must hold the card's leap target (the
         * row itself cannot be the one it follows), and the card may not already hold
         * [LeapFollowUp.MAX_FOLLOW_UPS] other follow-ups.
         */
        fun canBeFollowUp(steps: List<LeapAutoStep>, index: Int): Boolean {
            val leapElsewhere = steps.withIndex().any { (i, step) -> i != index && step.kind == LeapAutoKind.LEAP_TARGET }
            if (!leapElsewhere) return false
            val others = steps.withIndex().count { (i, step) -> i != index && step.kind == LeapAutoKind.FOLLOWUP_LEAP }
            return others < LeapFollowUp.MAX_FOLLOW_UPS
        }
    }
}

/** Result of routing a card's queueable steps: queued targets, armed sorts and steps that cannot run. */
data class LeapAutoRoute(
    val targets: List<AutoTarget>,
    val armedSorts: List<LeapAutoStep>,
    val skipped: List<LeapAutoStep>,
)

/** Receives an armed sort. The auto queue implements it in-game; tests pass fakes. */
fun interface ArmedSortRequester {
    fun requestArmedSort(presetName: String, cardKey: String, armTimeoutMs: Int, reopenBlockMs: Int, lockUntilDone: Boolean): Boolean
}

object LeapAutoRouter {
    /**
     * Armed sorts are never queued; they arm separately. Every other step is queued in order or skipped.
     * Leap target and follow-up rows are instant leap arms, so they are left out.
     */
    fun route(steps: List<LeapAutoStep>): LeapAutoRoute {
        val targets = mutableListOf<AutoTarget>()
        val armed = mutableListOf<LeapAutoStep>()
        val skipped = mutableListOf<LeapAutoStep>()
        for (step in steps) {
            if (LeapAutoStepList.isInstant(step)) continue
            if (isArmedSort(step)) {
                if (step.sortPreset.isBlank()) skipped += step else armed += step
                continue
            }
            val target = LeapAutoStep.target(step)
            if (target == null) skipped += step else targets += target
        }
        return LeapAutoRoute(targets, armed, skipped)
    }

    fun isArmedSort(step: LeapAutoStep): Boolean = step.kind == LeapAutoKind.SORT_INVENTORY && step.sortArmed

    /** Arms every armed sort of [route] through [requester]. Returns how many were accepted. */
    fun arm(route: LeapAutoRoute, cardKey: String, requester: ArmedSortRequester): Int =
        route.armedSorts.count { step ->
            requester.requestArmedSort(
                step.sortPreset,
                cardKey,
                step.sortArmTimeoutMs,
                step.sortReopenBlockMs,
                step.sortLockUntilDone,
            )
        }
}

/** Ordered list helpers. Each returns a new list; indexes outside the list leave it unchanged. */
object LeapAutoStepList {
    fun moved(steps: List<LeapAutoStep>, from: Int, to: Int): List<LeapAutoStep> {
        if (from !in steps.indices || to !in steps.indices || from == to) return steps
        return steps.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Appends [step]. Refused when the list is full, when [step] is a second leap target, or when [step]
     * is a follow-up on a card without a leap target or already at the follow-up cap.
     */
    fun added(steps: List<LeapAutoStep>, step: LeapAutoStep): List<LeapAutoStep> = when {
        steps.size >= LeapAutoStep.MAX_STEPS -> steps
        step.kind == LeapAutoKind.LEAP_TARGET && hasLeapTarget(steps) -> steps
        step.kind == LeapAutoKind.FOLLOWUP_LEAP && !LeapAutoStep.canBeFollowUp(steps, steps.size) -> steps
        else -> steps + step
    }

    fun removed(steps: List<LeapAutoStep>, index: Int): List<LeapAutoStep> =
        if (index !in steps.indices) steps else steps.filterIndexed { i, _ -> i != index }

    /**
     * Replaces the step at [index]. A transform that would create a second leap target, or a follow-up
     * the card cannot hold, leaves the list unchanged.
     */
    fun replaced(steps: List<LeapAutoStep>, index: Int, transform: (LeapAutoStep) -> LeapAutoStep): List<LeapAutoStep> {
        if (index !in steps.indices) return steps
        val changed = transform(steps[index])
        val makesSecondLeap = changed.kind == LeapAutoKind.LEAP_TARGET &&
            steps.withIndex().any { (i, step) -> i != index && step.kind == LeapAutoKind.LEAP_TARGET }
        val makesOrphanFollowUp = changed.kind == LeapAutoKind.FOLLOWUP_LEAP && !LeapAutoStep.canBeFollowUp(steps, index)
        if (makesSecondLeap || makesOrphanFollowUp) return steps
        return steps.mapIndexed { i, step -> if (i == index) changed else step }
    }

    fun hasLeapTarget(steps: List<LeapAutoStep>): Boolean = steps.any { it.kind == LeapAutoKind.LEAP_TARGET }

    /** Rows applied instantly (leap arms and marks), never queued as game actions. */
    fun isInstant(step: LeapAutoStep): Boolean = when (step.kind) {
        LeapAutoKind.LEAP_TARGET, LeapAutoKind.FOLLOWUP_LEAP, LeapAutoKind.MARK_PLAYER -> true
        else -> false
    }

    /** Keeps the first leap target and drops later ones, so one card never arms two leaps. */
    fun withSingleLeapTarget(steps: List<LeapAutoStep>): List<LeapAutoStep> {
        var seen = false
        return steps.filter { step ->
            if (step.kind != LeapAutoKind.LEAP_TARGET) return@filter true
            val keep = !seen
            seen = true
            keep
        }
    }
}
