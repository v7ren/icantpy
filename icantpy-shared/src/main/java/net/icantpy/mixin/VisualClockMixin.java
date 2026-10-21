package net.icantpy.mixin;

import net.icantpy.api.VisualHook;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixed client time of day. The sky, celestial angle and time based environment attributes are sampled by
 * AttributeTrackSampler, which reads the clock through the ClockManager interface. On the client that is
 * ClientClockManager.getTotalTicks(Holder). Only the overworld clock (WorldClocks.OVERWORLD) is changed, so other clocks
 * are not affected. The day count is kept: the result is (total / 24000) * 24000 + fixedDayTime.
 * ClientClockManager is client-only, so the integrated server's ServerClockManager and ServerLevel stay vanilla.
 */
@Mixin(net.minecraft.client.ClientClockManager.class)
public abstract class VisualClockMixin {
    private static final long ICANTPY_DAY_TICKS = 24000L;

    @Inject(
        method = "getTotalTicks(Lnet/minecraft/core/Holder;)J",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void icantpy$fixedDayTime(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        long fixed = VisualHook.fixedDayTime;
        if (!VisualHook.enabled || fixed < 0L) return;
        if (clock == null || !clock.is(WorldClocks.OVERWORLD)) return;
        long total = cir.getReturnValue();
        long day = total / ICANTPY_DAY_TICKS;
        cir.setReturnValue(day * ICANTPY_DAY_TICKS + (fixed % ICANTPY_DAY_TICKS));
    }
}
