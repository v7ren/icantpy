package net.icantpy.mixin;

import net.icantpy.proxy.ProxyHook;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;

/** The legacy ping bypasses Connection, so it would reveal the real IP; skip it while a proxy is active. */
@Mixin(ServerStatusPinger.class)
public class ProxyLegacyPingMixin {
    @Inject(method = "pingLegacyServer", at = @At("HEAD"), cancellable = true)
    private void icantpy$skipLegacyPing(InetSocketAddress address, ServerAddress rawAddress, ServerData data, EventLoopGroupHolder group, CallbackInfo ci) {
        if (ProxyHook.blocksLegacyPing()) ci.cancel();
    }
}
