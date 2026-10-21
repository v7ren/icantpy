package net.icantpy.mixin;

import net.icantpy.api.GlowHook;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies the outline color for payload-requested glowing entities. Inert unless {@link GlowHook#enabled}. */
@Mixin(Entity.class)
public abstract class EntityGlowTeamColorMixin {
    @Inject(method = "getTeamColor()I", at = @At("HEAD"), cancellable = true)
    private void icantpy$glowTeamColor(CallbackInfoReturnable<Integer> cir) {
        if (!GlowHook.enabled) return;
        int color = GlowHook.colorFor(((Entity) (Object) this).getId());
        if (color != GlowHook.NO_GLOW) {
            cir.setReturnValue(color);
        }
    }
}
