package net.icantpy.mixin;

import net.icantpy.api.CullHook;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Name tag cull for living renderers, which override shouldShowName without calling super. Inert unless enabled. */
@Mixin(LivingEntityRenderer.class)
public abstract class CullLivingNameTagMixin {
    @Inject(
        method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$hideNameTag(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (CullHook.hideNameTag(entity)) cir.setReturnValue(false);
    }
}
