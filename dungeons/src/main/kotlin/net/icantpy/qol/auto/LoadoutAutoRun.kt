package net.icantpy.qol.auto

import net.icantpy.qol.invsort.InvSortTiming
import net.icantpy.qol.loadout.Loadout
import net.icantpy.qol.loadout.LoadoutEquipped
import net.icantpy.qol.loadout.LoadoutPlan
import net.icantpy.qol.loadout.LoadoutScreen
import net.icantpy.qol.loadout.LoadoutStep
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

/**
 * Switches to one Hypixel loadout: finds the icon named "Loadout N" on the current page, turning
 * pages with the arrow items when it is not there (at most [MAX_PAGE_FLIPS] times), then left-clicks
 * it and closes. Nothing is assumed about slot positions.
 */
class LoadoutAutoRun(
    private val number: Int,
    timing: InvSortTiming,
    moveCursor: Boolean,
    safety: CursorGuard.SafetyLevel,
    startedMs: Long,
) : AutoMenuRun(
    label = "Auto loadout",
    isMenuTitle = Loadout::isLoadoutTitle,
    openDeadlineMs = startedMs + AutoMenuRun.RESPONSE_TIMEOUT_MS,
    timing = timing,
    moveCursor = moveCursor,
    safety = safety,
) {
    private var pageFlips = 0

    /** When the menu first showed no loadout icons, or null once it shows some. */
    private var emptySinceMs: Long? = null

    override fun onOpened(screen: AbstractContainerScreen<*>) {
        schedule(sampleDelayMs()) { current, _ -> navigate(current) }
    }

    /** Hypixel may re-send the Loadouts menu with a refreshed title after the switch: any "Loadout" title counts. */
    override fun isWindowTitle(title: String): Boolean = Loadout.isLoadoutWindowTitle(title)

    /** Our own LoadoutScreen takes over a re-sent menu when armed; it is closed like the vanilla one. */
    override fun isCustomMenuScreen(screen: Screen): Boolean = screen is LoadoutScreen

    override fun onWindowEnded() {
        Loadout.onAutoRunFinished(System.currentTimeMillis())
    }

    /** Decides the next move from the menu as it is now: press the icon, or turn a page toward it. */
    private fun navigate(current: AbstractContainerScreen<*>): Boolean {
        val menu = current.menu
        // An empty icon list is usually a menu still refreshing after a click: wait before judging it.
        if (Loadout.loadoutEntries(menu).isEmpty()) {
            val since = emptySinceMs ?: System.currentTimeMillis().also { emptySinceMs = it }
            if (LoadoutEquipped.shouldWaitForIcons(System.currentTimeMillis() - since)) {
                schedule(EMPTY_POLL_MS) { again, _ -> navigate(again) }
                return false
            }
        } else {
            emptySinceMs = null
        }
        val page = LoadoutPlan.pageOf(current.title.string)?.first ?: run {
            say("Auto loadout: could not read the page number")
            return closeMenu(current)
        }
        val step = LoadoutPlan.pageDirection(
            numbersOnPage = Loadout.loadoutEntries(menu).map { it.second },
            number = number,
            hasNext = Loadout.navigationSlot(menu, next = true) != null,
            hasPrev = Loadout.navigationSlot(menu, next = false) != null,
        )
        return when (step) {
            LoadoutStep.PRESS -> {
                // Already equipped: the click would only draw "already equipped" from Hypixel, so close instead.
                val icon = iconFor(menu)
                if (icon != null && Loadout.isEquippedIcon(icon, number)) {
                    say("Auto loadout: Loadout $number already equipped - skipped")
                    return closeMenu(current)
                }
                pressIcon()
                false
            }
            LoadoutStep.NEXT_PAGE, LoadoutStep.PREVIOUS_PAGE -> {
                if (pageFlips >= MAX_PAGE_FLIPS) {
                    say("Auto loadout: Loadout $number not found")
                    return closeMenu(current)
                }
                pageFlips++
                turnPage(page, forward = step == LoadoutStep.NEXT_PAGE)
                false
            }
            LoadoutStep.MISSING -> {
                say("Auto loadout: Loadout $number not found")
                closeMenu(current)
            }
        }
    }

    private fun turnPage(page: Int, forward: Boolean) {
        pressSlot(
            missing = "no page arrow to reach Loadout $number",
            resolve = { menu -> Loadout.navigationSlot(menu, next = forward) },
            next = { screen, nowMs -> awaitPageChange(page, nowMs + RESPONSE_TIMEOUT_MS)(screen, nowMs) },
        )
    }

    /** Waits until the title shows another page, then decides again. Gives up at [deadlineMs]. */
    private fun awaitPageChange(fromPage: Int, deadlineMs: Long): MenuAction = { current, nowMs ->
        val page = LoadoutPlan.pageOf(current.title.string)?.first
        when {
            page != null && page != fromPage -> {
                schedule(sampleDelayMs()) { again, _ -> navigate(again) }
                false
            }
            nowMs >= deadlineMs -> {
                say("Auto loadout: the page did not change")
                closeMenu(current)
            }
            else -> {
                schedule(AutoMenuPacing.pollMs(instant, POLL_MS)) { again, later -> awaitPageChange(fromPage, deadlineMs)(again, later) }
                false
            }
        }
    }

    private fun pressIcon() {
        pressSlot(
            missing = "Loadout $number not found",
            resolve = { menu -> iconFor(menu) },
            next = { _, _ ->
                requestPostClickWindow()
                closeAfterDelay()
                false
            },
        )
    }

    /** Resolved at click time, so the icon is looked up on the page as it is then. */
    private fun iconFor(menu: AbstractContainerMenu): Slot? {
        val slotIndex = LoadoutPlan.pickByNumber(Loadout.loadoutEntries(menu), number) ?: return null
        return menu.slots.getOrNull(slotIndex)
    }

    companion object {
        /** Page arrows followed while searching for the loadout, so a bad menu cannot loop forever. */
        const val MAX_PAGE_FLIPS = 5

        /** Re-check interval while waiting for icons; never 0, so the wait cannot spin within a tick. */
        const val EMPTY_POLL_MS = 50L
    }
}
