package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand shader hook for {@link VisualHook#afterHand}. Runs after vanilla {@code GameRenderer.renderItemInHand}
 * finished. Injected at RETURN, so every early return is covered. The provider checks itself whether the
 * first-person hand was actually drawn this frame.
 *
 * Target (identical in 26.2 and 26.1.2, verified with javap -s):
 * {@code private renderItemInHand(CameraRenderState, F, Matrix4fc)V}.
 * Vanilla behaves exactly as before unless {@link VisualHook#enabled} is set.
 */
@Mixin(GameRenderer.class)
public abstract class VisualHandShaderMixin {
    @Inject(
        method = "renderItemInHand",
        at = @At("RETURN"),
        require = 0
    )
    private void icantpy$afterHand(CallbackInfo ci) {
        if (VisualHook.enabled) VisualHook.afterHand(this);
    }
}
