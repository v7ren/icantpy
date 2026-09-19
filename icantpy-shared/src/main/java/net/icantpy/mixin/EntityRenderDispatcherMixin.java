package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the selected client-only entity renderer while retaining the local player's render state. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void icantpy$beginAppearanceOwner(
            Entity entity,
            float partialTick,
            CallbackInfoReturnable<EntityRenderState> cir
    ) {
        IcantpyBridge.INSTANCE.beginAppearanceOwner(entity);
    }

    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private void icantpy$renderProxy(
            Entity entity,
            float partialTick,
            CallbackInfoReturnable<EntityRenderState> cir
    ) {
        Entity proxy = IcantpyBridge.INSTANCE.renderProxy(entity, partialTick);
        if (proxy == null || proxy == entity) return;
        EntityRenderState state = ((EntityRenderDispatcher) (Object) this).extractEntity(proxy, partialTick);
        IcantpyBridge.INSTANCE.adaptRenderState(entity, proxy, state, partialTick);
        cir.setReturnValue(state);
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void icantpy$endAppearanceOwner(
            Entity entity,
            float partialTick,
            CallbackInfoReturnable<EntityRenderState> cir
    ) {
        IcantpyBridge.INSTANCE.endAppearanceOwner();
    }
}
