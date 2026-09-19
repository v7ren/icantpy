package net.icantpy.gui

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.gui.config.IcantpyConfigScreen
import net.icantpy.gui.config.IcantpyHudEditorScreen
import net.icantpy.gui.config.ConfigUiSession
import net.icantpy.gui.customize.CustomizeScreen
import net.icantpy.gui.neurename.NeurenameScreen
import net.icantpy.api.IcantpyKeyBindings
import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.CustomRenameEditorSession
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.dungeon.timer.TimerHud
import net.icantpy.qol.waypoint.CommandWaypoints
import net.icantpy.qol.stats.StatsArmor
import net.icantpy.qol.loadout.Loadout
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.loader.api.FabricLoader
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
        IcantpyKeyBindings.put(
            IcantpyKeyBindings.FREELOOK,
            KeyMappingHelper.registerKeyMapping(
                KeyMapping(
                    "key.icantpy.freelook",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_F6,
                    category,
                ),
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
            GuiPending.OpenCustomize -> showCustomizeScreen()
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

    fun requestPayloadReload() {
        TickTimers.persist()
        CustomRename.persist()
        if (!FabricLoader.getInstance().isModLoaded("icantpy_loader")) {
            Minecraft.getInstance().player?.sendSystemMessage(
                Component.literal("icantpy reload needs the loader"),
            )
            return
        }
        closeIfOpen()
        Minecraft.getInstance().execute { invokeLoaderReload() }
    }

    fun closeIfOpen() {
        queue.clear()
        CustomRenameEditorSession.close()
        val mc = Minecraft.getInstance()
        val screen = McUi.currentScreen(mc)
        if (screen is net.icantpy.dungeon.leap.LeapMenuScreen && screen.liveContainerMenu() != null) {
            mc.player?.closeContainer()
        }
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
        ConfigUiSession.selectCosmeticsItems()
        queue.requestOpen()
    }

    fun showShards() {
        queue.clear()
        ConfigUiSession.selectShards()
        showConfig()
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

    /** Opens the Skyblocker-style tabbed customizer (`,icantpy custom`). */
    fun showCustomize() {
        CustomRenameEditorSession.capture(CustomRename.heldItem())
        queue.requestCustomize()
    }

    private fun showCustomizeScreen() {
        val mc = Minecraft.getInstance()
        McUi.setScreen(mc, CustomizeScreen(McUi.currentScreen(mc), false))
        McUi.releaseMouse(mc)
    }

    internal fun isOurScreen(screen: Screen?): Boolean =
        screen is IcantpyConfigScreen || screen is IcantpyHudEditorScreen ||
            screen is NeurenameScreen || screen is CustomizeScreen ||
            screen is net.icantpy.dungeon.leap.LeapMenuEditorScreen || screen is net.icantpy.dungeon.leap.LeapMenuScreen

    private fun invokeLoaderReload() {
        try {
            val clazz = Class.forName("net.icantpy.loader.IcantpyChat")
            val instance = clazz.getField("INSTANCE").get(null)
            clazz.getMethod("handleChatMessage", String::class.java).invoke(instance, ".icantpy reload")
        } catch (exception: Exception) {
            val cause = exception.cause ?: exception
            Minecraft.getInstance().player?.sendSystemMessage(
                Component.literal("icantpy reload failed: ${cause.message ?: cause.javaClass.simpleName}"),
            )
        }
    }
}
