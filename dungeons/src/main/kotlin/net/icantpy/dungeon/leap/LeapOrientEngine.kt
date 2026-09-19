package net.icantpy.dungeon.leap

import net.icantpy.dungeon.timer.TickClocks
import net.icantpy.util.Text

data class LeapArmRequest(
    val sender: String,
    val location: OrientSpot = OrientSpot.ANY,
    val clazz: LeapDungeonClass = LeapDungeonClass.EMPTY,
    val targetName: String = sender,
    val targetClass: LeapDungeonClass? = null,
    val priority: Int = 0,
    val label: String,
    val zone: String = "",
    val reason: String = "",
    val phase: LeapPhase = LeapPhase.UNKNOWN,
    val sourceKind: LeapSourceKind = LeapSourceKind.CUSTOM,
    val sourceTriggerId: String = "",
    val section: Int = 0,
)

data class LeapEligibility(
    val trigger: LeapOrientTrigger,
    val rejected: String? = null,
) {
    val ok: Boolean get() = rejected == null
}

object LeapOrientEngine {
    fun bossEvent(line: String): LeapTriggerEvent? {
        val normalized = normalizeBossLine(line)
        return when {
            TickClocks.STORM_END.matches(normalized) -> LeapTriggerEvent.BOSS_STORM_END
            TickClocks.STORM_START.matches(normalized) -> LeapTriggerEvent.BOSS_STORM_START
            TickClocks.GOLDOR.matches(normalized) -> LeapTriggerEvent.BOSS_GOLDOR
            TickClocks.CORE_OPENING.matches(normalized) -> LeapTriggerEvent.BOSS_CORE
            GOLDOR_DEATH.matches(normalized) -> LeapTriggerEvent.BOSS_GOLDOR_DEATH
            TickClocks.NECRON.matches(normalized) -> LeapTriggerEvent.BOSS_NECRON
            NECRON_ENTRY.matches(normalized) -> LeapTriggerEvent.BOSS_GOLDOR_DEATH
            P5_RELICS.matches(normalized) -> LeapTriggerEvent.BOSS_NECRON_DEATH
            MAXOR_END.matches(normalized) -> LeapTriggerEvent.BOSS_STORM_START
            else -> null
        }
    }

    fun bossEventToken(token: String): LeapTriggerEvent? = when (token.lowercase()) {
        "maxor", "p1", "storm-start" -> LeapTriggerEvent.BOSS_STORM_START
        "storm", "storm-end", "p2" -> LeapTriggerEvent.BOSS_STORM_END
        "goldor", "goldor-start", "p3" -> LeapTriggerEvent.BOSS_GOLDOR
        "core", "goldor-core" -> LeapTriggerEvent.BOSS_CORE
        "goldor-die", "goldor-death", "goldor-dies" -> LeapTriggerEvent.BOSS_GOLDOR_DEATH
        "necron-drop", "necron-start" -> LeapTriggerEvent.BOSS_NECRON
        "necron-die", "necron-death", "necron-dies", "necron", "p5" -> LeapTriggerEvent.BOSS_NECRON_DEATH
        "p5-relics", "relics", "p5-start", "wither" -> LeapTriggerEvent.BOSS_P5
        "relic-pickup", "picked-relic", "relic-picked" -> LeapTriggerEvent.RELIC_PICKUP
        else -> null
    }

    fun bossChatLine(event: LeapTriggerEvent): String? = when (event) {
        LeapTriggerEvent.BOSS_STORM_END -> "[BOSS] Storm: I should have known that I stood no chance."
        LeapTriggerEvent.BOSS_STORM_START -> "[BOSS] Storm: Pathetic Maxor, just like expected."
        LeapTriggerEvent.BOSS_GOLDOR -> "[BOSS] Goldor: Who dares trespass into my domain?"
        LeapTriggerEvent.BOSS_CORE -> "The Core entrance is opening!"
        LeapTriggerEvent.BOSS_GOLDOR_DEATH -> "[BOSS] Necron: You went further than any human before, congratulations."
        LeapTriggerEvent.BOSS_NECRON -> "[BOSS] Necron: I'm afraid, your journey ends now."
        LeapTriggerEvent.BOSS_NECRON_DEATH ->
            "[BOSS] Necron: All this, for nothing..."
        LeapTriggerEvent.BOSS_P5 -> "[BOSS] Necron: All this, for nothing..."
        LeapTriggerEvent.RELIC_PICKUP -> null
        LeapTriggerEvent.LEAPED_TO, LeapTriggerEvent.SELF_LEAP, LeapTriggerEvent.LEAP_CHAIN, LeapTriggerEvent.CLOCK -> null
    }

