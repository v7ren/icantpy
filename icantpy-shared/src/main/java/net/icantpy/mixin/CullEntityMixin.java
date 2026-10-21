package net.icantpy.mixin;

import net.icantpy.api.CullHook;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets payloads skip whole entities (and their shadows, which are extracted with them) before extraction.
 * Same erased descriptor in 26.2 and 26.1.2. Inert unless {@link CullHook#enabled}.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class CullEntityMixin {
    @Inject(
        method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$skipEntity(
            Entity entity,
            Frustum frustum,
            double x,
            double y,
            double z,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (CullHook.skipEntity(entity)) cir.setReturnValue(false);
    }
}
