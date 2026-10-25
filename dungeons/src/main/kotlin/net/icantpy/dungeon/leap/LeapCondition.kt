package net.icantpy.dungeon.leap

/** What a condition term compares against. */
enum class LeapFactInput { NUMBER, PERCENT, CLASS, PHASE, FLOOR, ROUTE }

/** Something about the player or run that a condition term can test. [label] reads as "<label> <value>". */
enum class LeapFact(val id: String, val label: String, val input: LeapFactInput) {
    LOADOUT("loadout", "Loadout is", LeapFactInput.NUMBER),
    MY_CLASS("myclass", "My class is", LeapFactInput.CLASS),
    PARTY_HAS("party", "Party has", LeapFactInput.CLASS),
    LEAPED_TO("leapedto", "Already leaped to", LeapFactInput.CLASS),
    PHASE("phase", "Phase is", LeapFactInput.PHASE),
    FLOOR("floor", "Floor is", LeapFactInput.FLOOR),
    ROUTE("route", "Route is", LeapFactInput.ROUTE),
    HEALTH_BELOW("healthbelow", "Health below", LeapFactInput.PERCENT),
    ;

    companion object {
        fun fromId(raw: String?): LeapFact? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() || it.name.equals(raw?.trim(), true) }
    }
}

/** How a term joins the result so far. AND binds tighter than OR, so `a OR b AND c` is `a OR (b AND c)`. */
enum class LeapJoin(val id: String, val label: String) {
    AND("and", "And"),
    OR("or", "Or"),
    ;

    fun other(): LeapJoin = if (this == AND) OR else AND

    companion object {
        fun fromId(raw: String?): LeapJoin? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * One test such as "Loadout is 3" or "NOT Party has Healer". [value] is a token from [LeapConditions.choices] for the
 * fact. [join] says how this term combines with the terms before it; it is ignored on the first term.
 */
data class LeapTerm(
    val fact: LeapFact,
    val value: String,
    val negate: Boolean = false,
    val join: LeapJoin = LeapJoin.AND,
)

/** Everything a condition can read, captured once when a trigger fires. Unknown values are null / empty. */
data class LeapFacts(
    val loadout: Int? = null,
    val selfClass: LeapDungeonClass = LeapDungeonClass.EMPTY,
    val partyClasses: Set<LeapDungeonClass> = emptySet(),
    val leapedTo: Set<LeapDungeonClass> = emptySet(),
    val phase: LeapPhase = LeapPhase.UNKNOWN,
    val floor: LeapFloor = LeapFloor.UNKNOWN,
    val route: LeapStormRoute = LeapStormRoute.ANY,
    val healthPercent: Int? = null,
)

/** Which branch role a step plays. [IF] opens a chain, [ELSE_IF] and [ELSE] continue the chain above them. */
enum class LeapBranch(val id: String, val label: String) {
    ALWAYS("always", "Always"),
    IF("if", "If"),
    ELSE_IF("elseif", "Else if"),
    ELSE("else", "Else"),
    ;

    /** True for the roles that carry a condition. */
    val hasCondition: Boolean get() = this == IF || this == ELSE_IF

    companion object {
        fun fromId(raw: String?): LeapBranch? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() || it.name.equals(raw?.trim(), true) }
    }
}

/** Pure rules for conditions and if / else if / else chains over a card's steps. Free of Minecraft types. */
object LeapConditions {
    /** A step holds at most this many terms. */
    const val MAX_TERMS = 4

    /** Tokens and labels the value picker offers for [fact]. */
    fun choices(fact: LeapFact): List<Pair<String, String>> = when (fact.input) {
        LeapFactInput.NUMBER -> (1..net.icantpy.qol.loadout.LoadoutPlan.MAX_LOADOUT).map { it.toString() to "$it" }
        LeapFactInput.PERCENT -> (10..90 step 10).map { it.toString() to "$it%" }
        LeapFactInput.CLASS -> LeapDungeonClass.PLAYABLE.map { it.name to it.displayName }
        LeapFactInput.PHASE -> listOf(LeapPhase.P1, LeapPhase.P2, LeapPhase.P3, LeapPhase.P4, LeapPhase.P5).map { it.name to it.name }
        LeapFactInput.FLOOR -> listOf(LeapFloor.F7, LeapFloor.M7).map { it.name to it.name }
        LeapFactInput.ROUTE -> listOf(LeapStormRoute.GY, LeapStormRoute.PY).map { it.name to it.name }
    }

    fun defaultValue(fact: LeapFact): String = choices(fact).first().first

    fun valueLabel(fact: LeapFact, value: String): String =
        choices(fact).firstOrNull { it.first.equals(value, true) }?.second ?: value.ifBlank { "?" }

    /** Text such as "Loadout is 3" or "NOT Party has Healer". */
    fun describe(term: LeapTerm): String =
        (if (term.negate) "not " else "") + term.fact.label.lowercase() + " " + valueLabel(term.fact, term.value)

