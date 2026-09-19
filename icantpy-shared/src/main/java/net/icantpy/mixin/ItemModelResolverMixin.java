package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.icantpy.render.CustomGlintState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory and hotbar icons resolve through {@code updateForTopItem} with no living owner.
 * Scope those reads to the local player so custom heads/dyes match the worn-armour path, and
 * carry the selected glint colour with the vanilla render state.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("HEAD"))
    private void icantpy$beginItemOwner(
            ItemStackRenderState state,
            ItemStack stack,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed,
            CallbackInfo ci
    ) {
        Object entity = owner instanceof Entity e ? e : Minecraft.getInstance().player;
        IcantpyBridge.INSTANCE.beginAppearanceOwner(entity);
    }

    @Inject(method = "updateForTopItem", at = @At("RETURN"))
    private void icantpy$captureGlintColor(
            ItemStackRenderState state,
            ItemStack stack,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed,
            CallbackInfo ci
    ) {
        try {
            ((CustomGlintState) state).icantpy$setCustomGlintColor(IcantpyBridge.INSTANCE.customGlintColor(stack));
        } finally {
            IcantpyBridge.INSTANCE.endAppearanceOwner();
        }
    }
}