    fun parseAtPing(message: String, keyword: String): Pair<OrientSpot, LeapDungeonClass?>? {
        if (!message.contains(keyword, ignoreCase = true)) return null
        val escaped = Regex.escape(keyword)
        val match = Regex("(?i)$escaped\\s+at\\s+([a-z0-9_]+)").find(message) ?: return null
        val token = match.groupValues[1]
        val clazz = LeapDungeonClass.fromToken(token)?.takeIf { it.isReal() }
        val spot = OrientSpot.fromToken(token)
        if (clazz == null && spot == null) return OrientSpot.ANY to null
        return (spot ?: OrientSpot.ANY) to clazz
    }

    fun leapedTemplates(settings: LeapOrientSettings): List<String> =
        LeapChatPatterns.allTemplates(settings.leapedMessages, settings.announceTemplate)

    fun parseLeaped(message: String, settings: LeapOrientSettings): LeapedChatMatch? =
        LeapChatPatterns.parseLeaped(message, leapedTemplates(settings))

    fun looksLikeLeaped(message: String, settings: LeapOrientSettings): Boolean =
        parseLeaped(message, settings) != null

    fun whenIdFor(event: LeapTriggerEvent, currentId: String): String {
        val native = event.nativeState() ?: return currentId
        return "m7$native"
    }

    fun triggerApplies(
        trigger: LeapOrientTrigger,
        event: LeapTriggerEvent,
        currentId: String,
        context: LeapRouteContext = contextFromStateId(currentId),
    ): Boolean = eligibility(trigger, event, context).ok

    fun eligibility(
        trigger: LeapOrientTrigger,
        event: LeapTriggerEvent,
        context: LeapRouteContext,
    ): LeapEligibility {
        if (!trigger.enabled) return LeapEligibility(trigger, "disabled")
        if (trigger.event != event) return LeapEligibility(trigger, "wrong event")
        if (context.floor == LeapFloor.F7 && (trigger.phase == LeapPhase.P5 || trigger.group.equals("P5", true))) {
            return LeapEligibility(trigger, "F7 no P5")
        }
        if (trigger.floor != LeapFloor.ANY && trigger.floor != LeapFloor.UNKNOWN) {
            if (context.floor == LeapFloor.UNKNOWN) return LeapEligibility(trigger, "floor unknown")
            if (context.floor != trigger.floor) return LeapEligibility(trigger, "wrong floor")
        }
        val requiredPhase = trigger.resolvedPhase()
        val actualPhase = event.nativePhase() ?: context.phase
        if (requiredPhase != LeapPhase.ANY && requiredPhase != LeapPhase.UNKNOWN) {
            if (actualPhase == LeapPhase.UNKNOWN) return LeapEligibility(trigger, "phase unknown")
            if (actualPhase != requiredPhase) return LeapEligibility(trigger, "wrong phase")
        }
        val wantSection = trigger.resolvedSection()
        if (wantSection in 1..4 && context.section in 1..4 && wantSection != context.section && event.nativePhase() == null) {
            return LeapEligibility(trigger, "wrong section")
        }
        if (trigger.route != LeapStormRoute.ANY && context.route != trigger.route) {
            return LeapEligibility(trigger, "wrong route")
        }
        if (trigger.forClass.isReal()) {
            if (!context.selfClass.isReal()) return LeapEligibility(trigger, "class unknown")
            if (context.selfClass != trigger.forClass) return LeapEligibility(trigger, "wrong class")
        }
        if (trigger.ee2Owner.isReal() && context.ee2Owner.isReal() && context.ee2Owner != trigger.ee2Owner) {
            return LeapEligibility(trigger, "wrong ee2")
        }
        return LeapEligibility(trigger)
    }

