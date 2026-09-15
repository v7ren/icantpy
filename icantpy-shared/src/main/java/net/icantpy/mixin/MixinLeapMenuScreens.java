package net.icantpy.mixin;

import net.icantpy.api.IcantpyBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MenuScreens.ScreenConstructor.class, priority = 1100)
public interface MixinLeapMenuScreens<T extends AbstractContainerMenu> {
    @Inject(method = "fromPacket", at = @At("HEAD"), cancellable = true)
    private void icantpy$leapFromPacket(Component name, MenuType<T> type, Minecraft client, int id, CallbackInfo ci) {
        boolean wantsLeap = IcantpyBridge.INSTANCE.wantsLeapMenu(name.getString());
        boolean wantsStats = IcantpyBridge.INSTANCE.wantsStatsMenu(name.getString());
        boolean wantsLoadout = IcantpyBridge.INSTANCE.wantsLoadoutMenu(name.getString());
        if (!wantsLeap && !wantsStats && !wantsLoadout) return;
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        T created = type.create(id, player.getInventory());
        if (!(created instanceof ChestMenu menu)) {
            return;
        }
        player.containerMenu = menu;
        boolean openedLeap = IcantpyBridge.INSTANCE.openLeapMenu(menu, name);
        boolean openedStats = !openedLeap && IcantpyBridge.INSTANCE.openStatsMenu(menu, name);
        boolean openedLoadout = !openedLeap && !openedStats && IcantpyBridge.INSTANCE.openLoadoutMenu(menu, name);
        if (!openedLeap && !openedStats && !openedLoadout) {
            return;
        }
        ci.cancel();
    }
}
