package net.icantpy.mixin;

import net.icantpy.api.CullHook;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Name tag cull for armor stands, which override shouldShowName without calling super. Inert unless enabled. */
@Mixin(ArmorStandRenderer.class)
public abstract class CullArmorStandNameTagMixin {
    @Inject(
        method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ArmorStand;D)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$hideNameTag(ArmorStand entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (CullHook.hideNameTag(entity)) cir.setReturnValue(false);
    }
}