    fun leapedMatchHits(
        trigger: LeapOrientTrigger,
        leaped: LeapedChatMatch,
        selfName: String?,
        selfClass: LeapDungeonClass,
        classOf: (String) -> LeapDungeonClass,
    ): Boolean {
        val want = trigger.match.trim().ifBlank { "any" }.lowercase()
        if (want == "any") return true
        val leapedClass = leaped.asClass
            ?: leaped.asName?.let(classOf)?.takeIf { it.isReal() }
        val leapedName = leaped.asName
        if (want == "self" || want == "me") {
            val nameHit = selfName != null && leapedName?.equals(selfName, ignoreCase = true) == true
            val classHit = selfClass.isReal() && leapedClass == selfClass
            val rawClassHit = selfClass.isReal() && LeapDungeonClass.fromToken(leaped.rawTarget) == selfClass
            return nameHit || classHit || rawClassHit
        }
        LeapDungeonClass.fromToken(want)?.takeIf { it.isReal() }?.let { wantClass ->
            return leapedClass == wantClass || LeapDungeonClass.fromToken(leaped.rawTarget) == wantClass
        }
        return leapedName?.equals(want, ignoreCase = true) == true ||
            leaped.rawTarget.equals(want, ignoreCase = true)
    }

    fun resolveArm(
        trigger: LeapOrientTrigger,
        sender: String,
        senderClass: LeapDungeonClass,
        leaped: LeapedChatMatch?,
        classOf: (String) -> LeapDungeonClass,
        playerForClass: (LeapDungeonClass) -> LeapPlayer?,
        context: LeapRouteContext = LeapRouteContext(),
    ): LeapArmRequest? {
        val leapedClass = leaped?.asClass
            ?: leaped?.asName?.let(classOf)?.takeIf { it.isReal() }
        val leapedName = leaped?.asName
        val leapedPlayer = when {
            leapedName != null -> LeapPlayer(leapedName, leapedClass ?: classOf(leapedName))
            leapedClass != null -> playerForClass(leapedClass)
            else -> null
        }
        val assigned = if (trigger.target == LeapCenterTarget.CLASS || trigger.target == LeapCenterTarget.NAME) {
            assignedPlayer(trigger, context)
        } else {
            null
        }
        if (assigned != null) {
            return armPlayer(assigned.first, assigned.second, trigger, sender, context)
        }
        return when (trigger.target) {
            LeapCenterTarget.SENDER ->
                armPlayer(sender, senderClass, trigger, sender, context)
            LeapCenterTarget.LEAPED_FROM ->
                armPlayer(sender, senderClass, trigger, sender, context)
            LeapCenterTarget.LEAPED_TO -> {
                val player = leapedPlayer ?: return null
                armPlayer(player.name, player.clazz, trigger, sender, context)
            }
            LeapCenterTarget.NAME -> {
                val name = trigger.targetPlayer.ifBlank { trigger.targetValue.trim().ifBlank { leapedName ?: sender } }
                val clazz = classOf(name)
                if (duplicateClassUnsafe(clazz, context) && trigger.targetPlayer.isBlank()) return null
                armPlayer(name, clazz, trigger, sender, context)
            }
            LeapCenterTarget.CLASS -> {
                val clazz = LeapDungeonClass.fromToken(trigger.targetValue)?.takeIf { it.isReal() } ?: return null
                if (duplicateClassUnsafe(clazz, context) && context.classPlayers[clazz].isNullOrBlank()) {
                    return null
                }
                val named = context.classPlayers[clazz]
                val player = named?.let { LeapPlayer(it, clazz) } ?: playerForClass(clazz)
                LeapArmRequest(
                    sender = sender,
                    clazz = clazz,
                    targetName = player?.name.orEmpty(),
                    targetClass = clazz,
                    priority = trigger.lockPriority(),
                    label = zoneLabel(trigger, clazz),
                    zone = trigger.zone,
                    reason = trigger.reason.ifBlank { trigger.event.label() },
                    phase = destinationPhase(trigger, context),
                    sourceKind = trigger.sourceKind(),
                    sourceTriggerId = trigger.id,
                    section = destinationSection(trigger, context),
                )
            }
        }
    }

