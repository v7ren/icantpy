package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinLeapContainerScreen {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void icantpy$leapRender(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo ci
    ) {
        if (IcantpyBridge.INSTANCE.renderLeapContainerOverlay(
            (Screen) (Object) this,
            graphics,
            mouseX,
            mouseY,
            partialTick
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void icantpy$leapClick(
        MouseButtonEvent event,
        boolean doubleClick,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Boolean handled = IcantpyBridge.INSTANCE.leapMouseClicked(
            (Screen) (Object) this,
            event,
            doubleClick
        );
        if (handled != null) {
            cir.setReturnValue(handled);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void icantpy$leapRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        Boolean handled = IcantpyBridge.INSTANCE.leapMouseReleased(
            (Screen) (Object) this,
            event
        );
        if (handled != null) {
            cir.setReturnValue(handled);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void icantpy$leapKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Boolean handled = IcantpyBridge.INSTANCE.leapKeyPressed(
            (Screen) (Object) this,
            event
        );
        if (handled != null) {
            cir.setReturnValue(handled);
        }
    }
}
