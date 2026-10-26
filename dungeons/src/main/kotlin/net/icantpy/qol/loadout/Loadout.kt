package net.icantpy.qol.loadout

import net.icantpy.Icantpy
import net.icantpy.api.IcantpyClientActions
import net.icantpy.gui.McUi
import net.icantpy.qol.auto.AutoActionQueue
import net.icantpy.cosmetics.items.HypixelItemData
import net.icantpy.util.Text
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

object Loadout {
    private const val ARM_WINDOW_MS = 4_000L

    /** The Hypixel menu appends the 36 player-inventory slots after its container slots. */
    private const val PLAYER_INVENTORY_SLOTS = 36
    private val mc: Minecraft get() = Minecraft.getInstance()
    private var armedUntil: Long = 0L

    /** Container dump: armed by `/icantpy loadoutdump`, plus one automatic log-only dump per game session. */
    private val dumpGate = LoadoutDumpGate()

    /** Equipped loadout as last seen in chat or on an icon's lore; reset on disconnect. */
    private val memory = LoadoutMemory()

    /** The loadout number last seen equipped, or null when unknown. Read by Spirit Leap conditions. */
    fun equippedNumber(): Int? = memory.equipped

    /** Last Loadouts page title seen (stripped), used to number icons on the page the screen shows. */
    private var lastTitle: String? = null

    /** Icons on a page 1 that has more pages; used to number icons on later pages. Reset on disconnect. */
    private var iconsPerPageSeen: Int? = null

    /** Until this time a Loadouts menu is never taken over, so a menu re-sent after an auto run stays vanilla. */
    private var suppressInterceptUntil: Long = 0L

    fun openAltered() {
        if (mc.player == null || mc.connection == null) return
        armedUntil = System.currentTimeMillis() + ARM_WINDOW_MS
        IcantpyClientActions.sendCommand("loadout")
    }

    fun handleCommand(raw: String): String? {
        val body = net.icantpy.api.IcantpyCommandPrefix.body(raw) ?: return null
        if (body.equals("loadoutdump", ignoreCase = true)) {
            dumpGate.arm(System.currentTimeMillis())
            return "Loadout dump armed — open /loadout within ${LoadoutDumpGate.ARM_WINDOW_MS / 1000}s"
        }
        if (!body.equals("loadout", ignoreCase = true)) return null
        openAltered()
        return ""
    }

    /** Open Loadouts menu (vanilla or behind [LoadoutScreen]) with its title, or null when none is open. */
    private fun openLoadoutMenu(): Pair<AbstractContainerMenu, String>? {
        LoadoutScreen.instance?.let { return it.menu to it.title.string }
        val screen = McUi.currentScreen(mc) as? AbstractContainerScreen<*> ?: return null
        return screen.menu to screen.title.string
    }

    /** True when any container slot of [menu] holds an item. */
    private fun isPopulated(menu: AbstractContainerMenu): Boolean {
        val containerEnd = containerEnd(menu)
        return menu.slots.any { it.index < containerEnd && !it.item.isEmpty }
    }

    /** Logs every container slot of [menu] to the game log and returns the number of container slots logged. */
    private fun dumpMenu(menu: AbstractContainerMenu, title: String): Int {
        val containerEnd = containerEnd(menu)
        val numbers = loadoutEntries(menu, title).toMap()
        Icantpy.LOGGER.info("[icantpy loadoutdump] title='{}' containerSlots={}", title, containerEnd)
        var logged = 0
        for (slot in menu.slots) {
            if (slot.index >= containerEnd) continue
            logged++
            val stack = slot.item
            if (stack.isEmpty) {
                Icantpy.LOGGER.info("[icantpy loadoutdump] slot={} item=empty", slot.index)
                continue
            }
            val lore = loreOf(slot).map { Text.strip(it) }
            Icantpy.LOGGER.info(
                "[icantpy loadoutdump] slot={} item={} name='{}' number={} filler={} lore={}",
                slot.index,
                BuiltInRegistries.ITEM.getKey(stack.item).toString(),
                Text.strip(stack.hoverName.string),
                numbers[slot.index] ?: "-",
                isFiller(slot),
                lore,
            )
        }
        return logged
    }

    /** Runs the dump gate for the open Loadouts menu, if any. Called from [tick]. */
    private fun checkLoadoutDump(nowMs: Long) {
        val open = openLoadoutMenu()?.takeIf { isLoadoutWindowTitle(it.second) }
        val decision = dumpGate.update(nowMs, menuOpen = open != null, populated = open?.let { isPopulated(it.first) } ?: false)
        if (decision == LoadoutDumpGate.Decision.NONE || open == null) return
        val logged = dumpMenu(open.first, open.second)
        if (decision == LoadoutDumpGate.Decision.DUMP_ARMED) {
            mc.player?.sendSystemMessage(Component.literal("Dumped $logged slots to latest.log"))
        }
    }

    private fun containerEnd(menu: AbstractContainerMenu): Int =
        (menu.slots.size - PLAYER_INVENTORY_SLOTS).coerceAtLeast(0)

    /** Glass panes and other blank filler: no visible name, or a glass pane item. */
    private fun isFiller(slot: Slot): Boolean =
        slot.item.hoverName.string.isBlank() || slot.item.item.descriptionId.contains("glass_pane")

    fun tick() {
        val now = System.currentTimeMillis()
        if (armedUntil != 0L && now > armedUntil) armedUntil = 0L
        checkLoadoutDump(now)
    }

    fun onDisconnect() {
        armedUntil = 0L
        memory.reset()
        lastTitle = null
        iconsPerPageSeen = null
    }

