package net.icantpy.qol.invsort

import net.icantpy.compat.McCompat
import net.icantpy.qol.auto.AutoActionQueue
import net.icantpy.qol.auto.CursorGuard
import net.icantpy.qol.auto.CursorSafety
import net.icantpy.gui.McUi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Runs one sort at a time. Clicks are the vanilla left click through [McUi.clickContainerSlot];
 * [InvSortRunState] decides which click comes next and [InvSortPacer] decides when the server has confirmed
 * enough of the previous ones for it to go ahead. Confirmation is checked on every tick, so a divergence is
 * always caught; how long the server must agree depends on the [CursorGuard.SafetyLevel]. A stray item on the
 * cursor is put back before the run ends, closes the screen or releases the input lock. Driven from the client tick.
 */
object InvSortRunner {
    /** Why the run is waiting: the next planned click, the final settle before finishing, or a put-back click. */
    private enum class Purpose { CLICK, FINISH, RECOVER }

    private class Job(
        val screen: Screen,
        val containerId: Int,
        val state: InvSortScript,
        val timing: InvSortTiming,
        val safety: CursorGuard.SafetyLevel,
        val closeWhenDone: Boolean,
        val presetIndex: Int,
        /** True when the inventory already matched the preset: no clicks, only the finish and close. */
        val alreadyMatched: Boolean,
        /** Hotbar swap runs: the hotbar targets the plan works toward; null for a full sort. */
        val hotbarTargets: Map<Int, String>? = null,
        /** Hotbar swap run started by a press: only the first swap is planned. */
        val oneShot: Boolean = false,
    ) {
        var closeAtNanos: Long? = null
        val pacer = InvSortPacer()
        var purpose = Purpose.CLICK

        /** Slot the item now on the cursor was picked up from, or null when the cursor is empty. */
        var pickedFrom: Int? = null

        /** Set once the run must stop: the put-back that follows ends in [endAbort] or [endLeftOpen]. */
        var recovery: CursorGuard.Recovery? = null
        var recoveryReason: String? = null
        var recoveryMessage: String? = null
    }

    private var job: Job? = null

    /** Reason the most recent run was cancelled with a message (Esc, click, changed inventory). Reset when a run starts. */
    var lastAbortReason: String? = null
        private set

    val isRunning: Boolean get() = job != null

    /** Index of the preset being sorted with, or null when no sort is running. */
    val runningPresetIndex: Int? get() = job?.presetIndex

    /**
     * Returns an error message, or null when the sort started (or the inventory already matched).
     * [perPress] marks a run started by a button or hotkey press: with hotbar swap and "one swap per press"
     * on, it performs only the next swap. Other runs (commands, triggers, auto queue) do the full sort.
     */
    fun start(presetIndex: Int, closeWhenDone: Boolean, perPress: Boolean = false): String? {
        if (job != null) return "An inventory sort is already running"
        lastAbortReason = null
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return "Not in a world"
        val screen = McUi.currentScreen(mc)
        if (screen == null || !InvSortLayout.isPlayerInventory(screen)) return "Open your inventory first"
        if (!player.containerMenu.carried.isEmpty) return "Put down the item on your cursor first"
        val settings = InvSortController.settings
        val preset = settings.presets.getOrNull(presetIndex) ?: return "That preset no longer exists"
        val menu = player.inventoryMenu
        val startKeys = InvSortItems.currentKeys(menu)
        val timing = InvSortTiming(settings.clickDelayMinMs, settings.clickDelayMaxMs, instant = settings.instant)
        val oneShot = preset.hotbarSwap && settings.swapOnePerPress && perPress
        val state: InvSortScript
        val hotbarTargets: Map<Int, String>?
        val alreadyMatched: Boolean
        if (preset.hotbarSwap) {
            val swaps = HotbarSwapRun.swapsFor(startKeys, preset.slots, oneShot)
            hotbarTargets = HotbarSwapRun.hotbarTargets(preset.slots)
            alreadyMatched = swaps.isEmpty()
            state = HotbarSwapRunState(startKeys, swaps, timing, settings.moveCursor, System.nanoTime(), settings.openDelayMs)
        } else {
            val clicks = InvSortPlanner.plan(startKeys, preset.slots.mapValues { it.value.key })
            hotbarTargets = null
            // A sort with nothing to move still runs as a job with no clicks, so it finishes and closes like any other.
            alreadyMatched = clicks.isEmpty()
            state = InvSortRunState(
                startKeys = startKeys,
                clicks = clicks,
                timing = timing,
                moveCursor = settings.moveCursor,
                startNanos = System.nanoTime(),
                openDelayMs = settings.openDelayMs,
            )
        }
        // Hotbar runs report their result in finish(), so only full sorts announce an empty plan here.
        if (alreadyMatched && hotbarTargets == null) InvSortItems.say("Inventory already matches ${preset.name}")
        job = Job(
            screen,
            player.containerMenu.containerId,
            state,
            timing,
            settings.safetyLevel,
            closeWhenDone,
            presetIndex,
            alreadyMatched,
            hotbarTargets,
            oneShot,
        )
        AutoActionQueue.markProgress()
        return null
    }

