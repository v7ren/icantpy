package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the vignette drawn at the screen edges. extractVignette lives on Hud in 26.2 and on Gui in 26.1.2, so both are
 * listed as targets. @Pseudo lets the missing class be skipped, the same way HudCrosshairMixin handles it.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.client.gui.Hud", "net.minecraft.client.gui.Gui"})
public abstract class VisualVignetteMixin {
    @Inject(
        method = "extractVignette(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$noVignette(GuiGraphicsExtractor graphics, Entity entity, CallbackInfo ci) {
        if (VisualHook.enabled && VisualHook.noVignette) ci.cancel();
    }
}
