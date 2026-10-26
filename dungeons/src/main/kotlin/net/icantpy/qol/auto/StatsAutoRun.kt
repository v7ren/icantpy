package net.icantpy.qol.auto

import net.icantpy.qol.invsort.InvSortTiming
import net.icantpy.qol.stats.ArmorPreset
import net.icantpy.qol.stats.StatsArmor
import net.icantpy.qol.stats.StatsArmorScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

/**
 * Equips one Stats armor preset through the vanilla "Stats & Equipment" menu: one left click on the
 * saved item in the inventory, then close. The menu comes from "/stats" without the custom screen.
 */
class StatsAutoRun(
    private val preset: ArmorPreset,
    timing: InvSortTiming,
    moveCursor: Boolean,
    safety: CursorGuard.SafetyLevel,
    startedMs: Long,
) : AutoMenuRun(
    label = "Auto stats",
    isMenuTitle = StatsArmor::isStatsTitle,
    openDeadlineMs = startedMs + AutoMenuRun.openWaitMs(),
    timing = timing,
    moveCursor = moveCursor,
    safety = safety,
) {
    /** A re-sent Stats menu may be taken over by our StatsArmorScreen when armed; the window closes it. */
    override fun isCustomMenuScreen(screen: Screen): Boolean = screen is StatsArmorScreen

    override fun onWindowEnded() {
        StatsArmor.onAutoRunFinished(System.currentTimeMillis())
    }

    override fun onOpened(screen: AbstractContainerScreen<*>) {
        pressSlot(
            missing = "${preset.itemName} is not in your inventory",
            resolve = { menu -> StatsArmor.resolve(menu, preset) },
            next = { _, _ ->
                requestPostClickWindow()
                closeAfterDelay()
                false
            },
        )
    }
}
