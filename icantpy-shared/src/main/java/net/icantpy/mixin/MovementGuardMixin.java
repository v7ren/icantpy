package net.icantpy.mixin;

import net.icantpy.api.MovementHook;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the payload shorten the local player's own movement vector, at the same point vanilla shortens it for
 * sneaking at a ledge. Player.move calls maybeBackOffFromEdge(Vec3, MoverType) and then moves by the returned
 * vector, so trimming the return value is exactly "the player walked into a wall" on the client: no packet is
 * sent, cancelled or rewritten. Only the client's own LocalPlayer moving by itself is touched (never other
 * players, never the integrated server's copy, never pistons or shulker pushes).
 * Same descriptor in 26.2 and 26.1.2.
 */
@Mixin(Player.class)
public abstract class MovementGuardMixin {
    @Inject(
        method = "maybeBackOffFromEdge(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/MoverType;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void icantpy$guardMovement(Vec3 movement, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        if (!MovementHook.enabled) return;
        if (type != MoverType.SELF && type != MoverType.PLAYER) return;
        Object self = this;
        if (!(self instanceof LocalPlayer)) return;
        Object adjusted = MovementHook.adjust(self, cir.getReturnValue());
        if (adjusted instanceof Vec3 vec) cir.setReturnValue(vec);
    }
}
