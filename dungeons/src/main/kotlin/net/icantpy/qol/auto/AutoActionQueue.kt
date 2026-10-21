package net.icantpy.qol.auto

import net.icantpy.api.IcantpyClientActions
import net.icantpy.compat.McCompat
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.gui.McUi
import net.icantpy.qol.invsort.InvSortController
import net.icantpy.qol.invsort.InvSortLayout
import net.icantpy.qol.invsort.InvSortOpener
import net.icantpy.qol.invsort.InvSortRunner
import net.icantpy.qol.invsort.InvSortTiming
import net.icantpy.qol.loadout.Loadout
import net.icantpy.qol.loadout.LoadoutPlan
import net.icantpy.qol.loadout.LoadoutScreen
import net.icantpy.qol.stats.StatsArmorScreen
import net.icantpy.qol.stats.ArmorPreset
import net.icantpy.qol.stats.ItemFingerprint
import net.icantpy.qol.stats.StatsArmor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

/**
 * The single queue for every trigger-started action: inventory sort, Stats armor equip and loadout
 * switch. A trigger card's steps enter as one ordered batch; only the front step starts, and only when
 * nothing else is running. Timing decisions live in [AutoActionRules]. Driven from the client tick;
 * cleared on disconnect.
 */
object AutoActionQueue {
    /** A menu routine in progress and the batch its steps belong to. */
    private class ActiveRun(val run: AutoRun, val batchId: Long, val label: String)

    /** An inventory sort started from a batch. Its batch ends if the player cancels that sort. */
    private class ActiveSort(val batchId: Long)

    private val pending = AutoBatchQueue()
    private val armed = ArmedSortState()
    private val dedupe = AutoRequestDedupe()
    private val inFlight = AutoBatchInFlight()
    private var activeRun: ActiveRun? = null
    private var activeSort: ActiveSort? = null
    /** A GUI pixel the cursor is held on while a click is pending, and the screen it belongs to. */
    private class CursorPin(val guiX: Int, val guiY: Int, val screen: Any?)

    private var cursorPin: CursorPin? = null

    /** Last click or step change while a run was active. The watchdog measures stalls from this. */
    private var lastProgressMs = 0L

    /** Whether a batch held input on the previous tick, so the tick it ends on can be seen. */
    private var wasActive = false

    /** True while a multi-step menu routine runs. Custom menu screens must not intercept its menu. */
    fun isMenuRunActive(): Boolean = activeRun != null

    /** True while any queued routine or inventory sort is running or waiting to start. */
    fun isBusy(): Boolean = activeRun != null || InvSortController.isSortBusy()

    /**
     * True for the whole life of a batch: from the first step being queued until its last step finishes,
     * including reaction delays and waits for a menu to open. This is what input locking follows.
     */
    fun isAutoRunActive(): Boolean = AutoActionRules.holdsInput(
        activeRun = activeRun != null,
        activeSort = activeSort != null,
        sortBusy = InvSortController.isSortBusy(),
        waitingSteps = pending.size,
    )

    /**
     * Remembers the GUI pixel of the slot an auto run is moving to, so the cursor is held there until the
     * click. The pin belongs to the screen open now; it is dropped when that screen changes.
     */
    fun pinCursor(guiX: Int, guiY: Int) {
        cursorPin = CursorPin(guiX, guiY, McUi.currentScreen(Minecraft.getInstance()))
    }

    /** Ends the cursor hold once the pending click has been sent. */
    fun releaseCursorPin() {
        cursorPin = null
    }

    /**
     * Stops the running menu routine or inventory sort the way a player click or Esc always has.
     * The rest of its batch is dropped when the run reports its abort.
     */
    fun cancelRunning(reason: String) {
        activeRun?.run?.cancel(reason)
        InvSortRunner.cancel(reason)
    }

    /** Drops every step still waiting to start. Steps of a run in progress are left to [cancelRunning]. */
    fun dropWaiting(reason: String) {
        val dropped = pending.size
        pending.clear()
        InvSortController.cancelPendingRun()
        if (dropped > 0) say("Auto actions cancelled: $reason; $dropped step(s) dropped")
    }

