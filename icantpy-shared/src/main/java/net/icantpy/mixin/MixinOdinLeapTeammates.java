package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.odtheking.odin.features.impl.dungeon.LeapMenu", remap = false)
public class MixinOdinLeapTeammates {
    @Inject(
        method = "currentLeapScreen",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void icantpy$hideOdinLeap(CallbackInfoReturnable<Object> cir) {
        if (IcantpyBridge.INSTANCE.hideOdinLeapMenu()) {
            cir.setReturnValue(null);
        }
    }
}
