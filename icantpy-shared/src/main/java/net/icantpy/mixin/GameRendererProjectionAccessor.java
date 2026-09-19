package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only access to the same camera effects vanilla uses to render the world. */
@Mixin(GameRenderer.class)
public interface GameRendererProjectionAccessor {
    @Accessor("gameRenderState")
    GameRenderState icantpy$renderState();

    @Accessor("spinningEffectTime")
    float icantpy$spinningEffectTime();

    @Accessor("spinningEffectSpeed")
    float icantpy$spinningEffectSpeed();

    @Invoker("bobHurt")
    void icantpy$bobHurt(CameraRenderState camera, PoseStack pose);

    @Invoker("bobView")
    void icantpy$bobView(CameraRenderState camera, PoseStack pose);
}