    /**
     * Queues the steps of one trigger card, in order, as one batch. [triggerId] is the card key (every member
     * of a grouped card passes the same key, see LeapTriggerCards.cardKeyOf). Steps whose feature is off are
     * dropped with a chat line. A repeat of the key inside the dedupe window is ignored as a whole, unless
     * [bypassDedupe] is set (the Test buttons). An identical batch still waiting or running is refused even then.
     */
    fun requestBatch(triggerId: String, targets: List<AutoTarget>, reason: String, bypassDedupe: Boolean = false): Boolean {
        val nowMs = System.currentTimeMillis()
        val allowed = targets.filter { target ->
            val enabled = isEnabled(target)
            if (!enabled) say("${label(target.kind)} skipped: that feature is turned off")
            enabled
        }
        if (allowed.isEmpty()) return false
        inFlight.prune { pending.hasBatch(it) || activeRun?.batchId == it || activeSort?.batchId == it }
        if (inFlight.contains(triggerId, allowed)) return false
        if (!dedupe.acceptUnlessBypassed(BATCH_KEY_PREFIX + triggerId, nowMs, bypassDedupe)) return false
        if (!pending.hasRoom(allowed.size)) {
            say("Auto actions skipped: too many are queued")
            return false
        }
        val batchId = pending.enqueue(allowed, reason)
        inFlight.add(batchId, triggerId, allowed)
        return true
    }

    /**
     * Arms one inventory sort of [presetName]: it runs the next time the player opens their own inventory.
     * A newer request replaces the pending one. Returns false when Inventory Sort is off or the preset is unknown.
     * [armTimeoutMs] of 0 means no expiry. [reopenBlockMs] of 0 allows reopening right after the run.
     * [lockUntilDone] keeps the player from leaving the inventory until the run finishes.
     */
    fun requestArmedSort(
        presetName: String,
        cardKey: String,
        armTimeoutMs: Int,
        reopenBlockMs: Int,
        lockUntilDone: Boolean,
    ): Boolean {
        val settings = InvSortController.settings
        if (!settings.enabled) return false
        if (settings.presets.none { it.name.equals(presetName, ignoreCase = true) }) {
            say("Armed sort: preset '$presetName' not found")
            return false
        }
        val armedNow = armed.arm(
            nowMs = System.currentTimeMillis(),
            presetName = presetName,
            cardKey = cardKey,
            armTimeoutMs = armTimeoutMs,
            reopenBlockMs = reopenBlockMs,
            lockUntilDone = lockUntilDone,
            sortEnabled = settings.enabled,
        )
        if (armedNow) say("[icantpy] Sort armed: $presetName — open your inventory")
        return armedNow
    }

    /** True while an armed sort waits for the player to open their inventory. */
    fun armedSortPending(): Boolean = armed.hasRequest()

    /** Drops a pending armed sort, or stops the armed run that is in progress. */
    fun cancelArmedSort(reason: String) {
        val hadRequest = armed.cancelRequest()
        if (armed.isRunning()) InvSortRunner.cancel(reason)
        if (hadRequest) say("Armed sort cancelled: $reason")
    }

    /** True while an armed run that asked for lockUntilDone is active. The input gate forces the lock on then. */
    fun armedSortLocksInput(): Boolean = armed.lockActive()

    fun tick() {
        val nowMs = System.currentTimeMillis()
        holdCursorOnTarget()
        watchSort()
        tickArmedSort(nowMs)
        watchdog(nowMs)
        val running = activeRun
        var finishedRun = false
        if (running != null) {
            if (running.run.tick(nowMs)) {
                finishRun(running)
                finishedRun = true
            }
        } else {
            startNextStep(nowMs)
        }
        if (AutoActionRules.chainsSameTick(instantMode(), finishedRun)) startNextStep(nowMs)
        closeAtBatchEnd()
    }

