package net.icantpy.cosmetics.morph

import com.mojang.blaze3d.vertex.PoseStack
import net.icantpy.mixin.GameRendererProjectionAccessor
import net.minecraft.client.Minecraft
import net.minecraft.world.effect.MobEffects
import org.joml.Matrix4f

internal object MorphViewProjection {
    fun matrix(mc: Minecraft, partialTick: Float): Matrix4f {
        val accessor = mc.gameRenderer as GameRendererProjectionAccessor
        val state = accessor.`icantpy$renderState`()
        val camera = state.levelRenderState.cameraRenderState
        val effects = PoseStack()
        accessor.`icantpy$bobHurt`(camera, effects)
        if (state.optionsRenderState.bobView) accessor.`icantpy$bobView`(camera, effects)
        val player = mc.player
        val portal = if (player != null) {
            player.oPortalEffectIntensity + (player.portalEffectIntensity - player.oPortalEffectIntensity) * partialTick
        } else 0f
        val nausea = player?.getEffectBlendFactor(MobEffects.NAUSEA, partialTick) ?: 0f
        val scale = state.optionsRenderState.screenEffectScale
        val distortion = maxOf(portal, nausea) * scale * scale
        val spinDegrees = accessor.`icantpy$spinningEffectTime`() + partialTick * accessor.`icantpy$spinningEffectSpeed`()
        return MorphCrosshairProjection.worldMatrix(
            camera.projectionMatrix, effects.last().pose(), camera.viewRotationMatrix, distortion, spinDegrees,
        )
    }
}
