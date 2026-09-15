package net.icantpy.api

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object IcantpyClientCommands {
    fun tree(
        name: String,
        handle: (String) -> Boolean = { IcantpyBridge.onOutgoingChat(it) },
    ): LiteralArgumentBuilder<FabricClientCommandSource> {
        val root = LiteralArgumentBuilder.literal<FabricClientCommandSource>(name)
            .executes { dispatch(it.source, handle, IcantpyCommandPrefix.toDotMessage(name)) }
        root.then(
            RequiredArgumentBuilder.argument<FabricClientCommandSource, String>(
                "args",
                StringArgumentType.greedyString(),
            ).executes { ctx ->
                dispatch(
                    ctx.source,
                    handle,
                    IcantpyCommandPrefix.toDotMessage(name, StringArgumentType.getString(ctx, "args")),
                )
            },
        )
        return root
    }

    private fun dispatch(
        source: FabricClientCommandSource,
        handle: (String) -> Boolean,
        message: String,
    ): Int {
        if (handle(message)) return 1
        source.sendError(Component.literal("unknown icantpy command"))
        return 0
    }

}
