package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
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

    @Inject(
            method = "renderItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void icantpy$customGlint(
            MultiBufferSource.BufferSource buffers,
            OutlineBufferSource outlines,
            SubmitNodeStorage.ItemSubmit submit,
            CallbackInfo ci
    ) {
        if (submit.foilType() != ItemStackRenderState.FoilType.NONE) {
            Integer color = ((CustomGlintSubmit) (Object) submit).icantpy$getCustomGlintColor();
            // Preserve the selected alpha/chroma instead of forcing an opaque tint.
            quadInstance.setColor(color == null ? 0xFFFFFFFF : color);
        }
    }
}
