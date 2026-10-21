package net.icantpy.mixin;

import net.icantpy.api.CullHook;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Name tag cull for item frames, which override shouldShowName without calling super. Inert unless enabled. */
@Mixin(ItemFrameRenderer.class)
public abstract class CullItemFrameNameTagMixin {
    @Inject(
        method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ItemFrame;D)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$hideNameTag(ItemFrame entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (CullHook.hideNameTag(entity)) cir.setReturnValue(false);
    }
}
