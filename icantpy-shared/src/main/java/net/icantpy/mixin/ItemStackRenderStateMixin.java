package net.icantpy.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.icantpy.render.CustomGlintState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements CustomGlintState {
    @Unique
    private Integer icantpy$customGlintColor;

    @Override
    public void icantpy$setCustomGlintColor(Integer color) {
        icantpy$customGlintColor = color;
    }

    @Override
    public Integer icantpy$getCustomGlintColor() {
        return icantpy$customGlintColor;
    }
}
