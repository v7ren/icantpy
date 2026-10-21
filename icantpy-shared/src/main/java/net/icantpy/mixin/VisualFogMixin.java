package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adjusts the fog that FogRenderer.setupFog returns. FogData fields are public and not final (checked with javap),
 * so the returned object is edited at RETURN. noFog pushes every fog distance to a far value.
 * Otherwise fogDistanceScale multiplies the environmental and render distance start/end values, and also the sky and
 * cloud end distances, so the fog scales together. Nothing changes when both settings are off.
 */
@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public abstract class VisualFogMixin {
    private static final float ICANTPY_FAR = 1.0e6f;

    @Inject(
        method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;"
            + "FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
        at = @At("RETURN"),
        require = 0
    )
    private void icantpy$fogAdjust(Camera camera, int renderDistance, DeltaTracker deltaTracker, float partialTick,
            ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!VisualHook.enabled) return;
        FogData data = cir.getReturnValue();
        if (data == null) return;
        if (VisualHook.noFog) {
            data.environmentalStart = ICANTPY_FAR;
            data.environmentalEnd = ICANTPY_FAR;
            data.renderDistanceStart = ICANTPY_FAR;
            data.renderDistanceEnd = ICANTPY_FAR;
            data.skyEnd = ICANTPY_FAR;
            data.cloudEnd = ICANTPY_FAR;
            return;
        }
        float scale = VisualHook.fogDistanceScale;
        if (scale == 1.0f || !(scale > 0.0f)) return;
        data.environmentalStart *= scale;
        data.environmentalEnd *= scale;
        data.renderDistanceStart *= scale;
        data.renderDistanceEnd *= scale;
        data.skyEnd *= scale;
        data.cloudEnd *= scale;
    }
}
