package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Redirects vanilla leather rendering to the UUID-local color selected in the editor. */
@Mixin(DyedItemColor.class)
public abstract class DyedItemColorMixin {
    @Inject(method = "getOrDefault", at = @At("RETURN"), cancellable = true)
    private static void icantpy$customLeatherColor(
            ItemStack stack,
            int defaultColor,
            CallbackInfoReturnable<Integer> cir
    ) {
        cir.setReturnValue(IcantpyBridge.INSTANCE.customLeatherColor(stack, cir.getReturnValue()));
    }
}