    fun tick() {
        val running = job ?: return
        val mc = Minecraft.getInstance()
        val now = System.nanoTime()
        running.closeAtNanos?.let { closeAt ->
            if (now >= closeAt) closeInventory()
            return
        }
        if (!stillOnScreen(mc, running)) {
            job = null
            InvSortItems.say("Inventory sort cancelled: the inventory was closed")
            return
        }
        running.pacer.beginTick()
        // Instant runs perform several clicks per tick; each one that needs no settle tick is followed at once.
        val budget = if (running.timing.instant) InvSortTiming.MAX_CLICKS_PER_TICK else 1
        repeat(budget) {
            if (job !== running) return
            if (!gate(mc, running, now)) return
            if (!advance(mc, running, now)) return
        }
    }

    /**
     * Decides whether the plan may act now. While a click is unconfirmed this waits; a divergence or timeout
     * starts the put-back. Returns true only when the next planned step may run.
     */
    private fun gate(mc: Minecraft, running: Job, now: Long): Boolean {
        val menu = mc.player?.containerMenu ?: return false
        val verdict = running.pacer.observe(now / 1_000_000L) { keys -> CursorSafety.snapshot(menu, keys) }
        if (verdict != null) {
            if (verdict == CursorGuard.Verdict.WAITING) return false
            val confirmed = verdict == CursorGuard.Verdict.SYNCED
            when {
                running.purpose == Purpose.RECOVER -> advanceRecovery(mc, running)
                confirmed && running.purpose == Purpose.CLICK -> return true
                confirmed -> finish(running, now)
                else -> startRecovery(mc, running, SERVER_REASON, SERVER_MESSAGE)
            }
            return false
        }
        if (running.recovery != null) {
            advanceRecovery(mc, running)
            return false
        }
        return true
    }

    /**
     * Performs the next due step. Returns true when the tick may continue with another step (a cursor move, or a
     * click after which the run is still going), false when this tick should stop: nothing is due yet or the run ended.
     */
    private fun advance(mc: Minecraft, running: Job, now: Long): Boolean {
        return when (val step = running.state.poll(now)) {
            null -> false
            is InvSortStep.MoveCursor -> {
                moveCursorToSlot(mc, running.screen, step.slot)
                true
            }
            is InvSortStep.Click -> {
                performClick(mc, running, step)
                job === running
            }
            is InvSortStep.Swap -> {
                performSwap(mc, running, step)
                job === running
            }
            InvSortStep.Finished -> {
                // Finishing needs the cursor empty and confirmed, like any other step.
                running.purpose = Purpose.FINISH
                running.pacer.arm(
                    CursorSafety.arm(
                        mc,
                        CursorGuard.Expectation(carried = null, slotKeys = emptyMap()),
                        now / 1_000_000L,
                        running.safety.closeSettle,
                    ),
                )
                false
            }
        }
    }

    /**
     * Aborts the current run. A non-null [reason] is shown in chat. If an item is on the cursor the run keeps
     * its input lock and puts the item back first; the abort message follows that.
     */
    fun cancel(reason: String?) {
        val running = job ?: return
        if (running.recovery != null) return
        val menu = Minecraft.getInstance().player?.containerMenu
        if (menu == null || menu.carried.isEmpty) {
            job = null
            if (reason != null) {
                lastAbortReason = reason
                InvSortItems.say("Inventory sort cancelled: $reason")
            }
            return
        }
        // Recovery clicks are made by the tick, not from the input event that requested the cancel.
        running.pacer.clear()
        running.recovery = newRecovery(running)
        running.recoveryReason = reason ?: "cancelled"
        running.recoveryMessage = reason?.let { "Inventory sort cancelled: $it" } ?: "Inventory sort cancelled"
        lastAbortReason = reason
    }

