package net.icantpy.loader.mixin;

import net.icantpy.loader.IcantpyChat;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    public abstract String normalizeChatMessage(String message);

    @Inject(
        method = "handleChatInput",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"
        ),
        cancellable = true
    )
    private void icantpy$outgoingCommand(String message, boolean addToRecent, CallbackInfo ci) {
        if (IcantpyChat.INSTANCE.handleChatMessage(this.normalizeChatMessage(message))) {
            ci.cancel();
        }
    }

    @Inject(
        method = "handleChatInput",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"
        ),
        cancellable = true
    )
    private void icantpy$outgoingChat(String message, boolean addToRecent, CallbackInfo ci) {
        if (IcantpyChat.INSTANCE.handleChatMessage(this.normalizeChatMessage(message))) {
            ci.cancel();
        }
    }
}
