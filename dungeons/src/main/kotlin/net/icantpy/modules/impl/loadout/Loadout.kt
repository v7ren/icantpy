package net.icantpy.modules.impl.loadout

import net.icantpy.gui.McUi
import net.icantpy.util.Text
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

object Loadout {
    private const val ARM_WINDOW_MS = 4_000L
    private val LOADOUT_SLOT_INDICES = setOf(
        14, 15, 16,
        23, 24, 25,
        32, 33, 34,
        41, 42, 43,
    )
    private val LOADOUT_ICON_NAME = Regex("^Loadout\\s+\\d+$", RegexOption.IGNORE_CASE)
    private val mc: Minecraft get() = Minecraft.getInstance()
    private var armedUntil: Long = 0L

    fun openAltered() {
        if (mc.player == null || mc.connection == null) return
        armedUntil = System.currentTimeMillis() + ARM_WINDOW_MS
        mc.connection?.sendCommand("loadout")
    }

    fun handleCommand(raw: String): String? {
        val body = net.icantpy.api.IcantpyCommandPrefix.body(raw) ?: return null
        if (!body.equals("loadout", ignoreCase = true)) return null
        openAltered()
        return ""
    }

    fun tick() {
        if (armedUntil != 0L && System.currentTimeMillis() > armedUntil) armedUntil = 0L
    }

    fun onDisconnect() {
        armedUntil = 0L
    }

    fun wantsMenu(title: String): Boolean =
        isLoadoutTitle(title) && (armedUntil > System.currentTimeMillis() || LoadoutScreen.instance != null)

    fun openMenu(menu: AbstractContainerMenu, title: Component): Boolean {
        if (!wantsMenu(title.string)) return false
        armedUntil = 0L
        McUi.setScreen(mc, LoadoutScreen.live(menu, title))
        return true
    }

    fun iconSlots(menu: AbstractContainerMenu): List<Slot> = menu.slots.filter { slot ->
        isLoadoutSlot(slot.index) && isLoadoutIconName(slot.item.hoverName.string)
    }

    fun isLoadoutSlot(index: Int): Boolean = index in LOADOUT_SLOT_INDICES

    fun isLoadoutIconName(name: String): Boolean =
        LOADOUT_ICON_NAME.matches(Text.strip(name).trim())

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
}
