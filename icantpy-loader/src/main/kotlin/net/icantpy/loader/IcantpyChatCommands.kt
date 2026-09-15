package net.icantpy.loader

import net.icantpy.api.IcantpyCommandPrefix

enum class IcantpyChatAction {
    RELOAD,
    TOGGLE,
    UNKNOWN_RELOAD,
    FORWARD,
}

object IcantpyChatCommands {
    fun classify(message: String): IcantpyChatAction {
        val normalized = IcantpyCommandPrefix.canonical(message) ?: return IcantpyChatAction.FORWARD
        val body = IcantpyCommandPrefix.body(normalized) ?: return IcantpyChatAction.FORWARD
        if (!body.startsWith("reload", ignoreCase = true)) {
            return IcantpyChatAction.FORWARD
        }
        return when (body.substring("reload".length).trim().lowercase()) {
            "", "now" -> IcantpyChatAction.RELOAD
            "toggle" -> IcantpyChatAction.TOGGLE
            else -> IcantpyChatAction.UNKNOWN_RELOAD
        }
    }

    fun fromSlashCommand(command: String): String = ".${command.trim().trimStart('/')}"

    fun isOpenGuiMessage(message: String): Boolean {
        val normalized = IcantpyCommandPrefix.canonical(message) ?: return false
        if (normalized.trim().equals(".neurename", ignoreCase = true)) return false
        val body = IcantpyCommandPrefix.body(normalized) ?: return false
        return body.isEmpty() || body.equals("gui", ignoreCase = true)
    }
}
