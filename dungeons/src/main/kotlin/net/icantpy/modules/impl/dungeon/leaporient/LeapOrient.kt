package net.icantpy.modules.impl.dungeon.leaporient

import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.gui.McUi
import net.icantpy.modules.impl.dungeon.DungeonListener
import net.icantpy.modules.impl.timer.ClockSnapshot
import net.icantpy.modules.impl.timer.TickTimers
import net.icantpy.modules.impl.timer.TimerClock
import net.icantpy.util.Text
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object LeapOrient {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val partyChat = Regex("^Party > ((?:\\[[^]]*?])? ?)?(\\w{1,16}): (.+)$")
    private val leapSuccess = Regex("^You have teleported to (\\w{1,16})!$")
    private val debugFlags = mutableSetOf<String>()
    private var registered = false
    private val clockWatcher = LeapClockWatcher()
    val bossTracker = LeapBossTracker()
    private var pyTankLeaped = false
    private var lastRelicId = ""
    private var lastRelicAt = 0L
    private var lastPhase: LeapPhase = LeapPhase.UNKNOWN
    private var lastSection: Int = 0
    private var lastPartyKey: String = ""
    private var lastPartyAt: Long = 0L
    private var lastAnnounce: String = ""
    private var lastAnnounceAt: Long = 0L

    fun settings(): LeapOrientSettings = TickTimers.settings.leap

    fun debugActive(): Boolean = debugFlags.contains("leaporient") || TickTimers.settings.debugMode

    fun load() {
        OdinLeapBridge.init()
        if (registered) return
        registered = true
        UseItemCallback.EVENT.register { player, world, hand ->
            if (!world.isClientSide) return@register InteractionResult.PASS
            if (!debugActive()) return@register InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (!isDebugLeapItem(stack)) return@register InteractionResult.PASS
            McUi.runOnClientThread(mc) { openDebugLeapMenu() }
            InteractionResult.SUCCESS
        }
    }

    fun onTick() {
        LeapMenu.tick()
        if (LeapGameState.leapPhase() == LeapPhase.P2 && !pyTankLeaped) {
            LeapOrientTargets.refreshSource("mage-py-lightning-tank", settings().durationMs)
        }
        LeapOrientTargets.prune()
        LeapOrientState.tick()
        val phase = LeapGameState.leapPhase()
        if (lastPhase != LeapPhase.UNKNOWN && phase != LeapPhase.UNKNOWN && phase != lastPhase) {
            LeapOrientTargets.dropIfPhaseChanged(phase)
            LeapTriggerLockState.reset()
        }
        LeapRelicState.update(phase == LeapPhase.P5)?.let(::handleRelicPickup)
        lastPhase = phase
        val section = LeapGameState.leapSection()
        if (lastSection in 1..4 && section in 1..4 && section != lastSection) {
            LeapOrientTargets.dropIfSectionChanged(section)
        }
        lastSection = section
        val names = LeapRoster.knownPlayers().map { it.name }.toSet()
        if (names.isNotEmpty()) {
            LeapOrientTargets.dropIfTargetMissing(names)
        }
        onClocks(ClockSnapshot.from(TickTimers.clocks))
        processLeapChains()
        maintainNecronBers()
    }

    fun onBossBar(name: String, progress: Float) {
        if (!DungeonListener.state.inBoss || DungeonListener.floor.name !in setOf("F7", "M7")) return
        bossTracker.onBossBars(listOf(LeapBossBar(name, progress)))
            .forEach { dispatchBoss(it, "Server boss health reached zero") }
    }

    fun onClocks(snapshot: ClockSnapshot) {
        if (!settings().enabled && !debugActive()) return
        val fired = clockWatcher.evaluate(snapshot, settings().triggers)
        fired.forEach { trigger ->
            applyTrigger(trigger, LeapTriggerEvent.CLOCK, sender = "clock", leaped = null, message = trigger.title())
        }
    }

    fun resetClocks() {
        clockWatcher.reset()
        lastPhase = LeapPhase.UNKNOWN
        lastSection = 0
        bossTracker.reset()
        pyTankLeaped = false
        LeapTriggerLockState.reset()
        lastRelicId = ""
        lastRelicAt = 0L
    }

    fun onIncomingChat(raw: String) {
        val line = Text.strip(raw)
        if (line.isBlank()) return
        leapSuccess.find(line)?.groupValues?.getOrNull(1)?.let { leaped ->
            handleSelfLeap(leaped)
            return
        }
        LeapRelicState.parsePickup(line)?.let { pickup ->
            if (DungeonListener.floor.name !in setOf("F7", "M7") && !debugActive()) return
            bossTracker.confirmP5().forEach { dispatchBoss(it, "P5 confirmed by relic pickup") }
            if (pickup.player.equals(LeapRoster.selfName(), ignoreCase = true)) handleRelicPickup(pickup.relicId)
            return
        }
        val bossEvents = bossTracker.onChat(line)
        bossEvents.forEach { dispatchBoss(it, line) }
        if (!bossTracker.necronBersActive) LeapOrientTargets.removeSources(LeapRoutePreset.necronFightIds())
        if (LeapOrientEngine.bossEvent(line) != null || bossEvents.isNotEmpty()) return
        if (!settings().enabled && !debugActive()) return
        partyChat.find(line)?.let { match ->
            tryArmFromChat(match.groupValues[2], match.groupValues[3])
        }
    }

    fun onDisconnect() {
        LeapOrientTargets.clear()
        LeapOrientState.clear()
        LeapOrientState.debugSelfSpot = null
        LeapOrientState.debugSelfClass = null
        LeapOrientState.debugSenderSpots.clear()
        LeapOrientChatDebug.clear()
        LeapBossDeathNotifier.clear()
        LeapGameState.clearSimulate()
        LeapRelicState.reset()
        resetClocks()
        LeapChainScheduler.clear()
        lastAnnounce = ""
        lastPartyKey = ""
    }

    fun onDebugLeap(name: String) {
        tell("§a[debug] leaped to $name")
        onIncomingChat("You have teleported to $name!")
    }

    fun hudText(): List<String> {
        val lines = mutableListOf<String>()
        val cfg = settings()
        if (cfg.showLockHud) {
            val pending = LeapOrientTargets.pick(LeapGameState.currentId(), cfg)
            if (pending != null) {
                val remaining = LeapOrientTargets.remainingMs(pending) / 1000.0
                lines += "§d${pending.centerTitle()} §7${"%.1f".format(remaining)}s"
                lines += "§7${pending.centerReason()}"
            }
        }
        if (cfg.showBossDeathNotifier) {
            LeapBossDeathNotifier.hudLine()?.let { lines += it }
        }
        if (debugActive()) {
            lines += "§8state §f${LeapGameState.currentId()}"
            if (cfg.showChatDebugHud) {
                lines += LeapOrientChatDebug.hudLines(cfg.showChatDebugSkips)
            }
        }
        return lines
    }

    fun handleCommand(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw) ?: return null
        val lower = body.lowercase()
        if (lower == "leaporient" || lower.startsWith("leaporient ")) {
            return handleLeapOrientCommand(lower.removePrefix("leaporient").trim())
        }
        if (lower == "debug leaporient" || lower == "debug leaporient on") {
            debugFlags.add("leaporient")
            ensureDebugStubs()
            return "leaporient debug on"
        }
        if (lower == "debug leaporient off") {
            debugFlags.remove("leaporient")
            return "leaporient debug off"
        }
        return null
    }

    fun simulateClassChat(clazz: LeapDungeonClass): String {
        ensureDebugStubs()
        val member = DebugParty.members.firstOrNull { it.clazz == clazz }
            ?: return "no debug ${clazz.displayName}"
        val token = member.spot.token()
        val message = "${settings().keyword} at $token"
        onIncomingChat("Party > ${member.name}: $message")
        return "simulated ${member.name} ($token)"
    }

    fun tryArmFromChat(sender: String, message: String, force: Boolean = false) {
        val cfg = settings()
        val debug = debugActive() || force
        if (!force && !cfg.enabled && !debugActive()) return
        if (debug) ensureDebugStubs()
        val now = System.currentTimeMillis()
        val self = LeapRoster.selfName()
        if (self != null && sender.equals(self, ignoreCase = true) &&
            lastAnnounce.isNotBlank() &&
            message.equals(lastAnnounce, ignoreCase = true) &&
            now - lastAnnounceAt < 2500
        ) {
            record(sender, message, true, false, "ignored own announce")
            return
        }
        val partyKey = "${sender.lowercase()}:${message.lowercase()}"
        if (partyKey == lastPartyKey && now - lastPartyAt < 1200) {
            record(sender, message, true, false, "duplicate chat")
            return
        }
        lastPartyKey = partyKey
        lastPartyAt = now
        val leaped = LeapOrientEngine.parseLeaped(message, cfg)
        if (leaped != null) {
            applyEvent(LeapTriggerEvent.LEAPED_TO, sender, leaped, message)
            if (classOf(sender) == LeapDungeonClass.MAGE && LeapRoster.selfClass(cfg) == LeapDungeonClass.HEALER &&
                (leaped.asClass == LeapDungeonClass.HEALER || leaped.asName?.equals(self, true) == true)) {
                cfg.triggers.firstOrNull { it.id == "heal-py-landed-bers" }?.let {
                    applyTrigger(it, LeapTriggerEvent.LEAP_CHAIN, sender, leaped, "Mage landed on Healer")
                }
            }
            return
        }
        val ping = LeapOrientEngine.parseAtPing(message, cfg.keyword)
        if (ping == null) {
            if (debug && message.contains(cfg.keyword, ignoreCase = true)) {
                record(sender, message, true, false, "no at-target")
            } else if (debug) {
                record(sender, message, false, false, "no keyword")
            }
            return
        }
        if (!force && !debugActive() && DungeonListener.floor.name !in setOf("F7", "M7")) {
            record(sender, message, true, false, "not F7/M7")
            return
        }
        val (location, atClass) = ping
        val ctx = routeContext()
        val request = LeapOrientEngine.pingArm(
            sender = sender,
            senderClass = classOf(sender),
            spot = location,
            atClass = atClass,
            playerForClass = { clazz -> LeapRoster.playerForClass(clazz, LeapRoster.knownPlayers()) },
            context = ctx,
        )
        if (LeapOrientEngine.duplicateClassUnsafe(request.targetClass ?: LeapDungeonClass.EMPTY, ctx) &&
            request.targetName.isBlank()
        ) {
            record(sender, message, true, false, "duplicate class needs player")
            return
        }
        armRequest(request, sender, message, keywordHit = true)
    }

    fun previewNext(): String {
        val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
        if (pending != null) return "${pending.centerTitle()} · ${pending.centerReason()}"
        return LeapOrientEngine.nextPreview(settings(), routeContext())
    }

    fun lastSkipReason(): String = LeapOrientChatDebug.last?.detail ?: "none"

    fun simulateClockCrossing(trigger: LeapOrientTrigger): String {
        val spec = trigger.clock ?: return "no clock"
        resetClocks()
        val before = if (spec.crossing == ClockCrossing.RISING) spec.thresholdTicks - 1 else spec.thresholdTicks + 1
        val after = spec.thresholdTicks
        fun snap(value: Int): ClockSnapshot {
            val values = TimerClock.entries.associateWith { clock ->
                if (clock == spec.clock) value else -1
            }
            return ClockSnapshot(values)
        }
        clockWatcher.evaluate(snap(before.coerceAtLeast(0)), emptyList())
        val fired = clockWatcher.evaluate(snap(after.coerceAtLeast(0)), listOf(trigger))
        if (fired.isEmpty()) return "${trigger.title()} → no crossing"
        fired.forEach { applyTrigger(it, LeapTriggerEvent.CLOCK, "clock", null, trigger.title()) }
        val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
        return "${trigger.title()} → ${pending?.centerTitle() ?: "none"}"
    }

    fun simulateBoss(event: LeapTriggerEvent): String {
        ensureDebugStubs()
        applyEvent(event, sender = "boss", leaped = null)
        val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
        return "${event.label()} → ${pending?.centerTitle() ?: "none"}"
    }

    fun simulateBossToken(token: String): String {
        val event = LeapOrientEngine.bossEventToken(token)
            ?: return "unknown boss: $token (${bossDebugTokens()})"
        val chat = LeapOrientEngine.bossChatLine(event)
        if (chat != null) {
            ensureDebugStubs()
            onIncomingChat(chat)
            val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
            return "${event.label()} → ${pending?.centerTitle() ?: "none"}"
        }
        return simulateBoss(event)
    }

    fun simulatePartyPing(sender: String, spot: String = "ee2", atClass: String? = null): String {
        ensureDebugStubs()
        val cfg = settings()
        val member = DebugParty.members.firstOrNull {
            it.name.equals(sender, ignoreCase = true) ||
                it.clazz == LeapDungeonClass.fromToken(sender)
        }
        val name = member?.name ?: sender
        val atToken = atClass?.let { LeapDungeonClass.fromToken(it)?.shortName?.lowercase() ?: it }
            ?: LeapDungeonClass.fromToken(spot)?.shortName?.lowercase()
        val location = if (atToken != null) atToken else spot
        val message = "${cfg.keyword} at $location"
        tryArmFromChat(name, message, force = true)
        val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
        return "$name ping @ $location → ${pending?.centerTitle() ?: "none"}"
    }

    fun simulateLeapedFrom(sender: String, target: String): String {
        ensureDebugStubs()
        val cfg = settings()
        val member = DebugParty.members.firstOrNull {
            it.name.equals(sender, ignoreCase = true) ||
                it.clazz == LeapDungeonClass.fromToken(sender)
        }
        val name = member?.name ?: sender
        val clazz = LeapDungeonClass.fromToken(target)?.takeIf { it.isReal() }
            ?: LeapRoster.classOf(name, cfg).takeIf { it.isReal() }
            ?: LeapDungeonClass.HEALER
        val filled = when (target.lowercase()) {
            "self", "me" -> LeapChatPatterns.formatAnnounce(cfg.announceTemplate, name, clazz)
            else -> {
                val targetClass = LeapDungeonClass.fromToken(target)?.takeIf { it.isReal() }
                if (targetClass != null) {
                    LeapChatPatterns.formatAnnounce(cfg.announceTemplate, name, targetClass)
                } else {
                    LeapChatPatterns.formatAnnounce(
                        cfg.announceTemplate.replace("{class}", "{name}", ignoreCase = true),
                        target,
                        LeapRoster.classOf(target, cfg),
                    )
                }
            }
        }
        onIncomingChat("Party > $name: $filled")
        val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
        return "$name leaped → ${pending?.centerTitle() ?: "none"}"
    }

    fun simulateTrigger(triggerId: String): String {
        ensureDebugStubs()
        val trigger = settings().triggers.firstOrNull { it.id.equals(triggerId, ignoreCase = true) }
            ?: return "no trigger: $triggerId"
        return when (trigger.event) {
            LeapTriggerEvent.CLOCK -> simulateClockCrossing(trigger)
            LeapTriggerEvent.LEAP_CHAIN -> {
                val match = trigger.match.trim().lowercase()
                val leapedName = when {
                    match == "any" -> trigger.targetValue.ifBlank { "dbgMage" }
                    LeapDungeonClass.fromToken(match)?.isReal() == true -> {
                        LeapRoster.playerForClass(
                            LeapDungeonClass.fromToken(match)!!,
                            LeapRoster.knownPlayers(),
                        )?.name ?: "dbg${match.replaceFirstChar { it.uppercase() }}"
                    }
                    else -> match
                }
                onIncomingChat("You have teleported to $leapedName!")
                flushPendingChains()
                val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
                "${trigger.title()} → ${pending?.centerTitle() ?: "none"} (chain pending=${LeapChainScheduler.pendingCount()})"
            }
            LeapTriggerEvent.LEAPED_TO, LeapTriggerEvent.SELF_LEAP -> {
                val match = trigger.match.trim().lowercase()
                val sender = when {
                    match == "self" || match == "me" -> settings().selfClass.shortName.lowercase()
                    LeapDungeonClass.fromToken(match)?.isReal() == true -> match
                    else -> "dbgMage"
                }
                val target = trigger.targetValue.ifBlank { "healer" }
                simulateLeapedFrom(sender, target)
            }
            else -> {
                val chat = LeapOrientEngine.bossChatLine(trigger.event)
                if (chat != null) {
                    onIncomingChat(chat)
                } else {
                    applyTrigger(trigger, trigger.event, "boss", null, trigger.title())
                }
                val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
                "${trigger.title()} → ${pending?.centerTitle() ?: "none"}"
            }
        }
    }

    fun bossDebugTokens(): String =
        "maxor|storm|goldor|core|goldor-die|necron-drop|necron-die|p5-relics"

    fun simulateLeapedTo(target: String): String {
        ensureDebugStubs()
        val cfg = settings()
        val self = LeapRoster.selfClass(cfg)
        val clazz = when (target.lowercase()) {
            "self", "me" -> self.takeIf { it.isReal() } ?: LeapDungeonClass.HEALER
            else -> LeapDungeonClass.fromToken(target)
        }
        val filled = if (clazz?.isReal() == true) {
            LeapChatPatterns.formatAnnounce(cfg.announceTemplate, LeapRoster.selfName() ?: "you", clazz)
        } else {
            LeapChatPatterns.formatAnnounce(
                cfg.announceTemplate.replace("{class}", "{name}", ignoreCase = true),
                target,
                classOf(target),
            )
        }
        onIncomingChat("Party > dbgMage: $filled")
        return "simulated leaped chat: $filled"
    }

    private fun handleLeapOrientCommand(args: String): String? {
        if (args.isBlank()) {
            TickTimers.updateLeap { it.copy(enabled = !it.enabled) }
            return if (settings().enabled) "leaporient on" else "leaporient off"
        }
        val tokens = args.split(Regex("\\s+"))
        val head = tokens.first().lowercase()
        return when (head) {
            "status" -> status()
            "clear" -> {
                LeapOrientTargets.clear()
                LeapOrientState.clear()
                "leaporient cleared"
            }
            "lock" -> {
                if (!canUseDebugCommands()) return "leaporient lock requires debug"
                val name = tokens.getOrNull(1) ?: return "usage: leaporient lock <name> [seconds]"
                val seconds = tokens.getOrNull(2)?.toLongOrNull() ?: settings().durationSeconds.toLong()
                val clazz = classOf(name)
                LeapOrientTargets.arm(name, OrientSpot.ANY, clazz, seconds * 1000)
                LeapOrientState.arm(name, seconds * 1000)
                "locked $name for ${seconds}s"
            }
            "state" -> {
                if (!canUseDebugCommands()) return "leaporient state requires debug"
                val token = tokens.getOrNull(1) ?: return "state=${LeapGameState.currentId()}"
                if (token == "clear" || token == "here") {
                    LeapGameState.clearSimulate()
                    "game state override cleared (${LeapGameState.currentId()})"
                } else {
                    LeapGameState.simulate(token)
                }
            }
            "class" -> {
                if (!canUseDebugCommands()) return "leaporient class requires debug"
                val clazz = LeapDungeonClass.fromToken(tokens.getOrNull(1) ?: "") ?: return "usage: leaporient class archer|bers|heal|mage|tank"
                simulateClassChat(clazz)
            }
            "me" -> {
                val token = tokens.getOrNull(1) ?: return "self class=${settings().selfClass.displayName}"
                val clazz = LeapDungeonClass.fromToken(token) ?: return "usage: leaporient me archer|bers|heal|mage|tank|auto"
                TickTimers.updateLeap { it.copy(selfClass = clazz) }
                LeapOrientState.debugSelfClass = clazz.takeIf { it.isReal() }
                "self class -> ${if (clazz.isReal()) clazz.displayName else "auto"}"
            }
            "party" -> {
                if (!canUseDebugCommands()) return "leaporient party requires debug"
                DebugParty.seedSenderSpots()
                val shown = DebugParty.leapPlayers(
                    mc.player?.name?.string,
                    LeapRoster.selfClass(settings()),
                ).joinToString { "${it.name}(${it.clazz.displayName})" }
                "party: $shown"
            }
            "item" -> giveDebugItem()
            "open" -> {
                if (!canUseDebugCommands()) return "leaporient open requires debug"
                openDebugLeapMenu()
                "opened Spirit Leap menu"
            }
            "chat", "sim" -> {
                if (!canUseDebugCommands()) return "leaporient chat requires debug"
                val name = tokens.getOrNull(1) ?: return "usage: leaporient chat <name> <message...>"
                val message = tokens.drop(2).joinToString(" ")
                ensureDebugStubs()
                onIncomingChat("Party > $name: $message")
                "simulated party chat"
            }
            "boss" -> {
                if (!canUseDebugCommands()) return "leaporient boss requires debug"
                val token = tokens.getOrNull(1)
                    ?: return "usage: leaporient boss " + bossDebugTokens()
                simulateBossToken(token)
            }
            "ping" -> {
                if (!canUseDebugCommands()) return "leaporient ping requires debug"
                val sender = tokens.getOrNull(1) ?: return "usage: leaporient ping <class|name> [at spot|class]"
                val spot = tokens.getOrNull(3)?.takeIf { tokens.getOrNull(2)?.equals("at", true) == true }
                    ?: tokens.getOrNull(2)
                    ?: "ee2"
                simulatePartyPing(sender, spot)
            }
            "leaped" -> {
                if (!canUseDebugCommands()) return "leaporient leaped requires debug"
                val sender = tokens.getOrNull(1) ?: return "usage: leaporient leaped <class|name> [target]"
                val target = tokens.getOrNull(2) ?: "self"
                simulateLeapedFrom(sender, target)
            }
            "trigger" -> {
                if (!canUseDebugCommands()) return "leaporient trigger requires debug"
                val id = tokens.getOrNull(1) ?: return "usage: leaporient trigger <id>"
                simulateTrigger(id)
            }
            "scene" -> handleScene(tokens.getOrNull(1))
            else -> help()
        }
    }

    private fun handleScene(id: String?): String = when (id) {
        null -> "scenes: icantpy, two-calls, expire, leaped, storm, goldor, necron, p5, sender"
        "icantpy" -> {
            DebugParty.seedSenderSpots()
            LeapGameState.simulate("m7p3s2")
            tryArmFromChat("dbgArcher", "${settings().keyword} at ee2", force = true)
            "scene icantpy — center should be dbgArcher; open leap menu"
        }
        "two-calls" -> {
            DebugParty.seedSenderSpots()
            LeapGameState.simulate("m7p3s2")
            tryArmFromChat("dbgHealer", "${settings().keyword} at ee3", force = true)
            tryArmFromChat("dbgTank", "${settings().keyword} at s4", force = true)
            val pick = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
            "two-calls @ m7p3s2 -> ${pick?.sender ?: "none"} (prefer ee2 if present, else latest)"
        }
        "expire" -> {
            LeapOrientTargets.arm("dbgArcher", OrientSpot.EE2, LeapDungeonClass.ARCHER, 2_000)
            "locked dbgArcher 2s"
        }
        "leaped" -> {
            LeapOrientTargets.arm("dbgArcher", OrientSpot.EE2, LeapDungeonClass.ARCHER, settings().durationMs)
            onIncomingChat("You have teleported to dbgArcher!")
            if (LeapOrientTargets.all().isEmpty()) "scene ok: lock cleared" else "scene failed: lock still active"
        }
        "storm" -> {
            LeapGameState.simulate("m7p2")
            simulateBossToken("storm")
        }
        "goldor" -> {
            LeapGameState.simulate("m7p3s1")
            simulateBossToken("goldor")
        }
        "necron" -> {
            LeapGameState.simulate("m7p4")
            TickTimers.updateLeap { leap ->
                leap.copy(selfClass = LeapDungeonClass.MAGE).applyPreset()
            }
            LeapOrientState.debugSelfClass = LeapDungeonClass.MAGE
            simulateBossToken("necron-die")
        }
        "p5" -> {
            LeapGameState.simulate("m7p5")
            TickTimers.updateLeap { leap ->
                leap.copy(selfClass = LeapDungeonClass.HEALER).applyPreset()
            }
            LeapOrientState.debugSelfClass = LeapDungeonClass.HEALER
            simulateBossToken("relic-pickup")
        }
        "sender" -> {
            DebugParty.seedSenderSpots()
            LeapGameState.simulate("m7p3s2")
            LeapOrientState.debugSelfClass = LeapDungeonClass.HEALER
            TickTimers.updateLeap { leap ->
                leap.copy(selfClass = LeapDungeonClass.HEALER).upsertTrigger(
                    LeapOrientTrigger.create(LeapTriggerEvent.LEAPED_TO, "berserk").copy(
                        id = "debug-sender-lock",
                        match = "bers",
                        target = LeapCenterTarget.SENDER,
                        forClass = LeapDungeonClass.HEALER,
                        reason = "Leap to announcer",
                        zone = "SS",
                    ),
                )
            }
            simulateLeapedFrom("mage", "bers")
        }
        else -> "unknown scene: $id"
    }

    private fun handleSelfLeap(leaped: String) {
        val cfg = settings()
        val clazz = classOf(leaped)
        val consumedPyTank = LeapGameState.leapPhase() == LeapPhase.P2 &&
            LeapRoster.selfClass(cfg) == LeapDungeonClass.MAGE && clazz == LeapDungeonClass.TANK
        if (consumedPyTank) pyTankLeaped = true
        val unlocked = LeapTriggerLockState.markCompleted(clazz)
        if (cfg.leapAnnounce && (DungeonListener.state.inDungeon || debugActive())) {
            val line = LeapChatPatterns.formatAnnounce(cfg.announceTemplate, leaped, clazz)
            lastAnnounce = line
            lastAnnounceAt = System.currentTimeMillis()
            mc.player?.connection?.sendCommand("pc $line")
        }
        LeapOrientTargets.clear()
        LeapOrientState.clear()
        if (debugActive()) tell("§7[leaporient] leaped to $leaped")
        val leapedMatch = LeapedChatMatch(leaped, clazz.takeIf { it.isReal() }, leaped)
        applyEvent(LeapTriggerEvent.SELF_LEAP, LeapRoster.selfName() ?: "you", leapedMatch)
        scheduleLeapChains(leapedMatch)
        unlocked.forEach { pending ->
            val current = cfg.triggers.firstOrNull { it.id == pending.trigger.id } ?: return@forEach
            applyTrigger(current, pending.event, pending.sender, pending.leaped, pending.message)
        }
    }

    private fun scheduleLeapChains(leaped: LeapedChatMatch) {
        val cfg = settings()
        if (!cfg.enabled && !debugActive()) return
        val ctx = routeContext()
        val selfName = LeapRoster.selfName()
        val selfClass = LeapRoster.selfClass(cfg)
        cfg.triggers.filter { it.enabled && it.event == LeapTriggerEvent.LEAP_CHAIN }.forEach { trigger ->
            val check = LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.LEAP_CHAIN, ctx)
            if (!check.ok) {
                if (debugActive()) record("chain", trigger.title(), true, false, check.rejected ?: "rejected")
                return@forEach
            }
            if (!LeapOrientEngine.leapedMatchHits(trigger, leaped, selfName, selfClass) { classOf(it) }) {
                if (debugActive()) record("chain", trigger.title(), true, false, "leap match missed")
                return@forEach
            }
            val request = LeapOrientEngine.resolveArm(
                trigger = trigger,
                sender = selfName ?: "you",
                senderClass = selfClass,
                leaped = leaped,
                classOf = { classOf(it) },
                playerForClass = { clazz -> LeapRoster.playerForClass(clazz, LeapRoster.knownPlayers()) },
                context = ctx,
            ) ?: run {
                if (debugActive()) record("chain", trigger.title(), true, false, "target missing")
                return@forEach
            }
            if (LeapOrientEngine.duplicateClassUnsafe(request.targetClass ?: LeapDungeonClass.EMPTY, ctx) &&
                request.targetName.isBlank()
            ) {
                if (debugActive()) record("chain", trigger.title(), true, false, "duplicate class needs player")
                return@forEach
            }
            val delayMs = trigger.chainDelaySeconds.coerceAtLeast(0) * 1000L
            val durationMs = chainDurationMs(trigger, cfg)
            if (delayMs == 0L) {
                armRequest(request, "chain", request.reason, true, durationMs)
            } else {
                LeapChainScheduler.schedule(trigger, request, delayMs, durationMs)
            }
            if (debugActive()) {
                val delayLabel = if (trigger.chainDelaySeconds > 0) " in ${trigger.chainDelaySeconds}s" else " now"
                tell("§7[leaporient] chain → ${request.targetName.ifBlank { request.targetClass?.displayName ?: "?" }}$delayLabel")
            }
        }
    }

    fun flushPendingChains(): Int {
        val pending = LeapChainScheduler.pendingCount()
        processLeapChains()
        return pending
    }

    private fun processLeapChains() {
        if (!settings().enabled && !debugActive()) return
        LeapChainScheduler.due().forEach { pending ->
            val trigger = settings().triggers.firstOrNull { it.id == pending.triggerId }
            if (trigger == null || !trigger.enabled) return@forEach
            val ctx = routeContext()
            val check = LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.LEAP_CHAIN, ctx)
            if (!check.ok) {
                record("chain", pending.request.reason, true, false, check.rejected ?: "rejected")
                return@forEach
            }
            armRequest(
                pending.request,
                sender = "chain",
                message = pending.request.reason,
                keywordHit = true,
                durationMs = pending.durationMs,
            )
        }
    }

    private fun chainDurationMs(trigger: LeapOrientTrigger, cfg: LeapOrientSettings): Long =
        (if (trigger.chainDurationSeconds > 0) trigger.chainDurationSeconds else cfg.durationSeconds) * 1000L

    private fun dispatchBoss(event: LeapTriggerEvent, line: String) {
        LeapOrientTargets.dropIfPhaseChanged(bossTracker.phase)
        LeapBossDeathNotifier.record(event, line)
        applyEvent(event, "boss", null)
        if (event == LeapTriggerEvent.BOSS_NECRON_DEATH) applyEvent(LeapTriggerEvent.BOSS_P5, "boss", null)
    }

    private fun handleRelicPickup(relicId: String) {
        val now = System.currentTimeMillis()
        if (relicId == lastRelicId && now - lastRelicAt < 2000L) return
        lastRelicId = relicId
        lastRelicAt = now
        applyEvent(LeapTriggerEvent.RELIC_PICKUP, "relic", null, "Picked up ${relicId.removeSuffix("_KING_RELIC").lowercase()} relic")
    }

    private fun maintainNecronBers() {
        val ids = LeapRoutePreset.necronFightIds()
        if (!bossTracker.necronBersActive || LeapGameState.leapPhase() != LeapPhase.P4) {
            LeapOrientTargets.removeSources(ids)
            return
        }
        val active = LeapOrientTargets.all()
        active.filter { it.sourceTriggerId in ids }.forEach {
            LeapOrientTargets.refreshSource(it.sourceTriggerId, settings().durationMs)
        }
        if (active.isEmpty()) {
            settings().triggers.filter { it.id in ids &&
                LeapOrientEngine.eligibility(it, LeapTriggerEvent.BOSS_NECRON, routeContext()).ok
            }.forEach { applyTrigger(it, LeapTriggerEvent.BOSS_NECRON, "boss", null, "Necron fight until ARGH!") }
        }
    }

    private fun applyEvent(
        event: LeapTriggerEvent,
        sender: String,
        leaped: LeapedChatMatch?,
        message: String = event.label(),
    ) {
        val cfg = settings()
        if (!cfg.enabled && !debugActive()) return
        val ctx = routeContext()
        val hits = cfg.triggers.map { LeapOrientEngine.eligibility(it, event, ctx) }
        if (hits.none { it.ok }) {
            val why = hits.firstOrNull { it.trigger.event == event }?.rejected ?: "no ${event.label()} trigger"
            if (debugActive()) record(sender, message, false, false, why)
            return
        }
        var armed = 0
        hits.filter { it.ok }.forEach { hit ->
            val trigger = hit.trigger
            if (trigger.id in LeapRoutePreset.necronFightIds() && !bossTracker.necronBersActive) return@forEach
            if (event == LeapTriggerEvent.LEAP_CHAIN) {
                return@forEach
            }
            if (event == LeapTriggerEvent.LEAPED_TO || event == LeapTriggerEvent.SELF_LEAP) {
                if (leaped == null || !LeapOrientEngine.leapedMatchHits(
                        trigger,
                        leaped,
                        LeapRoster.selfName(),
                        LeapRoster.selfClass(cfg),
                    ) { classOf(it) }
                ) {
                    record(sender, message, true, false, "leap match missed")
                    return@forEach
                }
            }
            applyTrigger(trigger, event, sender, leaped, message)
            armed += 1
        }
        if (armed == 0 && debugActive()) {
            record(sender, message, true, false, "${event.label()} did not match")
        }
    }

    private fun applyTrigger(
        trigger: LeapOrientTrigger,
        event: LeapTriggerEvent,
        sender: String,
        leaped: LeapedChatMatch?,
        message: String,
    ) {
        val ctx = routeContext()
        val check = LeapOrientEngine.eligibility(trigger, event, ctx)
        if (!check.ok) {
            record(sender, message, true, false, check.rejected ?: "rejected")
            return
        }
        if (!LeapTriggerLockState.isSatisfied(trigger)) {
            LeapTriggerLockState.defer(DeferredLeapTrigger(trigger, event, sender, leaped, message))
            record(
                sender,
                message,
                true,
                false,
                "waiting for leap to ${trigger.requiresTargetClass.displayName}",
            )
            return
        }
        val request = LeapOrientEngine.resolveArm(
            trigger = trigger,
            sender = sender,
            senderClass = classOf(sender),
            leaped = leaped,
            classOf = { classOf(it) },
            playerForClass = { clazz -> LeapRoster.playerForClass(clazz, LeapRoster.knownPlayers()) },
            context = ctx,
        ) ?: run {
            record(sender, message, true, false, "target missing")
            return
        }
        if (LeapOrientEngine.duplicateClassUnsafe(request.targetClass ?: LeapDungeonClass.EMPTY, ctx) &&
            request.targetName.isBlank()
        ) {
            record(sender, message, true, false, "duplicate class needs player")
            return
        }
        armRequest(request, sender, message, keywordHit = true)
    }

    private fun routeContext(): LeapRouteContext {
        val cfg = settings()
        return cfg.routeContext(
            floor = LeapGameState.leapFloor().takeIf { it != LeapFloor.UNKNOWN }
                ?: LeapOrientEngine.contextFromStateId(LeapGameState.currentId()).floor,
            phase = LeapGameState.leapPhase(),
            section = LeapGameState.leapSection(),
            known = LeapRoster.knownPlayers(),
            self = LeapRoster.selfClass(cfg),
        )
    }

    private fun armRequest(
        request: LeapArmRequest,
        sender: String,
        message: String,
        keywordHit: Boolean,
        durationMs: Long = settings().durationMs,
    ) {
        val cfg = settings()
        val armed = LeapOrientTargets.arm(
            sender = request.sender,
            location = request.location,
            clazz = request.clazz,
            durationMs = durationMs,
            targetName = request.targetName,
            targetClass = request.targetClass,
            priority = request.priority,
            label = request.label,
            zone = request.zone,
            reason = request.reason,
            phase = request.phase,
            sourceKind = request.sourceKind,
            sourceTriggerId = request.sourceTriggerId,
            section = request.section,
        )
        if (armed == null) {
            record(sender, message, keywordHit, false, "lower priority than current lock")
            return
        }
        val lockName = request.targetName.ifBlank { request.targetClass?.displayName ?: request.sender }
        LeapOrientState.arm(lockName, durationMs)
        record(sender, message, keywordHit, true, "armed ${armed.centerTitle()} (${request.reason}) @ ${LeapGameState.currentId()}")
        if (debugActive()) tell("§aLeap Orient: ${armed.centerTitle()} · ${armed.centerReason()} (${durationMs / 1000}s)")
    }

    private fun classOf(sender: String): LeapDungeonClass = LeapRoster.classOf(sender, settings())

    private fun record(sender: String, message: String, keywordHit: Boolean, armed: Boolean, detail: String) {
        val entry = LeapOrientChatDebug.Entry(
            sender = sender,
            message = message,
            keywordHit = keywordHit,
            selfOk = true,
            senderOk = true,
            armed = armed,
            detail = detail,
        )
        LeapOrientChatDebug.record(entry)
        val cfg = settings()
        if (debugActive() && cfg.showChatDebugMessages && (entry.armed || cfg.showChatDebugSkips)) {
            val color = if (armed) "§a" else "§c"
            tell("$color[trigger] §f$sender§7: §f$message $color→ ${if (armed) "LOCK" else "SKIP"} §7($detail)")
        }
    }

    fun ensureDebugStubs() {
        if (!debugActive()) return
        if (LeapOrientState.debugSenderSpots.isEmpty()) DebugParty.seedSenderSpots()
        if (LeapOrientState.debugSelfSpot == null) LeapOrientState.debugSelfSpot = OrientSpot.GOLDOR_S2
    }

    fun openPreview(): String {
        debugFlags.add("leaporient")
        openDebugLeapMenu()
        return "opened Spirit Leap menu"
    }

    fun givePreviewItem(): String {
        debugFlags.add("leaporient")
        return giveDebugItem()
    }

    private fun openDebugLeapMenu() {
        ensureDebugStubs()
        LeapMenuScreen.open(
            DebugParty.leapPlayers(mc.player?.name?.string, LeapRoster.selfClass(settings())),
        )
    }

    private fun giveDebugItem(): String {
        if (!canUseDebugCommands()) return "leaporient item requires debug"
        val player = mc.player ?: return "no player"
        val stack = ItemStack(Items.ENDER_EYE)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§5Spirit Leap"))
        player.inventory.add(stack)
        return "gave debug Spirit Leap item"
    }

    private fun isDebugLeapItem(stack: ItemStack): Boolean {
        if (stack.item != Items.ENDER_EYE && stack.item != Items.RECOVERY_COMPASS) return false
        val name = stack.get(DataComponents.CUSTOM_NAME)?.string ?: return debugActive()
        return name.contains("Spirit Leap", ignoreCase = true)
    }

    private fun status(): String {
        val pending = LeapOrientTargets.pick(LeapGameState.currentId(), settings())
        return "enabled=${settings().enabled} state=${LeapGameState.currentId()} " +
            "self=${settings().selfClass.displayName} " +
            "center=${pending?.targetName?.ifBlank { pending.targetClass?.displayName } ?: pending?.sender ?: "none"} " +
            "odin=${if (OdinLeapBridge.isPresent()) "yes" else "no"}"
    }

    private fun canUseDebugCommands(): Boolean = debugActive()

    private fun help(): String =
        "leaporient status|clear|lock|state|class|me|party|item|open|chat|boss|ping|leaped|trigger|scene"

    private fun tell(text: String) {
        mc.player?.sendSystemMessage(Component.literal(text))
    }
}
