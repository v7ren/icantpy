package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the UUID-local name at the same display boundary used by vanilla item names. */
@Mixin(ItemStack.class)
public abstract class ItemStackNameMixin {
    @Inject(method = {"getHoverName", "getDisplayName"}, at = @At("RETURN"), cancellable = true)
    private void icantpy$customName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        cir.setReturnValue(IcantpyBridge.INSTANCE.customItemName(stack, cir.getReturnValue()));
    }
}