    /**
     * Armed sort bookkeeping, once per tick: expiry, starting the request when the player's inventory is open and
     * nothing else runs, detecting the end of the armed run and enforcing the reopen window.
     */
    private fun tickArmedSort(nowMs: Long) {
        if (armed.takeExpired(nowMs)) say("Armed sort expired")
        if (armed.isRunning() && !isBusy()) armed.runFinished(nowMs)
        val mc = Minecraft.getInstance()
        val inventoryOpen = InvSortLayout.isPlayerInventory(McUi.currentScreen(mc))
        val request = armed.startIfReady(nowMs, inventoryOpen, otherRunActive = isBusy())
        if (request != null) startArmedSort(request, nowMs)
        val check = armed.reopenCheck(nowMs, inventoryOpen)
        if (check.close) McUi.setScreen(mc, null)
        check.hintSeconds?.let { say("Inventory locked for ${it}s") }
    }

    /** Queues the armed preset as a normal sort that closes the inventory when done. */
    private fun startArmedSort(request: ArmedSortState.Request, nowMs: Long) {
        val index = InvSortController.settings.presets.indexOfFirst { it.name.equals(request.presetName, ignoreCase = true) }
        if (index < 0) {
            say("Armed sort: preset '${request.presetName}' no longer exists")
            armed.runFinished(nowMs)
            return
        }
        lastProgressMs = nowMs
        InvSortController.queueRun(index, closeWhenDone = true)
    }

    /** Records progress on every tick a run is active, and stops a run that has made none for [AutoActionRules.STALL_LIMIT_MS]. */
    private fun watchdog(nowMs: Long) {
        if (activeRun == null && activeSort == null) {
            lastProgressMs = nowMs
            return
        }
        if (AutoActionRules.isStalled(running = true, lastProgressMs = lastProgressMs, nowMs = nowMs)) {
            abortStalled(nowMs)
        }
    }

    /** Stops everything the batch is doing without waiting for a further click. A held item is put back by the run that holds it. */
    private fun abortStalled(nowMs: Long) {
        lastProgressMs = nowMs
        pending.clear()
        InvSortController.cancelPendingRun()
        activeRun?.run?.cancel(STALL_REASON)
        cursorPin = null
        // Silent cancel: the sort runner puts a held item back itself, then the lock follows it.
        if (activeSort != null) InvSortRunner.cancel(reason = null)
        // With an empty cursor the routine is dropped now, so the lock releases this tick. With an item held
        // the routine keeps driving its own put-back and finishes on its own.
        if (!cursorHoldsItem()) activeRun = null
        activeSort = null
        say(STALL_MESSAGE)
    }

    private fun startNextStep(nowMs: Long) {
        val head = pending.peek() ?: return
        val busy = isBusy()
        if (!busy && head.waitStartMs < 0L) {
            head.waitStartMs = nowMs
            val sampled = InvSortTiming(AutoActionRules.REACTION_MIN_MS, AutoActionRules.REACTION_MAX_MS).sampleDelayMs().toLong()
            head.reactionDelayMs = AutoActionRules.reactionDelayMs(instantMode(), sampled)
        }
        val elapsedMs = if (head.waitStartMs < 0L) 0L else nowMs - head.waitStartMs
        val kind = head.target.kind
        when (AutoActionRules.decide(kind, elapsedMs, head.reactionDelayMs, screenState(), busy)) {
            AutoDecision.WAIT -> Unit
            // A skipped step does not stop its batch: the next step is considered on a later tick.
            AutoDecision.GIVE_UP -> {
                pending.pop()
                say("${label(kind)} skipped: a screen stayed open")
            }
            AutoDecision.START -> {
                pending.pop()
                start(head, nowMs)
            }
        }
    }

