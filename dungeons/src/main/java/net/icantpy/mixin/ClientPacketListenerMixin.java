package net.icantpy.mixin;

import net.icantpy.Icantpy;
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
        if (Icantpy.INSTANCE.onOutgoingChat(msg)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void icantpy$outgoingCommand(String command, CallbackInfo ci) {
        if (Icantpy.INSTANCE.onOutgoingChat("." + command.trim())) {
            ci.cancel();
        }
    }


    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void icantpy$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        Icantpy.INSTANCE.onBlock(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void icantpy$section(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates((pos, state) -> Icantpy.INSTANCE.onBlock(pos, state));
    }
}
