package net.icantpy.mixin;

import java.util.List;
import java.util.function.Consumer;
import net.icantpy.api.IcantpyBridge;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Substitutes the item's LORE component output with the UUID-local custom tooltip at the source,
 * before other mods (e.g. SkyHanni's rainbow enchants) or vanilla build the final tooltip. This
 * keeps our custom lore editable while letting other mods re-style the lines they recognise, and
 * cannot duplicate the original lore.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private void icantpy$customLore(
            DataComponentType<?> type,
            Item.TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> consumer,
            TooltipFlag flag,
            CallbackInfo ci
    ) {
        if (type != DataComponents.LORE) return;
        List<Component> custom = IcantpyBridge.INSTANCE.customTooltip((ItemStack) (Object) this);
        if (custom.isEmpty()) return;
        for (Component line : custom) consumer.accept(line);
        ci.cancel();
    }
}