    fun pingArm(
        sender: String,
        senderClass: LeapDungeonClass,
        spot: OrientSpot,
        atClass: LeapDungeonClass?,
        playerForClass: (LeapDungeonClass) -> LeapPlayer?,
        context: LeapRouteContext = LeapRouteContext(),
    ): LeapArmRequest {
        if (atClass != null) {
            if (duplicateClassUnsafe(atClass, context) && context.classPlayers[atClass].isNullOrBlank()) {
                return LeapArmRequest(
                    sender = sender,
                    location = spot,
                    clazz = atClass,
                    targetName = "",
                    targetClass = atClass,
                    priority = LeapSourceKind.PARTY_PING.rank(),
                    label = atClass.shortName.lowercase(),
                    zone = spot.token(),
                    reason = "Party ping",
                    phase = context.phase,
                    sourceKind = LeapSourceKind.PARTY_PING,
                    sourceTriggerId = "ping",
                )
            }
            val named = context.classPlayers[atClass]
            val player = named?.let { LeapPlayer(it, atClass) } ?: playerForClass(atClass)
            return LeapArmRequest(
                sender = sender,
                location = spot,
                clazz = atClass,
                targetName = player?.name.orEmpty(),
                targetClass = atClass,
                priority = LeapSourceKind.PARTY_PING.rank(),
                label = atClass.shortName.lowercase(),
                zone = spot.token(),
                reason = "Party ping",
                phase = context.phase,
                sourceKind = LeapSourceKind.PARTY_PING,
                sourceTriggerId = "ping",
            )
        }
        return LeapArmRequest(
            sender = sender,
            location = spot,
            clazz = senderClass,
            targetName = sender,
            priority = LeapSourceKind.PARTY_PING.rank(),
            label = spot.token(),
            zone = spot.token(),
            reason = "Party ping",
            phase = context.phase,
            sourceKind = LeapSourceKind.PARTY_PING,
            sourceTriggerId = "ping",
        )
    }

    fun contextFromStateId(
        currentId: String,
        selfClass: LeapDungeonClass = LeapDungeonClass.EMPTY,
        route: LeapStormRoute = LeapStormRoute.PY,
        ee2: LeapDungeonClass = LeapDungeonClass.MAGE,
    ): LeapRouteContext {
        val id = currentId.lowercase()
        val floor = when {
            id.startsWith("f7") -> LeapFloor.F7
            id.startsWith("m7") -> LeapFloor.M7
            else -> LeapFloor.UNKNOWN
        }
        val phase = when {
            id.contains("p5") -> LeapPhase.P5
            id.contains("p4") -> LeapPhase.P4
            id.contains("p3") -> LeapPhase.P3
            id.contains("p2") -> LeapPhase.P2
            id.contains("p1") -> LeapPhase.P1
            else -> LeapPhase.UNKNOWN
        }
        val section = Regex("""p3s([1-4])""").find(id)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return LeapRouteContext(
            floor = floor,
            phase = phase,
            section = section,
            route = route,
            selfClass = selfClass,
            ee2Owner = ee2,
        )
    }

    fun destinationPhase(trigger: LeapOrientTrigger, context: LeapRouteContext): LeapPhase = when (trigger.event) {
        LeapTriggerEvent.BOSS_STORM_START -> LeapPhase.P2
        LeapTriggerEvent.BOSS_STORM_END -> LeapPhase.P3
        LeapTriggerEvent.BOSS_GOLDOR_DEATH -> LeapPhase.P4
        LeapTriggerEvent.BOSS_NECRON_DEATH -> LeapPhase.P5
        LeapTriggerEvent.RELIC_PICKUP -> LeapPhase.P5
        else -> trigger.resolvedPhase().takeIf { it != LeapPhase.ANY && it != LeapPhase.UNKNOWN } ?: context.phase
    }

    fun duplicateClassUnsafe(clazz: LeapDungeonClass, context: LeapRouteContext): Boolean {
        if (!clazz.isReal()) return false
        val count = context.knownPlayers.count { it.clazz == clazz }
        return count > 1
    }

