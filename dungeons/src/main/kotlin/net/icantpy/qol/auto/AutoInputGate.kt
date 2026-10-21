package net.icantpy.qol.auto

import net.icantpy.Icantpy
import net.icantpy.qol.invsort.InvSortController
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

/**
 * Applies [AutoInputLock] to the player's input on screens an auto run uses.
 *
 * Two layers report the same physical press: the per-screen Fabric events ([allow]) and the payload's
 * HEAD-cancellable container hooks ([mixinVerdict]). The first report decides; a second report of the
 * same press reuses that decision, so one Esc never counts twice toward the double-Esc cancel.
 * Holds the Esc timing and hint state.
 */
object AutoInputGate {
    const val HINT_TEXT = "Auto action running - Esc twice to cancel"
    private const val HINT_DURATION_NANOS = 1_500_000_000L
    private const val HINT_Y = 6
    private const val HINT_COLOR = 0xFFFFD27F.toInt()
    private const val REASON_CLICK = "mouse button pressed"
    private const val REASON_ESC = "Esc pressed"
    private const val REASON_DOUBLE_ESC = "Esc pressed twice"

    private var lastEscNanos: Long? = null
    private var hintUntilNanos = 0L
    private var lastPress: AutoInputLock.Press? = null
    private var lastInstance: Any? = null
    private var lastDeliver = true
    private var loggedThisRun = false

    /**
     * True when the input may reach the screen. [instance] is the platform event object when known, so
     * the second layer can recognise the same press. [consumedByScreen] runs for presses the screen's own
     * controls may take (for example the inventory sort buttons); it is skipped while the lock swallows input.
     */
    fun allow(
        event: AutoInputEvent,
        instance: Any? = null,
        consumedByScreen: () -> Boolean = { false },
    ): Boolean {
        val nowNanos = System.nanoTime()
        val sameInstance = instance != null && instance === lastInstance
        if (AutoInputLock.isDuplicatePress(lastPress, event, nowNanos, sameInstance)) return lastDeliver
        val deliver = decide(event, nowNanos, consumedByScreen)
        lastPress = AutoInputLock.Press(event, nowNanos)
        lastInstance = instance
        lastDeliver = deliver
        return deliver
    }

    /**
     * Result for a HEAD-cancellable container hook: true when the press was swallowed or cancelled here
     * (vanilla must be skipped), null when the press should continue through normal handling.
     */
    fun mixinVerdict(event: AutoInputEvent, instance: Any?): Boolean? =
        if (allow(event, instance)) null else true

    /** Text to draw this frame, or null when the hint is not showing. */
    fun hintText(nowNanos: Long = System.nanoTime()): String? = HINT_TEXT.takeIf { nowNanos < hintUntilNanos }

    /** Draws the hint at the top of [screen] while it is showing. */
    fun renderHint(screen: Screen, graphics: GuiGraphicsExtractor) {
        val text = hintText() ?: return
        graphics.centeredText(Minecraft.getInstance().font, text, screen.width / 2, HINT_Y, HINT_COLOR)
    }

    private fun decide(event: AutoInputEvent, nowNanos: Long, consumedByScreen: () -> Boolean): Boolean {
        // An armed run with lockUntilDone holds input even when the global lock setting is off.
        val lock = InvSortController.settings.lockInput || AutoActionQueue.armedSortLocksInput()
        val runActive = AutoActionQueue.isAutoRunActive()
        if (!runActive) loggedThisRun = false
        val decision = AutoInputLock.decide(event, runActive, lock, lastEscNanos, nowNanos)
        return when (decision) {
            AutoInputDecision.PASS -> {
                lastEscNanos = null
                !consumedByScreen()
            }
            AutoInputDecision.SWALLOW -> {
                swallow(event, nowNanos)
                logOnce()
                false
            }
            AutoInputDecision.CANCEL -> cancel(event, lock, consumedByScreen)
        }
    }

    private fun logOnce() {
        if (loggedThisRun) return
        loggedThisRun = true
        Icantpy.LOGGER.info("Auto input lock active: player input is held until the auto run finishes")
    }

    private fun swallow(event: AutoInputEvent, nowNanos: Long) {
        if (event != AutoInputEvent.ESCAPE) return
        lastEscNanos = nowNanos
        hintUntilNanos = nowNanos + HINT_DURATION_NANOS
    }

    private fun cancel(event: AutoInputEvent, lock: Boolean, consumedByScreen: () -> Boolean): Boolean {
        lastEscNanos = null
        if (!lock) {
            // Unlocked: a press the screen's own control takes does not cancel; otherwise cancel and pass through.
            if (consumedByScreen()) return false
            AutoActionQueue.cancelRunning(if (event == AutoInputEvent.ESCAPE) REASON_ESC else REASON_CLICK)
            return true
        }
        AutoActionQueue.cancelRunning(REASON_DOUBLE_ESC)
        AutoActionQueue.dropWaiting(REASON_DOUBLE_ESC)
        return false
    }
}
