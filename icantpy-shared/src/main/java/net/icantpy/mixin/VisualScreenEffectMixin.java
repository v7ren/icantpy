package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the in-screen overlays drawn over the camera: the fire overlay, the water overlay and the block texture
 * shown when the camera is inside a block. Each overlay is drawn by a private static helper of ScreenEffectRenderer,
 * so the cancel happens at HEAD of that helper.
 * 26.2 names the helpers submitFire / submitWater / submitBlockSprite; 26.1.2 names them renderFire / renderWater /
 * renderTex. Both names are listed with their own descriptors, and require = 0 makes the missing names a no-op.
 * The callbacks take only CallbackInfo, so they do not depend on the per-version parameter types
 * (SubmitNodeCollector in 26.2, MultiBufferSource in 26.1.2).
 */
@Mixin(net.minecraft.client.renderer.ScreenEffectRenderer.class)
public abstract class VisualScreenEffectMixin {
    @Inject(
        method = {
            "submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
            "renderFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
                + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
        },
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void icantpy$noFireOverlay(CallbackInfo ci) {
        if (VisualHook.enabled && VisualHook.noFireOverlay) ci.cancel();
    }

    @Inject(
        method = {
            "submitWater(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            "renderWater(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;)V"
        },
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void icantpy$noWaterOverlay(CallbackInfo ci) {
        if (VisualHook.enabled && VisualHook.noWaterOverlay) ci.cancel();
    }

    @Inject(
        method = {
            "submitBlockSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            "renderTex(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"
        },
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void icantpy$noInWallOverlay(CallbackInfo ci) {
        if (VisualHook.enabled && VisualHook.noInWallOverlay) ci.cancel();
    }
}
