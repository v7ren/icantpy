package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The world player/entity armour is rendered from {@code getEquipmentIfRenderable}, which copies the
 * worn stack into the entity render state. The item-model path already honours the UUID override, but
 * this copied state can miss it, so bake the override into the copy's {@code dyed_color}. The copy is
 * render-only, so mutating it cannot desync the real inventory stack.
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidEquipmentLeatherMixin {
    @Inject(method = "getEquipmentIfRenderable", at = @At("HEAD"))
    private static void icantpy$beginLeatherOwner(
            LivingEntity entity,
            EquipmentSlot slot,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        IcantpyBridge.INSTANCE.beginAppearanceOwner(entity);
    }

    @Inject(method = "getEquipmentIfRenderable", at = @At("RETURN"))
    private static void icantpy$bakeLeatherColour(
            LivingEntity entity,
            EquipmentSlot slot,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        try {
            ItemStack stack = cir.getReturnValue();
            if (stack == null || stack.isEmpty()) return;
            // Read the backing map so a DYED_COLOR overlay cannot make this look like a no-op.
            DyedItemColor dyed = stack.getComponents().get(DataComponents.DYED_COLOR);
            int vanilla = dyed != null ? 0xFF000000 | (dyed.rgb() & 0xFFFFFF) : 0;
            int colour = IcantpyBridge.INSTANCE.customLeatherColor(stack, vanilla);
            if (colour == vanilla) return;
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(colour & 0xFFFFFF));
        } finally {
            IcantpyBridge.INSTANCE.endAppearanceOwner();
        }
    }
}
