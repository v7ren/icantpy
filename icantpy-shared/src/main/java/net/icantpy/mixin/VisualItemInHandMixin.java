package net.icantpy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icantpy.api.VisualHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person viewmodel hooks for {@link ItemInHandRenderer}. Vanilla behaves exactly as before unless
 * {@link VisualHook#enabled} is set.
 *
 * <ul>
 *   <li>Item placement: right before {@code renderItem(...)} draws the held item, {@link VisualHook#transformItem}
 *       may adjust the pose stack. The arm side matches vanilla: main hand uses the main arm, off hand the opposite.
 *       The method name differs by version ({@code submitArmWithItem} in 26.2, {@code renderArmWithItem} in 26.1.2),
 *       the descriptor is identical.</li>
 *   <li>Swing: {@code applyItemArmAttackTransform(PoseStack, HumanoidArm, float)V} is cancelled when
 *       {@link VisualHook#transformSwing} returns true.</li>
 *   <li>No equip dip: at the end of {@code tick()} with {@code noEquipReset}, the shown items become the player's
 *       current items and the heights become 1. Vanilla lowers the height to 0 first and only swaps the item below
 *       0.1, so a height pinned at 1 would stop the swap. When hands are busy (eating, drawing, etc.) vanilla is kept.</li>
 * </ul>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class VisualItemInHandMixin {
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;

    @Inject(
        method = {"submitArmWithItem", "renderArmWithItem"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem"
                + "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
        ),
        require = 0
    )
    private void icantpy$itemPlacement(
        AbstractClientPlayer player,
        float partialTick,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equipProgress,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        CallbackInfo ci
    ) {
        if (!VisualHook.enabled) return;
        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        VisualHook.transformItem(poseStack, mainHand, arm == HumanoidArm.RIGHT, swingProgress, equipProgress);
    }

    @Inject(
        method = "applyItemArmAttackTransform",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void icantpy$swing(PoseStack poseStack, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        if (!VisualHook.enabled) return;
        if (VisualHook.transformSwing(poseStack, arm == HumanoidArm.RIGHT, swingProgress)) ci.cancel();
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void icantpy$noEquipReset(CallbackInfo ci) {
        if (!VisualHook.enabled || !VisualHook.noEquipReset) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        LocalPlayer player = client.player;
        if (player == null || player.isHandsBusy()) return;
        this.mainHandItem = player.getMainHandItem();
        this.offHandItem = player.getOffhandItem();
        this.mainHandHeight = 1.0f;
        this.oMainHandHeight = 1.0f;
        this.offHandHeight = 1.0f;
        this.oOffHandHeight = 1.0f;
    }
}
