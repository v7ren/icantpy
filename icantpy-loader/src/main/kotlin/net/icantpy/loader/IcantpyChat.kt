package net.icantpy.loader

import net.icantpy.api.IcantpyBridge
import net.icantpy.api.IcantpyCommandPrefix
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object IcantpyChat {
    fun handleChatMessage(message: String): Boolean {
        val normalized = IcantpyCommandPrefix.canonical(message) ?: return false
        return when (IcantpyChatCommands.classify(normalized)) {
            IcantpyChatAction.RELOAD -> executeReload()
            IcantpyChatAction.TOGGLE -> executeToggle()
            IcantpyChatAction.UNKNOWN_RELOAD -> {
                chat("Unknown reload command. Use /icantpy reload or /icantpy reload toggle")
                true
            }
            IcantpyChatAction.FORWARD -> {
                if (IcantpyChatCommands.isOpenGuiMessage(normalized)) {
                    IcantpyBridge.openGui()
                    true
                } else {
                    IcantpyBridge.onOutgoingChat(normalized)
                }
            }
        }
    }

    private fun executeToggle(): Boolean {
        val next = IcantpyReloadSettings.toggleSource()
        chat("icantpy reload source: ${next.name}")
        return true
    }

    private fun executeReload(): Boolean {
        return try {
            val version = IcantpyPayloadManager.reload()
            chat("icantpy reloaded ($version)")
            true
        } catch (exception: Exception) {
            chat("icantpy reload failed: ${exception.message ?: exception.javaClass.simpleName}")
            true
        }
    }

    private fun chat(text: String) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(Component.literal(text))
    }
}
