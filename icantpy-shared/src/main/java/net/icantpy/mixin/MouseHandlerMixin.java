package net.icantpy.mixin;

import java.util.Map;
import net.icantpy.api.IcantpyBridge;
import net.icantpy.api.IcantpyDispatchResult;
import net.icantpy.api.IcantpyQueryResult;
import net.icantpy.api.IcantpyRuntimeQuery;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    // Freelook turn consumption lives in EntityTurnMixin (HEAD of Entity.turn). Redirecting the player turn
    // call inside turnPlayer collided with other mods' MouseHandler mixins and crashed startup.

    @Inject(
        method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void icantpy$mouseButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        IcantpyQueryResult result = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "input.mouse.button",
            1,
            Map.of("window", window, "button", button, "action", action)
        ));
        if (result.getResult() != IcantpyDispatchResult.PASS) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void icantpy$mouseScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        IcantpyQueryResult result = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "input.mouse.scroll",
            1,
            Map.of("window", window, "x", xOffset, "y", yOffset)
        ));
        if (result.getResult() != IcantpyDispatchResult.PASS) {
            ci.cancel();
        }
    }
}