    /** Terms joined in reading order, such as "loadout is 3 and not party has Healer". */
    fun describe(terms: List<LeapTerm>): String = terms.withIndex().joinToString(" ") { (index, term) ->
        (if (index == 0) "" else term.join.id + " ") + describe(term)
    }

    /** Whether one term holds. A fact that is unknown makes the term false, so NOT flips it to true. */
    fun holds(term: LeapTerm, facts: LeapFacts): Boolean {
        val raw = when (term.fact) {
            LeapFact.LOADOUT -> facts.loadout != null && facts.loadout == term.value.toIntOrNull()
            LeapFact.MY_CLASS -> facts.selfClass.isReal() && facts.selfClass == LeapDungeonClass.fromToken(term.value)
            LeapFact.PARTY_HAS -> LeapDungeonClass.fromToken(term.value)?.let { it in facts.partyClasses } == true
            LeapFact.LEAPED_TO -> LeapDungeonClass.fromToken(term.value)?.let { it in facts.leapedTo } == true
            LeapFact.PHASE -> facts.phase.name.equals(term.value, true)
            LeapFact.FLOOR -> facts.floor.name.equals(term.value, true)
            LeapFact.ROUTE -> facts.route.name.equals(term.value, true)
            LeapFact.HEALTH_BELOW -> facts.healthPercent != null && facts.healthPercent < (term.value.toIntOrNull() ?: 0)
        }
        return raw != term.negate
    }

    /** AND of terms inside each OR group; true when any group holds. No terms means true. */
    fun evaluate(terms: List<LeapTerm>, facts: LeapFacts): Boolean {
        if (terms.isEmpty()) return true
        var groupHolds = true
        terms.forEachIndexed { index, term ->
            if (index > 0 && term.join == LeapJoin.OR) {
                if (groupHolds) return true
                groupHolds = true
            }
            if (!holds(term, facts)) groupHolds = false
        }
        return groupHolds
    }

    /**
     * For each step, whether its branch lets it run. ALWAYS rows run and end any open chain. IF opens a chain and runs
     * when its condition holds. ELSE_IF runs when no earlier link of the chain ran and its own condition holds. ELSE
     * runs when no earlier link ran. An ELSE_IF or ELSE with no open chain above it acts as IF or ALWAYS.
     */
    fun plan(steps: List<LeapAutoStep>, facts: LeapFacts): List<Boolean> {
        var open = false
        var taken = false
        return steps.map { step ->
            when (step.branch) {
                LeapBranch.ALWAYS -> {
                    open = false
                    true
                }
                LeapBranch.IF -> {
                    open = true
                    taken = evaluate(step.conditions, facts)
                    taken
                }
                LeapBranch.ELSE_IF -> {
                    val run = if (open && taken) false else evaluate(step.conditions, facts)
                    taken = taken && open || run
                    open = true
                    run
                }
                LeapBranch.ELSE -> {
                    val run = !(open && taken)
                    open = false
                    run
                }
            }
        }
    }

    /** The steps of [steps] that run for [facts], in order. */
    fun active(steps: List<LeapAutoStep>, facts: LeapFacts): List<LeapAutoStep> {
        val run = plan(steps, facts)
        return steps.filterIndexed { index, _ -> run[index] }
    }

    /** Branch roles the row at [index] may take: ELSE_IF and ELSE need an IF or ELSE_IF directly above to continue. */
    fun allowedBranches(steps: List<LeapAutoStep>, index: Int): List<LeapBranch> {
        val above = steps.getOrNull(index - 1)?.branch
        val continues = above == LeapBranch.IF || above == LeapBranch.ELSE_IF
        return if (continues) LeapBranch.entries else listOf(LeapBranch.ALWAYS, LeapBranch.IF)
    }

    /**
     * Fixes branch roles that no longer make sense after rows moved or were removed: an ELSE_IF or ELSE with no chain
     * above becomes IF or ALWAYS. Conditions are kept so switching the role back does not lose them.
     */
    fun repaired(steps: List<LeapAutoStep>): List<LeapAutoStep> {
        var open = false
        return steps.map { step ->
            val branch = when (step.branch) {
                LeapBranch.ELSE_IF -> if (open) LeapBranch.ELSE_IF else LeapBranch.IF
                LeapBranch.ELSE -> if (open) LeapBranch.ELSE else LeapBranch.ALWAYS
                else -> step.branch
            }
            open = branch == LeapBranch.IF || branch == LeapBranch.ELSE_IF
            if (branch == step.branch) step else step.copy(branch = branch)
        }
    }

    /** Short prefix for a step label such as "If loadout is 3: ", empty for ALWAYS rows. */
    fun prefix(step: LeapAutoStep): String = when (step.branch) {
        LeapBranch.ALWAYS -> ""
        LeapBranch.ELSE -> "Else: "
        LeapBranch.IF, LeapBranch.ELSE_IF -> {
            val head = if (step.branch == LeapBranch.IF) "If" else "Else if"
            "$head ${step.conditions.takeIf { it.isNotEmpty() }?.let(::describe) ?: "always"}: "
        }
    }
}
