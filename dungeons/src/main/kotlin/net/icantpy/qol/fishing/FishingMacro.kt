package net.icantpy.qol.fishing

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.gui.McUi
import net.icantpy.qol.auto.AutoActionQueue
import net.icantpy.qol.auto.CursorSafety
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items

/**
 * Casts the rod, waits for the bite marker next to your hook and reels in, over and over. The cast and
 * reel are ordinary use-item actions through the game mode, like a right click. It pauses while any screen
 * or an auto menu run is open, which also lets "Swap on cast" (Pet swap) finish its menu, and stops itself
 * when the rod leaves your hand, you disconnect or the cycle stops making progress.
 */
object FishingMacro {
    @Volatile
    var config: FishingMacroConfig = FishingMacroConfig()
        private set

    @Volatile
    var running: Boolean = false
        private set

    private val cycle = FishingCycle()
    private var keyWasDown = false

    fun load() {
        config = FishingMacroStore.load()
        stop(silent = true)
    }

    fun edit(change: (FishingMacroConfig) -> FishingMacroConfig) {
        val next = change(config)
        if (next == config) return
        config = next
        FishingMacroStore.save(next)
    }

    fun toggle() {
        if (running) stop(silent = false) else start()
    }

    fun start() {
        if (running) return
        cycle.reset()
        lastBiteMs = 0L
        running = true
        AutoActionQueue.say("[icantpy] Fishing macro on")
    }

    fun stop(silent: Boolean, reason: String = "") {
        val was = running
        running = false
        cycle.reset()
        lastBiteMs = 0L
        if (was && !silent) AutoActionQueue.say("[icantpy] Fishing macro off" + if (reason.isEmpty()) "" else ": $reason")
    }

    fun reset() = stop(silent = true)

    fun tick() {
        val mc = Minecraft.getInstance()
        pollKey(mc)
        if (!running) return
        val player = mc.player
        if (player == null || mc.level == null) {
            stop(silent = true)
            return
        }
        val now = System.currentTimeMillis()
        val hook = player.fishing
        val bite = hook != null && biteNear(mc, hook.x, hook.y, hook.z)
        if (bite && lastBiteMs == 0L) lastBiteMs = now
        if (McUi.currentScreen(mc) != null || AutoActionQueue.isAutoRunActive()) {
            // A bite while the cast-time pet swap is still running: drop the swap so its menu closes and the
            // reel is not held up. The swap happens again on the next cast.
            if (bite && AutoActionQueue.isAutoRunActive()) interruptSwapForBite()
            return
        }
        if (player.mainHandItem.item != Items.FISHING_ROD) {
            stop(silent = false, reason = "hold a fishing rod")
            return
        }
        // A bite seen while paused is remembered for a moment, since the marker may be gone by now.
        val biteKnown = bite || (lastBiteMs != 0L && now - lastBiteMs < BITE_MEMORY_MS)
        val step = cycle.step(hook != null, biteKnown, now, config.timing())
        if (step.quiet && hook != null) reportNearbyNames(mc, hook.x, hook.y, hook.z)
        when (step.action) {
            FishingAction.CAST, FishingAction.REEL -> useRod(mc)
            FishingAction.NONE -> Unit
        }
        if (step.action == FishingAction.REEL && !step.quiet) reportSlowReel(now)
        if (step.action != FishingAction.NONE || hook == null) {
            lastBiteMs = 0L
            swapInterrupted = false
        }
        if (step.stop != FishingStop.NONE) stop(silent = false, reason = step.stop.message)
    }

    /** First time a bite marker was seen for the current hook; 0 when none is pending. */
    private var lastBiteMs = 0L

    /** Says so in chat when the reel went out well after the marker showed, with the likely reason. */
    private fun reportSlowReel(now: Long) {
        if (lastBiteMs == 0L) return
        val late = now - lastBiteMs
        if (late < SLOW_REEL_MS) return
        val c = config
        val why = if (swapInterrupted) "a pet swap was running" else "marker seen while paused or delay ${c.reelMinMs}-${c.reelMaxMs} ms"
        AutoActionQueue.say("[icantpy] Fishing: reel sent ${late}ms after the bite marker ($why)")
    }

    private var swapInterrupted = false

    private fun interruptSwapForBite() {
        swapInterrupted = true
        AutoActionQueue.cancelRunning("fish bite")
        AutoActionQueue.dropWaiting("fish bite")
        // Close the pet menu now instead of waiting for the run to wind down, so the reel can follow at once.
        val mc = Minecraft.getInstance()
        if (McUi.currentScreen(mc) is AbstractContainerScreen<*>) CursorSafety.safeClose(mc)
    }

    private fun pollKey(mc: Minecraft) {
        val key = config.key
        if (key <= 0) {
            keyWasDown = false
            return
        }
        val down = InputConstants.isKeyDown(mc.window, key)
        val pressed = down && !keyWasDown
        keyWasDown = down
        if (pressed && McUi.currentScreen(mc) == null && mc.player != null) toggle()
    }

    /**
     * The Hypixel bite marker is a named entity ("!!!") at the hook. Any entity with a custom name counts, not
     * only armor stands, and the search is wide because the marker can sit a few blocks off the hook.
     */
    private fun biteNear(mc: Minecraft, x: Double, y: Double, z: Double): Boolean {
        val level = mc.level ?: return false
        val rangeSq = BITE_RANGE * BITE_RANGE
        for (entity in level.entitiesForRendering()) {
            if (!entity.hasCustomName()) continue
            val dx = entity.x - x
            val dy = entity.y - y
            val dz = entity.z - z
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue
            if (entity.customName?.string?.contains(BITE_MARKER) == true) return true
        }
        return false
    }

    /** Says what named things were near the hook when a whole timeout passed with no bite, to tune the marker check. */
    private fun reportNearbyNames(mc: Minecraft, x: Double, y: Double, z: Double) {
        val level = mc.level ?: return
        val names = level.entitiesForRendering().asSequence()
            .filter { it.hasCustomName() }
            .filter { val dx = it.x - x; val dy = it.y - y; val dz = it.z - z; dx * dx + dy * dy + dz * dz <= REPORT_RANGE * REPORT_RANGE }
            .mapNotNull { it.customName?.string?.trim()?.takeIf(String::isNotEmpty) }
            .distinct().take(REPORT_NAMES).toList()
        val seen = if (names.isEmpty()) "nothing named" else names.joinToString(" | ")
        AutoActionQueue.say("[icantpy] Fishing: no bite marker in ${config.noBiteSeconds}s; near the hook: $seen")
    }

    private fun useRod(mc: Minecraft) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        gameMode.useItem(player, InteractionHand.MAIN_HAND)
        player.swing(InteractionHand.MAIN_HAND)
    }

    private const val BITE_MARKER = "!!!"
    private const val BITE_MEMORY_MS = 4_000L
    private const val SLOW_REEL_MS = 450L
    private const val BITE_RANGE = 6.0
    private const val REPORT_RANGE = 8.0
    private const val REPORT_NAMES = 6
}
