package net.icantpy.mixin;

import net.icantpy.api.CullHook;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips quad particles at extraction time. Position comes from the fields declared on Particle, read through
 * {@link ParticleAccessor} because @Shadow cannot target inherited fields.
 * Non-quad particle groups (for example item pickup or elder guardian effects) have their own extraction and are not culled.
 * 26.1.2 and 26.2 both declare SingleQuadParticle.extract(QuadParticleRenderState, Camera, float); there is no shared
 * per-particle extraction entry point across all groups, so only quads are culled.
 */
@Mixin(SingleQuadParticle.class)
public abstract class CullParticleMixin {
    @Inject(
        method = "extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;"
            + "Lnet/minecraft/client/Camera;F)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$skipParticle(QuadParticleRenderState state, Camera camera, float partialTick, CallbackInfo ci) {
        ParticleAccessor particle = (ParticleAccessor) (Object) this;
        if (CullHook.skipParticle(particle.icantpy$x(), particle.icantpy$y(), particle.icantpy$z())) ci.cancel();
    }
}