    private fun stillOnScreen(mc: Minecraft, running: Job): Boolean {
        val player = mc.player ?: return false
        return McUi.currentScreen(mc) === running.screen && player.containerMenu.containerId == running.containerId
    }

    private fun moveCursorToSlot(mc: Minecraft, screen: Screen, slot: Int) {
        val player = mc.player ?: return
        val menuSlot = player.containerMenu.slots.getOrNull(slot) ?: return
        val (guiX, guiY) = InvSortLayout.slotCenter(screen, menuSlot.x, menuSlot.y)
        McCompat.moveCursorToGuiPixel(mc, guiX, guiY)
        AutoActionQueue.pinCursor(guiX, guiY)
        AutoActionQueue.markProgress()
    }

    /**
     * Clicks only when the slot and cursor still match the plan, then arms the predicted state for
     * confirmation. The settle depends on the safety level: clicks inside a swap need no settle, a swap
     * that leaves the cursor empty is a checkpoint. A mismatch before the click means the inventory changed,
     * so the run is stopped and any held item is put back.
     */
    private fun performClick(mc: Minecraft, running: Job, step: InvSortStep.Click) {
        val player = mc.player ?: return
        val menu = player.containerMenu
        val slotKey = InvSortItems.keyAt(menu, step.slot)
        val carriedKey = InvSortItems.keyOf(menu.carried)
        if (slotKey != step.expectedSlotKey || carriedKey != step.expectedCarriedKey) {
            startRecovery(
                mc,
                running,
                "the inventory changed while sorting",
                "Inventory sort cancelled: the inventory changed while sorting",
            )
            return
        }
        val expectation = CursorGuard.expectAfterClick(carriedKey, slotKey, step.slot)
        McUi.clickContainerSlot(mc, running.containerId, step.slot)
        AutoActionQueue.markProgress()
        running.pickedFrom = when {
            expectation.carried == null -> null
            carriedKey == null -> step.slot
            else -> running.pickedFrom
        }
        running.purpose = Purpose.CLICK
        val settle = running.safety.clickSettle(endsEmpty = expectation.carried == null)
        running.pacer.arm(CursorSafety.arm(mc, expectation, System.nanoTime() / 1_000_000L, settle))
    }

    /**
     * One hotbar swap, only when the cursor is empty and both slots still hold the planned keys. The swap is a
     * checkpoint: its predicted state (both slots exchanged, cursor empty) must be confirmed before the next step.
     */
    private fun performSwap(mc: Minecraft, running: Job, step: InvSortStep.Swap) {
        val menu = mc.player?.containerMenu ?: return
        val hotbarSlot = InvSortSlots.HOTBAR_FIRST + step.hotbarIndex
        if (!menu.carried.isEmpty) {
            startRecovery(mc, running, "the cursor was not empty before a hotbar swap", SWAP_CURSOR_MESSAGE)
            return
        }
        val slotMatches = InvSortItems.keyAt(menu, step.slot) == step.expectedSlotKey
        val hotbarMatches = InvSortItems.keyAt(menu, hotbarSlot) == step.expectedHotbarKey
        if (!slotMatches || !hotbarMatches) {
            startRecovery(
                mc,
                running,
                "the inventory changed while sorting",
                "Inventory sort cancelled: the inventory changed while sorting",
            )
            return
        }
        McUi.swapWithHotbar(mc, running.containerId, step.slot, step.hotbarIndex)
        AutoActionQueue.markProgress()
        running.pickedFrom = null
        running.purpose = Purpose.CLICK
        val expectation = CursorGuard.Expectation(
            carried = null,
            slotKeys = mapOf(step.slot to step.expectedHotbarKey, hotbarSlot to step.expectedSlotKey),
        )
        val settle = running.safety.clickSettle(endsEmpty = true)
        running.pacer.arm(CursorSafety.arm(mc, expectation, System.nanoTime() / 1_000_000L, settle))
    }

