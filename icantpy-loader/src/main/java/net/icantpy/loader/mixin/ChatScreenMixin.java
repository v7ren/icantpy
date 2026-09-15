package net.icantpy.loader.mixin;

import net.icantpy.loader.IcantpyChat;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void icantpy$clientCommand(String message, boolean addToRecent, CallbackInfo ci) {
        if (IcantpyChat.INSTANCE.handleChatMessage(message)) {
            ci.cancel();
        }
    }
}