    /** Feed every incoming chat line here (from Icantpy.onIncomingChat). */
    fun onChat(line: String) {
        memory.onChat(line)
    }

    /**
     * Call before sending /loadout for an auto switch. True only when loadout [number] was recently learned as
     * equipped from an unambiguous source. Otherwise the menu is opened and its lore decides.
     */
    fun skipIfAlreadyEquipped(number: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!memory.isFreshlyKnown(number, nowMs)) return false
        AutoActionQueue.say("Auto loadout: Loadout $number already equipped - skipped")
        return true
    }

    /** Icon lore lines with § codes kept; empty when the item has no lore. */
    fun loreOf(slot: Slot): List<String> =
        HypixelItemData.raw(slot.item, DataComponents.LORE)?.lines?.map { it.string }.orEmpty()

    /** True when the icon's lore marks it as the equipped loadout; also records that number in memory. */
    fun isEquippedIcon(slot: Slot, number: Int): Boolean {
        val equipped = LoadoutEquipped.isEquippedLore(loreOf(slot))
        if (equipped) memory.learn(number) else if (memory.isKnownEquipped(number)) memory.forget()
        return equipped
    }

    fun wantsMenu(title: String): Boolean {
        val now = System.currentTimeMillis()
        if (isLoadoutTitle(title)) lastTitle = Text.strip(title).trim()
        return isLoadoutTitle(title) && !AutoActionQueue.isMenuRunActive() && now >= suppressInterceptUntil &&
            (armedUntil > now || LoadoutScreen.instance != null)
    }

    /**
     * Called when an auto loadout run has finished its post-click window. Clears the command arm and
     * suppresses custom-screen takeover for [SUPPRESS_AFTER_RUN_MS], so a late re-sent menu stays vanilla.
     */
    fun onAutoRunFinished(nowMs: Long) {
        armedUntil = 0L
        suppressInterceptUntil = nowMs + SUPPRESS_AFTER_RUN_MS
    }

    fun openMenu(menu: AbstractContainerMenu, title: Component): Boolean {
        if (!wantsMenu(title.string)) return false
        armedUntil = 0L
        McUi.setScreen(mc, LoadoutScreen.live(menu, title))
        return true
    }

    /**
     * Loadout icons on the menu as (slot index, loadout number), sorted by number. Icons are found by lore, so a
     * renamed icon counts; numbers come from explicit "Loadout N" text, else from order on the page. [title] is
     * the Loadouts page title, which gives the page; the last title seen is used when it is omitted.
     */
    fun loadoutEntries(menu: AbstractContainerMenu, title: String? = lastTitle): List<Pair<Int, Int>> {
        val containerEnd = containerEnd(menu)
        val candidates = menu.slots
            .filter { it.index < containerEnd && !it.item.isEmpty && !isFiller(it) }
            .mapNotNull { slot ->
                val name = slot.item.hoverName.string
                val lore = loreOf(slot)
                if (LoadoutIcons.isLoadoutCandidate(name, lore)) LoadoutIcons.Candidate(slot.index, name, lore) else null
            }
        val pageInfo = title?.let { LoadoutPlan.pageOf(it) }
        val page = pageInfo?.first ?: 1
        val multiPage = (pageInfo?.second ?: 1) > 1
        if (page == 1 && multiPage && candidates.isNotEmpty()) iconsPerPageSeen = candidates.size
        val perPage = if (page == 1) candidates.size else iconsPerPageSeen ?: LoadoutIcons.FALLBACK_ICONS_PER_PAGE
        val entries = LoadoutIcons.number(candidates, page, perPage)
        // Lore is authoritative: an icon marked equipped on this menu sets the memory.
        for (candidate in candidates) {
            if (!LoadoutEquipped.isEquippedLore(candidate.lore)) continue
            entries.firstOrNull { it.first == candidate.slotIndex }?.let { memory.learn(it.second) }
        }
        memory.rememberNames(entries.map { (slotIndex, number) -> Text.strip(menu.slots[slotIndex].item.hoverName.string) to number })
        return entries
    }

    /** Icon slots in loadout-number order, so the screen lists 1..N. */
    fun iconSlots(menu: AbstractContainerMenu, title: String? = lastTitle): List<Slot> =
        loadoutEntries(menu, title).mapNotNull { (slotIndex, _) -> menu.slots.getOrNull(slotIndex) }

    fun navigationSlot(menu: AbstractContainerMenu, next: Boolean): Slot? = menu.slots.firstOrNull { slot ->
        if (slot.item.isEmpty || slot.index >= 45) return@firstOrNull false
        val name = Text.strip(slot.item.hoverName.string)
        if (next) name.contains("next page", ignoreCase = true)
        else name.contains("previous page", ignoreCase = true) || name.contains("back", ignoreCase = true)
    }

    fun click(menu: AbstractContainerMenu, slot: Slot): Boolean {
        if (slot.item.isEmpty) return false
        McUi.clickContainerSlot(mc, menu.containerId, slot.index)
        armedUntil = System.currentTimeMillis() + ARM_WINDOW_MS
        return true
    }

    fun isLoadoutTitle(title: String): Boolean =
        Regex("^\\(\\d+\\s*/\\s*\\d+\\)\\s+Loadouts$", RegexOption.IGNORE_CASE)
            .matches(Text.strip(title).trim())

    /** Looser than [isLoadoutTitle]: any title naming Loadout, used to recognize a re-sent menu after a click. */
    fun isLoadoutWindowTitle(title: String): Boolean =
        Text.strip(title).contains("loadout", ignoreCase = true)

    private const val SUPPRESS_AFTER_RUN_MS = 2_000L
}