    private fun newRecovery(running: Job): CursorGuard.Recovery =
        CursorGuard.Recovery(source = running.pickedFrom, candidates = CursorGuard.INVENTORY_SLOTS)

    /** Stops the run and puts the held item back. [reason] goes to the batch report, [message] to chat. */
    private fun startRecovery(mc: Minecraft, running: Job, reason: String, message: String) {
        running.pacer.clear()
        running.recovery = newRecovery(running)
        running.recoveryReason = reason
        running.recoveryMessage = message
        lastAbortReason = reason
        advanceRecovery(mc, running)
    }

    /** One put-back step: a click into an empty slot followed by confirmation, or the end of the recovery. */
    private fun advanceRecovery(mc: Minecraft, running: Job) {
        val recovery = running.recovery ?: return
        val menu = mc.player?.containerMenu
        if (menu == null) {
            job = null
            return
        }
        val isEmpty = { slot: Int -> menu.slots.getOrNull(slot)?.item?.isEmpty ?: false }
        when (val action = recovery.next(menu.carried.isEmpty, isEmpty)) {
            is CursorGuard.RecoverAction.Click -> {
                val carriedKey = InvSortItems.keyOf(menu.carried)
                McUi.clickContainerSlot(mc, running.containerId, action.slot)
                AutoActionQueue.markProgress()
                running.purpose = Purpose.RECOVER
                // Put-backs always get the full confirmation, whatever the safety level.
                running.pacer.arm(
                    CursorSafety.arm(
                        mc,
                        CursorGuard.expectAfterClick(carriedKey, null, action.slot),
                        System.nanoTime() / 1_000_000L,
                    ),
                )
            }
            CursorGuard.RecoverAction.Done -> endAbort(running)
            CursorGuard.RecoverAction.NoRoom -> endLeftOpen(running, NO_ROOM_MESSAGE)
            CursorGuard.RecoverAction.Exhausted -> endLeftOpen(running, EXHAUSTED_MESSAGE)
        }
    }

    /** The cursor is empty again: the run ends and the screen is left as it is. */
    private fun endAbort(running: Job) {
        job = null
        lastAbortReason = running.recoveryReason
        running.recoveryMessage?.let(InvSortItems::say)
    }

    /** The held item could not be put back: the run ends and the screen stays open for the player. */
    private fun endLeftOpen(running: Job, message: String) {
        job = null
        lastAbortReason = running.recoveryReason
        InvSortItems.say(message)
    }

    private fun finish(running: Job, now: Long) {
        AutoActionQueue.markProgress()
        val targets = running.hotbarTargets
        when {
            targets != null -> InvSortItems.say(hotbarResultMessage(running, targets))
            !running.alreadyMatched -> InvSortItems.say("Inventory sorted")
        }
        if (!running.closeWhenDone) {
            job = null
            return
        }
        val closeAt = now + InvSortTiming.millisToNanos(running.timing.sampleDelayMs())
        if (closeAt <= now) {
            closeInventory()
        } else {
            running.closeAtNanos = closeAt
        }
    }

    /** Result line for a hotbar run: a press reports the swaps still needed, a full hotbar sort just reports done. */
    private fun hotbarResultMessage(running: Job, targets: Map<Int, String>): String {
        val menu = Minecraft.getInstance().player?.containerMenu ?: return "Hotbar sorted"
        if (!running.oneShot) return "Hotbar sorted"
        val left = HotbarSwapPlanner.stepsRemaining(InvSortItems.currentKeys(menu), targets)
        return if (left == 0) "Hotbar: all swaps done" else "Hotbar: $left swaps left"
    }

    /** Closes like vanilla (Esc / inventory key). [CursorSafety.safeClose] refuses while an item is held. */
    private fun closeInventory() {
        job = null
        CursorSafety.safeClose(Minecraft.getInstance())
    }

    private const val SWAP_CURSOR_MESSAGE = "Hotbar swap stopped: an item was on your cursor — your items are safe"
    private const val SERVER_REASON = "server didn't confirm a move"
    private const val SERVER_MESSAGE = "Sort stopped: server didn't confirm a move — your items are safe"
    private const val NO_ROOM_MESSAGE =
        "Sort stopped: couldn't put the held item back (inventory full) — inventory left open, place it manually"
    private const val EXHAUSTED_MESSAGE =
        "Sort stopped: couldn't put the held item back — inventory left open, place it manually"
}
