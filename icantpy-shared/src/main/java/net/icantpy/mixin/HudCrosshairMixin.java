package net.icantpy.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.icantpy.api.IcantpyBridge;
import net.icantpy.api.IcantpyRuntimeQuery;
import net.icantpy.render.HudRenderTransform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Version-neutral HUD hook; only one target owns extractCrosshair in each supported game. */
@Pseudo
@Mixin(targets = {"net.minecraft.client.gui.Gui", "net.minecraft.client.gui.Hud"})
public abstract class HudCrosshairMixin {
    // 26.1.2 owns this method on Gui; 26.2 moved it to Hud. The other target has no method.
    @WrapMethod(method = "extractCrosshair", require = 0, remap = false)
    private void icantpy$crosshairOffset(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            Operation<Void> original
    ) {
        Object offset = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
                "render.hud.crosshair.offset", 1,
                Map.of("width", graphics.guiWidth(), "height", graphics.guiHeight(),
                        "partialTick", deltaTracker.getGameTimeDeltaPartialTick(false))
        )).getValue();
        HudRenderTransform.render(graphics.pose(), offset, () -> original.call(graphics, deltaTracker));
    }
}
