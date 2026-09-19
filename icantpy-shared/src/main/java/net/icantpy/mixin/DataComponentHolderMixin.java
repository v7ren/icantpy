package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.icantpy.api.IcantpyHeadProfiles;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Substitutes UUID-local components on read: custom item model, glint, leather dye and
 * player-head textures. Names and lore stay on {@code getHoverName}/{@code addToTooltip} so the
 * editors keep seeing Hypixel's original CUSTOM_NAME/LORE.
 */
@Mixin(DataComponentHolder.class)
public interface DataComponentHolderMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void icantpy$customComponent(DataComponentType<?> type, CallbackInfoReturnable<Object> cir) {
        if (!((Object) this instanceof ItemStack stack)) return;
        if (type == DataComponents.ITEM_MODEL) {
            String model = IcantpyBridge.INSTANCE.customItemModel(stack);
            if (model == null) return;
            Identifier id = Identifier.tryParse(model);
            if (id != null) cir.setReturnValue(id);
        } else if (type == DataComponents.ENCHANTMENT_GLINT_OVERRIDE) {
            Boolean glint = IcantpyBridge.INSTANCE.customGlintOverride(stack);
            if (glint != null) cir.setReturnValue(glint);
        } else if (type == DataComponents.PROFILE && stack.is(Items.PLAYER_HEAD)) {
            String texture = IcantpyBridge.INSTANCE.customHeadTexture(stack);
            if (texture != null) cir.setReturnValue(IcantpyHeadProfiles.fromTexture(texture));
        } else if (type == DataComponents.TRIM) {
            Object trim = IcantpyBridge.INSTANCE.customTrim(stack);
            if (trim != null) cir.setReturnValue(trim);
        } else if (type == DataComponents.DYED_COLOR) {
            DyedItemColor vanilla = (DyedItemColor) cir.getReturnValue();
            int vanillaRgb = vanilla != null ? 0xFF000000 | (vanilla.rgb() & 0xFFFFFF) : 0;
            int colour = IcantpyBridge.INSTANCE.customLeatherColor(stack, vanillaRgb);
            if (colour == vanillaRgb) return;
            cir.setReturnValue(new DyedItemColor(colour & 0xFFFFFF));
        }
    }
}
