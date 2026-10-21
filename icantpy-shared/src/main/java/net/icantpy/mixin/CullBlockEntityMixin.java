package net.icantpy.mixin;

import net.icantpy.api.CullHook;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips a block entity by returning null from extraction; both 26.x callers already treat null as "nothing to draw".
 * 26.2 adds a trailing boolean parameter to tryExtractRenderState, 26.1.2 does not, so each signature has its own
 * injection with require = 0. Only the one matching the running game matches.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class CullBlockEntityMixin {
    @Inject(
        method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;"
            + "FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)"
            + "Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$skipBlockEntity262(
            BlockEntity blockEntity,
            float partialTick,
            ModelFeatureRenderer.CrumblingOverlay overlay,
            boolean inFrustum,
            CallbackInfoReturnable<BlockEntityRenderState> cir
    ) {
        if (CullHook.skipBlockEntity(blockEntity)) cir.setReturnValue(null);
    }

    @Inject(
        method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;"
            + "FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)"
            + "Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$skipBlockEntity2612(
            BlockEntity blockEntity,
            float partialTick,
            ModelFeatureRenderer.CrumblingOverlay overlay,
            CallbackInfoReturnable<BlockEntityRenderState> cir
    ) {
        if (CullHook.skipBlockEntity(blockEntity)) cir.setReturnValue(null);
    }
}
