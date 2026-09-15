package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.client.color.item.CustomModelDataSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hypixel animates dyed items through the {@code custom_model_data} colour array, which the
 * {@code minecraft:custom_model_data} tint source reads directly instead of {@code dyed_color}.
 * Redirect that tint through the UUID-local leather colour so a static custom colour wins over
 * the animation. Returns the vanilla tint unchanged for items without an override.
 */
@Mixin(CustomModelDataSource.class)
public abstract class CustomModelDataTintMixin {
    @Inject(method = "calculate", at = @At("RETURN"), cancellable = true)
    private void icantpy$customLeatherTint(
            ItemStack stack,
            ClientLevel level,
            LivingEntity owner,
            CallbackInfoReturnable<Integer> cir
    ) {
        cir.setReturnValue(IcantpyBridge.INSTANCE.customLeatherColor(stack, cir.getReturnValue()));
    }
}
