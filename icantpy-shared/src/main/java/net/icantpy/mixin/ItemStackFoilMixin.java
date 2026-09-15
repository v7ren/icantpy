package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the per-item glint override without writing a client-only component into the stack. */
@Mixin(ItemStack.class)
public abstract class ItemStackFoilMixin {
    @Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
    private void icantpy$customFoil(CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        Boolean override = IcantpyBridge.INSTANCE.customGlintOverride(stack);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
