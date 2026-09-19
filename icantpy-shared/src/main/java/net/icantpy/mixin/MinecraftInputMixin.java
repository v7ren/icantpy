package net.icantpy.mixin;

import java.util.HashMap;
import java.util.Map;
import net.icantpy.api.IcantpyBridge;
import net.icantpy.api.IcantpyDispatchResult;
import net.icantpy.api.IcantpyQueryResult;
import net.icantpy.api.IcantpyRuntimeEvent;
import net.icantpy.api.IcantpyRuntimeQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftInputMixin {
    @Inject(method = "pick(F)V", at = @At("HEAD"), cancellable = true)
    private void icantpy$pick(float partialTick, CallbackInfo ci) {
        IcantpyQueryResult result = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "world.pick",
            1,
            Map.of("partialTick", partialTick)
        ));
        if (result.getResult() != IcantpyDispatchResult.PASS) {
            ci.cancel();
        }
    }

    @Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
    private void icantpy$startAttack(CallbackInfoReturnable<Boolean> cir) {
        IcantpyQueryResult result = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "input.attack.start",
            1,
            Map.of()
        ));
        if (result.getResult() != IcantpyDispatchResult.PASS) {
            cir.setReturnValue(Boolean.TRUE.equals(result.getValue()));
        }
    }

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void icantpy$startUseItem(CallbackInfo ci) {
        IcantpyQueryResult result = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "input.use.start",
            1,
            Map.of()
        ));
        if (result.getResult() != IcantpyDispatchResult.PASS) {
            ci.cancel();
        }
    }

    @Inject(method = "handleKeybinds()V", at = @At("HEAD"))
    private void icantpy$handleKeybinds(CallbackInfo ci) {
        IcantpyBridge.INSTANCE.dispatch(new IcantpyRuntimeEvent("input.keybinds", 1, Map.of()));
    }

    @Inject(
        method = "setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At("HEAD")
    )
    private void icantpy$setScreen(Screen screen, CallbackInfo ci) {
        Map<String, Object> context = new HashMap<>();
        context.put("screen", screen);
        IcantpyBridge.INSTANCE.dispatch(new IcantpyRuntimeEvent("gui.screen", 1, context));
    }
}
