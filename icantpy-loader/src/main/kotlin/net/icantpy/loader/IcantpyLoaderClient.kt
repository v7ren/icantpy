package net.icantpy.loader

import net.icantpy.api.IcantpyBridge
import net.icantpy.api.IcantpyClientCommands
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
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

        IcantpyLoaderKeys.register()
        IcantpyLoaderEvents.register()
        registerClientCommands()

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("icantpy", "tick-timers")) { graphics, _ ->
            IcantpyBridge.onRenderHud(graphics)
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            IcantpyLoaderKeys.pollClicks()
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

    private fun registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(IcantpyClientCommands.tree("icantpy") { IcantpyChat.handleChatMessage(it) })
            dispatcher.register(IcantpyClientCommands.tree("crypt") { IcantpyChat.handleChatMessage(it) })
        }
    }
}
