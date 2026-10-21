package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales the swing animation length for the local player only. {@code getCurrentSwingDuration()I} is private in
 * both 26.2 and 26.1.2 (verified with javap). LivingEntity also runs on the integrated server in the same JVM, so the
 * change applies only when {@code this} is the client's LocalPlayer instance. Other entities are never changed.
 */
@Mixin(LivingEntity.class)
public abstract class VisualSwingDurationMixin {
    @Inject(
        method = "getCurrentSwingDuration",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void icantpy$swingDuration(CallbackInfoReturnable<Integer> cir) {
        if (!VisualHook.enabled) return;
        float scale = VisualHook.swingDurationScale;
        if (scale == 1.0f || !(scale > 0.0f) || !Float.isFinite(scale)) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        LocalPlayer local = client.player;
        Object self = this;
        if (local == null || self != local) return;
        int base = cir.getReturnValue();
        cir.setReturnValue(Math.max(1, Math.round(base * scale)));
    }
}
