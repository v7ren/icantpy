package net.icantpy.mixin;

import java.util.Map;
import net.icantpy.api.IcantpyBridge;
import net.icantpy.api.IcantpyDispatchResult;
import net.icantpy.api.IcantpyQueryResult;
import net.icantpy.api.IcantpyRuntimeQuery;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    /**
     * Sensitivity-scaled mouse deltas normally call {@code LocalPlayer.turn}. Freelook
     * consumes them here so Entity yaw/pitch and movement packets stay put.
     */
    @Redirect(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void icantpy$freelookMouseTurn(LocalPlayer player, double yaw, double pitch) {
        Object consumed = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "input.player.turn", 1,
            Map.of("yaw", yaw, "pitch", pitch)
        )).getValue();
        if (Boolean.TRUE.equals(consumed)) return;
        player.turn(yaw, pitch);
    }

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
