package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import net.icantpy.render.CustomGlintRenderContext;
import net.icantpy.render.CustomGlintSubmit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(ItemFeatureRenderer.Submit.class)
public abstract class ItemFeatureSubmitMixin implements CustomGlintSubmit {
    @Unique private Integer icantpy$customGlintColor;

    @Override
    public void icantpy$setCustomGlintColor(Integer color) { icantpy$customGlintColor = color; }

    @Override
    public Integer icantpy$getCustomGlintColor() { return icantpy$customGlintColor; }

    @org.spongepowered.asm.mixin.injection.Inject(method = "<init>", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    private void icantpy$capture(
            PoseStack.Pose pose,
            ItemDisplayContext displayContext,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            int[] tintLayers,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
    ) { icantpy$customGlintColor = CustomGlintRenderContext.get(); }
}
