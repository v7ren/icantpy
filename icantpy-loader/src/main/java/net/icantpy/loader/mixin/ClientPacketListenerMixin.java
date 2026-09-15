package net.icantpy.loader.mixin;

import net.icantpy.api.IcantpyBridge;
import net.icantpy.loader.IcantpyChat;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 2000)
public class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void icantpy$outgoing(String msg, CallbackInfo ci) {
        if (IcantpyChat.INSTANCE.handleChatMessage(msg)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void icantpy$outgoingCommand(String command, CallbackInfo ci) {
        if (IcantpyChat.INSTANCE.handleChatMessage("/" + command.trim())) {
            ci.cancel();
        }
    }


    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void icantpy$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        IcantpyBridge.INSTANCE.onBlock(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void icantpy$section(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates((pos, state) -> IcantpyBridge.INSTANCE.onBlock(pos, state));
    }
}