    private fun destinationSection(trigger: LeapOrientTrigger, context: LeapRouteContext): Int =
        if (destinationPhase(trigger, context) == LeapPhase.P3) {
            trigger.resolvedSection().takeIf { it in 1..4 } ?: context.section
        } else 0

    fun nextPreview(settings: LeapOrientSettings, context: LeapRouteContext): String {
        if (!context.selfClass.isReal()) return "class unknown"
        val upcoming = settings.visibleTriggers(context.selfClass).firstOrNull { trigger ->
            trigger.enabled &&
                (trigger.route == LeapStormRoute.ANY || trigger.route == context.route) &&
                (!trigger.forClass.isReal() || trigger.forClass == context.selfClass) &&
                (trigger.phase == LeapPhase.ANY || trigger.phase == LeapPhase.UNKNOWN ||
                    context.phase == LeapPhase.UNKNOWN || trigger.phase == context.phase)
        } ?: return "none"
        val at = upcoming.zone.takeIf { it.isNotBlank() }?.let { " @ $it" }.orEmpty()
        return "${upcoming.title()} → ${upcoming.destLabel()}$at"
    }

    private fun assignedPlayer(
        trigger: LeapOrientTrigger,
        context: LeapRouteContext,
    ): Pair<String, LeapDungeonClass>? {
        val named = trigger.targetPlayer.trim()
        if (named.isNotEmpty()) {
            val clazz = LeapDungeonClass.fromToken(trigger.targetValue)?.takeIf { it.isReal() }
                ?: context.knownPlayers.firstOrNull { it.name.equals(named, ignoreCase = true) }?.clazz
                ?: LeapDungeonClass.EMPTY
            return named to clazz
        }
        val clazz = LeapDungeonClass.fromToken(trigger.targetValue)?.takeIf { it.isReal() } ?: return null
        val assigned = context.classPlayers[clazz] ?: return null
        return assigned to clazz
    }

    private fun armPlayer(
        name: String,
        clazz: LeapDungeonClass,
        trigger: LeapOrientTrigger,
        sender: String,
        context: LeapRouteContext,
    ): LeapArmRequest = LeapArmRequest(
        sender = sender,
        clazz = clazz,
        targetName = name,
        targetClass = clazz.takeIf { it.isReal() },
        priority = trigger.lockPriority(),
        label = zoneLabel(trigger, clazz.takeIf { it.isReal() } ?: LeapDungeonClass.EMPTY).ifBlank { name },
        zone = trigger.zone,
        reason = trigger.reason.ifBlank { trigger.event.label() },
        phase = destinationPhase(trigger, context),
        sourceKind = trigger.sourceKind(),
        sourceTriggerId = trigger.id,
        section = destinationSection(trigger, context),
    )

    private fun zoneLabel(trigger: LeapOrientTrigger, clazz: LeapDungeonClass): String {
        val zone = trigger.zone.trim()
        val className = clazz.takeIf { it.isReal() }?.displayName ?: trigger.destLabel()
        return if (zone.isBlank()) className.lowercase() else "$className @ $zone"
    }

    internal fun normalizeBossLine(line: String): String {
        val stripped = Text.strip(line).replace('\u00A0', ' ')
        val payloadStart = listOf(
            stripped.indexOf("[BOSS]"),
            stripped.indexOf("The Core entrance is opening!"),
        ).filter { it > 0 }.minOrNull()
        return (payloadStart?.let { stripped.substring(it) } ?: stripped)
        .replace('…', '.')
        .replace(Regex("\\s+"), " ")
        .trim()
    }

    private val MAXOR_END = Regex("""^\[BOSS]\s+Maxor:\s+I'M TOO YOUNG TO DIE AGAIN!$""", RegexOption.IGNORE_CASE)
    private val GOLDOR_DEATH = Regex(
        """^\[BOSS]\s+Goldor:\s+You have done it, you destroyed the factory\.{1,3}$""",
        RegexOption.IGNORE_CASE,
    )
    private val NECRON_ENTRY =
        Regex(
            """^\[BOSS]\s+Necron:\s+You went further than any human before, congratulations\.!?$""",
            RegexOption.IGNORE_CASE,
        )
    private val P5_RELICS = Regex("""^\[BOSS] Necron: All this, for nothing\.{1,3}$""")
}
