package net.icantpy.qol.invsort

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.gui.McUi
import net.minecraft.client.Minecraft

/**
 * Per-preset hotkeys. Keys are polled once per client tick (edge detected), the same way Freelook
 * reads its key, so no keyboard hook is needed and no key event is consumed or altered.
 */
object InvSortHotkeys {
    private val heldLastTick = mutableSetOf<Int>()

    fun reset() {
        heldLastTick.clear()
    }

    fun tick() {
        val settings = InvSortController.settings
        val hotkeys = settings.presets.map { it.hotkey }.filter { it >= 0 }.distinct()
        val mc = Minecraft.getInstance()
        val heldNow = hotkeys.filterTo(mutableSetOf()) { InputConstants.isKeyDown(mc.window, it) }
        val newlyPressed = hotkeys.firstOrNull { it in heldNow && it !in heldLastTick }
        heldLastTick.clear()
        heldLastTick += heldNow
        if (newlyPressed == null || !settings.enabled) return
        if (McUi.currentScreen(mc) != null || InvSortRunner.isRunning) return
        val index = settings.presets.indexOfFirst { it.hotkey == newlyPressed }
        if (index >= 0 && InvSortOpener.openPlayerInventory(mc)) {
            // The screen is not guaranteed to be current yet, so the run starts on the next tick.
            // Hotkey sorts are automatic: the inventory they opened closes when they finish.
            InvSortController.queueRun(index, closeWhenDone = true, perPress = true)
        }
    }
}
