package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icantpy.api.VisualHook;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips drawing lightning bolt entities. The bolt's sky flash and sound are not in this renderer, so they are unchanged.
 * Same submit signature in 26.2 and 26.1.2; the bridge overload that takes EntityRenderState is not targeted.
 */
@Mixin(net.minecraft.client.renderer.entity.LightningBoltRenderer.class)
public abstract class VisualLightningMixin {
    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$noLightning(LightningBoltRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (VisualHook.enabled && VisualHook.noLightning) ci.cancel();
    }
}
