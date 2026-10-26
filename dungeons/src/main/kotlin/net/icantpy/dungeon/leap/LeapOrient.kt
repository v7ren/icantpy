package net.icantpy.dungeon.leap

import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.icantpy.hud.markers.PlayerMarkers
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.api.IcantpyClientActions
import net.icantpy.dungeon.dragons.DragonController
import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.gui.McUi
import net.icantpy.qol.auto.AutoActionQueue
import net.icantpy.qol.loadout.Loadout
import net.icantpy.qol.stats.StatsArmor
import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.DungeonListener
import net.icantpy.dungeon.score.ScoreTracker
import net.icantpy.dungeon.splits.RunSplitsController
import net.icantpy.dungeon.timer.ClockSnapshot
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.dungeon.timer.TimerClock
import net.icantpy.location.LocationChange
import net.icantpy.location.LocationSample
import net.icantpy.location.SkyblockLocation
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
    private val progressWatcher = LeapProgressWatcher()
    val bossTracker = LeapBossTracker()
    private var pyTankLeaped = false
    /** "This ability is on cooldown" repeats while a key is held; one trigger per window. */
    private val abilityCooldownThrottle = LeapEventThrottle(ABILITY_COOLDOWN_THROTTLE_MS)
    private const val ABILITY_COOLDOWN_THROTTLE_MS = 3_000L
    private val chatCooldown = LeapChatCooldown()

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
        if (FabricLoader.getInstance().isModLoaded("icantpy_loader")) return
        if (registered) return
        registered = true
        UseItemCallback.EVENT.register { player, world, hand -> onUseItem(player, world, hand) }
        net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback.EVENT.register { _, _, clicks ->
            clicks > 0 && LeapAutoLeapController.onAttack()
        }
    }

    fun onUseItem(
        player: net.minecraft.world.entity.player.Player,
        world: net.minecraft.world.level.Level,
        hand: net.minecraft.world.InteractionHand,
    ): InteractionResult {
        if (!world.isClientSide) return InteractionResult.PASS
        if (!debugActive()) return InteractionResult.PASS
        val stack = player.getItemInHand(hand)
        if (!isDebugLeapItem(stack)) return InteractionResult.PASS
        McUi.runOnClientThread(mc) { openDebugLeapMenu() }
        return InteractionResult.SUCCESS
    }

    fun onTick() {
        LeapMenu.tick()
        dispatchDragonEvents()
        dispatchLocationSample(SkyblockLocation.tick())
        if (LeapGameState.leapPhase() == LeapPhase.P2 && !pyTankLeaped) {
            LeapOrientTargets.refreshSource("mage-py-lightning-tank", settings().durationMs)
        }
        LeapOrientTargets.prune()
        LeapOrientState.tick()
        val phase = LeapGameState.leapPhase()
        if (lastPhase != LeapPhase.UNKNOWN && phase != LeapPhase.UNKNOWN && phase != lastPhase) {
            LeapOrientTargets.dropIfPhaseChanged(phase)
            LeapTriggerLockState.reset()
            LeapFollowUps.discardAll("phase changed").forEach { discardFollowUp(it.card, it.reason) }
        }
        LeapRelicState.update(phase == LeapPhase.P5)?.let(::handleRelicPickup)
        LeapRelicPlacementWatcher.tick(phase == LeapPhase.P5).forEach { placed ->
            applyEvent(placed.event, placed.color, null, "Relic placed: ${placed.color.lowercase()}")
        }
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
        LeapFollowUps.tick(System.currentTimeMillis(), LeapOrientTargets.all()).forEach { discardFollowUp(it.card, it.reason) }
        recordHoldEvents()
        onClocks(ClockSnapshot.from(TickTimers.clocks))
        evaluateHealth()
        processLeapChains()
        maintainNecronBers()
        LeapAutoLeapController.pollAttack()
        LeapAutoLeapController.tick()
    }

    fun onBossBar(name: String, progress: Float) {
        if (!DungeonListener.state.inBoss || DungeonListener.floor.name !in setOf("F7", "M7")) return
        bossTracker.onBossBars(listOf(LeapBossBar(name, progress)))
            .forEach { dispatchBoss(it, "boss bar", "boss bar (${LeapBossTracker.bossKey(name)} health 0)") }
    }

    fun onClocks(snapshot: ClockSnapshot) {
        if (!settings().enabled && !debugActive()) return
        val fired = clockWatcher.evaluate(snapshot, settings().triggers)
        fired.forEach { trigger ->
            applyTrigger(trigger, LeapTriggerEvent.CLOCK, sender = "clock", leaped = null, message = trigger.title())
        }
    }

    fun resetClocks() {
        healthWatches.clear()
        LeapAutoLeapController.clear()
        LeapAutoLeapDoorOpener.clear()
        clockWatcher.reset()
        lastPhase = LeapPhase.UNKNOWN
        lastSection = 0
        bossTracker.reset()
        pyTankLeaped = false
        LeapTriggerLockState.reset()
        lastRelicId = ""
        lastRelicAt = 0L
        LeapRelicPlacementWatcher.reset()
    }

    fun onIncomingChat(raw: String) {
        LeapAutoLeapDoorOpener.onChat(raw)
        val line = Text.strip(raw)
        if (line.isBlank()) return
        ChatMessageMatch.parse(line)?.let { dispatchChatMessage(it, line, System.currentTimeMillis()) }
        leapSuccess.find(line)?.groupValues?.getOrNull(1)?.let { leaped ->
            handleSelfLeap(leaped)
            return
        }
        LeapRelicState.parsePickup(line)?.let { pickup ->
            if (DungeonListener.floor.name !in setOf("F7", "M7") && !debugActive()) return
            bossTracker.confirmP5().forEach { dispatchBoss(it, line, "relic pickup") }
            if (pickup.player.equals(LeapRoster.selfName(), ignoreCase = true)) handleRelicPickup(pickup.relicId)
            return
        }
        for ((chatEvent, player) in LeapChatEvents.detect(line)) {
            if (chatEvent == LeapTriggerEvent.ABILITY_COOLDOWN &&
                !abilityCooldownThrottle.allow(System.currentTimeMillis())
            ) {
                continue
            }
            applyEvent(chatEvent, player ?: "game", null, line)
        }
        val bossEvents = bossTracker.onChat(line)
        bossEvents.forEach { dispatchBoss(it, line, "chat") }
        if (!bossTracker.necronBersActive) LeapOrientTargets.removeSources(LeapRoutePreset.necronFightIds())
        if (LeapOrientEngine.bossEvent(line) != null || bossEvents.isNotEmpty()) return
        if (!settings().enabled && !debugActive()) return
        partyChat.find(line)?.let { match ->
            tryArmFromChat(match.groupValues[2], match.groupValues[3])
        }
    }

    fun onDisconnect() {
        LeapAutoLeapController.clear()
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
        LeapFollowUps.clear()
        SkyblockLocation.reset()
        progressWatcher.reset()
        lastAnnounce = ""
        lastPartyKey = ""
        chatCooldown.clear()
    }

    /**
     * Turns one location sample into trigger events, then checks the tab and sidebar counts. A run starts
     * and ends with the dungeon, so the progress counts restart with it.
     */
    private fun dispatchLocationSample(sample: LocationSample?) {
        if (sample == null) return
        sample.changes.forEach { change ->
            if (change is LocationChange.DungeonEntered || change == LocationChange.DungeonLeft) {
                progressWatcher.reset()
            }
            LeapLocationSignals.eventsFor(change).forEach { (event, value) ->
                applyEvent(event, value, null, value, source = "location")
            }
        }
        progressWatcher.update(sample.snapshot.stats, settings().triggers).forEach { hit ->
            forwardDetectedEvent(hit.event)
            val now = System.currentTimeMillis()
            val fired = if (applyTrigger(hit.trigger, hit.event, "tab", null, hit.value)) 1 else 0
            LeapEventLog.record(now, hit.event, hit.value, "tab", fired)
        }
    }

    /**
     * Debug page entry point. With [honorFilters] the event goes through the normal card matching, as if the
     * game produced it. Without, every enabled card for the event runs, skipping the match text filters.
     */
    fun fireDebugEvent(event: LeapTriggerEvent, value: String, honorFilters: Boolean): String {
        val captured = value.trim().ifBlank { event.label() }
        if (event == LeapTriggerEvent.CHAT_MESSAGE) {
            // The value is the message text; chat cards always apply their own match and cooldown.
            val fake = ParsedChat(ChatChannel.PUBLIC, LeapRoster.selfName() ?: "debug", value.trim())
            val fired = dispatchChatMessage(fake, value.trim(), System.currentTimeMillis(), bypassCooldown = true)
            return "fired ${event.label()}: $fired card(s)"
        }
        if (honorFilters) {
            val fired = applyEvent(event, captured, null, captured, source = "debug")
            return "fired ${event.label()}: $fired card(s)"
        }
        val fired = settings().triggers
            .filter { it.enabled && it.event == event }
            .count { applyTrigger(it, event, captured, null, captured) }
        LeapEventLog.record(System.currentTimeMillis(), event, captured, "debug", fired)
        return "fired ${event.label()} ignoring filters: $fired card(s)"
    }

    fun onDebugLeap(name: String) {
        tell("§a[debug] leaped to $name")
        // In a local test world the dummy players are mannequins; a typed /tp (cheats) walks to the matching one.
        if (mc.hasSingleplayerServer() && name.startsWith("dbg")) {
            IcantpyClientActions.sendCommand("tp @s @e[type=mannequin,name=$name,limit=1]")
        }
        onIncomingChat("You have teleported to $name!")
    }

    fun hudText(): List<String> {
        val lines = mutableListOf<String>()
        val cfg = settings()
        if (cfg.showLockHud) {
            val pending = LeapOrientTargets.pick(LeapGameState.currentId(), cfg)
            if (pending != null) {
                val status = LeapHoldText.status(pending, LeapOrientTargets.remainingMs(pending))
                lines += "§d${pending.centerTitle()} §7$status"
                lines += "§7${pending.centerReason()}"
                LeapHoldText.thenLine(pending, LeapOrientTargets.fallback())?.let { lines += "§8$it" }
            }
            lines += LeapAutoLeapDoorOpener.hudLines(cfg)
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
        return LeapOrientEngine.nextPreview(settings(), routeContext(), StatsArmor.presetLabels())
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
        return simulateBossEvent(event)
    }

    /** Any trigger event: boss events with a chat line go through chat, the rest fire straight into card matching. */
    fun simulateBossEvent(event: LeapTriggerEvent): String {
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

    /**
     * Runs a trigger card as a demo: the same leap arm and step queue as a real event, but without event
     * matching or floor/phase eligibility. A grouped card plans one member only (the one for the player's
     * class), so its leap arms once and its steps queue once. Called by the Test button after the config screen closes.
     */
    fun testCard(triggers: List<LeapOrientTrigger>) {
        if (triggers.isEmpty()) return
        val picked = LeapTriggerCards.pickTestMember(triggers, LeapRoster.selfClass(settings()))
        // Test follows the same if / else if / else rules as a real firing, with the state you have right now.
        val steps = LeapConditions.active(picked.steps, currentFacts())
        val member = if (steps.size == picked.steps.size) picked else picked.copy(steps = steps)
        if (member !== picked) tell("[icantpy] Conditions skip ${picked.steps.size - steps.size} step(s) right now")
        val plan = LeapTestPlanner.plan(member)
        if (plan.nothingToTest) {
            tell("[icantpy] Nothing to test")
            return
        }
        tell("[icantpy] Testing: ${member.title()}")
        runTestPlan(member, plan)
    }

    private fun runTestPlan(trigger: LeapOrientTrigger, plan: LeapTestPlan) {
        plan.skipped.forEach { AutoActionQueue.say("Auto step skipped: ${LeapAutoStep.problem(it)}") }
        if (plan.armLeap) armTestLeap(trigger)
        val key = LeapTriggerCards.cardKeyOf(trigger, settings().triggers)
        LeapMarkRouter.apply(plan.marks, key, System.currentTimeMillis())
        LeapAutoRouter.arm(LeapAutoRoute(plan.targets, plan.armedSorts, plan.skipped), key, inGameArmedSorts)
        if (plan.targets.isNotEmpty()) {
            AutoActionQueue.requestBatch(key, plan.targets, trigger.title(), bypassDedupe = true)
        }
    }

    /** Arms as if the player themself fired the trigger; a leap that needs a leaped-to player reports the miss. */
    private fun armTestLeap(trigger: LeapOrientTrigger) {
        val cfg = settings()
        val self = LeapRoster.selfName() ?: "you"
        val request = LeapOrientEngine.resolveArm(
            trigger = trigger,
            sender = self,
            senderClass = LeapRoster.selfClass(cfg),
            leaped = null,
            classOf = { classOf(it) },
            playerForClass = { clazz -> LeapRoster.playerForClass(clazz, LeapRoster.knownPlayers()) },
            context = routeContext(),
        )
        if (request == null) {
            tell("[icantpy] Test: no leap target for ${trigger.title()}")
            return
        }
        val armed = armRequest(request, self, trigger.title(), keywordHit = true)
        if (armed != null) LeapFollowUps.register(trigger, armed, request.targetName, request.targetClass)
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
                        steps = listOf(LeapAutoStep.leapTarget()),
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
        val expected = LeapOrientTargets.pick(LeapGameState.currentId(), cfg)?.centerTitle() ?: "none armed"
        flow("you leaped to $leaped (${clazz.displayName}); armed target was: $expected")
        val unlocked = LeapTriggerLockState.markCompleted(clazz)
        if (cfg.leapAnnounce && (DungeonListener.state.inDungeon || debugActive())) {
            val line = LeapChatPatterns.formatAnnounce(cfg.announceTemplate, leaped, clazz)
            lastAnnounce = line
            lastAnnounceAt = System.currentTimeMillis()
            IcantpyClientActions.sendCommand("pc $line")
        }
        LeapOrientTargets.clear()
        LeapOrientState.clear()
        handleFollowUps(leaped, clazz)
        if (debugActive()) tell("§7[leaporient] leaped to $leaped")
        val leapedMatch = LeapedChatMatch(leaped, clazz.takeIf { it.isReal() }, leaped)
        applyEvent(LeapTriggerEvent.SELF_LEAP, LeapRoster.selfName() ?: "you", leapedMatch)
        scheduleLeapChains(leapedMatch)
        unlocked.forEach { pending ->
            val current = cfg.triggers.firstOrNull { it.id == pending.trigger.id } ?: return@forEach
            applyTrigger(current, pending.event, pending.sender, pending.leaped, pending.message)
        }
        // Rows that waited on this leap on their own. They already passed their conditions when the card fired.
        LeapTriggerLockState.takeSteps(clazz).forEach { waiting ->
            val current = cfg.triggers.firstOrNull { it.id == waiting.trigger.id } ?: return@forEach
            val step = waiting.step.copy(requires = LeapDungeonClass.EMPTY, branch = LeapBranch.ALWAYS, conditions = emptyList())
            flow("${stepLabel(waiting.step)}: RELEASED, the leap to ${clazz.displayName} it waited for just happened (card '${current.title()}')")
            applyAutoSteps(current.copy(steps = listOf(step)), waiting.sender, waiting.message, keySuffix = "#after-${clazz.name}")
        }
        LeapTriggerLockState.waitingLines { stepLabel(it) }.let { lines ->
            if (lines.isNotEmpty()) flow("still waiting: " + lines.joinToString("; "))
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
            if (trigger.armLeap) armChainLeap(trigger, leaped, selfName, selfClass, ctx)
            // Chain cards fire on this very leap, so a row's requirement must be this leap, and its conditions are checked now.
            val steps = LeapConditions.active(trigger.steps, currentFacts()).filter {
                LeapAutoStepList.isLeapRow(it) || LeapTriggerLockState.isSatisfiedByLeap(it, leaped.asClass)
            }
            flow(
                "chain card '${trigger.title()}' fired by your leap to ${leaped.asClass?.displayName ?: leaped.rawTarget}; running " +
                    (steps.joinToString(" | ") { stepLabel(it) }.ifBlank { "no rows" }) +
                    " (${trigger.steps.size - steps.size} skipped by requirement or condition)",
            )
            applyAutoSteps(trigger.copy(steps = steps), selfName ?: "you", trigger.title())
        }
    }

    /** The delayed or immediate leap part of a leap chain card. Steps are queued by the caller. */
    private fun armChainLeap(
        trigger: LeapOrientTrigger,
        leaped: LeapedChatMatch,
        selfName: String?,
        selfClass: LeapDungeonClass,
        ctx: LeapRouteContext,
    ) {
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
            return
        }
        if (LeapOrientEngine.duplicateClassUnsafe(request.targetClass ?: LeapDungeonClass.EMPTY, ctx) &&
            request.targetName.isBlank()
        ) {
            if (debugActive()) record("chain", trigger.title(), true, false, "duplicate class needs player")
            return
        }
        val cfg = settings()
        val delayMs = trigger.chainDelaySeconds.coerceAtLeast(0) * 1000L
        val durationMs = request.hold.durationMs(cfg.durationSeconds)
        if (delayMs == 0L) {
            val armed = armRequest(request, "chain", request.reason, true, durationMs)
            if (armed != null) LeapFollowUps.register(trigger, armed, request.targetName, request.targetClass)
        } else {
            LeapChainScheduler.schedule(trigger, request, delayMs, durationMs)
        }
        if (debugActive()) {
            val delayLabel = if (trigger.chainDelaySeconds > 0) " in ${trigger.chainDelaySeconds}s" else " now"
            tell("§7[leaporient] chain → ${request.targetName.ifBlank { request.targetClass?.displayName ?: "?" }}$delayLabel")
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
            val armed = armRequest(
                pending.request,
                sender = "chain",
                message = pending.request.reason,
                keywordHit = true,
                durationMs = pending.durationMs,
            )
            if (armed != null) LeapFollowUps.register(trigger, armed, pending.request.targetName, pending.request.targetClass)
        }
    }

    private val healthWatches = HashMap<String, LeapHealthHysteresis>()

    /** Fires HEALTH_LOW triggers on the edge of their threshold. Health is read from the local player only. */
    private fun evaluateHealth() {
        val cfg = settings()
        val triggers = cfg.triggers.filter { it.enabled && it.event == LeapTriggerEvent.HEALTH_LOW }
        healthWatches.keys.retainAll(triggers.map { it.id }.toSet())
        if (triggers.isEmpty()) return
        if (!cfg.enabled && !debugActive()) return
        val player = Minecraft.getInstance().player
        val max = player?.maxHealth ?: 0f
        val ratio = if (player != null && player.isAlive && max > 0f && DungeonListener.state.inDungeon) {
            (player.health / max).toDouble()
        } else {
            null
        }
        val nowMs = System.currentTimeMillis()
        val ctx = routeContext()
        triggers.forEach { trigger ->
            val watch = healthWatches.getOrPut(trigger.id) { LeapHealthHysteresis() }
            if (ratio == null || !LeapOrientEngine.eligibility(trigger, LeapTriggerEvent.HEALTH_LOW, ctx).ok) {
                watch.rearm()
                return@forEach
            }
            if (watch.update(ratio, trigger.healthBelowPercent, nowMs)) {
                applyTrigger(
                    trigger,
                    LeapTriggerEvent.HEALTH_LOW,
                    LeapRoster.selfName() ?: "self",
                    null,
                    "Health below ${trigger.healthBelowPercent}%",
                )
            }
        }
    }

    private fun dispatchDragonEvents() {
        for (event in DragonController.events()) {
            val mapped = LeapDragonEvents.map(event) ?: continue
            applyEvent(mapped.event, mapped.color, null, mapped.message, source = "dragon")
        }
    }

    private fun dispatchBoss(event: LeapTriggerEvent, line: String, source: String) {
        if (debugActive()) tell("§8[icantpy debug] event ${event.name} from $source")
        LeapOrientTargets.dropIfPhaseChanged(bossTracker.phase)
        LeapBossDeathNotifier.record(event, line)
        applyEvent(event, "boss", null, source = source)
        if (event == LeapTriggerEvent.BOSS_NECRON_DEATH) applyEvent(LeapTriggerEvent.BOSS_P5, "boss", null, source = source)
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

    /**
     * Runs the enabled CHAT_MESSAGE cards whose match settings accept [parsed]. Each card keeps its own
     * cooldown. Not tied to a dungeon: chat cards work anywhere, like location and lobby events.
     * Returns how many cards were armed or queued.
     */
    private fun dispatchChatMessage(
        parsed: ParsedChat,
        line: String,
        now: Long,
        bypassCooldown: Boolean = false,
    ): Int {
        val cfg = settings()
        if (!cfg.enabled && !debugActive()) return 0
        val self = LeapRoster.selfName() ?: ""
        val ids = cfg.triggers
            .filter { it.enabled && it.event == LeapTriggerEvent.CHAT_MESSAGE }
            .filter {
                ChatMessageMatch.matches(
                    parsed, self, it.chatSender, it.chatPlayer, it.chatChannel, it.chatMode, it.chatText,
                )
            }
            .map { it.id }
            .toSet()
        if (ids.isEmpty()) return 0
        val gate: (LeapOrientTrigger) -> Boolean = { bypassCooldown || chatCooldown.ready(it.id, now, it.chatCooldownMs) }
        val fired = applyEventToCards(LeapTriggerEvent.CHAT_MESSAGE, parsed.sender, null, line, ids, gate)
        LeapEventLog.record(now, LeapTriggerEvent.CHAT_MESSAGE, parsed.sender, "chat", fired)
        return fired
    }

    /** Records every detected event for the debug log, then runs the cards that match it. */
    private fun applyEvent(
        event: LeapTriggerEvent,
        sender: String,
        leaped: LeapedChatMatch?,
        message: String = event.label(),
        source: String = "chat",
    ): Int {
        forwardDetectedEvent(event)
        val fired = applyEventToCards(event, sender, leaped, message)
        LeapEventLog.record(System.currentTimeMillis(), event, sender, source, fired)
        return fired
    }

    /**
     * Feeds detected events to the run features. Runs before and independent of card matching, so splits and
     * score keep working when the Spirit Leap toggle is off or no card matches.
     */
    private fun forwardDetectedEvent(event: LeapTriggerEvent) {
        when (event) {
            LeapTriggerEvent.P5_START, LeapTriggerEvent.BOSS_NECRON_DEATH -> DragonController.onP5Start()
            LeapTriggerEvent.RUN_END, LeapTriggerEvent.DUNGEON_LEFT -> {
                DragonController.onRunEnd()
                PlayerMarkers.clearAll()
            }
            else -> Unit
        }
        val floor = SkyblockLocation.snapshot.floor.takeIf { it != DungeonFloor.NONE } ?: DungeonListener.floor
        RunSplitsController.onEvent(event, floor.takeIf { it != DungeonFloor.NONE })
        ScoreTracker.onEvent(event)
    }

    /** Runs every enabled card for [event] whose filters pass. Returns how many cards were armed or queued. */
    private fun applyEventToCards(
        event: LeapTriggerEvent,
        sender: String,
        leaped: LeapedChatMatch?,
        message: String,
        onlyIds: Set<String>? = null,
        cooldownGate: ((LeapOrientTrigger) -> Boolean)? = null,
    ): Int {
        val cfg = settings()
        if (!cfg.enabled && !debugActive()) return 0
        val ctx = routeContext()
        val hits = cfg.triggers.map { LeapOrientEngine.eligibility(it, event, ctx) }
        if (hits.none { it.ok }) {
            val why = hits.firstOrNull { it.trigger.event == event }?.rejected ?: "no ${event.label()} trigger"
            if (debugActive()) record(sender, message, false, false, why)
            return 0
        }
        var armed = 0
        val candidates = mutableListOf<LeapOrientTrigger>()
        hits.filter { it.ok }.forEach { hit ->
            val trigger = hit.trigger
            if (onlyIds != null && trigger.id !in onlyIds) return@forEach
            if (trigger.id in LeapRoutePreset.necronFightIds() && !bossTracker.necronBersActive) return@forEach
            if (event == LeapTriggerEvent.LEAP_CHAIN) {
                return@forEach
            }
            if (event in LeapOrientEngine.NAME_FILTERED_EVENTS &&
                !LeapOrientEngine.namedMatchHits(trigger, sender, LeapRoster.selfName())
            ) {
                return@forEach
            }
            if (event in LeapOrientEngine.COLOR_FILTERED_EVENTS &&
                !LeapOrientEngine.namedMatchHits(trigger, sender, null)
            ) {
                return@forEach
            }
            if (event in LeapOrientEngine.VALUE_FILTERED_EVENTS &&
                !LeapOrientEngine.valueMatchHits(trigger, sender)
            ) {
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
            if (cooldownGate != null && !cooldownGate(trigger)) return@forEach
            candidates += trigger
        }
        // Grouped cards hold several member rows that share steps. Only one member per card acts, so the
        // leap arms once and the steps queue once per card.
        LeapTriggerCards.pickOnePerCard(candidates, cfg.triggers).forEach { trigger ->
            if (applyTrigger(trigger, event, sender, leaped, message)) armed += 1
        }
        if (armed == 0 && debugActive()) {
            record(sender, message, true, false, "${event.label()} did not match")
        }
        return armed
    }

    private fun applyTrigger(
        trigger: LeapOrientTrigger,
        event: LeapTriggerEvent,
        sender: String,
        leaped: LeapedChatMatch?,
        message: String,
    ): Boolean {
        val ctx = routeContext()
        val check = LeapOrientEngine.eligibility(trigger, event, ctx)
        if (!check.ok) {
            record(sender, message, true, false, check.rejected ?: "rejected")
            return false
        }
        if ((trigger.bossOnly || settings().bossOnlyTriggers) && !DungeonListener.state.inBoss && !debugActive()) {
            record(sender, message, true, false, "only in boss fight")
            return false
        }
        // If / else if / else rows are decided now, from the player's state at the moment the card fires.
        val steps = LeapConditions.active(trigger.steps, currentFacts())
        val live = if (steps.size == trigger.steps.size) trigger else trigger.copy(steps = steps)
        if (live !== trigger) {
            val dropped = trigger.steps.size - steps.size
            record(sender, message, true, false, "conditions skipped $dropped step${if (dropped == 1) "" else "s"}")
        }
        flow(
            "card '${trigger.title()}' fired by ${event.label()} (from $sender). Rows: " +
                live.steps.joinToString(" | ") { step ->
                    stepLabel(step) + step.requires.takeIf { it.isReal() }?.let { " [needs a leap to ${it.displayName}]" }.orEmpty()
                }.ifBlank { "none" },
        )
        // A locked leap waits as a whole card, so its auto steps also wait for the prerequisite leap.
        if (live.armLeap && !LeapTriggerLockState.isSatisfied(live)) {
            LeapTriggerLockState.defer(DeferredLeapTrigger(trigger, event, sender, leaped, message))
            flow("card '${trigger.title()}' is WAITING on a leap to ${trigger.requiresTargetClass.displayName} before it arms its target or runs any row")
            record(
                sender,
                message,
                true,
                false,
                "waiting for leap to ${trigger.requiresTargetClass.displayName}",
            )
            return true
        }
        if (live.armLeap) armLeapTarget(live, sender, leaped, message)
        // A row with its own requirement waits for that leap on its own; the other rows run now.
        // A card fired by my own leap only runs rows whose requirement is that leap; the others are not parked, or
        // they would fire later on a different leap of the same class.
        val byThisLeap = event == LeapTriggerEvent.SELF_LEAP && leaped != null
        val (ready, waiting) = live.steps.partition {
            LeapAutoStepList.isLeapRow(it) ||
                if (byThisLeap) LeapTriggerLockState.isSatisfiedByLeap(it, leaped.asClass) else !it.requires.isReal()
        }
        ready.forEach { step ->
            if (!LeapAutoStepList.isLeapRow(step)) flow("${stepLabel(step)}: no leap required, runs now")
        }
        waiting.forEach { step ->
            if (byThisLeap) {
                flow("${stepLabel(step)}: SKIPPED, this leap was to ${leaped?.asClass?.displayName ?: "an unknown class"} but the row needs ${step.requires.displayName}")
                record(sender, message, true, false, "${step.label(StatsArmor.presetLabels())} needs a leap to ${step.requires.displayName}")
                return@forEach
            }
            LeapTriggerLockState.deferStep(DeferredLeapStep(live, step, sender, message))
            flow("${stepLabel(step)}: WAITING for your next leap to ${step.requires.displayName} (Hypixel: runs right after you teleport to the ${step.requires.displayName})")
            record(sender, message, true, false, "${step.label(StatsArmor.presetLabels())} waits for leap to ${step.requires.displayName}")
        }
        applyAutoSteps(live.copy(steps = ready), sender, message)
        return true
    }

    /** What conditions can read right now. Unknown values stay null or empty so their terms are false. */
    private fun currentFacts(): LeapFacts {
        val ctx = routeContext()
        val player = Minecraft.getInstance().player
        val health = player?.takeIf { it.maxHealth > 0f }?.let { (it.health / it.maxHealth * 100f).toInt() }
        return LeapFacts(
            loadout = Loadout.equippedNumber(),
            selfClass = ctx.selfClass,
            partyClasses = ctx.knownPlayers.map { it.clazz }.filter { it.isReal() }.toSet(),
            leapedTo = LeapTriggerLockState.completed(),
            phase = ctx.phase,
            floor = ctx.floor,
            route = ctx.route,
            healthPercent = health,
        )
    }

    private fun armLeapTarget(
        trigger: LeapOrientTrigger,
        sender: String,
        leaped: LeapedChatMatch?,
        message: String,
    ) {
        val request = LeapOrientEngine.resolveArm(
            trigger = trigger,
            sender = sender,
            senderClass = classOf(sender),
            leaped = leaped,
            classOf = { classOf(it) },
            playerForClass = { clazz -> LeapRoster.playerForClass(clazz, LeapRoster.knownPlayers()) },
            context = routeContext(),
        ) ?: run {
            flow("leap target for '${trigger.title()}': NONE found (no player of the wanted class is known); nothing armed, so left-click leap has no target")
            record(sender, message, true, false, "target missing")
            return
        }
        if (LeapOrientEngine.duplicateClassUnsafe(request.targetClass ?: LeapDungeonClass.EMPTY, routeContext()) &&
            request.targetName.isBlank()
        ) {
            flow("leap target for '${trigger.title()}': two players share that class, refusing to guess")
            record(sender, message, true, false, "duplicate class needs player")
            return
        }
        flow("leap target for '${trigger.title()}': ${request.targetName.ifBlank { request.targetClass?.displayName ?: "?" }} (${request.targetClass?.displayName ?: "unknown class"}); left-click leap will go there")
        val armed = armRequest(request, sender, message, keywordHit = true) ?: return
        LeapFollowUps.register(trigger, armed, request.targetName, request.targetClass)
    }

    /** Resolves pending follow-ups against the leap just made, and arms the next follow-up of each match. */
    private fun handleFollowUps(leaped: String, clazz: LeapDungeonClass) {
        val leapedClass = clazz.takeIf { it.isReal() }
        LeapFollowUps.onLeap(leaped, leapedClass).forEach { outcome ->
            when (outcome) {
                is LeapFollowUpOutcome.Discard -> discardFollowUp(outcome.card, outcome.reason)
                is LeapFollowUpOutcome.Hit -> armFollowUp(outcome.pending, leaped, leapedClass)
            }
        }
    }

    /** Arms the next follow-up of [pending] through the normal arm path, then keeps the chain waiting on it. */
    private fun armFollowUp(pending: PendingFollowUp, leaped: String, leapedClass: LeapDungeonClass?) {
        val cfg = settings()
        val card = pending.card
        val ctx = routeContext()
        val request = LeapOrientEngine.resolveArm(
            trigger = LeapFollowUp.asLeapTrigger(card, pending.next),
            sender = LeapRoster.selfName() ?: "you",
            senderClass = LeapRoster.selfClass(cfg),
            leaped = LeapedChatMatch(leaped, leapedClass, leaped),
            classOf = { classOf(it) },
            playerForClass = { clazz -> LeapRoster.playerForClass(clazz, LeapRoster.knownPlayers()) },
            context = ctx,
        )
        if (request == null) {
            discardFollowUp(card, "target missing")
            return
        }
        if (LeapOrientEngine.duplicateClassUnsafe(request.targetClass ?: LeapDungeonClass.EMPTY, ctx) &&
            request.targetName.isBlank()
        ) {
            discardFollowUp(card, "duplicate class needs player")
            return
        }
        val armed = armRequest(request, "follow-up", card.title(), keywordHit = true)
        if (armed == null) {
            discardFollowUp(card, "refused by an active target")
            return
        }
        record("follow-up", card.title(), true, true, "follow-up armed: ${armed.centerTitle()}")
        LeapFollowUps.continueWith(pending, armed, request.targetName, request.targetClass)
    }

    private fun discardFollowUp(card: LeapOrientTrigger, reason: String) {
        record("follow-up", card.title(), true, false, "follow-up discarded: $reason")
    }

    /**
     * Queues the card's action rows as one ordered batch. The leap target row is applied instantly by
     * [applyTrigger], so only the other rows are queued. Rows without a complete target are reported
     * in chat and left out; the rest still run in order.
     */
    private fun applyAutoSteps(trigger: LeapOrientTrigger, sender: String, message: String, keySuffix: String = "") {
        val key = LeapTriggerCards.cardKeyOf(trigger, settings().triggers) + keySuffix
        // Mark rows are instant, like the leap target: they go on the world now and never enter the queue.
        val marks = LeapMarkRouter.route(trigger.steps)
        marks.skipped.forEach { AutoActionQueue.say("Auto step skipped: ${LeapAutoStep.problem(it)}") }
        LeapMarkRouter.apply(marks.marks, key, System.currentTimeMillis())
        val queueable = trigger.queueableSteps()
        if (queueable.isEmpty()) {
            if (!trigger.armLeap) {
                val detail = if (marks.marks.isNotEmpty()) "marked" else "no actions chosen"
                record(sender, message, true, marks.marks.isNotEmpty(), detail)
            }
            return
        }
        val route = LeapAutoRouter.route(queueable)
        route.skipped.forEach { AutoActionQueue.say("Auto step skipped: ${LeapAutoStep.problem(it)}") }
        // Armed sorts wait for the player to open inventory; the queued steps after them run now.
        LeapAutoRouter.arm(route, key, inGameArmedSorts)
        if (route.targets.isEmpty()) {
            val detail = if (route.armedSorts.isNotEmpty()) "armed: waiting for inventory" else "no runnable steps"
            record(sender, message, true, route.armedSorts.isNotEmpty(), detail)
            return
        }
        val queued = AutoActionQueue.requestBatch(key, route.targets, trigger.title())
        flow(
            if (queued) "queued now: ${trigger.stepLabels(StatsArmor.presetLabels()).joinToString(" -> ")} (Hypixel: each one sends its command, waits for its menu, then clicks)"
            else "NOT queued (already queued or repeated): ${trigger.stepLabels(StatsArmor.presetLabels()).joinToString(" -> ")}",
        )
        val detail = if (queued) trigger.stepLabels(StatsArmor.presetLabels()).joinToString(" → ") else "already queued or repeated"
        record(sender, message, true, queued, detail)
    }

    private val inGameArmedSorts = ArmedSortRequester { preset, key, armTimeoutMs, reopenBlockMs, lockUntilDone ->
        AutoActionQueue.requestArmedSort(preset, key, armTimeoutMs, reopenBlockMs, lockUntilDone)
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
        durationMs: Long = request.hold.durationMs(settings().durationSeconds),
    ): PendingLeap? {
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
            holdMode = request.hold.mode,
            locked = request.hold.locked,
        )
        val events = recordHoldEvents()
        if (armed == null) {
            val refusal = events.lastOrNull()?.describe() ?: "not armed"
            record(sender, message, keywordHit, false, refusal)
            return null
        }
        val lockName = request.targetName.ifBlank { request.targetClass?.displayName ?: request.sender }
        LeapOrientState.arm(lockName, durationMs)
        record(sender, message, keywordHit, true, "armed ${armed.centerTitle()} (${request.reason}) @ ${LeapGameState.currentId()}")
        if (debugActive()) {
            val hold = if (request.hold.mode == LeapHoldMode.TIMED) "${durationMs / 1000}s" else request.hold.mode.label()
            tell("§aLeap Orient: ${armed.centerTitle()} · ${armed.centerReason()} ($hold)")
        }
        return armed
    }

    /** Writes hold-machine events (armed, blocked by lock, fallback restored, expired) to the debug log. */
    private fun recordHoldEvents(): List<LeapHoldEvent> {
        val events = LeapOrientTargets.drainEvents()
        events.forEach { event ->
            val armedLike = event.kind == LeapHoldEventKind.ARMED ||
                event.kind == LeapHoldEventKind.FALLBACK_SET ||
                event.kind == LeapHoldEventKind.FALLBACK_RESTORED
            record("hold", event.title, false, armedLike, event.describe())
        }
        return events
    }

    private fun classOf(sender: String): LeapDungeonClass = LeapRoster.classOf(sender, settings())

    private var lastAutoLeapNote: String = ""

    /** Debug-only reason a left-click Spirit Leap did not auto leap; repeats of the same reason are dropped. */
    fun noteAutoLeap(detail: String) {
        if (!debugActive() || detail == lastAutoLeapNote) return
        lastAutoLeapNote = detail
        flow("left click: $detail")
    }

    /**
     * Debug narration of what the card pipeline did and what it is waiting on, including what the same step would do on
     * Hypixel. Printed to chat and the chat-debug HUD while leaporient debug is on.
     */
    fun flow(text: String) {
        if (!debugActive()) return
        LeapOrientChatDebug.record(
            LeapOrientChatDebug.Entry(
                sender = "flow",
                message = text,
                keywordHit = true,
                selfOk = true,
                senderOk = true,
                armed = true,
                detail = "",
            ),
        )
        tell("§b[flow] §f$text")
    }

    private fun stepLabel(step: LeapAutoStep): String = step.label(StatsArmor.presetLabels())

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

    fun isDebugLeapItem(stack: ItemStack): Boolean {
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
