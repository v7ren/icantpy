package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.icantpy.api.IcantpyRuntimeQuery;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera height stays on the vanilla player unless the payload asks to follow a morph
 * entity's eye height. Hitbox and movement dimensions are left unchanged.
 *
 * Freelook replaces the entity view angles that {@code alignWithEntity} feeds into
 * {@code setRotation} and the third-person orbit. Writing Camera after that method
 * returns is too late for the same-frame look.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Shadow
    private Entity entity;

    @Shadow private Vec3 position;
    @Shadow private Matrix4f cachedViewRotMatrix;
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow public abstract Matrix4f getViewRotationMatrix(Matrix4f destination);
    @Shadow private Matrix4f createProjectionMatrixForCulling() { throw new AssertionError(); }
    @Shadow private void prepareCullFrustum(Matrix4fc view, Matrix4f projection, Vec3 position) {
        throw new AssertionError();
    }

    @Redirect(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F")
    )
    private float icantpy$viewYaw(Entity entity, float partialTick) {
        float[] look = cosmeticLookAngles();
        return look != null ? look[0] : entity.getViewYRot(partialTick);
    }

    @Redirect(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F")
    )
    private float icantpy$viewPitch(Entity entity, float partialTick) {
        float[] look = cosmeticLookAngles();
        return look != null ? look[1] : entity.getViewXRot(partialTick);
    }

    /**
     * Vanilla {@code update} realigns from the player after pick. Apply payload look
     * immediately after that so later frustum/projection math and extract use it.
     */
    @Inject(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER)
    )
    private void icantpy$cosmeticLookAfterAlign(DeltaTracker deltaTracker, CallbackInfo ci) {
        applyCosmeticLook();
    }

    /** Copies look into the render state even if {@code update} was skipped this frame. */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void icantpy$cosmeticLookDirection(CameraRenderState state, float partialTick, CallbackInfo ci) {
        applyCosmeticLook();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void icantpy$morphEyeHeight(CallbackInfo ci) {
        Float override = IcantpyBridge.INSTANCE.cameraEyeHeight(this.entity, this.eyeHeight);
        if (override == null) return;
        this.eyeHeight = override;
        this.eyeHeightOld = override;
    }

    private void applyCosmeticLook() {
        float[] angles = cosmeticLookAngles();
        if (angles == null) return;
        // Only Camera is written. Never call an Entity rotation setter or change the hit result.
        setRotation(angles[0], angles[1]);
        prepareCullFrustum(getViewRotationMatrix(cachedViewRotMatrix),
            createProjectionMatrixForCulling(), position);
    }

    private float[] cosmeticLookAngles() {
        Object result = IcantpyBridge.INSTANCE.query(new IcantpyRuntimeQuery(
            "render.camera.look_direction", 1,
            Map.of("camera", (Camera) (Object) this)
        )).getValue();
        if (!(result instanceof float[] angles) || angles.length != 2
            || !Float.isFinite(angles[0]) || !Float.isFinite(angles[1])) return null;
        return angles;
    }
}
