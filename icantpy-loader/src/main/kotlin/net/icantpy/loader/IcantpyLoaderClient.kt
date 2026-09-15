package net.icantpy.loader

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.api.IcantpyBridge
import net.icantpy.api.IcantpyClientCommands
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.nio.file.Files

object IcantpyLoaderClient : ClientModInitializer {
    private val LOGGER = LoggerFactory.getLogger("icantpy-loader")

    override fun onInitializeClient() {
        try {
            Files.createDirectories(IcantpyLoaderPaths.runtime())
        } catch (exception: Exception) {
            LOGGER.error("Failed to prepare icantpy runtime directory", exception)
            return
        }
        if (FabricLoader.getInstance().isModLoaded(IcantpyLoaderPaths.PAYLOAD_MOD_ID)) {
            LOGGER.warn("icantpy is also installed in mods/; keep only icantpy-loader.jar and let the loader pull icantpy.jar")
        }

        val category = keyCategory()
        val configKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.icantpy_loader.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category,
            ),
        )
        val waypointKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.icantpy_loader.add_waypoint",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category,
            ),
        )
        val statsKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.icantpy_loader.open_stats",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category,
            ),
        )
        val loadoutKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.icantpy_loader.open_loadout",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category,
            ),
        )
        registerClientCommands()

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("icantpy", "tick-timers")) { graphics, _ ->
            IcantpyBridge.onRenderHud(graphics)
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            while (configKey.consumeClick()) {
                IcantpyBridge.openGui()
            }
            while (waypointKey.consumeClick()) {
                IcantpyBridge.addWaypointAtLook()
            }
            while (statsKey.consumeClick()) {
                IcantpyBridge.openStats()
            }
            while (loadoutKey.consumeClick()) {
                IcantpyBridge.openLoadout()
            }
            IcantpyBridge.onTick()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            IcantpyBridge.onDisconnect()
        }

        try {
            val version = IcantpyPayloadManager.reload()
            LOGGER.info("Started icantpy {} through the loader", version)
        } catch (exception: Exception) {
            LOGGER.error("Failed to start the icantpy payload", exception)
        }
    }

    private fun keyCategory(): KeyMapping.Category {
        return KeyMapping.Category.register(Identifier.fromNamespaceAndPath("icantpy", "controls"))
    }

    private fun registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(IcantpyClientCommands.tree("icantpy") { IcantpyChat.handleChatMessage(it) })
            dispatcher.register(IcantpyClientCommands.tree("crypt") { IcantpyChat.handleChatMessage(it) })
        }
    }
}
