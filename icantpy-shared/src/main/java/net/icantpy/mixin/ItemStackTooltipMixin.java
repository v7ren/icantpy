package net.icantpy.mixin;

import java.util.List;
import net.icantpy.api.IcantpyBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inserts the UUID-local custom tooltip lines just below the item name. */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void icantpy$customTooltip(
            Item.TooltipContext context,
            Player player,
            TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> cir
    ) {
        List<Component> custom = IcantpyBridge.INSTANCE.customTooltip((ItemStack) (Object) this);
        if (custom.isEmpty()) return;
        List<Component> lines = cir.getReturnValue();
        if (lines.isEmpty()) {
            lines.addAll(custom);
        } else {
            lines.addAll(1, custom);
        }
    }
}
