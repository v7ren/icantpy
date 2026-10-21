package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright. lightmap.fsh computes color = max(ambient, nightVisionColor * nightVisionFactor) before adding sky and
 * block light and clamping to 1. Setting nightVisionEffectIntensity to 1 with a white nightVisionColor makes that max
 * white, so every lightmap texel is at full brightness. Gamma (brightness) is left as the player set it.
 * LightmapRenderStateExtractor.extract returns early without recomputing when its own needsUpdate is false, so the
 * values are written at RETURN on every call while fullbright is on, and needsUpdate is set so the texture re-uploads.
 * When fullbright turns off, the next vanilla recompute restores the values; there is no immediate reset.
 */
@Mixin(net.minecraft.client.renderer.LightmapRenderStateExtractor.class)
public abstract class VisualLightmapMixin {
    private static final Vector3fc ICANTPY_WHITE = new Vector3f(1.0f, 1.0f, 1.0f);

    @Inject(
        method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
        at = @At("RETURN"),
        require = 0
    )
    private void icantpy$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (!VisualHook.enabled || !VisualHook.fullbright) return;
        state.nightVisionEffectIntensity = 1.0f;
        state.nightVisionColor = ICANTPY_WHITE;
        state.needsUpdate = true;
    }
}
