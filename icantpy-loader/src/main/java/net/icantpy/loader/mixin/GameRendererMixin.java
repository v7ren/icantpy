package net.icantpy.loader.mixin;

import net.icantpy.api.FrameGenHook;
import net.icantpy.api.IcantpyBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    /** True only between the start and end of one render call that chose a generated frame. */
    @Unique
    private boolean icantpy$skipLevel;

    /** True once this render call actually rendered the level, so the pre-GUI capture holds a world frame. */
    @Unique
    private boolean icantpy$levelRendered;

    @Inject(method = "render", at = @At("HEAD"))
    private void icantpy$world(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) {
            IcantpyBridge.INSTANCE.onRenderWorld();
        }
    }

    /** Frame generation: decide once per frame, at the start of render, whether the level render is replaced. */
    @Inject(method = "render", at = @At("HEAD"))
    private void icantpy$frameGenBegin(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        icantpy$skipLevel = renderLevel && FrameGenHook.skipLevelRender();
        icantpy$levelRendered = false;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void icantpy$frameGenEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        icantpy$skipLevel = false;
        icantpy$levelRendered = false;
    }

    /**
     * Skips only the level render. renderLevel is called from render only, and the flag above is set only while
     * render runs, so GUI, HUD and screens are still drawn every frame.
     */
    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void icantpy$skipGeneratedLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (icantpy$skipLevel) ci.cancel();
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void icantpy$levelDone(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!icantpy$skipLevel) icantpy$levelRendered = true;
    }

    /**
     * Captures the finished world frame right before the GUI is drawn. Capturing at renderLevel's return was too
     * early: entity outlines, post effects and other mods still draw into the main target after it, so generated
     * frames warped an unfinished image and the sky flickered. GuiRenderer.render takes a GpuBufferSlice on 26.1.2
     * and nothing on 26.2; each injection applies only on its own version.
     */
    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"),
        require = 0
    )
    private void icantpy$captureBeforeGui(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (icantpy$levelRendered) FrameGenHook.afterLevelRender();
    }

    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"),
        require = 0
    )
    private void icantpy$captureBeforeGuiLegacy(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (icantpy$levelRendered) FrameGenHook.afterLevelRender();
    }
}
