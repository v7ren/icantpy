package net.icantpy.gui

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.gui.configUI.IcantpyConfigScreen
import net.icantpy.gui.configUI.IcantpyHudEditorScreen
import net.icantpy.gui.configUI.ConfigTab
import net.icantpy.gui.configUI.ConfigUiSession
import net.icantpy.gui.neurename.NeurenameScreen
import net.icantpy.modules.impl.appearance.CustomRename
import net.icantpy.modules.impl.appearance.CustomRenameEditorSession
import net.icantpy.modules.impl.timer.TimerHud
import net.icantpy.modules.impl.waypoint.CommandWaypoints
import net.icantpy.modules.impl.stats.StatsArmor
import net.icantpy.modules.impl.loadout.Loadout
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object IcantpyGui {
    private var configKey: KeyMapping? = null
    private var waypointKey: KeyMapping? = null
    private var statsKey: KeyMapping? = null
    private var loadoutKey: KeyMapping? = null
    private val queue = GuiOpenQueue()

    fun register() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("icantpy", "controls"))
        configKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.icantpy.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category,
            ),
        )
        waypointKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.icantpy.add_waypoint",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category,
            ),
        )
        statsKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.icantpy.open_stats",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category,
            ),
        )
        loadoutKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.icantpy.open_loadout",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category,
            ),
        )
        ClientTickEvents.END_CLIENT_TICK.register {
            val key = configKey
            if (key != null) {
                while (key.consumeClick()) toggle()
            }
            val add = waypointKey
            if (add != null) {
                while (add.consumeClick()) {
                    CommandWaypoints.tell(CommandWaypoints.addAtLook())
                }
            }
            val stats = statsKey
            if (stats != null) {
                while (stats.consumeClick()) StatsArmor.openAltered()
            }
            val loadout = loadoutKey
            if (loadout != null) {
                while (loadout.consumeClick()) Loadout.openAltered()
            }
            tick()
        }
    }

    fun toggle() {
        val mc = Minecraft.getInstance()
        queue.requestToggle(isOurScreen(McUi.currentScreen(mc)))
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        when (queue.poll()) {
            GuiPending.None -> Unit
            GuiPending.OpenConfig -> showConfig()
            GuiPending.OpenItemCustomize -> showItemCustomizeScreen()
            GuiPending.Close -> {
                if (isOurScreen(McUi.currentScreen(mc))) {
                    McUi.setScreen(mc, null)
                }
            }
        }
    }

    fun openHudEditor(hud: TimerHud? = null) {
        val mc = Minecraft.getInstance()
        queue.clear()
        McUi.setScreen(mc, IcantpyHudEditorScreen(hud))
        McUi.releaseMouse(mc)
    }

    fun closeIfOpen() {
        queue.clear()
        CustomRenameEditorSession.close()
        val mc = Minecraft.getInstance()
        if (isOurScreen(McUi.currentScreen(mc))) {
            McUi.setScreen(mc, null)
        }
    }

    fun showConfig() {
        val mc = Minecraft.getInstance()
        SkiaContext.initialize()
        McUi.setScreen(mc, IcantpyConfigScreen())
        McUi.releaseMouse(mc)
    }

    fun showRename() {
        ConfigUiSession.select(ConfigTab.RENAME)
        queue.requestOpen()
    }

    /** Captures the held item and queues opening the NEU-style editor on the client thread. */
    fun showItemCustomize() {
        val session = CustomRenameEditorSession.capture(CustomRename.heldItem())
        if (session == null) {
            val held = CustomRename.heldItem()
            val message = when {
                held == null || held.isEmpty -> "hold an item in your main hand"
                else -> "this item has no Hypixel UUID; persistent customization is unavailable"
            }
            Minecraft.getInstance().player?.sendSystemMessage(Component.literal(message))
            return
        }
        queue.requestItemCustomize()
    }

    private fun showItemCustomizeScreen() {
        val mc = Minecraft.getInstance()
        McUi.setScreen(mc, NeurenameScreen())
        McUi.releaseMouse(mc)
    }

    internal fun isOurScreen(screen: Screen?): Boolean =
        screen is IcantpyConfigScreen || screen is IcantpyHudEditorScreen || screen is NeurenameScreen
}
