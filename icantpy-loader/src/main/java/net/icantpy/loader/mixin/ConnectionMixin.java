package net.icantpy.loader.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.icantpy.api.IcantpyInboundPackets;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Connection.class, priority = 3000)
public class ConnectionMixin {
    @Inject(
        method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD")
    )
    private void icantpy$serverTick(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        IcantpyInboundPackets.INSTANCE.capture(packet);
    }
}
