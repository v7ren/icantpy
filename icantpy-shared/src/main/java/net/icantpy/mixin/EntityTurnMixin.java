package net.icantpy.mixin;

import java.util.Map;
import net.icantpy.api.IcantpyBridge;
import net.icantpy.api.IcantpyRuntimeQuery;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook consumes mouse turn on the local player only. Entity yaw/pitch are left
 * unchanged so movement packets keep the original facing.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void icantpy$freelookTurn(double yaw, double pitch, CallbackInfo ci) {
        if (!((Object) this instanceof LocalPlayer)) return;
        Object consumed = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "input.player.turn", 1,
            Map.of("yaw", yaw, "pitch", pitch)
        )).getValue();
        if (Boolean.TRUE.equals(consumed)) ci.cancel();
    }
}
