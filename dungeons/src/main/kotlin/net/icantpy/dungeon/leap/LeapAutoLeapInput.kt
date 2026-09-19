package net.icantpy.dungeon.leap

data class LeapAutoLeapInputStep(val state: LeapAutoLeapInputState, val pressed: Boolean)

/** Constant-size key history shared by immediate hooks and the older-loader tick fallback. */
data class LeapAutoLeapInputState(val attackWasDown: Boolean = false) {
    fun poll(isDown: Boolean): LeapAutoLeapInputStep =
        LeapAutoLeapInputStep(copy(attackWasDown = isDown), isDown && !attackWasDown)

    fun handled(): LeapAutoLeapInputState = copy(attackWasDown = true)
}
