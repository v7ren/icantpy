package net.icantpy.dungeon

import com.google.gson.JsonObject
import net.icantpy.Icantpy
import net.icantpy.compat.McCompat
import net.icantpy.dungeon.timer.TickTimers
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import org.lwjgl.glfw.GLFW

/**
 * Dungeon class-ability helper. Pressing the drop key selects the configured slot so
 * the class ultimate / ability item there fires server-side, then restores the previous
 * hotbar slot. The held item is never thrown. Active only inside dungeons (or debug
 * mode) and only while enabled.
 *
 * Two abilities are supported:
 * - plain drop key (default Q): single-item ability from [DropUltimateSettings.slot].
 * - Ctrl + drop key, or the captured [DropUltimateSettings.stackKey]: whole-stack
 *   ability from [DropUltimateSettings.stackSlot].
 */
object DropUltimate {
    /** How long after a drop to keep watching for the SkyBlock Menu before closing it. */
    private const val MENU_CLOSE_WINDOW_MS = 1500L

    private var closeMenuUntilMs = 0L

    fun reset() {
        closeMenuUntilMs = 0L
    }

    /**
     * Handles a raw keyboard event before Minecraft routes it to key mapping clicks.
     * Returns true when the event was consumed (the swap and drop were performed).
     */
    fun handleKey(event: KeyEvent, action: Int): Boolean {
        val settings = TickTimers.settings.dropUltimate
        if (!settings.enabled) return false
        if (action == GLFW.GLFW_RELEASE) return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (mc.connection == null) return false

        val dropKey = mc.options.keyDrop.matches(event)
        val stackKey = settings.stackKey != DropUltimateSettings.UNBOUND && event.key() == settings.stackKey
        if (!dropKey && !stackKey) return false

        val screenOpen = McCompat.currentScreen(mc) != null
        val inDungeon = DungeonListener.state.inDungeon || TickTimers.settings.debugMode
        if (!inDungeon || screenOpen || player.isSpectator) return false

        // Ctrl+Q and the captured stack key use the stack ability; plain Q uses the single-drop ability.
        val stackAbility = stackKey || mc.hasControlDown()
        if (stackAbility) {
            swapAndDrop(mc, player, settings.stackTargetIndex(), dropAll = true)
            armMenuClose(settings)
            return true
        }
        if (player.inventory.selectedSlot == settings.targetIndex()) {
            // Already holding the single-drop ability slot: let vanilla drop one item.
            armMenuClose(settings)
            return false
        }
        swapAndDrop(mc, player, settings.targetIndex(), dropAll = false)
        armMenuClose(settings)
        return true
    }

    /** Closes the SkyBlock Menu shortly after a drop if the selected slot opened it. */
    fun tick() {
        if (closeMenuUntilMs == 0L) return
        if (System.currentTimeMillis() > closeMenuUntilMs) {
            closeMenuUntilMs = 0L
            return
        }
        val settings = TickTimers.settings.dropUltimate
        if (!settings.enabled || !settings.closeSkyblockMenu) {
            closeMenuUntilMs = 0L
            return
        }
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val screen = McCompat.currentScreen(mc) as? AbstractContainerScreen<*> ?: return
        if (!isSkyblockMenu(screen.title.string)) return
        closeMenuUntilMs = 0L
        player.closeContainer()
    }

    /** Pure decision so the guard can be unit tested without a live client. */
    internal fun shouldSwap(
        enabled: Boolean,
        inDungeon: Boolean,
        screenOpen: Boolean,
        spectator: Boolean,
        previousSlot: Int,
        targetSlot: Int,
    ): Boolean = enabled &&
        inDungeon &&
        !screenOpen &&
        !spectator &&
        previousSlot != targetSlot

    internal fun isSkyblockMenu(title: String): Boolean =
        title.contains(SKYBLOCK_MENU_TITLE, ignoreCase = true)

    private fun armMenuClose(settings: DropUltimateSettings) {
        closeMenuUntilMs = if (settings.closeSkyblockMenu) {
            System.currentTimeMillis() + MENU_CLOSE_WINDOW_MS
        } else {
            0L
        }
    }

    private fun swapAndDrop(mc: Minecraft, player: LocalPlayer, target: Int, dropAll: Boolean) {
        val inventory = player.inventory
        val previous = inventory.selectedSlot
        inventory.selectedSlot = target
        try {
            // The server drops whatever slot its carried-item index points at, so sync
            // the target slot before the drop and restore the old index afterwards.
            mc.connection?.send(ServerboundSetCarriedItemPacket(target))
            if (player.drop(dropAll)) player.swing(InteractionHand.MAIN_HAND)
        } catch (failure: Throwable) {
            Icantpy.LOGGER.warn("Dungeon class-ability drop swap failed", failure)
        } finally {
            inventory.selectedSlot = previous
            try {
                mc.connection?.send(ServerboundSetCarriedItemPacket(previous))
            } catch (failure: Throwable) {
                Icantpy.LOGGER.warn("Failed to restore carried item after class-ability drop", failure)
            }
        }
    }

    private const val SKYBLOCK_MENU_TITLE = "skyblock menu"
}

data class DropUltimateSettings(
    val enabled: Boolean = false,
    val slot: Int = 9,
    val stackSlot: Int = 9,
    val stackKey: Int = UNBOUND,
    val closeSkyblockMenu: Boolean = true,
) {
    /** Zero-based hotbar index for the single-drop ability [slot], clamped to the hotbar range. */
    fun targetIndex(): Int = (slot.coerceIn(MIN_SLOT, MAX_SLOT) - 1)

    /** Zero-based hotbar index for the stack ability [stackSlot], clamped to the hotbar range. */
    fun stackTargetIndex(): Int = (stackSlot.coerceIn(MIN_SLOT, MAX_SLOT) - 1)

    fun toJson(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("enabled", enabled)
        obj.addProperty("slot", slot)
        obj.addProperty("stackSlot", stackSlot)
        obj.addProperty("stackKey", stackKey)
        obj.addProperty("closeSkyblockMenu", closeSkyblockMenu)
        return obj
    }

    companion object {
        const val MIN_SLOT: Int = 1
        const val MAX_SLOT: Int = 9

        /** Matches Minecraft's GLFW_KEY_UNKNOWN used by the other captured binds. */
        const val UNBOUND: Int = -1

        fun fromJson(obj: JsonObject): DropUltimateSettings {
            val defaults = DropUltimateSettings()
            return DropUltimateSettings(
                enabled = if (obj.has("enabled")) obj.get("enabled").asBoolean else defaults.enabled,
                slot = slotOrDefault(obj, "slot", defaults.slot),
                stackSlot = slotOrDefault(obj, "stackSlot", defaults.stackSlot),
                stackKey = if (obj.has("stackKey")) obj.get("stackKey").asInt else defaults.stackKey,
                closeSkyblockMenu = if (obj.has("closeSkyblockMenu")) {
                    obj.get("closeSkyblockMenu").asBoolean
                } else {
                    defaults.closeSkyblockMenu
                },
            )
        }

        private fun slotOrDefault(obj: JsonObject, key: String, default: Int): Int =
            if (obj.has(key)) obj.get(key).asInt.coerceIn(MIN_SLOT, MAX_SLOT) else default
    }
}
