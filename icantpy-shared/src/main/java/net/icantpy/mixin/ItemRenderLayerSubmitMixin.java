package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.icantpy.render.CustomGlintRenderContext;
import net.icantpy.render.CustomGlintState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import net.minecraft.client.resources.model.geometry.BakedQuad;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemRenderLayerSubmitMixin {
    @Shadow @Final private ItemStackRenderState this$0;

    @Inject(method = "submit", at = @At("HEAD"))
    private void icantpy$beginSubmit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        CustomGlintRenderContext.set(((CustomGlintState) this$0).icantpy$getCustomGlintColor());
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void icantpy$endSubmit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        CustomGlintRenderContext.set(null);
    }
}
