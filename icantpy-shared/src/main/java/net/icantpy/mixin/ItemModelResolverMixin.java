package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.icantpy.render.CustomGlintState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries the stack's selected glint color along with the vanilla render state. */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void icantpy$captureGlintColor(
            ItemStackRenderState state,
            ItemStack stack,
            net.minecraft.world.item.ItemDisplayContext displayContext,
            net.minecraft.world.level.Level level,
            net.minecraft.world.entity.ItemOwner owner,
            int seed,
            CallbackInfo ci
    ) {
        ((CustomGlintState) state).icantpy$setCustomGlintColor(IcantpyBridge.INSTANCE.customGlintColor(stack));
    }
}
