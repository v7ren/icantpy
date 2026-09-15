package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.icantpy.render.CustomGlintSubmit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {
    @Shadow @Final private QuadInstance quadInstance;

    @Inject(method = "prepareFoilSubmit", at = @At("HEAD"))
    private void icantpy$customGlint(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        Integer color = ((CustomGlintSubmit) (Object) submit).icantpy$getCustomGlintColor();
        // Preserve the selected alpha/chroma instead of forcing an opaque tint.
        quadInstance.setColor(color == null ? 0xFFFFFFFF : color);
    }
}
