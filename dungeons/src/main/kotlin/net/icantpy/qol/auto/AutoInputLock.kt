package net.icantpy.qol.auto

/**
 * What the player did on a screen an auto action is using. [MOUSE_PRESS] is a button press that starts
 * a click; [ESCAPE] is the Esc key press; [OTHER] covers releases, scrolls, other keys and typed text.
 */
enum class AutoInputEvent { MOUSE_PRESS, ESCAPE, OTHER }

/** SWALLOW drops the input and keeps the run; CANCEL stops the run; PASS delivers the input untouched. */
enum class AutoInputDecision { SWALLOW, CANCEL, PASS }

/**
 * Free of Minecraft types. Decides one input while auto actions may be running.
 *
 * - No run active: PASS.
 * - Lock off: a click or Esc CANCELS the run and reaches the screen, as before. Other input passes.
 * - Lock on: input is SWALLOWED so the player cannot move items or close the screen. The second Esc
 *   within [DOUBLE_ESC_WINDOW_NANOS] of the previous swallowed Esc CANCELS the run instead.
 *
 * [lastEscNanos] is the time of the last Esc that was swallowed, or null. Times are System.nanoTime values.
 */
object AutoInputLock {
    /** Two reports of one physical press (Fabric screen event and mixin HEAD hook) land within this window. */
    const val DUPLICATE_PRESS_WINDOW_NANOS = 25_000_000L

    /** The last input that reached the gate: its kind and time. */
    data class Press(val event: AutoInputEvent, val atNanos: Long)

    /**
     * True when [event] is the same physical press already decided by another input layer. The same
     * event object always counts; an Esc press counts when it lands inside [DUPLICATE_PRESS_WINDOW_NANOS]
     * of the previous press, so the second layer never counts one Esc twice toward the double-Esc timer.
     */
    fun isDuplicatePress(previous: Press?, event: AutoInputEvent, nowNanos: Long, sameInstance: Boolean): Boolean {
        if (previous == null) return false
        if (sameInstance) return true
        if (event != AutoInputEvent.ESCAPE || previous.event != AutoInputEvent.ESCAPE) return false
        val elapsed = nowNanos - previous.atNanos
        return elapsed in 0..DUPLICATE_PRESS_WINDOW_NANOS
    }

    const val DOUBLE_ESC_WINDOW_NANOS = 600_000_000L

    fun decide(
        event: AutoInputEvent,
        runActive: Boolean,
        lockInput: Boolean,
        lastEscNanos: Long?,
        nowNanos: Long,
    ): AutoInputDecision {
        if (!runActive) return AutoInputDecision.PASS
        if (!lockInput) return unlockedDecision(event)
        if (event != AutoInputEvent.ESCAPE) return AutoInputDecision.SWALLOW
        return if (isSecondEsc(lastEscNanos, nowNanos)) AutoInputDecision.CANCEL else AutoInputDecision.SWALLOW
    }

    private fun unlockedDecision(event: AutoInputEvent): AutoInputDecision = when (event) {
        AutoInputEvent.MOUSE_PRESS, AutoInputEvent.ESCAPE -> AutoInputDecision.CANCEL
        AutoInputEvent.OTHER -> AutoInputDecision.PASS
    }

    private fun isSecondEsc(lastEscNanos: Long?, nowNanos: Long): Boolean {
        if (lastEscNanos == null) return false
        val elapsed = nowNanos - lastEscNanos
        return elapsed in 0..DOUBLE_ESC_WINDOW_NANOS
    }
}
