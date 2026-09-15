package net.icantpy.loader.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void icantpy$world(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) {
            IcantpyBridge.INSTANCE.onRenderWorld();
        }
    }
}
