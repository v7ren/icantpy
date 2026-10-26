package net.icantpy.qol.auto

import net.icantpy.qol.invsort.InvSortTiming
import net.icantpy.qol.pets.PetMenu
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

/**
 * Summons one pet through the vanilla Pets menu opened by "/pets": finds the icon named [petName] on the
 * current page, turning pages with the "Next Page" arrow when it is not there (at most [MAX_PAGE_FLIPS]
 * times), then left-clicks it and closes. A pet that is already out is left alone, since clicking it
 * would despawn it.
 */
class PetAutoRun(
    val petName: String,
    timing: InvSortTiming,
    moveCursor: Boolean,
    safety: CursorGuard.SafetyLevel,
    startedMs: Long,
) : AutoMenuRun(
    label = "Pet swap",
    isMenuTitle = PetMenu::isPetsTitle,
    openDeadlineMs = startedMs + AutoMenuRun.openWaitMs(),
    timing = timing,
    moveCursor = moveCursor,
    safety = safety,
) {
    private var pageFlips = 0

    /** When the menu first showed no pet icons, or null once it shows some. */
    private var emptySinceMs: Long? = null

    override fun onOpened(screen: AbstractContainerScreen<*>) {
        schedule(sampleDelayMs()) { current, _ -> navigate(current) }
    }

    /** Decides the next move from the menu as it is now: press the icon, or turn a page toward it. */
    private fun navigate(current: AbstractContainerScreen<*>): Boolean {
        val menu = current.menu
        val icons = PetMenu.icons(menu)
        // An empty icon list is usually a menu still filling in: wait before judging it.
        if (icons.isEmpty()) {
            val now = System.currentTimeMillis()
            val since = emptySinceMs ?: now.also { emptySinceMs = it }
            if (now - since < EMPTY_WAIT_MS) {
                schedule(EMPTY_POLL_MS) { again, _ -> navigate(again) }
                return false
            }
            say("Pet swap: no pets in the menu")
            return closeMenu(current)
        }
        emptySinceMs = null
        val icon = PetMenu.find(icons, petName)
        if (icon != null) {
            if (icon.active) {
                say("Pet swap: ${icon.name} is already out")
                return closeMenu(current)
            }
            pressPet()
            return false
        }
        if (PetMenu.nextPageSlot(menu) == null || pageFlips >= MAX_PAGE_FLIPS) {
            say("Pet swap: no pet named \"$petName\"")
            return closeMenu(current)
        }
        pageFlips++
        turnPage(current)
        return false
    }

    private fun turnPage(current: AbstractContainerScreen<*>) {
        val fromPage = PetMenu.pageOf(current.title.string)
        val fromSignature = signature(current.menu)
        pressSlot(
            missing = "no next page to reach $petName",
            resolve = { menu -> PetMenu.nextPageSlot(menu)?.let { menu.slots.getOrNull(it) } },
            next = { screen, nowMs -> awaitPageChange(fromPage, fromSignature, nowMs + RESPONSE_TIMEOUT_MS)(screen, nowMs) },
        )
    }

    /** Waits until the title or the icons show another page, then decides again. Gives up at [deadlineMs]. */
    private fun awaitPageChange(fromPage: Int?, fromSignature: List<String>, deadlineMs: Long): MenuAction = { current, nowMs ->
        val page = PetMenu.pageOf(current.title.string)
        val signature = signature(current.menu)
        val changed = (page != null && fromPage != null && page != fromPage) ||
            (signature.isNotEmpty() && signature != fromSignature)
        when {
            changed -> {
                schedule(sampleDelayMs()) { again, _ -> navigate(again) }
                false
            }
            nowMs >= deadlineMs -> {
                say("Pet swap: the page did not change")
                closeMenu(current)
            }
            else -> {
                schedule(AutoMenuPacing.pollMs(instant, POLL_MS)) { again, later ->
                    awaitPageChange(fromPage, fromSignature, deadlineMs)(again, later)
                }
                false
            }
        }
    }

    private fun pressPet() {
        pressSlot(
            missing = "no pet named \"$petName\"",
            resolve = { menu -> iconSlot(menu) },
            next = { _, _ ->
                requestPostClickWindow()
                closeAfterDelay()
                false
            },
        )
    }

    /** Resolved at click time, so the icon is looked up on the page as it is then. */
    private fun iconSlot(menu: AbstractContainerMenu): Slot? {
        val icon = PetMenu.find(PetMenu.icons(menu), petName) ?: return null
        // A pet that came out while the click was waiting would be despawned by it: leave it.
        if (icon.active) return null
        return menu.slots.getOrNull(icon.slotIndex)
    }

    private fun signature(menu: AbstractContainerMenu): List<String> =
        PetMenu.icons(menu).map { "${it.slotIndex}:${it.level}:${it.name}" }

    companion object {
        /** Page arrows followed while searching for the pet, so a bad menu cannot loop forever. */
        const val MAX_PAGE_FLIPS = 12

        /** Longest wait for icons to appear in a freshly opened menu. */
        const val EMPTY_WAIT_MS = 1_000L

        /** Re-check interval while waiting for icons; never 0, so the wait cannot spin within a tick. */
        const val EMPTY_POLL_MS = 50L
    }
}
