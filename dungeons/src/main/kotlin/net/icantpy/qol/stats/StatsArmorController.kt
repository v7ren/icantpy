package net.icantpy.qol.stats

import net.icantpy.gui.McUi
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.util.Text
import net.icantpy.api.IcantpyClientActions
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW

object StatsArmor {
    private const val STATS_TITLE = "Stats & Equipment"
    private const val ARM_WINDOW_MS = 4_000L
    private val mc: Minecraft get() = Minecraft.getInstance()
    private var armedUntil: Long = 0L
    private var resolutionMenu: AbstractContainerMenu? = null
    private var cachedSourceSlots: List<Slot> = emptyList()
    private val resolutionCache = mutableMapOf<String, Slot?>()

    fun openAltered() {
        val player = mc.player ?: return
        if (mc.connection == null) return
        if (!TickTimers.settings.statsArmor.enabled) return
        armedUntil = System.currentTimeMillis() + ARM_WINDOW_MS
        IcantpyClientActions.sendCommand("stats")
    }

    fun handleCommand(raw: String): String? {
        val body = net.icantpy.api.IcantpyCommandPrefix.body(raw) ?: return null
        if (!body.equals("stats", ignoreCase = true)) return null
        openAltered()
        return ""
    }

    fun tick() {
        if (armedUntil != 0L && System.currentTimeMillis() > armedUntil) armedUntil = 0L
    }

    fun onDisconnect() {
        armedUntil = 0L
        invalidateResolutionCache()
    }

    fun wantsMenu(title: String): Boolean =
        isStatsTitle(title) && (armedUntil > System.currentTimeMillis() || StatsArmorScreen.instance != null)

    fun openMenu(menu: AbstractContainerMenu, title: Component): Boolean {
        if (!wantsMenu(title.string)) return false
        armedUntil = 0L
        invalidateResolutionCache()
        McUi.setScreen(mc, StatsArmorScreen.live(menu, title))
        return true
    }

    fun sourceSlots(menu: AbstractContainerMenu): List<Slot> {
        if (resolutionMenu === menu) return cachedSourceSlots
        val inventory = mc.player?.inventory ?: return emptyList()
        resolutionMenu = menu
        cachedSourceSlots = menu.slots.filter { it.container === inventory }
        resolutionCache.clear()
        return cachedSourceSlots
    }

    fun invalidateResolutionCache() {
        resolutionMenu = null
        cachedSourceSlots = emptyList()
        resolutionCache.clear()
    }

    fun capture(slot: Slot): ArmorPreset? {
        if (slot.item.isEmpty) return null
        val fingerprint = ItemFingerprint.fromStack(slot.item)
        return ArmorPreset(
            inventorySlot = slot.containerSlot,
            itemId = fingerprint.itemId,
            itemName = fingerprint.itemName,
            itemUuid = fingerprint.itemUuid,
        )
    }

    fun resolve(menu: AbstractContainerMenu, preset: ArmorPreset): Slot? {
        val candidates = sourceSlots(menu)
        if (resolutionCache.containsKey(preset.id)) return resolutionCache[preset.id]
        val resolved = candidates.firstOrNull { slot ->
            slot.containerSlot == preset.inventorySlot && preset.fingerprint().matches(ItemFingerprint.fromStack(slot.item))
        } ?: candidates.firstOrNull { slot ->
            !slot.item.isEmpty && preset.fingerprint().matches(ItemFingerprint.fromStack(slot.item))
        }
        resolutionCache[preset.id] = resolved
        return resolved
    }

    fun swap(menu: AbstractContainerMenu, preset: ArmorPreset): Boolean {
        val slot = resolve(menu, preset) ?: return false
        McUi.clickContainerSlot(mc, menu.containerId, slot.index)
        if (TickTimers.settings.statsArmor.closeAfterSwap) {
            armedUntil = 0L
            mc.player?.closeContainer()
        } else {
            // Hypixel commonly answers a click by sending a fresh Stats & Equipment
            // menu. Keep that response routed back into the custom screen.
            armedUntil = System.currentTimeMillis() + ARM_WINDOW_MS
        }
        invalidateResolutionCache()
        return true
    }

    fun currentArmor(slot: StatsArmorSlot) = mc.player?.getItemBySlot(slot.equipmentSlot)

    fun isStatsTitle(title: String): Boolean = Text.strip(title).trim().equals(STATS_TITLE, ignoreCase = true)

    fun equipmentSlot(slot: StatsArmorSlot): EquipmentSlot = when (slot) {
        StatsArmorSlot.HEAD -> EquipmentSlot.HEAD
        StatsArmorSlot.CHEST -> EquipmentSlot.CHEST
        StatsArmorSlot.LEGS -> EquipmentSlot.LEGS
        StatsArmorSlot.FEET -> EquipmentSlot.FEET
    }
}
