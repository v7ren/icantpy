package net.icantpy.dungeon.terminal

import org.lwjgl.glfw.GLFW

/** One container click to send: the mouse [button], whether it is a middle-click [clone], and its colour [steps]. */
data class TerminalClick(val button: Int, val clone: Boolean, val steps: Int)

/** Decides whether a click on a cell is sent and which click it becomes. Pure. */
object TerminalClickPolicy {
    private const val SERVER_LEFT = 0
    private const val SERVER_RIGHT = 1
    private const val SERVER_MIDDLE = 2

    /** With "block wrong clicks" on, only the cells the solver wants clicked (and, in order, only the next one) pass. */
    fun allows(cell: TerminalCell, type: TerminalType, settings: TerminalSettings): Boolean {
        if (type == TerminalType.MELODY) return allowsMelody(cell, settings)
        if (!settings.blockWrongClicks) return true
        if (cell.state != TerminalCellState.TARGET) return false
        return type != TerminalType.IN_ORDER || cell.order == 0
    }

    /**
     * The slot a click on [cell] goes to. On melody the moving pane and the active button both press the button, so
     * the player can click wherever they are looking; it is still one click on the button slot, as vanilla sends.
     */
    fun slotFor(cell: TerminalCell, solution: TerminalSolution): Int? {
        if (solution.type != TerminalType.MELODY) return cell.slot
        return solution.melodyButton
    }

    /** Only the moving pane and the active button react; with "block wrong clicks" on, only while they are lined up. */
    private fun allowsMelody(cell: TerminalCell, settings: TerminalSettings): Boolean {
        val live = cell.melodyRole == MelodyRole.POINTER ||
            (cell.melodyRole == MelodyRole.BUTTON && cell.state != TerminalCellState.DONE)
        return live && (!settings.blockWrongClicks || cell.state == TerminalCellState.TARGET)
    }

    /**
     * The click for the player's [mouseButton], or null to ignore it. The colour cycle keeps the direction of the
     * player's button: forward goes out as a middle click and backward as a right click, as Odin sends them, because a
     * middle click never makes the client pretend to pick the pane up. Every other terminal uses the configured button.
     */
    fun clickFor(type: TerminalType, settings: TerminalSettings, mouseButton: Int): TerminalClick? {
        if (type == TerminalType.RUBIX) {
            return when (mouseButton) {
                GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> TerminalClick(SERVER_MIDDLE, clone = true, steps = 1)
                GLFW.GLFW_MOUSE_BUTTON_RIGHT -> TerminalClick(SERVER_RIGHT, clone = false, steps = -1)
                else -> null
            }
        }
        if (mouseButton !in GLFW.GLFW_MOUSE_BUTTON_LEFT..GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return null
        return when (settings.clickButton) {
            TerminalClickButton.MIDDLE -> TerminalClick(SERVER_MIDDLE, clone = true, steps = 1)
            TerminalClickButton.LEFT -> TerminalClick(SERVER_LEFT, clone = false, steps = 1)
        }
    }
}
