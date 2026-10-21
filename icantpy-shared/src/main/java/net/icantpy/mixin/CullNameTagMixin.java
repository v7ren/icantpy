package net.icantpy.mixin;

import net.icantpy.api.CullHook;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides name tags of entities whose renderer does not override shouldShowName. Renderers that override it
 * (living, armor stand, item frame) are covered by the sibling mixins. Inert unless {@link CullHook#enabled}.
 */
@Mixin(EntityRenderer.class)
public abstract class CullNameTagMixin {
    @Inject(
        method = "shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$hideNameTag(Entity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (CullHook.hideNameTag(entity)) cir.setReturnValue(false);
    }
}
