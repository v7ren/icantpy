package net.icantpy.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to the particle position fields declared on {@link Particle}. @Shadow cannot reach fields
 * inherited from a superclass of the target, so subclass mixins read them through this accessor.
 */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x")
    double icantpy$x();

    @Accessor("y")
    double icantpy$y();

    @Accessor("z")
    double icantpy$z();
}
