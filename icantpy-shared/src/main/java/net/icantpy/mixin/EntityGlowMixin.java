package net.icantpy.mixin;

import net.icantpy.api.GlowHook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes payload-requested entities render with the vanilla outline glow. Inert unless {@link GlowHook#enabled}. */
@Mixin(Minecraft.class)
public abstract class EntityGlowMixin {
    @Inject(
        method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void icantpy$shouldGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!GlowHook.enabled || entity == null) return;
        if (GlowHook.colorFor(entity.getId()) != GlowHook.NO_GLOW) {
            cir.setReturnValue(true);
        }
    }
}
