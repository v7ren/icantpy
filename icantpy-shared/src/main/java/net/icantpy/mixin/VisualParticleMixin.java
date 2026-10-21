package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes particle effects spawned by ClientLevel. Sounds are not touched.
 * Block break particles: addDestroyBlockEffect and addBreakingBlockEffect are cancelled when noBlockBreakParticles is set.
 * Explosions: trackExplosionEffects only forwards to ClientExplosionTracker.track, so it is not cancelled there.
 * doAddParticle, the private funnel that every ClientLevel particle goes through, is cancelled only when the particle
 * type is ParticleTypes.EXPLOSION or EXPLOSION_EMITTER and noExplosionParticles is set. Other particles are unchanged.
 */
@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
public abstract class VisualParticleMixin {
    @Inject(
        method = "addDestroyBlockEffect(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$noDestroyParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (VisualHook.enabled && VisualHook.noBlockBreakParticles) ci.cancel();
    }

    @Inject(
        method = "addBreakingBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$noBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        if (VisualHook.enabled && VisualHook.noBlockBreakParticles) ci.cancel();
    }

    @Inject(
        method = "doAddParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$noExplosionParticles(ParticleOptions options, boolean force, boolean decorative,
            double x, double y, double z, double xd, double yd, double zd, CallbackInfo ci) {
        if (!VisualHook.enabled || !VisualHook.noExplosionParticles || options == null) return;
        var type = options.getType();
        if (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER) ci.cancel();
    }
}
