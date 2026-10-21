package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Camera visuals driven by {@link VisualHook}: third-person distance, through-walls, zoom (FOV multiplier) and
 * aspect ratio. Every hook reads the master switch first and returns vanilla values when it is off.
 *
 * Targets (identical in 26.2 and 26.1.2, verified with javap):
 * <ul>
 *   <li>{@code alignWithEntity(F)V} calls {@code getMaxZoom(F)F} once with max(distance, CAMERA_DISTANCE).
 *       That argument is replaced with {@code cameraDistance}.</li>
 *   <li>{@code getMaxZoom(F)F} returns the requested distance unchanged for {@code cameraThroughWalls}.</li>
 *   <li>{@code calculateFov(F)F} result is multiplied by {@code fovMultiplier} at RETURN. The HUD FOV is not touched.</li>
 *   <li>{@code update(DeltaTracker)V} calls the private {@code Camera.setupPerspective(FFFFF)V}, whose args are
 *       (zNear, zFar, fov, width, height). Width becomes height * {@code aspectRatio}.</li>
 * </ul>
 * This mixin does not touch the alignWithEntity redirects or the update/extract injects in {@link CameraMixin}.
 */
@Mixin(Camera.class)
public abstract class VisualCameraMixin {
    @ModifyArg(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
        index = 0,
        require = 0
    )
    private float icantpy$cameraDistance(float distance) {
        if (!VisualHook.enabled) return distance;
        float wanted = VisualHook.cameraDistance;
        return wanted > 0.0f && Float.isFinite(wanted) ? wanted : distance;
    }

    @Inject(
        method = "getMaxZoom(F)F",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$cameraThroughWalls(float distance, CallbackInfoReturnable<Float> cir) {
        if (VisualHook.enabled && VisualHook.cameraThroughWalls) cir.setReturnValue(distance);
    }

    @Inject(
        method = "calculateFov(F)F",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void icantpy$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!VisualHook.enabled) return;
        float multiplier = VisualHook.fovMultiplier;
        if (multiplier == 1.0f || !Float.isFinite(multiplier) || multiplier <= 0.0f) return;
        float zoomed = cir.getReturnValueF() * multiplier;
        if (!Float.isFinite(zoomed)) return;
        cir.setReturnValue(Math.min(178.9f, Math.max(1.1f, zoomed)));
    }

    @ModifyArgs(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"),
        require = 0
    )
    private void icantpy$aspectRatio(Args args) {
        if (!VisualHook.enabled) return;
        float ratio = VisualHook.aspectRatio;
        if (!(ratio > 0.0f) || !Float.isFinite(ratio)) return;
        float height = args.get(4);
        args.set(3, height * ratio);
    }
}
