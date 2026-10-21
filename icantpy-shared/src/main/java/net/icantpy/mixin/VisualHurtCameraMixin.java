package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icantpy.api.VisualHook;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the hurt camera tilt. GameRenderer.bobHurt applies the tilt when the player takes damage. Cancelling at HEAD
 * leaves the pose stack untouched, which matches vanilla with no hurt animation. Same signature in 26.2 and 26.1.2.
 */
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class VisualHurtCameraMixin {
    @Inject(
        method = "bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$noHurtCamera(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (VisualHook.enabled && VisualHook.noHurtCamera) ci.cancel();
    }
}
