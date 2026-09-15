package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerScreen.class, priority = 1100)
public abstract class MixinLeapScreen {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void icantpy$leapBackground(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo ci
    ) {
        Screen screen = (Screen) (Object) this;
        if (IcantpyBridge.INSTANCE.leapOverlayReady(screen)) {
            graphics.fill(0, 0, screen.width, screen.height, 0x40000000);
            ci.cancel();
        }
    }
}
