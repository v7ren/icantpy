package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icantpy.api.VisualHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person player model visuals for the local player only, driven by {@link VisualHook}. Other players and
 * the entity itself are never changed. Vanilla behaves exactly as before unless {@link VisualHook#enabled} and
 * {@link VisualHook#playerModel} are set.
 *
 * Targets (identical in 26.2 and 26.1.2, verified with javap -s):
 * <ul>
 *   <li>{@code extractRenderState(Avatar, AvatarRenderState, F)V} (public). The erased descriptor uses
 *       {@code net.minecraft.world.entity.Avatar}, not the bridge's LivingEntity. Injected at RETURN, after
 *       {@code LivingEntityRenderer.extractRenderState} has filled the state. Vanilla does
 *       {@code yRot = wrapDegrees(headYaw - bodyRot)} and, when {@code isUpsideDown}, negates {@code xRot} and
 *       {@code yRot}.</li>
 *   <li>{@code scale(AvatarRenderState, PoseStack)V} (protected). Injected at RETURN and applies the
 *       {@code modelScale} values after vanilla scaling.</li>
 * </ul>
 *
 * Local player check: {@code state.id} (entity id, set from {@code Avatar.getId()}) equals
 * {@code Minecraft.getInstance().player.getId()}.
 */
@Mixin(AvatarRenderer.class)
public abstract class VisualPlayerModelMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
            + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("RETURN"),
        require = 0
    )
    private void icantpy$playerState(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (!VisualHook.enabled || !VisualHook.playerModel) return;
        if (!icantpy$isLocalPlayer(state)) return;

        if (VisualHook.dinnerbone && !state.isUpsideDown) {
            // Same flip vanilla applies to upside-down entities: negate both rotations, then mark it flipped
            // so the model is drawn upside down.
            state.isUpsideDown = true;
            state.xRot = -state.xRot;
            state.yRot = -state.yRot;
        }

        float spin = VisualHook.spinDegreesPerTick;
        if (spin > 0.0f && Float.isFinite(spin)) {
            // Body turns with time. The head stays aligned with the body (yRot = 0) so it spins as one piece
            // instead of looking off to a fixed world direction. Only this visual state is changed.
            state.bodyRot = (state.ageInTicks * spin) % 360.0f;
            state.yRot = 0.0f;
        }
    }

    @Inject(
        method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At("RETURN"),
        require = 0
    )
    private void icantpy$playerScale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (!VisualHook.enabled || !VisualHook.playerModel) return;
        float sx = VisualHook.modelScaleX;
        float sy = VisualHook.modelScaleY;
        float sz = VisualHook.modelScaleZ;
        if (!Float.isFinite(sx) || !Float.isFinite(sy) || !Float.isFinite(sz)) return;
        if (sx == 1.0f && sy == 1.0f && sz == 1.0f) return;
        if (!icantpy$isLocalPlayer(state)) return;
        poseStack.scale(sx, sy, sz);
    }

    private static boolean icantpy$isLocalPlayer(AvatarRenderState state) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        LocalPlayer player = client.player;
        return player != null && state.id == player.getId();
    }
}