    /**
     * Runs on the tick the batch stops holding input (last step finished, aborted, timed out or skipped).
     * Closes the screen the auto system uses (inventory or Stats/Loadout menu), also when it was open before.
     * [CursorSafety.safeClose] refuses and says so while an item is still on the cursor.
     */
    private fun closeAtBatchEnd() {
        val nowActive = isAutoRunActive()
        val ended = AutoActionRules.batchEnded(wasActive, nowActive)
        wasActive = nowActive
        if (!ended) return
        val mc = Minecraft.getInstance()
        val screen = McUi.currentScreen(mc) ?: return
        if (AutoActionRules.closesAtBatchEnd(ownedScreen = ownsScreen(screen), batchEnded = ended)) {
            CursorSafety.safeClose(mc)
        }
    }

    /** The inventory, or a Stats/Loadout menu. Other screens belong to the player and are never closed here. */
    private fun ownsScreen(screen: Screen): Boolean {
        if (InvSortLayout.isPlayerInventory(screen)) return true
        if (screen is LoadoutScreen || screen is StatsArmorScreen) return true
        val menu = screen as? AbstractContainerScreen<*> ?: return false
        val title = menu.title.string
        return StatsArmor.isStatsTitle(title) || Loadout.isLoadoutWindowTitle(title)
    }

    private fun cursorHoldsItem(): Boolean {
        val menu = Minecraft.getInstance().player?.containerMenu ?: return false
        return !menu.carried.isEmpty
    }

    /** Called by the runners on every click or put-back, so the watchdog sees progress. */
    fun markProgress() {
        lastProgressMs = System.currentTimeMillis()
    }

    /**
     * With the input lock on and moveCursor on, re-pins the cursor to the current target slot every tick so
     * the player's mouse movement does not visibly fight the run. Drops the pin once nothing holds input.
     */
    private fun holdCursorOnTarget() {
        val pin = cursorPin ?: return
        val screen = McUi.currentScreen(Minecraft.getInstance())
        val hold = AutoActionRules.holdsCursorPin(
            screenOwnsPin = screen === pin.screen,
            runActive = isAutoRunActive(),
            lockInput = InvSortController.settings.lockInput,
        )
        if (!hold) {
            cursorPin = null
            return
        }
        McCompat.moveCursorToGuiPixel(Minecraft.getInstance(), pin.guiX, pin.guiY)
    }

    fun clear() {
        armed.clear()
        activeRun = null
        activeSort = null
        cursorPin = null
        wasActive = false
        lastProgressMs = 0L
        pending.clear()
        dedupe.clear()
        inFlight.clear()
    }

