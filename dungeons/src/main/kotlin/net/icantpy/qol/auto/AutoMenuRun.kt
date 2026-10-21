package net.icantpy.qol.auto

import net.icantpy.Icantpy
import net.icantpy.compat.McCompat
import net.icantpy.gui.McUi
import net.icantpy.qol.auto.AutoMenuPacing.StepOutcome
import net.icantpy.qol.invsort.InvSortItems
import net.icantpy.qol.invsort.InvSortLayout
import net.icantpy.qol.invsort.InvSortPacer
import net.icantpy.qol.invsort.InvSortTiming
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

/** A menu action; returns true when the whole run has finished. */
typealias MenuAction = (AbstractContainerScreen<*>, Long) -> Boolean

/**
 * Drives one command-opened container the way a player would: a click waits for the server to confirm
 * the predicted state at the checkpoint of its [CursorGuard.SafetyLevel], and the menu closes only after
 * a close checkpoint. Non-instant runs add a random delay per step and one tick before a click when the
 * cursor moves; instant runs click the tick the slot is populated, and chain steps within the tick.
 * Any unexpected change ends the run with a chat message, and a held item is put back first.
 */
abstract class AutoMenuRun(
    private val label: String,
    private val isMenuTitle: (String) -> Boolean,
    private val openDeadlineMs: Long,
    private val timing: InvSortTiming,
    private val moveCursor: Boolean,
    private val safety: CursorGuard.SafetyLevel,
) : AutoRun {
    private class Step(val dueMs: Long, val action: MenuAction)

    private var step: Step? = null
    private var screen: AbstractContainerScreen<*>? = null
    private var cancelReason: String? = null
    private var closedBeforeClick = false
    private var lastNowMs = 0L
    private var clicked = false
    private var lastClickSlot: Int? = null

    /**
     * Ghost tracking. After a click on a container slot the vanilla client may show the clicked item on the
     * cursor until the server answers; [ghostArmed] says such a click happened, [clickedKey] is that item.
     */
    private var ghostArmed = false
    private var clickedKey: String? = null
    private var ghostSinceMs: Long? = null

    /** Id of the menu the last click went to, and whether the terminal click has been sent. */
    private var clickedContainerId: Int? = null
    private var awaitingClose = false

    /** Set by a subclass when its last click is sent; the run then keeps a post-click window once it ends. */
    private var windowRequested = false
    private var postClick: PostClickCloseWindow? = null

    /** When the current wait for a populated slot began, or null when no wait is running. */
    private var populateSinceMs: Long? = null

    /** Confirmations of clicks and the close checkpoint. */
    private val pacer = InvSortPacer()

    /** Put-back in progress after an unexpected cursor state. [recoveryMessage] is shown when it ends, if set. */
    private var recovery: CursorGuard.Recovery? = null
    private var recoveryMessage: String? = null

    /** True when the run has no random delays; see [AutoMenuPacing]. */
    protected val instant: Boolean get() = timing.instant

    override val abortReason: String?
        get() = cancelReason ?: CLOSED_MESSAGE.takeIf { closedBeforeClick }

    /** Called once when the menu first appears; schedule the first step from here. */
    protected abstract fun onOpened(screen: AbstractContainerScreen<*>)

    override fun cancel(reason: String) {
        if (cancelReason == null) cancelReason = reason
    }

    override fun tick(nowMs: Long): Boolean {
        lastNowMs = nowMs
        pacer.beginTick()
        postClick?.takeIf { it.isActive }?.let { return tickPostClick(it, nowMs) }
        val ended = AutoMenuPacing.runSteps(instant) { tickOnce(nowMs) }
        // The run is done with its clicks: keep watching for a menu the server re-sends after the last click.
        if (ended && windowRequested && cancelReason == null && recovery == null) {
            windowRequested = false
            postClick = PostClickCloseWindow().also { it.begin(nowMs) }
            AutoActionQueue.markProgress()
            return false
        }
        return ended
    }

    /**
     * Post-click window: a run menu on screen (vanilla or custom) is closed again, and the run ends only after
     * the window's quiet period. Nothing here sends a click.
     */
    private fun tickPostClick(window: PostClickCloseWindow, nowMs: Long): Boolean {
        val mc = Minecraft.getInstance()
        val shown = McUi.currentScreen(mc)
        val menuOpen = shown != null && (isCustomMenuScreen(shown) ||
            (shown is AbstractContainerScreen<*> && isWindowTitle(shown.title.string)))
        val holdsItem = mc.player?.containerMenu?.carried?.isEmpty == false
        return when (window.observe(nowMs, menuOpen, holdsItem)) {
            PostClickCloseWindow.Action.WAIT -> false
            PostClickCloseWindow.Action.CLOSE -> {
                CursorSafety.safeClose(mc)
                if (shown != null && McUi.currentScreen(mc) === shown) McUi.setScreen(mc, null)
                window.closed()
                AutoActionQueue.markProgress()
                false
            }
            PostClickCloseWindow.Action.DONE -> {
                postClick = null
                if (window.closes > 0) {
                    Icantpy.LOGGER.info("[icantpy] ${label.lowercase()}: closed menu after click (reopened=${window.closes})")
                }
                onWindowEnded()
                true
            }
        }
    }

    /** Asks for the post-click window: call it from the click that ends the run's menu work. */
    protected fun requestPostClickWindow() {
        windowRequested = true
    }

    /** Title match used during the post-click window; may be wider than [isMenuTitle]. */
    protected open fun isWindowTitle(title: String): Boolean = isMenuTitle(title)

    /** A custom screen that stands in for this run's menu during the window (not an AbstractContainerScreen). */
    protected open fun isCustomMenuScreen(screen: Screen): Boolean = false

    /** Called once the post-click window has ended. */
    protected open fun onWindowEnded() {}

    private fun tickOnce(nowMs: Long): StepOutcome {
        val current = currentMenuScreen()
        if (recovery != null) return if (driveRecovery(current, nowMs)) StepOutcome.ENDED else StepOutcome.WAITING
        // A cancelled run ends silently; AutoActionQueue reports it once, with any dropped steps.
        // A held item is put back first, so the cancel waits for that when needed.
        if (cancelReason != null) return cancelStep(current)
        if (screen == null) return waitForOpen(current, nowMs)
        // The server closed or replaced the menu after the terminal click: the run is done, no more clicks.
        if (current == null || (awaitingClose && menuReplaced(current))) {
            return if (finishAfterClose()) StepOutcome.ENDED else StepOutcome.WAITING
        }
        screen = current
        when (checkSync(current.menu, nowMs)) {
            CursorGuard.Verdict.WAITING -> return StepOutcome.WAITING
            CursorGuard.Verdict.DIVERGED, CursorGuard.Verdict.TIMED_OUT -> {
                // A ghost is never placed by recovery: the server clears it, or the menu is closed over it.
                if (isGhostHeld(current.menu)) return deferToGhost()
                startRecovery(current.menu, lastClickSlot, SERVER_REASON, serverMessage())
                return if (driveRecovery(current, nowMs)) StepOutcome.ENDED else StepOutcome.WAITING
            }
            CursorGuard.Verdict.SYNCED, null -> Unit
        }
        val due = step ?: return StepOutcome.WAITING
        if (nowMs < due.dueMs) return StepOutcome.WAITING
        step = null
        return if (due.action(current, nowMs)) StepOutcome.ENDED else StepOutcome.PROGRESSED
    }

    /** A cancelled run puts a real held item back; a ghost is closed over, since the server's cursor is empty. */
    private fun cancelStep(current: AbstractContainerScreen<*>?): StepOutcome {
        if (current == null || current.menu.carried.isEmpty) return StepOutcome.ENDED
        if (isGhostHeld(current.menu)) {
            CursorSafety.safeCloseMenu(Minecraft.getInstance(), ghostKeysOf(current.menu))
            return StepOutcome.ENDED
        }
        startRecovery(current.menu, lastClickSlot, cancelReason.orEmpty(), message = null)
        return if (driveRecovery(current, lastNowMs)) StepOutcome.ENDED else StepOutcome.WAITING
    }

    /** True when the menu on screen is not the one the last click went to (the server opened a new one). */
    private fun menuReplaced(current: AbstractContainerScreen<*>): Boolean =
        CursorGuard.menuReplacedAfterClick(clickedContainerId, current.menu.containerId)

    /** True when the item on the cursor is a ghost of an automated container click (see [CursorGuard.isGhost]). */
    private fun isGhostHeld(menu: AbstractContainerMenu): Boolean =
        ghostArmed && CursorGuard.isGhost(InvSortItems.keyOf(menu.carried), clickedKey, CursorSafety.containerKeys(menu))

    private fun ghostKeysOf(menu: AbstractContainerMenu): Set<String> =
        CursorSafety.containerKeys(menu).toSet() + listOfNotNull(clickedKey)

    /** Lets the server clear the ghost on a later step, then closes over it (see [waitForGhostThenClose]). */
    private fun deferToGhost(): StepOutcome {
        pacer.clear()
        schedule(0L) { again, later -> waitForGhostThenClose(again, later) }
        return StepOutcome.PROGRESSED
    }

    /**
     * Waits up to [CursorGuard.GHOST_WAIT_MS] for the server to clear a ghost, then closes the menu over it.
     * Once the cursor is empty or holds a real item, the normal close or put-back path takes over.
     */
    private fun waitForGhostThenClose(current: AbstractContainerScreen<*>, nowMs: Long): Boolean {
        val menu = current.menu
        val ghost = isGhostHeld(menu)
        val since = if (ghost) ghostSinceMs ?: nowMs.also { ghostSinceMs = it } else nowMs
        when (CursorGuard.planForHeldItem(held = !menu.carried.isEmpty, ghost = ghost, waitedMs = nowMs - since)) {
            CursorGuard.HeldItemPlan.WAIT_FOR_SERVER -> {
                schedule(AutoMenuPacing.pollMs(instant, POLL_MS)) { again, later -> waitForGhostThenClose(again, later) }
                return false
            }
            CursorGuard.HeldItemPlan.CLOSE_OVER_GHOST -> {
                ghostSinceMs = null
                // The ghost keys are the ones isGhostHeld matched, so the close is never refused here.
                CursorSafety.safeCloseMenu(Minecraft.getInstance(), ghostKeysOf(menu))
                return true
            }
            CursorGuard.HeldItemPlan.CLOSE_NOW, CursorGuard.HeldItemPlan.RECOVER -> {
                ghostSinceMs = null
                return closeMenu(current)
            }
        }
    }

    private fun waitForOpen(current: AbstractContainerScreen<*>?, nowMs: Long): StepOutcome {
        if (current != null) {
            screen = current
            AutoMenuInput.attach(current)
            AutoActionQueue.markProgress()
            onOpened(current)
            return StepOutcome.PROGRESSED
        }
        if (nowMs < openDeadlineMs) return StepOutcome.WAITING
        say("$label: menu did not open")
        return StepOutcome.ENDED
    }

    private fun finishAfterClose(): Boolean {
        closedBeforeClick = !clicked
        return true
    }

    protected fun schedule(delayMs: Long, action: MenuAction) {
        step = Step(lastNowMs + delayMs, action)
    }

    protected fun sampleDelayMs(): Long = timing.sampleDelayMs().toLong()

    /**
     * Presses the slot returned by [resolve] after a random click delay, then runs [next].
     * When nothing resolves, [missing] is shown in chat and the menu is closed. In instant mode a slot that
     * is not populated yet is awaited for a short time. A cursor-carried stack cancels the run instead,
     * because a click would drop or swap it.
     */
    protected fun pressSlot(missing: String, resolve: (AbstractContainerMenu) -> Slot?, next: MenuAction) {
        schedule(sampleDelayMs()) { current, nowMs -> attemptPress(current, nowMs, missing, resolve, next) }
    }

    private fun attemptPress(
        current: AbstractContainerScreen<*>,
        nowMs: Long,
        missing: String,
        resolve: (AbstractContainerMenu) -> Slot?,
        next: MenuAction,
    ): Boolean {
        if (isGhostHeld(current.menu)) return retryWhileGhost(nowMs) { again, later ->
            attemptPress(again, later, missing, resolve, next)
        }
        ghostSinceMs = null
        if (!current.menu.carried.isEmpty) {
            cancel("an item is on your cursor")
            return false
        }
        val slot = resolve(current.menu)
        if (slot == null) {
            val since = populateSinceMs ?: nowMs.also { populateSinceMs = it }
            if (AutoMenuPacing.keepWaitingForPopulate(instant, nowMs - since)) {
                schedule(0L) { again, later -> attemptPress(again, later, missing, resolve, next) }
                return false
            }
            say("$label: $missing")
            return closeMenu(current)
        }
        populateSinceMs = null
        clickOrMove(current, slot, next)
        return false
    }

    /**
     * Re-runs [retry] on a later tick while a ghost is held, so no click lands on a cursor that holds an item.
     * When the ghost outlasts [CursorGuard.GHOST_WAIT_MS] the run is cancelled, and the cancel closes over it.
     */
    private fun retryWhileGhost(nowMs: Long, retry: MenuAction): Boolean {
        val since = ghostSinceMs ?: nowMs.also { ghostSinceMs = it }
        if (nowMs - since >= CursorGuard.GHOST_WAIT_MS) {
            ghostSinceMs = null
            cancel("an item is on your cursor")
            return false
        }
        schedule(AutoMenuPacing.pollMs(instant, POLL_MS)) { again, later -> retry(again, later) }
        return false
    }

    private fun clickOrMove(current: AbstractContainerScreen<*>, slot: Slot, next: MenuAction) {
        if (!moveCursor) {
            clickNow(current, slot, next)
            return
        }
        val (guiX, guiY) = InvSortLayout.slotCenter(current, slot.x, slot.y)
        McCompat.moveCursorToGuiPixel(Minecraft.getInstance(), guiX, guiY)
        AutoActionQueue.pinCursor(guiX, guiY)
        val settle = AutoMenuPacing.settleBeforeClickMs(instant, SETTLE_MS)
        if (settle == 0L) {
            clickNow(current, slot, next)
            return
        }
        schedule(settle) { menu, _ ->
            clickNow(menu, slot, next)
            false
        }
    }

    private fun clickNow(current: AbstractContainerScreen<*>, slot: Slot, next: MenuAction) {
        val mc = Minecraft.getInstance()
        val menu = current.menu
        val carriedKey = InvSortItems.keyOf(menu.carried)
        val slotKey = InvSortItems.keyAt(menu, slot.index)
        McUi.clickContainerSlot(mc, menu.containerId, slot.index)
        AutoActionQueue.markProgress()
        AutoActionQueue.releaseCursorPin()
        clicked = true
        lastClickSlot = slot.index
        clickedContainerId = menu.containerId
        if (slot.index < CursorSafety.playerSlotStart(menu)) {
            ghostArmed = true
            clickedKey = slotKey
        }
        val expectation = CursorGuard.expectAfterClick(carriedKey, slotKey, slot.index)
        val settle = safety.clickSettle(endsEmpty = expectation.carried == null)
        pacer.arm(CursorSafety.arm(mc, expectation, lastNowMs, settle))
        schedule(sampleDelayMs(), next)
    }

    /** Closes the menu after a random delay, as a player would when done, once the close checkpoint passes. */
    protected fun closeAfterDelay() {
        awaitingClose = true
        schedule(sampleDelayMs()) { current, nowMs ->
            // A ghost cannot be confirmed as empty, so the close waits for the server to clear it instead.
            if (isGhostHeld(current.menu)) {
                schedule(0L) { again, later -> waitForGhostThenClose(again, later) }
                return@schedule false
            }
            // The cursor must be empty and the server must agree on that before the menu closes.
            val expectation = CursorGuard.Expectation(carried = null, slotKeys = emptyMap())
            pacer.arm(CursorSafety.arm(Minecraft.getInstance(), expectation, nowMs, safety.closeSettle))
            schedule(0L) { again, _ -> closeMenu(again) }
            false
        }
    }

    /**
     * Closes like vanilla (Esc / inventory key) and ends the run. While an item is on the cursor the menu
     * stays open: the held item is put back first, and the run ends after that.
     */
    protected fun closeMenu(current: AbstractContainerScreen<*>): Boolean {
        val menu = current.menu
        if (menu.carried.isEmpty) {
            CursorSafety.safeClose(Minecraft.getInstance())
            return true
        }
        if (isGhostHeld(menu)) {
            schedule(0L) { again, later -> waitForGhostThenClose(again, later) }
            return false
        }
        startRecovery(menu, lastClickSlot, SERVER_REASON, serverMessage())
        return driveRecovery(current, lastNowMs)
    }

    protected fun say(text: String) = AutoActionQueue.say(text)

    /** Verdict of the pending confirmation, or null when nothing is pending. */
    private fun checkSync(menu: AbstractContainerMenu, nowMs: Long): CursorGuard.Verdict? =
        pacer.observe(nowMs) { keys -> CursorSafety.snapshot(menu, keys) }

    private fun startRecovery(menu: AbstractContainerMenu, source: Int?, reason: String, message: String?) {
        pacer.clear()
        if (cancelReason == null) cancelReason = reason
        recovery = CursorGuard.Recovery(source, CursorSafety.containerPlayerSlots(menu))
        recoveryMessage = message
    }

    /**
     * One put-back step per confirmed click. Ends the run when the cursor is empty, or when no slot
     * could take the item; the menu then stays open for the player.
     */
    private fun driveRecovery(current: AbstractContainerScreen<*>?, nowMs: Long): Boolean {
        val rec = recovery ?: return true
        if (current == null) {
            recovery = null
            say("$label: the menu closed while an item was on your cursor")
            return true
        }
        val menu = current.menu
        if (checkSync(menu, nowMs) == CursorGuard.Verdict.WAITING) return false
        val isEmpty = { slot: Int -> menu.slots.getOrNull(slot)?.item?.isEmpty ?: false }
        when (val action = rec.next(menu.carried.isEmpty, isEmpty)) {
            is CursorGuard.RecoverAction.Click -> {
                val mc = Minecraft.getInstance()
                val carriedKey = InvSortItems.keyOf(menu.carried)
                McUi.clickContainerSlot(mc, menu.containerId, action.slot)
                AutoActionQueue.markProgress()
                // Put-backs always get the full confirmation, whatever the safety level.
                pacer.arm(CursorSafety.arm(mc, CursorGuard.expectAfterClick(carriedKey, null, action.slot), nowMs))
                return false
            }
            CursorGuard.RecoverAction.Done -> {
                recovery = null
                recoveryMessage?.let(::say)
                return true
            }
            CursorGuard.RecoverAction.NoRoom -> {
                recovery = null
                say("$label: couldn't put the held item back (inventory full) - menu left open, place it manually")
                return true
            }
            CursorGuard.RecoverAction.Exhausted -> {
                recovery = null
                say("$label: couldn't put the held item back - menu left open, place it manually")
                return true
            }
        }
    }

    private fun serverMessage(): String = "$label stopped: server didn't confirm a move — your items are safe"

    private fun currentMenuScreen(): AbstractContainerScreen<*>? {
        val shown = McUi.currentScreen(Minecraft.getInstance()) as? AbstractContainerScreen<*> ?: return null
        return shown.takeIf { isMenuTitle(it.title.string) }
    }

    companion object {
        /** Longest wait for a command-opened menu, or for a page turn to show, before giving up. */
        const val RESPONSE_TIMEOUT_MS = 3_000L

        /** Abort reason when the player closes the menu before our first click. */
        const val CLOSED_MESSAGE = "the menu was closed"

        /** Abort reason and chat line when the server does not confirm a click. */
        const val SERVER_REASON = "server didn't confirm a move"

        /** Interval between checks while waiting for a menu to change. */
        const val POLL_MS = 50L

        /** One client tick: the cursor move must render a hover before the click. */
        const val SETTLE_MS = 50L
    }
}
