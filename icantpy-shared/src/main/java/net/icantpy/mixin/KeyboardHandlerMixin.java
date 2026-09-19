package net.icantpy.mixin;

import java.util.Map;
import net.icantpy.api.IcantpyBridge;
import net.icantpy.api.IcantpyDispatchResult;
import net.icantpy.api.IcantpyQueryResult;
import net.icantpy.api.IcantpyRuntimeQuery;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void icantpy$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        IcantpyQueryResult result = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "input.keyboard.key",
            1,
            Map.of("window", window, "action", action, "event", event)
        ));
        if (result.getResult() != IcantpyDispatchResult.PASS) {
            ci.cancel();
        }
    }
}