    /** Chat line for auto actions, shown to the local player only. */
    fun say(text: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(text))
    }

    private fun isEnabled(target: AutoTarget): Boolean = when (target) {
        is AutoTarget.Sort -> InvSortController.settings.enabled
        is AutoTarget.Stats -> TickTimers.settings.statsArmor.enabled
        is AutoTarget.Loadout -> true
    }

    private fun start(step: QueuedAutoStep, nowMs: Long) {
        lastProgressMs = nowMs
        when (val target = step.target) {
            is AutoTarget.Sort -> startSort(target.presetName, step.batchId)
            is AutoTarget.Stats -> startStats(target.presetId, step.batchId, nowMs)
            is AutoTarget.Loadout -> startLoadout(target.number, step.batchId, nowMs)
        }
    }

    /**
     * Called right after the inventory sort ticks. In instant mode a sort that just finished hands over to the
     * next queued step in the same tick; the batch-end close is checked here too.
     */
    fun chainAfterSort() {
        if (!AutoActionRules.chainsSameTick(instantMode(), previousFinished = activeRun == null)) return
        val nowMs = System.currentTimeMillis()
        watchSort()
        startNextStep(nowMs)
        closeAtBatchEnd()
    }

    private fun instantMode(): Boolean = InvSortController.settings.instant

    /** Runs the saved preset through the inventory sorter, opening the inventory first when needed. */
    private fun startSort(presetName: String, batchId: Long) {
        val settings = InvSortController.settings
        val index = settings.presets.indexOfFirst { it.name.equals(presetName, ignoreCase = true) }
        if (index < 0) {
            say("Auto sort: preset '$presetName' not found")
            return
        }
        if (screenState() != AutoScreenState.PLAYER_INVENTORY && !InvSortOpener.openPlayerInventory(Minecraft.getInstance())) return
        // Auto sorts always close the inventory when they finish, including one that was already open.
        InvSortController.queueRun(index, closeWhenDone = true)
        activeSort = ActiveSort(batchId)
    }

    private fun startStats(presetId: String, batchId: Long, nowMs: Long) {
        val stats = TickTimers.settings.statsArmor
        val preset = stats.presets.firstOrNull { it.id == presetId }
        if (preset == null) {
            say("Auto stats: that armor preset is no longer saved")
            return
        }
        if (isWearing(preset)) return
        if (!canSendCommands()) return
        val run = StatsAutoRun(preset, pacing(), InvSortController.settings.moveCursor, InvSortController.settings.safetyLevel, nowMs)
        activeRun = ActiveRun(run, batchId, label(AutoActionKind.EQUIP_STATS))
        IcantpyClientActions.sendCommand("stats")
    }

    private fun startLoadout(number: Int, batchId: Long, nowMs: Long) {
        if (number !in 1..LoadoutPlan.MAX_LOADOUT) {
            say("Auto loadout: Loadout $number does not exist")
            return
        }
        if (!canSendCommands()) return
        // Already equipped: the step counts as finished and the batch moves on to its next step.
        if (Loadout.skipIfAlreadyEquipped(number)) return
        val run = LoadoutAutoRun(number, pacing(), InvSortController.settings.moveCursor, InvSortController.settings.safetyLevel, nowMs)
        activeRun = ActiveRun(run, batchId, label(AutoActionKind.SWITCH_LOADOUT))
        IcantpyClientActions.sendCommand("loadout")
    }

    private fun finishRun(active: ActiveRun) {
        activeRun = null
        cursorPin = null
        lastProgressMs = System.currentTimeMillis()
        active.run.abortReason?.let { abortBatch(active.batchId, active.label, it) }
    }

    /** Ends a batch whose sort the player cancelled. */
    private fun watchSort() {
        val sort = activeSort ?: return
        if (InvSortController.isSortBusy()) return
        activeSort = null
        InvSortRunner.lastAbortReason?.let { abortBatch(sort.batchId, label(AutoActionKind.SORT_INVENTORY), it) }
    }

    /** Drops the rest of [batchId] and reports the cancellation in one chat line. */
    private fun abortBatch(batchId: Long, label: String, reason: String) {
        val dropped = pending.removeBatch(batchId)
        val tail = when (dropped) {
            0 -> ""
            1 -> "; 1 more step skipped"
            else -> "; $dropped more steps skipped"
        }
        say("$label cancelled: $reason$tail")
    }

    private fun isWearing(preset: ArmorPreset): Boolean {
        val worn = StatsArmor.currentArmor(preset.armorSlot) ?: return false
        return !worn.isEmpty && preset.fingerprint().matches(ItemFingerprint.fromStack(worn))
    }

    private fun canSendCommands(): Boolean {
        val mc = Minecraft.getInstance()
        return mc.player != null && mc.connection != null
    }

    private fun pacing(): InvSortTiming {
        val settings = InvSortController.settings
        return InvSortTiming(settings.clickDelayMinMs, settings.clickDelayMaxMs, instant = settings.instant)
    }

    private fun screenState(): AutoScreenState {
        val screen = McUi.currentScreen(Minecraft.getInstance()) ?: return AutoScreenState.NONE
        return if (InvSortLayout.isPlayerInventory(screen)) AutoScreenState.PLAYER_INVENTORY else AutoScreenState.OTHER
    }

    private fun label(kind: AutoActionKind): String = when (kind) {
        AutoActionKind.SORT_INVENTORY -> "Auto sort"
        AutoActionKind.EQUIP_STATS -> "Auto stats"
        AutoActionKind.SWITCH_LOADOUT -> "Auto loadout"
    }

    private const val BATCH_KEY_PREFIX = "batch:"
    private const val STALL_REASON = "no progress"
    private const val STALL_MESSAGE = "Auto action timed out — stopped safely"
}
