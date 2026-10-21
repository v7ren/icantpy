package net.icantpy.mixin;

import io.netty.channel.ChannelPipeline;
import net.icantpy.proxy.ProxyHook;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes outgoing client connections (joins and server-list pings) through the configured proxy. */
@Mixin(Connection.class)
public class ProxyConnectionMixin {
    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void icantpy$installProxy(ChannelPipeline pipeline, CallbackInfo ci) {
        ProxyHook.install(pipeline);
    }
}
